/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 给 agent 看的笔记"块视图"：每块一个 id + 内容指纹 hash + Markdown 文本。agent 按块 id 编辑，
 * 带上读取时的 hash，服务端据此发现"读取之后用户又改过这块"并整批拒绝。
 * Editor.js 用块自身 id；Markdown 笔记按空行切段落（围栏代码块内的空行不切），id 由序号+hash 组成。
 */
final class AgentNoteBlocks {

    record Block(String id, String type, String hash, String text, boolean editable) {}

    record MarkdownSegment(int start, int end, String text) {}

    static final Set<String> EDITABLE_TYPES =
            Set.of("paragraph", "header", "list", "checklist", "code", "quote", "table", "image", "delimiter", "math");

    private AgentNoteBlocks() {}

    static List<Block> editorjsBlocks(String content, ObjectMapper om) {
        List<Block> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;
        JsonNode blocks;
        try {
            blocks = om.readTree(content).path("blocks");
        } catch (Exception e) {
            return result;
        }
        int i = 0;
        for (JsonNode block : blocks) {
            String type = block.path("type").asText();
            String id = block.path("id").asText("b" + i);
            boolean editable = EDITABLE_TYPES.contains(type);
            result.add(new Block(id, type, editorjsBlockHash(block, om),
                    editable ? toMarkdown(block) : placeholder(type, block.path("data")), editable));
            i++;
        }
        return result;
    }

    static String editorjsBlockHash(JsonNode block, ObjectMapper om) {
        try {
            return hash(block.path("type").asText() + "|" + om.writeValueAsString(block.path("data")));
        } catch (Exception e) {
            return hash(block.toString());
        }
    }

