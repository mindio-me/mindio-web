/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownToEditorjsConverterTest {

    private final ObjectMapper om = new ObjectMapper();
    private final MarkdownToEditorjsConverter converter = new MarkdownToEditorjsConverter(om);

    private String json(ObjectNode block) {
        return block.path("type").asText() + " " + block.path("data").toString();
    }

    @Test
    void paragraphWithInlineFormattingIsEscaped() {
        List<ObjectNode> blocks = converter.convert("网关 **每 5 秒** 轮询 *一次* `poll()` [文档](https://x.com) <script>");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).path("id").asText()).hasSize(10);
        assertThat(json(blocks.get(0))).isEqualTo("paragraph {\"text\":\"网关 <b>每 5 秒</b> 轮询 <i>一次</i> "
                + "<code class=\\\"inline-code\\\">poll()</code> <a href=\\\"https://x.com\\\">文档</a> &lt;script&gt;\"}");
    }

    @Test
    void nestedListsUseEditorjsListV2Shape() {
        List<ObjectNode> blocks = converter.convert("1. 一\n   - 子\n2. 二");

        assertThat(json(blocks.get(0))).isEqualTo("list {\"style\":\"ordered\",\"meta\":{\"counterType\":\"numeric\"},"
                + "\"items\":[{\"content\":\"一\",\"meta\":{},\"items\":[{\"content\":\"子\",\"meta\":{},\"items\":[]}]},"
                + "{\"content\":\"二\",\"meta\":{},\"items\":[]}]}");
    }

    @Test
    void taskListBecomesChecklist() {
        assertThat(json(converter.convert("- [x] 已办\n- [ ] 待办").get(0)))
                .isEqualTo("checklist {\"items\":[{\"text\":\"已办\",\"checked\":true},{\"text\":\"待办\",\"checked\":false}]}");
    }

    @Test
    void pipeTableBecomesTableWithHeadings() {
        assertThat(json(converter.convert("| 方案 | 延迟 |\n| --- | --- |\n| **轮询** | 5s |").get(0)))
                .isEqualTo("table {\"withHeadings\":true,\"stretched\":false,"
                        + "\"content\":[[\"方案\",\"延迟\"],[\"<b>轮询</b>\",\"5s\"]]}");
    }

    @Test
    void codeQuoteDelimiterAndImage() {
        List<ObjectNode> blocks = converter.convert(
                "```java\nint a;\n\nint b;\n```\n\n> 引用 *斜*\n\n---\n\n![架构 <图>](/api/uploads/a.png)");

        assertThat(blocks).extracting(this::json).containsExactly(
                "code {\"code\":\"int a;\\n\\nint b;\"}",
                "quote {\"text\":\"引用 <i>斜</i>\",\"caption\":\"\",\"alignment\":\"left\"}",
                "delimiter {}",
                "image {\"file\":{\"url\":\"/api/uploads/a.png\"},\"caption\":\"架构 &lt;图&gt;\","
                        + "\"withBorder\":false,\"withBackground\":false,\"stretched\":false}");
    }

    @Test
    void emptyMarkdownGivesNoBlocks() {
        assertThat(converter.convert("  \n\n ")).isEmpty();
    }

    @Test
    void unsafeLinkSchemesAreDroppedKeepingText() {
        assertThat(json(converter.convert("[点此](javascript:alert(1)) [好](https://x.com) [相对](/notes/1)").get(0)))
                .isEqualTo("paragraph {\"text\":\"点此 <a href=\\\"https://x.com\\\">好</a> <a href=\\\"/notes/1\\\">相对</a>\"}");
    }

    @Test
    void headingsKeepLevelsOneToSix() {
        assertThat(converter.convert("# 一\n\n#### 四\n\n###### 六")).extracting(this::json).containsExactly(
                "header {\"text\":\"一\",\"level\":1}",
                "header {\"text\":\"四\",\"level\":4}",
                "header {\"text\":\"六\",\"level\":6}");
    }

    @Test
    void tableRightAfterHeadingWithoutBlankLine() {
        assertThat(converter.convert("## 表\n| a | b |\n| --- | --- |\n| 1 | 2 |")).extracting(this::json).containsExactly(
                "header {\"text\":\"表\",\"level\":2}",
                "table {\"withHeadings\":true,\"stretched\":false,\"content\":[[\"a\",\"b\"],[\"1\",\"2\"]]}");
    }

    @Test
    void strikethroughNeedsTwoTildes() {
        assertThat(json(converter.convert("~~删~~ 5~10 天").get(0)))
                .isEqualTo("paragraph {\"text\":\"<s>删</s> 5~10 天\"}");
    }

    @Test
    void highlightInlineMathAndDollarAmounts() {
        assertThat(json(converter.convert("==重点== 质能 $E=mc^2$，价格 $5 和 $10").get(0)))
                .isEqualTo("paragraph {\"text\":\"<mark class=\\\"cdx-marker\\\">重点</mark> 质能 "
                        + "<code class=\\\"inline-code\\\">$E=mc^2$</code>，价格 $5 和 $10\"}");
    }

    @Test
    void mathMermaidAndHtmlBlocks() {
        List<ObjectNode> blocks = converter.convert(
                "$$\nE=mc^2\n$$\n\n```mermaid\ngraph TD\nA-->B\n```\n\n<details>\n<summary>更多</summary>\n</details>");

        assertThat(blocks).extracting(this::json).containsExactly(
                "math {\"tex\":\"E=mc^2\"}",
                "markdown {\"markdown\":\"```mermaid\\ngraph TD\\nA-->B\\n```\",\"mode\":\"preview\"}",
                "markdown {\"markdown\":\"<details>\\n<summary>更多</summary>\\n</details>\",\"mode\":\"preview\"}");
    }

    @Test
    void frontmatterBecomesCodeBlock() {
        assertThat(converter.convert("---\ntitle: 笔记\n---\n\n正文")).extracting(this::json).containsExactly(
                "code {\"code\":\"title: 笔记\"}",
                "paragraph {\"text\":\"正文\"}");
    }

    @Test
    void leadingThematicBreakIsNotFrontmatter() {
        assertThat(converter.convert("---\n\n正文\n\n---\n\n尾")).extracting(b -> b.path("type").asText())
                .containsExactly("delimiter", "paragraph", "delimiter", "paragraph");
    }

    @Test
    void inlineImagesSplitParagraphAndLocalImagesBecomePlaceholders() {
        List<ObjectNode> blocks = converter.convert("看图 ![a](https://x.com/a.png) 再看 ![b](assets/b.png) 完");

        assertThat(blocks).extracting(this::json).containsExactly(
                "paragraph {\"text\":\"看图\"}",
                "image {\"file\":{\"url\":\"https://x.com/a.png\"},\"caption\":\"a\","
                        + "\"withBorder\":false,\"withBackground\":false,\"stretched\":false}",
                "paragraph {\"text\":\"再看\"}",
                "paragraph {\"text\":\"⚠ 图片未导入：assets/b.png\"}",
                "paragraph {\"text\":\"完\"}");
    }

    @Test
    void uploadImagesStayImageBlocks() {
        List<ObjectNode> blocks = converter.convert(
                "![a](/api/uploads/a.png)\n\n![b](http://localhost:8081/api/uploads/b.png)");

        assertThat(blocks).extracting(b -> b.path("type").asText()).containsExactly("image", "image");
        assertThat(blocks.get(1).path("data").path("file").path("url").asText())
                .isEqualTo("http://localhost:8081/api/uploads/b.png");
    }

    @Test
    void obsidianEmbedBecomesPlaceholderAndWikilinksBecomeText() {
        assertThat(converter.convert("![[my pic.png]]\n\n见 [[笔记甲#小节|别名]] 和 [[笔记乙]]")).extracting(this::json)
                .containsExactly(
                        "paragraph {\"text\":\"⚠ 图片未导入：my pic.png\"}",
                        "paragraph {\"text\":\"见 别名 和 笔记乙\"}");
    }

    @Test
    void calloutsBecomeWarnings() {
        assertThat(converter.convert("> [!tip] 小技巧\n> 先 **保存**\n\n> [!NOTE]\n> 正文")).extracting(this::json)
                .containsExactly(
                        "warning {\"title\":\"小技巧\",\"message\":\"先 <b>保存</b>\"}",
                        "warning {\"title\":\"Note\",\"message\":\"正文\"}");
    }

    @Test
    void plainQuoteStaysQuote() {
        assertThat(json(converter.convert("> 普通 [引用]").get(0)))
                .isEqualTo("quote {\"text\":\"普通 [引用]\",\"caption\":\"\",\"alignment\":\"left\"}");
    }

    @Test
    void footnotesBecomeSupAndTrailingList() {
        List<ObjectNode> blocks = converter.convert("正文[^a]，再引[^b]。\n\n[^b]: 乙说明\n[^a]: 甲说明");

        assertThat(blocks).extracting(this::json).containsExactly(
                "paragraph {\"text\":\"正文<sup>1</sup>，再引<sup>2</sup>。\"}",
                "header {\"text\":\"脚注\",\"level\":2}",
                "list {\"style\":\"ordered\",\"meta\":{\"counterType\":\"numeric\"},\"items\":["
                        + "{\"content\":\"甲说明\",\"meta\":{},\"items\":[]},"
                        + "{\"content\":\"乙说明\",\"meta\":{},\"items\":[]}]}");
    }

    @Test
    void looseListStaysOneList() {
        List<ObjectNode> blocks = converter.convert("- 一\n\n- 二\n\n- 三");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).path("data").path("items")).hasSize(3);
    }

    @Test
    void sourceOfReturnsOriginalBlockLines() {
        String md = "前\n\n> 引一\n> 引二";
        Node doc = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build().parse(md);

        assertThat(MarkdownToEditorjsConverter.sourceOf(doc.getLastChild(), md.split("\n", -1)))
                .isEqualTo("> 引一\n> 引二");
    }

    @Test
    void obsidianSampleUsesNativeBlocksAndOnlyOneMarkdownBlock() throws Exception {
        String md;
        try (var in = getClass().getResourceAsStream("/markdown/obsidian-sample.md")) {
            md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<ObjectNode> blocks = converter.convert(md);

        assertThat(blocks).extracting(b -> b.path("type").asText()).containsExactly(
                "code", "header", "header", "paragraph", "table", "warning", "checklist", "list", "header",
                "paragraph", "math", "markdown", "paragraph", "image", "paragraph", "header", "list");
        assertThat(blocks.get(7).path("data").path("items")).hasSize(2);
        assertThat(blocks.get(9).path("data").path("text").asText())
                .contains("<code class=\"inline-code\">$E=mc^2$</code>").contains("<sup>1</sup>");
    }
}
