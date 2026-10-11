/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * agent 用 Markdown 写内容，这里转成 Editor.js 块。只依赖 commonmark 核心库：表格（GFM）和
 * 任务列表不在核心语法里，表格按"每行以 | 开头、第二行是分隔行"的段落自己识别，任务列表按
 * 列表项开头的 [ ] / [x] 识别。行内只保留 Editor.js 支持的 b/i/code/a，其余文字全部 HTML 转义
 * （编辑器用 innerHTML 渲染，模型输出可能被网页内容注入）。
 */
final class MarkdownToEditorjsConverter {

    private static final Parser PARSER = Parser.builder().build();
    private static final Pattern TABLE_SEPARATOR =
            Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?$");
    private static final Pattern TASK_PREFIX = Pattern.compile("^\\[([ xX])\\]\\s+");

    private final ObjectMapper om;

    MarkdownToEditorjsConverter(ObjectMapper om) {
        this.om = om;
    }

    List<ObjectNode> convert(String markdown) {
        List<ObjectNode> blocks = new ArrayList<>();
        if (markdown == null) return blocks;
        for (AgentNoteBlocks.MarkdownSegment seg : AgentNoteBlocks.markdownSegments(markdown)) {
            if (isTable(seg.text())) {
                blocks.add(table(seg.text()));
                continue;
            }
            Node doc = PARSER.parse(seg.text());
            for (Node n = doc.getFirstChild(); n != null; n = n.getNext()) addBlock(n, blocks);
        }
        return blocks;
    }

    private void addBlock(Node n, List<ObjectNode> blocks) {
        if (n instanceof Heading h) {
            ObjectNode data = om.createObjectNode();
            data.put("text", inline(h));
            data.put("level", Math.max(1, Math.min(3, h.getLevel())));
            blocks.add(block("header", data));
        } else if (n instanceof Paragraph p) {
            if (p.getFirstChild() instanceof Image img && img.getNext() == null) {
                blocks.add(image(img));
            } else {
                ObjectNode data = om.createObjectNode();
                data.put("text", inline(p));
                blocks.add(block("paragraph", data));
            }
        } else if (n instanceof BulletList || n instanceof OrderedList) {
            blocks.add(isTaskList(n) ? checklist(n) : list(n));
        } else if (n instanceof FencedCodeBlock code) {
            blocks.add(code(code.getLiteral()));
        } else if (n instanceof IndentedCodeBlock code) {
            blocks.add(code(code.getLiteral()));
        } else if (n instanceof BlockQuote q) {
            List<String> parts = new ArrayList<>();
            for (Node c = q.getFirstChild(); c != null; c = c.getNext()) parts.add(inline(c));
            ObjectNode data = om.createObjectNode();
            data.put("text", String.join("<br>", parts));
            data.put("caption", "");
            data.put("alignment", "left");
            blocks.add(block("quote", data));
        } else if (n instanceof ThematicBreak) {
            blocks.add(block("delimiter", om.createObjectNode()));
        } else if (n instanceof HtmlBlock html) {
            ObjectNode data = om.createObjectNode();
            data.put("text", AgentHtmlImageEditor.escape(html.getLiteral().trim()));
            blocks.add(block("paragraph", data));
        } else {
            ObjectNode data = om.createObjectNode();
            data.put("text", inline(n));
            blocks.add(block("paragraph", data));
        }
    }

    private ObjectNode list(Node listNode) {
        boolean ordered = listNode instanceof OrderedList;
        ObjectNode data = om.createObjectNode();
        data.put("style", ordered ? "ordered" : "unordered");
        ObjectNode meta = data.putObject("meta");
        if (ordered) meta.put("counterType", "numeric");
        data.set("items", listItems(listNode));
        return block("list", data);
    }

