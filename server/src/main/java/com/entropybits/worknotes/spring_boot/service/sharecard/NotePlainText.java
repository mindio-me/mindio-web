/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.integration.wechat.EditorJsToHtmlConverter;
import com.entropybits.worknotes.spring_boot.integration.wechat.MarkdownToHtmlConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

import java.util.Map;

/**
 * 把笔记正文（Editor.js JSON / Markdown / 富文本 HTML）转成去标签、合并空白的纯文本。
 */
public final class NotePlainText {

    private static final EditorJsToHtmlConverter EDITOR_JS = new EditorJsToHtmlConverter(new ObjectMapper());
    private static final MarkdownToHtmlConverter MARKDOWN = new MarkdownToHtmlConverter();

    private NotePlainText() {
    }

    public static String extract(Note note, int maxChars) {
        String content = note.getContent();
        if (content == null || content.isBlank() || maxChars <= 0) {
            return "";
        }
        String html;
        String trimmed = content.stripLeading();
        try {
            if (trimmed.startsWith("{") && trimmed.contains("\"blocks\"")) {
                html = EDITOR_JS.convert(content, Map.of());
            } else if ("markdown".equalsIgnoreCase(note.getContentType())) {
                html = MARKDOWN.convert(content);
            } else {
                html = content;
            }
        } catch (Exception e) {
            html = content;
        }
        String text = Jsoup.parse(html).text().strip();
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