    static List<MarkdownSegment> markdownSegments(String content) {
        List<MarkdownSegment> result = new ArrayList<>();
        if (content == null) return result;
        int len = content.length();
        int pos = 0;
        int segStart = -1;
        String fence = null;
        while (pos < len) {
            int nl = content.indexOf('\n', pos);
            int lineEnd = nl < 0 ? len : nl;
            String trimmed = content.substring(pos, lineEnd).trim();
            if (fence != null) {
                if (trimmed.startsWith(fence)) fence = null;
            } else if (trimmed.isEmpty()) {
                if (segStart >= 0) {
                    result.add(segment(content, segStart, pos));
                    segStart = -1;
                }
            } else {
                if (segStart < 0) segStart = pos;
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) fence = trimmed.substring(0, 3);
            }
            pos = nl < 0 ? len : nl + 1;
        }
        if (segStart >= 0) result.add(segment(content, segStart, len));
        return result;
    }

    static List<Block> markdownBlocks(String content) {
        List<Block> result = new ArrayList<>();
        List<MarkdownSegment> segments = markdownSegments(content);
        for (int i = 0; i < segments.size(); i++) {
            String text = segments.get(i).text();
            String h = hash(text);
            result.add(new Block(markdownBlockId(i, h), markdownType(text), h, text, true));
        }
        return result;
    }

    static String markdownBlockId(int index, String hash) {
        return "m" + (index + 1) + "-" + hash.substring(0, 4);
    }

    static String hash(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String toMarkdown(JsonNode block) {
        JsonNode data = block.path("data");
        return switch (block.path("type").asText()) {
            case "paragraph" -> htmlToMarkdown(data.path("text").asText(""));
            case "header" -> "#".repeat(Math.max(1, Math.min(6, data.path("level").asInt(2)))) + " "
                    + htmlToMarkdown(data.path("text").asText(""));
            case "list" -> listToMarkdown(data.path("items"), "ordered".equals(data.path("style").asText()), 0);
            case "checklist" -> checklistToMarkdown(data.path("items"));
            case "code" -> "```\n" + data.path("code").asText("") + "\n```";
            case "quote" -> "> " + htmlToMarkdown(data.path("text").asText("")).replace("\n", "\n> ");
            case "table" -> tableToMarkdown(data.path("content"));
            case "image" -> "![" + AgentImageLocator.stripHtml(data.path("caption").asText("")) + "]("
                    + imageUrl(data) + ")";
            case "delimiter" -> "---";
            case "math" -> "$$\n" + data.path("tex").asText("") + "\n$$";
            default -> "";
        };
    }

    static String htmlToMarkdown(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<(b|strong)\\b[^>]*>(.*?)</\\1>", "**$2**");
        s = s.replaceAll("(?is)<(i|em)\\b[^>]*>(.*?)</\\1>", "*$2*");
        s = s.replaceAll("(?is)<code\\b[^>]*>(.*?)</code>", "`$1`");
        s = s.replaceAll("(?is)<s\\b[^>]*>(.*?)</s>", "~~$1~~");
        s = s.replaceAll("(?is)<mark\\b[^>]*>(.*?)</mark>", "==$1==");
        s = s.replaceAll("(?is)<a\\b[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "[$2]($1)");
        s = s.replaceAll("<[^>]+>", "");
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").trim();
    }

    private static String listToMarkdown(JsonNode items, boolean ordered, int depth) {
        List<String> lines = new ArrayList<>();
        int n = 1;
        for (JsonNode item : items) {
            String content = item.isTextual() ? item.asText() : item.path("content").asText("");
            lines.add("  ".repeat(depth) + (ordered ? (n++) + ". " : "- ") + htmlToMarkdown(content));
            JsonNode nested = item.path("items");
            if (nested.isArray() && !nested.isEmpty()) lines.add(listToMarkdown(nested, ordered, depth + 1));
        }
        return String.join("\n", lines);
    }

    private static String checklistToMarkdown(JsonNode items) {
        List<String> lines = new ArrayList<>();
        for (JsonNode item : items) {
            lines.add((item.path("checked").asBoolean() ? "- [x] " : "- [ ] ") + htmlToMarkdown(item.path("text").asText("")));
        }
        return String.join("\n", lines);
    }

    private static String tableToMarkdown(JsonNode rows) {
        List<String> lines = new ArrayList<>();
        for (JsonNode row : rows) {
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : row) {
                cells.add(htmlToMarkdown(cell.asText("")).replace("|", "\\|").replace("\n", " "));
            }
            lines.add("| " + String.join(" | ", cells) + " |");
            if (lines.size() == 1) lines.add("|" + " --- |".repeat(Math.max(1, cells.size())));
        }
        return String.join("\n", lines);
    }

    private static String imageUrl(JsonNode data) {
        String url = data.path("file").path("url").asText("");
        return url.isBlank() ? data.path("url").asText("") : url;
    }

    private static String placeholder(String type, JsonNode data) {
        return switch (type) {
            case "audio", "audioRecord" -> "[音频]";
            case "attaches" -> "[附件 " + data.path("title").asText(data.path("file").path("name").asText("")) + "]";
            case "references" -> "[参考资料 " + data.path("items").size() + " 条]";
            case "mediaGallery" -> "[媒体集 " + data.path("items").size() + " 项]";
            case "timeline" -> "[时间线 " + data.path("items").size() + " 条]";
            case "linkTool" -> "[链接卡片 " + data.path("link").asText("") + "]";
            case "warning" -> "[提示框 " + AgentImageLocator.stripHtml(data.path("title").asText("")) + "："
                    + AgentImageLocator.stripHtml(data.path("message").asText("")) + "]";
            case "markdown" -> "[Markdown 块]\n" + data.path("markdown").asText("");
            default -> "[" + type + "]";
        };
    }

    private static String markdownType(String text) {
        String t = text.stripLeading();
        if (t.startsWith("```") || t.startsWith("~~~")) return "code";
        if (t.startsWith("#")) return "header";
        if (t.startsWith("|")) return "table";
        if (t.startsWith(">")) return "quote";
        if (t.matches("(?s)^([-*+]|\\d+[.)])\\s.*")) return "list";
        if (t.matches("(?s)^!\\[[^\\]]*\\]\\([^)]*\\)\\s*$")) return "image";
        return "paragraph";
    }

    private static MarkdownSegment segment(String content, int start, int end) {
        int e = end;
        while (e > start && (content.charAt(e - 1) == '\n' || content.charAt(e - 1) == '\r')) e--;
        return new MarkdownSegment(start, e, content.substring(start, e));
    }
}
