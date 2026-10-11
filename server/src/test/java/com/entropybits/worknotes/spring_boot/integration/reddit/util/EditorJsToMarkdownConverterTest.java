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

    @Test
    void convert_mathTableChecklistMarkdownAndWarningBlocks() {
        String json = "{\"blocks\":["
                + "{\"type\":\"math\",\"data\":{\"tex\":\"E=mc^2\"}},"
                + "{\"type\":\"table\",\"data\":{\"withHeadings\":true,\"content\":[[\"方案\",\"延迟\"],[\"<b>轮询</b>\",\"5s\"]]}},"
                + "{\"type\":\"checklist\",\"data\":{\"items\":[{\"text\":\"已办\",\"checked\":true},{\"text\":\"待办\",\"checked\":false}]}},"
                + "{\"type\":\"markdown\",\"data\":{\"markdown\":\"```mermaid\\ngraph TD\\n```\"}},"
                + "{\"type\":\"warning\",\"data\":{\"title\":\"注意\",\"message\":\"先<s>删</s>再<code class=\\\"inline-code\\\">bak</code>\"}}]}";

        assertThat(converter.convert(json, 0)).isEqualTo(
                "$$\nE=mc^2\n$$\n\n"
                + "| 方案 | 延迟 |\n| --- | --- |\n| **轮询** | 5s |\n\n"
                + "- [x] 已办\n- [ ] 待办\n\n"
                + "```mermaid\ngraph TD\n```\n\n"
                + "> **注意**\n> 先~~删~~再`bak`");
    }
}
