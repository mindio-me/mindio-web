/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 笔记的图片插入/删除/改图注。图注存两处：alt（![图注](url)）和紧跟的下一行斜体
 * *图注*——只写 alt 的话渲染出来看不到。删除和改图注时按"下一行正好是 *这张图的 alt*"认出
 * 图注行，成对处理；不是这张图的斜体行不动。
 */
final class AgentMarkdownImageEditor {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("\\n[ \\t]*\\n");
    private static final Pattern MARKDOWN_EMPHASIS = Pattern.compile("[*_`>#~]");
    // 段落开头是标题（#）、列表（- * + 或 1.）、引用（>）、表格（|）时视为结构块
    private static final Pattern STRUCTURAL_LINE = Pattern.compile("^\\s*(#{1,6}\\s|[-*+]\\s|\\d+[.)]\\s|>|\\|)");

    private AgentMarkdownImageEditor() {}

    static AgentImageEditResult insert(String content, String url, String caption, String anchorText) {
        String text = content == null ? "" : content;
        String snippet = snippet(url, normalizeCaption(caption));
        String result;
        if (anchorText == null || anchorText.isBlank()) {
            result = text.isBlank() ? snippet : text.replaceAll("\\s+$", "") + "\n\n" + snippet + "\n";
        } else {
            String anchor = plain(anchorText);
            if (anchor.isEmpty()) throw new BadRequestException("锚点文字不能只有标点符号");
            int[] p = paragraphs(text).stream()
                    .filter(r -> plain(text.substring(r[0], r[1])).contains(anchor))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("找不到包含「" + anchorText.trim() + "」的段落"));
            // 只替换普通段落（占位符段）；标题、列表、引用、表格即使文字相同也插在后面，不能把它们删掉
            boolean replace = !STRUCTURAL_LINE.matcher(text.substring(p[0], p[1])).find()
                    && plain(text.substring(p[0], p[1])).equals(anchor);
            result = replace
                    ? text.substring(0, p[0]) + snippet + text.substring(p[1])
                    : text.substring(0, p[1]) + "\n\n" + snippet + text.substring(p[1]);
        }
        return new AgentImageEditResult(result, Map.of("op", "insert"));
    }

    static AgentImageEditResult delete(String content, AgentImageLocator.LocatedImage image) {
        int lineStart = content.lastIndexOf('\n', image.start() - 1) + 1;
        int lineEnd = lineEnd(content, image.end());
        boolean ownLine = content.substring(lineStart, image.start()).isBlank()
                && content.substring(image.end(), lineEnd).isBlank();
        if (!ownLine) {
            return new AgentImageEditResult(
                    content.substring(0, image.start()) + content.substring(image.end()), Map.of("op", "delete"));
        }
        int[] captionLine = lineEnd < content.length() ? captionLine(content, lineEnd + 1, image.caption()) : null;
        int removeEnd = captionLine != null ? captionLine[1] : lineEnd;
        removeEnd = Math.min(removeEnd + 1, content.length());
        return new AgentImageEditResult(content.substring(0, lineStart) + content.substring(removeEnd),
                Map.of("op", "delete"));
    }

    static AgentImageEditResult setCaption(String content, AgentImageLocator.LocatedImage image, String caption) {
        String cap = normalizeCaption(caption);
        String markup = image.markup().startsWith("!")
                ? "![" + cap + "](" + image.url() + ")"
                : AgentHtmlImageEditor.withAlt(image.markup(), AgentHtmlImageEditor.escape(cap));
        int lineEnd = lineEnd(content, image.end());
        int[] captionLine = lineEnd < content.length() ? captionLine(content, lineEnd + 1, image.caption()) : null;

        String head = content.substring(0, image.start()) + markup + content.substring(image.end(), lineEnd);
        String mid = cap.isEmpty() ? "" : "\n*" + cap + "*";
        String tail = captionLine != null ? content.substring(captionLine[1]) : content.substring(lineEnd);
        return new AgentImageEditResult(head + mid + tail, Map.of("op", "caption"));
    }

    static String normalizeCaption(String caption) {
        if (caption == null) return "";
        return caption.replace("\r", " ").replace("\n", " ")
                .replace("[", "［").replace("]", "］").replace("*", "＊").trim();
    }

    private static String snippet(String url, String cap) {
        return "![" + cap + "](" + url + ")" + (cap.isEmpty() ? "" : "\n*" + cap + "*");
    }

    /** 从 from 开始的这一行（不含换行）如果正是 *caption*，返回 {行首, 行尾}；否则 null */
    private static int[] captionLine(String content, int from, String caption) {
        if (caption == null || caption.isBlank() || from > content.length()) return null;
        int end = lineEnd(content, from);
        return content.substring(from, end).trim().equals("*" + caption + "*") ? new int[]{from, end} : null;
    }

    private static int lineEnd(String content, int from) {
        int e = content.indexOf('\n', from);
        return e < 0 ? content.length() : e;
    }

    /** 按空行切段落，返回每段的 {start, end}（跳过空白段） */
    private static List<int[]> paragraphs(String content) {
        List<int[]> result = new ArrayList<>();
        Matcher sep = PARAGRAPH_SEPARATOR.matcher(content);
        int pos = 0;
        while (sep.find()) {
            addIfNotBlank(result, content, pos, sep.start());
            pos = sep.end();
        }
        addIfNotBlank(result, content, pos, content.length());
        return result;
    }

    private static void addIfNotBlank(List<int[]> result, String content, int start, int end) {
        if (!content.substring(start, end).isBlank()) result.add(new int[]{start, end});
    }

    /** 锚点匹配时忽略 markdown 的强调/标题符号：用户看到的是"【配图位：图 1】"，原文可能是 **【配图位：图 1】** */
    private static String plain(String markdown) {
        return MARKDOWN_EMPHASIS.matcher(markdown).replaceAll("").trim();
    }
}
