/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本（HTML）笔记的图片插入/删除/改图注。结构和 Markdown 版对称：
 * &lt;p&gt;&lt;img alt="图注"&gt;&lt;/p&gt; + 紧跟的 &lt;p&gt;&lt;em&gt;图注&lt;/em&gt;&lt;/p&gt;。
 * 用正则按偏移改字符串，不用 DOM 解析重新序列化——那样会把整篇 HTML 的格式都改掉。
 */
final class AgentHtmlImageEditor {

    /** 先在这些"叶子"块里找锚点；找不到再退到 div（div 常常包着整篇，优先级最低） */
    private static final Pattern LEAF_BLOCK =
            Pattern.compile("(?is)<(p|h[1-6]|li|blockquote)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern DIV_BLOCK = Pattern.compile("(?is)<(div)\\b[^>]*>.*?</div\\s*>");
    private static final Pattern OPEN_P_BEFORE = Pattern.compile("(?i)<p\\b[^>]*>\\s*\\z");
    private static final Pattern CLOSE_P_AFTER = Pattern.compile("(?i)\\A\\s*</p\\s*>");
    private static final Pattern CAPTION_PARAGRAPH = Pattern.compile("(?is)\\A\\s*<p>\\s*<em>(.*?)</em>\\s*</p>");
    private static final Pattern ALT_ATTR = Pattern.compile("(?i)\\balt\\s*=\\s*([\"'])(.*?)\\1");

    private AgentHtmlImageEditor() {}

    static AgentImageEditResult insert(String content, String url, String caption, String anchorText) {
        String html = content == null ? "" : content;
        String cap = escape(normalize(caption));
        String snippet = "<p><img src=\"" + escape(url) + "\" alt=\"" + cap + "\"></p>"
                + (cap.isEmpty() ? "" : "<p><em>" + cap + "</em></p>");
        if (anchorText == null || anchorText.isBlank()) {
            return new AgentImageEditResult(html + snippet, Map.of("op", "insert"));
        }
        String anchor = anchorText.trim();
        int[] block = findBlock(LEAF_BLOCK, html, anchor);
        if (block == null) block = findBlock(DIV_BLOCK, html, anchor);
        if (block == null) throw new BadRequestException("找不到包含「" + anchor + "」的段落");

        // 只替换普通段落 <p>（占位符段）；标题、列表项等即使文字相同也插在后面，不能把它们删掉
        boolean replace = html.regionMatches(true, block[0], "<p", 0, 2)
                && AgentImageLocator.stripHtml(html.substring(block[0], block[1])).equals(anchor);
        String result = replace
                ? html.substring(0, block[0]) + snippet + html.substring(block[1])
                : html.substring(0, block[1]) + snippet + html.substring(block[1]);
        return new AgentImageEditResult(result, Map.of("op", "insert"));
    }

    static AgentImageEditResult delete(String content, AgentImageLocator.LocatedImage image) {
        int start = image.start();
        int end = image.end();
        Matcher open = OPEN_P_BEFORE.matcher(content.substring(0, start));
        Matcher close = CLOSE_P_AFTER.matcher(content.substring(end));
        if (open.find() && close.find()) {
            start = open.start();
            end = end + close.end();
            Matcher captionParagraph = CAPTION_PARAGRAPH.matcher(content.substring(end));
            if (!image.caption().isBlank() && captionParagraph.find()
                    && captionParagraph.group(1).trim().equals(image.caption())) {
                end = end + captionParagraph.end();
            }
        }
        return new AgentImageEditResult(content.substring(0, start) + content.substring(end), Map.of("op", "delete"));
    }

    static AgentImageEditResult setCaption(String content, AgentImageLocator.LocatedImage image, String caption) {
        String cap = escape(normalize(caption));
        String head = content.substring(0, image.start()) + withAlt(image.markup(), cap);
        int pos = image.end();
        Matcher close = CLOSE_P_AFTER.matcher(content.substring(pos));
        if (close.find()) pos += close.end();
        head = head + content.substring(image.end(), pos);

        String rest = content.substring(pos);
        Matcher captionParagraph = CAPTION_PARAGRAPH.matcher(rest);
        if (!image.caption().isBlank() && captionParagraph.find()
                && captionParagraph.group(1).trim().equals(image.caption())) {
            rest = rest.substring(captionParagraph.end());
        }
        String mid = cap.isEmpty() ? "" : "<p><em>" + cap + "</em></p>";
        return new AgentImageEditResult(head + mid + rest, Map.of("op", "caption"));
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String withAlt(String imgTag, String escapedAlt) {
        Matcher alt = ALT_ATTR.matcher(imgTag);
        if (alt.find()) {
            return imgTag.substring(0, alt.start()) + "alt=\"" + escapedAlt + "\"" + imgTag.substring(alt.end());
        }
        return imgTag.replaceFirst("(?i)^<img", Matcher.quoteReplacement("<img alt=\"" + escapedAlt + "\""));
    }

    private static String normalize(String caption) {
        return caption == null ? "" : caption.replace("\r", " ").replace("\n", " ").trim();
    }

    private static int[] findBlock(Pattern pattern, String html, String anchor) {
        Matcher m = pattern.matcher(html);
        while (m.find()) {
            if (AgentImageLocator.stripHtml(m.group()).contains(anchor)) return new int[]{m.start(), m.end()};
        }
        return null;
    }
}
