/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.integration.reddit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditorJsToMarkdownConverterTest {

    private final EditorJsToMarkdownConverter converter = new EditorJsToMarkdownConverter(new ObjectMapper());

    @Test
    void convert_referencesBlockRendersMarkdownLinkList() {
        String json = "{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                + "{\"kind\":\"link\",\"title\":\"参考文章\",\"url\":\"https://example.com\"}"
                + "]}}]}";

        String md = converter.convert(json, 0);

        assertThat(md).isEqualTo("- [参考文章](https://example.com)");
    }

    @Test
    void convert_mediaGalleryBlockRendersImageMarkdownAndLinksForOthers() {
        String json = "{\"blocks\":[{\"type\":\"mediaGallery\",\"data\":{\"items\":["
                + "{\"type\":\"image\",\"url\":\"https://example.com/a.png\"},"
                + "{\"type\":\"video\",\"embedUrl\":\"https://youtube.com/embed/x\"}"
                + "]}}]}";

        String md = converter.convert(json, 0);

        assertThat(md).contains("![](https://example.com/a.png)");
        assertThat(md).contains("[视频](https://youtube.com/embed/x)");
    }

    @Test
    void convert_timelineBlockRendersMarkdownList() {
        String json = "{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":["
                + "{\"date\":\"2024-01\",\"title\":\"事件一\"}"
                + "]}}]}";

        String md = converter.convert(json, 0);

        assertThat(md).isEqualTo("- **2024-01** 事件一");
    }
}
