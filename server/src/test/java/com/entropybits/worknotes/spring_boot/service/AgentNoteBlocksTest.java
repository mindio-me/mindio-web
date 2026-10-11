/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentNoteBlocksTest {

    private final ObjectMapper om = new ObjectMapper();

    private static final String DOC = """
            {"time":1,"blocks":[
              {"id":"h","type":"header","data":{"text":"4.3 方案","level":2}},
              {"id":"p","type":"paragraph","data":{"text":"网关<b>每 5 秒</b>轮询，见<a href=\\"https://x.com\\">文档</a>&nbsp;&amp;"}},
              {"id":"l","type":"list","data":{"style":"ordered","meta":{"counterType":"numeric"},"items":[
                 {"content":"一","meta":{},"items":[{"content":"一.1","meta":{},"items":[]}]},
                 {"content":"二","meta":{},"items":[]}]}},
              {"id":"c","type":"checklist","data":{"items":[{"text":"待办","checked":true},{"text":"未办","checked":false}]}},
              {"id":"t","type":"table","data":{"withHeadings":true,"content":[["a","b"],["1","2|3"]]}},
              {"id":"k","type":"code","data":{"code":"x = 1"}},
              {"id":"a","type":"audioRecord","data":{"url":"/u.webm"}}
            ],"version":"2.31.6"}""";

    @Test
    void editorjsBlocks_renderTextAsMarkdownAndMarkSpecialBlocksNonEditable() {
        List<AgentNoteBlocks.Block> blocks = AgentNoteBlocks.editorjsBlocks(DOC, om);

        assertThat(blocks).extracting(AgentNoteBlocks.Block::id).containsExactly("h", "p", "l", "c", "t", "k", "a");
        assertThat(blocks.get(0).text()).isEqualTo("## 4.3 方案");
        assertThat(blocks.get(1).text()).isEqualTo("网关**每 5 秒**轮询，见[文档](https://x.com) &");
        assertThat(blocks.get(2).text()).isEqualTo("1. 一\n  1. 一.1\n2. 二");
        assertThat(blocks.get(3).text()).isEqualTo("- [x] 待办\n- [ ] 未办");
        assertThat(blocks.get(4).text()).isEqualTo("| a | b |\n| --- | --- |\n| 1 | 2\\|3 |");
        assertThat(blocks.get(5).text()).isEqualTo("```\nx = 1\n```");
        assertThat(blocks.get(6).text()).isEqualTo("[音频]");
        assertThat(blocks.get(6).editable()).isFalse();
        assertThat(blocks.get(0).editable()).isTrue();
        assertThat(blocks.get(0).hash()).hasSize(8);
    }

    @Test
    void editorjsBlockHash_changesWhenDataChanges() throws Exception {
        String h1 = AgentNoteBlocks.editorjsBlockHash(om.readTree("{\"type\":\"paragraph\",\"data\":{\"text\":\"a\"}}"), om);
        String h2 = AgentNoteBlocks.editorjsBlockHash(om.readTree("{\"type\":\"paragraph\",\"data\":{\"text\":\"b\"}}"), om);

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void markdownSegments_splitOnBlankLines() {
        String md = "# 标题\n\n第一段\n第一段续\n\n\n- a\n- b\n";

        assertThat(AgentNoteBlocks.markdownSegments(md)).extracting(AgentNoteBlocks.MarkdownSegment::text)
                .containsExactly("# 标题", "第一段\n第一段续", "- a\n- b");
    }

    @Test
    void markdownSegments_keepFencedCodeWithBlankLinesTogether() {
        String md = "前\n\n```java\nint a;\n\nint b;\n```\n\n后";

        assertThat(AgentNoteBlocks.markdownSegments(md)).extracting(AgentNoteBlocks.MarkdownSegment::text)
                .containsExactly("前", "```java\nint a;\n\nint b;\n```", "后");
    }

    @Test
    void markdownBlocks_idsCombinePositionAndHash() {
        List<AgentNoteBlocks.Block> blocks = AgentNoteBlocks.markdownBlocks("# 标题\n\n正文");

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).id()).isEqualTo("m1-" + blocks.get(0).hash().substring(0, 4));
        assertThat(blocks.get(0).type()).isEqualTo("header");
        assertThat(blocks.get(1).type()).isEqualTo("paragraph");
        assertThat(blocks.get(1).editable()).isTrue();
    }

    @Test
    void htmlToMarkdown_handlesEmphasisCodeBreaksAndEntities() {
        assertThat(AgentNoteBlocks.htmlToMarkdown("<i>斜</i><br><code class=\"inline-code\">c</code> &lt;x&gt;"))
                .isEqualTo("*斜*\n`c` <x>");
    }
}
