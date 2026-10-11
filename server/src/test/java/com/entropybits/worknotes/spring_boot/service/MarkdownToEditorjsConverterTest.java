/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

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
    void headingsAreClampedToLevelThree() {
        List<ObjectNode> blocks = converter.convert("# 一\n\n#### 四");

        assertThat(blocks).extracting(this::json)
                .containsExactly("header {\"text\":\"一\",\"level\":1}", "header {\"text\":\"四\",\"level\":3}");
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
}