    private ArrayNode listItems(Node listNode) {
        ArrayNode items = om.createArrayNode();
        for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
            ObjectNode item = items.addObject();
            String content = "";
            ArrayNode nested = om.createArrayNode();
            for (Node c = li.getFirstChild(); c != null; c = c.getNext()) {
                if (c instanceof BulletList || c instanceof OrderedList) nested.addAll(listItems(c));
                else if (content.isEmpty()) content = inline(c);
            }
            item.put("content", content);
            item.putObject("meta");
            item.set("items", nested);
        }
        return items;
    }

    /** 用整段纯文本判断：commonmark 可能把 "[x]" 拆成多个 Text 节点，只看第一个节点会漏判 */
    private boolean isTaskList(Node listNode) {
        if (!(listNode instanceof BulletList) || listNode.getFirstChild() == null) return false;
        for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
            if (!(li.getFirstChild() instanceof Paragraph p) || !TASK_PREFIX.matcher(plainText(p)).find()) {
                return false;
            }
        }
        return true;
    }

    private ObjectNode checklist(Node listNode) {
        ObjectNode data = om.createObjectNode();
        ArrayNode items = data.putArray("items");
        for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
            String html = inline(li.getFirstChild());
            Matcher m = TASK_PREFIX.matcher(html);
            boolean checked = m.find() && !m.group(1).isBlank();
            ObjectNode item = items.addObject();
            item.put("text", TASK_PREFIX.matcher(html).replaceFirst(""));
            item.put("checked", checked);
        }
        return block("checklist", data);
    }

    private ObjectNode code(String literal) {
        ObjectNode data = om.createObjectNode();
        data.put("code", literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal);
        return block("code", data);
    }

    private ObjectNode image(Image img) {
        ObjectNode data = om.createObjectNode();
        data.putObject("file").put("url", img.getDestination());
        data.put("caption", AgentHtmlImageEditor.escape(plainText(img)));
        data.put("withBorder", false);
        data.put("withBackground", false);
        data.put("stretched", false);
        return block("image", data);
    }

    private boolean isTable(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 2 || !TABLE_SEPARATOR.matcher(lines[1].trim()).matches()) return false;
        for (String line : lines) {
            if (!line.trim().startsWith("|")) return false;
        }
        return true;
    }

    private ObjectNode table(String text) {
        ObjectNode data = om.createObjectNode();
        data.put("withHeadings", true);
        data.put("stretched", false);
        ArrayNode content = data.putArray("content");
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == 1) continue;
            String row = lines[i].trim();
            if (row.startsWith("|")) row = row.substring(1);
            if (row.endsWith("|") && !row.endsWith("\\|")) row = row.substring(0, row.length() - 1);
            ArrayNode cells = content.addArray();
            for (String cell : row.split("(?<!\\\\)\\|", -1)) {
                cells.add(inlineText(cell.trim().replace("\\|", "|")));
            }
        }
        return block("table", data);
    }

    private ObjectNode block(String type, ObjectNode data) {
        ObjectNode block = om.createObjectNode();
        block.put("id", AgentEditorjsImageEditor.newBlockId());
        block.put("type", type);
        block.set("data", data);
        return block;
    }

    /** 单元格等零散文本：按 Markdown 行内语法渲染 */
    private String inlineText(String text) {
        Node doc = PARSER.parse(text);
        return doc.getFirstChild() == null ? "" : inline(doc.getFirstChild());
    }

    private static String inline(Node parent) {
        StringBuilder sb = new StringBuilder();
        for (Node c = parent.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text t) sb.append(AgentHtmlImageEditor.escape(t.getLiteral()));
            else if (c instanceof StrongEmphasis) sb.append("<b>").append(inline(c)).append("</b>");
            else if (c instanceof Emphasis) sb.append("<i>").append(inline(c)).append("</i>");
            else if (c instanceof Code code) sb.append("<code class=\"inline-code\">")
                    .append(AgentHtmlImageEditor.escape(code.getLiteral())).append("</code>");
            else if (c instanceof Link link) {
                if (isSafeHref(link.getDestination())) {
                    sb.append("<a href=\"").append(AgentHtmlImageEditor.escape(link.getDestination()))
                            .append("\">").append(inline(c)).append("</a>");
                } else {
                    sb.append(inline(c));
                }
            }
            else if (c instanceof Image img) sb.append(AgentHtmlImageEditor.escape(plainText(img)));
            else if (c instanceof SoftLineBreak) sb.append(' ');
            else if (c instanceof HardLineBreak) sb.append("<br>");
            else if (c instanceof HtmlInline html) sb.append(AgentHtmlImageEditor.escape(html.getLiteral()));
            else sb.append(inline(c));
        }
        return sb.toString();
    }

    /**
     * 链接只放行 http/https/mailto 和不带协议的相对地址：模型输出可能被网页内容注入，
     * javascript: 之类的链接写进笔记后，公开页用 v-html 渲染，点一下就在本站执行脚本。
     */
    static boolean isSafeHref(String href) {
        if (href == null) return false;
        String h = href.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = h.indexOf(':');
        int firstDelimiter = h.length();
        for (char d : new char[]{'/', '?', '#'}) {
            int i = h.indexOf(d);
            if (i >= 0 && i < firstDelimiter) firstDelimiter = i;
        }
        if (colon < 0 || colon > firstDelimiter) return true;
        return h.startsWith("http:") || h.startsWith("https:") || h.startsWith("mailto:");
    }

    private static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text t) sb.append(t.getLiteral());
            else sb.append(plainText(c));
        }
        return sb.toString();
    }
}
