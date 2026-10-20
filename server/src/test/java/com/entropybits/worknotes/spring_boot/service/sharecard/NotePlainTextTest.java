/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.entity.Note;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotePlainTextTest {

    private static Note note(String content, String type) {
        return Note.builder().content(content).contentType(type).build();
    }

    @Test
    void editorJsKeepsBlockOrderWithoutTags() {
        String json = "{\"blocks\":["
                + "{\"type\":\"header\",\"data\":{\"text\":\"Title\",\"level\":2}},"
                + "{\"type\":\"paragraph\",\"data\":{\"text\":\"Hello <b>bold</b>\"}},"
                + "{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[\"one\",\"two\"]}}]}";
        String text = NotePlainText.extract(note(json, "richtext"), 1000);
        assertThat(text).doesNotContain("<").doesNotContain(">");
        assertThat(text).contains("Title", "Hello bold", "one", "two");
        assertThat(text.indexOf("Title")).isLessThan(text.indexOf("Hello"));
        assertThat(text.indexOf("Hello")).isLessThan(text.indexOf("one"));
    }

    @Test
    void markdownStripsSyntax() {
        assertThat(NotePlainText.extract(note("# 标题\n**粗**", "markdown"), 1000)).isEqualTo("标题 粗");
    }

    @Test
    void richTextHtmlDecodesEntities() {
        assertThat(NotePlainText.extract(note("<p>a&amp;b</p>", "richtext"), 1000)).isEqualTo("a&b");
    }

    @Test
    void truncatesToMaxChars() {
        assertThat(NotePlainText.extract(note("<p>abcdefghij</p>", "richtext"), 5)).isEqualTo("abcde");
    }

    @Test
    void nullContentGivesEmpty() {
        assertThat(NotePlainText.extract(note(null, "richtext"), 10)).isEmpty();
    }
}
