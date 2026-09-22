/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.integration.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EditorJsToHtmlConverterTest {

    private EditorJsToHtmlConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EditorJsToHtmlConverter(new ObjectMapper());
    }

    @Test
    void convertsHeaderBlock() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"header\",\"data\":{\"text\":\"Hello World\",\"level\":2}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("Hello World").contains("font-weight:bold").contains("<h2");
    }

    @Test
    void convertsParagraphBlock() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"paragraph\",\"data\":{\"text\":\"Some text\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<p ").contains("Some text").contains("line-height:1.8");
    }

    @Test
    void replacesImageUrlWithMappedWechatUrl() throws Exception {
        String localUrl = "http://localhost:8080/api/uploads/img.jpg";
        String wechatUrl = "https://mmbiz.qpic.cn/abc123";
        String json = "{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\""
            + localUrl + "\"},\"caption\":\"\"}}]}";
        String html = converter.convert(json, Map.of(localUrl, wechatUrl));
        assertThat(html).contains(wechatUrl).doesNotContain(localUrl);
    }

    @Test
    void convertsCodeBlock() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"code\",\"data\":{\"code\":\"int x = 1;\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("int x = 1;").contains("background:#1e1e1e").contains("<pre ");
    }

    @Test
    void convertsQuoteBlock() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"quote\",\"data\":{\"text\":\"A wise quote\",\"caption\":\"Author\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("A wise quote").contains("border-left:4px solid").contains("— Author");
    }

    @Test
    void convertsUnorderedList() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[\"Item 1\",\"Item 2\"]}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<ul ").contains("Item 1").contains("Item 2");
    }

    @Test
    void convertsDelimiter() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"delimiter\",\"data\":{}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("* * *");
    }

    @Test
    void convertsTableWithHeadings() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"table\",\"data\":{\"withHeadings\":true," +
            "\"content\":[[\"H1\",\"H2\"],[\"R1C1\",\"R1C2\"]]}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<table ").contains("<th ").contains("<td ").contains("H1");
    }

    @Test
    void extractsImageUrlsFromBlocks() throws Exception {
        String json = "{\"blocks\":["
            + "{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"http://localhost/img1.jpg\"},\"caption\":\"\"}},"
            + "{\"type\":\"paragraph\",\"data\":{\"text\":\"text\"}},"
            + "{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"http://localhost/img2.jpg\"},\"caption\":\"\"}}"
            + "]}";
        List<String> urls = converter.extractImageUrls(json);
        assertThat(urls).containsExactly("http://localhost/img1.jpg", "http://localhost/img2.jpg");
    }

    @Test
    void extractsPlainText() throws Exception {
        String json = "{\"blocks\":["
            + "{\"type\":\"header\",\"data\":{\"text\":\"Title\",\"level\":2}},"
            + "{\"type\":\"paragraph\",\"data\":{\"text\":\"Hello world\"}}"
            + "]}";
        String text = converter.extractPlainText(json, 200);
        assertThat(text).contains("Title").contains("Hello world");
    }

    @Test
    void returnsEmptyForNullOrBlankInput() throws Exception {
        assertThat(converter.convert("", Map.of())).isEmpty();
        assertThat(converter.convert(null, Map.of())).isEmpty();
        assertThat(converter.extractImageUrls(null)).isEmpty();
    }

    @Test
    void convertsMarkdownBlockWithLink() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"markdown\",\"data\":"
            + "{\"markdown\":\"Visit [example](https://example.com) for info\",\"mode\":\"split\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<a href=\"https://example.com\">example</a>");
        assertThat(html).contains("Visit");
        assertThat(html).doesNotContain("[example](https://example.com)");
    }

    @Test
    void convertsMarkdownBlockBoldAndItalic() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"markdown\",\"data\":"
            + "{\"markdown\":\"**bold** and *italic*\",\"mode\":\"edit\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<strong>bold</strong>");
        assertThat(html).contains("<em>italic</em>");
    }

    @Test
    void convertsMarkdownBlockHeadings() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"markdown\",\"data\":"
            + "{\"markdown\":\"# H1\\n## H2\",\"mode\":\"preview\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<h1 ").contains("H1");
        assertThat(html).contains("<h2 ").contains("H2");
    }

    @Test
    void convertsMarkdownBlockUnorderedList() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"markdown\",\"data\":"
            + "{\"markdown\":\"- Item A\\n- Item B\",\"mode\":\"split\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).contains("<li ").contains("Item A").contains("Item B");
        assertThat(html).contains("<ul ");
    }

    @Test
    void emptyMarkdownBlockReturnsEmpty() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"markdown\",\"data\":"
            + "{\"markdown\":\"  \",\"mode\":\"split\"}}]}";
        String html = converter.convert(json, Map.of());
        assertThat(html).isEmpty();
    }

    @Test
    void convert_referencesBlockRendersLinkedList() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                + "{\"kind\":\"link\",\"title\":\"参考文章\",\"url\":\"https://example.com\"},"
                + "{\"kind\":\"note\",\"title\":\"关联笔记\",\"noteId\":42}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).contains("参考文章").contains("href=\"https://example.com\"");
        assertThat(html).contains("关联笔记");
    }

    @Test
    void convert_mediaGalleryBlockRendersImagesAndEmbeds() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"mediaGallery\",\"data\":{\"items\":["
                + "{\"type\":\"image\",\"url\":\"https://example.com/a.png\",\"caption\":\"说明\"},"
                + "{\"type\":\"video\",\"embedUrl\":\"https://youtube.com/embed/x\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).contains("<img").contains("https://example.com/a.png").contains("说明");
        assertThat(html).contains("<iframe").contains("https://youtube.com/embed/x");
    }

    @Test
    void convert_timelineBlockRendersDateTitleDescription() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":["
                + "{\"date\":\"2024-01\",\"title\":\"事件一\",\"description\":\"详情\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).contains("2024-01").contains("事件一").contains("详情");
    }

    @Test
    void extractPlainText_referencesBlockIncludesTitleAndNote() {
        String json = "{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                + "{\"kind\":\"link\",\"title\":\"参考文章\",\"url\":\"https://example.com\",\"note\":\"备注\"}"
                + "]}}]}";

        String text = converter.extractPlainText(json, 500);

        assertThat(text).contains("参考文章").contains("备注");
    }

    @Test
    void convert_timelineBlockWithLinkRendersClickableIcon() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":["
                + "{\"date\":\"2024-01\",\"title\":\"事件\",\"description\":\"说明\",\"link\":\"https://example.com/article\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).contains("2024-01").contains("事件").contains("说明");
        assertThat(html).contains("<a href=\"https://example.com/article\"").contains("🔗");
    }

    @Test
    void convert_referencesBlockEscapesTitleAndNoteHtml() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                + "{\"kind\":\"link\",\"title\":\"<script>alert(1)</script>\",\"url\":\"https://example.com\","
                + "\"note\":\"<img src=x onerror=alert(1)>\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<img src=x onerror=alert(1)>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;");
        // href attribute value is left untouched (out of scope for this fix)
        assertThat(html).contains("href=\"https://example.com\"");
    }

    @Test
    void convert_mediaGalleryBlockEscapesCaptionHtml() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"mediaGallery\",\"data\":{\"items\":["
                + "{\"type\":\"image\",\"url\":\"https://example.com/a.png\","
                + "\"caption\":\"<script>alert(1)</script>\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        // src attribute value is left untouched (out of scope for this fix)
        assertThat(html).contains("src=\"https://example.com/a.png\"");
    }

    @Test
    void convert_timelineBlockEscapesTitleDateAndDescriptionHtml() throws Exception {
        String json = "{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":["
                + "{\"date\":\"<b>2024-01</b>\",\"title\":\"<script>alert(1)</script>\","
                + "\"description\":\"<img src=x onerror=alert(1)>\",\"link\":\"https://example.com/article\"}"
                + "]}}]}";

        String html = converter.convert(json, Map.of());

        assertThat(html).doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<img src=x onerror=alert(1)>")
                .doesNotContain("<b>2024-01</b>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;")
                .contains("&lt;b&gt;2024-01&lt;/b&gt;");
        // href attribute value is left untouched (out of scope for this fix)
        assertThat(html).contains("href=\"https://example.com/article\"");
    }
}
