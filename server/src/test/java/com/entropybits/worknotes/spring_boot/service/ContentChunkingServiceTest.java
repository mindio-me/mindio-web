/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentChunkingServiceTest {

    private final ContentChunkingService service = new ContentChunkingService(new ObjectMapper());

    @Test
    void chunkNote_markdownSplitsOnBlankLines() {
        Note note = Note.builder()
                .contentType("markdown")
                .content("第一段内容。\n\n第二段内容。\n\n第三段内容。")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("第一段内容。\n第二段内容。\n第三段内容。");
    }

    @Test
    void chunkNote_richtextSplitsOnParagraphTags() {
        Note note = Note.builder()
                .contentType("richtext")
                .content("<p>第一段<b>加粗</b>内容</p><p>第二段内容</p>")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("第一段加粗内容\n第二段内容");
    }

    @Test
    void chunkNote_editorjsExtractsParagraphHeaderAndCodeBlocks() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"header\",\"data\":{\"text\":\"标题一\"}},"
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"这是<b>正文</b>段落\"}},"
                        + "{\"type\":\"code\",\"data\":{\"code\":\"System.out.println(1);\"}},"
                        + "{\"type\":\"delimiter\",\"data\":{}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("标题一\n这是正文段落\nSystem.out.println(1);");
    }

    @Test
    void chunkNote_editorjsExtractsOldFormatListItems() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":[\"第一项\",\"第二项\"]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("第一项").contains("第二项");
    }

    @Test
    void chunkNote_editorjsExtractsNewFormatNestedListItems() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"list\",\"data\":{\"style\":\"unordered\",\"items\":["
                        + "{\"content\":\"父项\",\"items\":[{\"content\":\"子项\",\"items\":[]}]}"
                        + "]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("父项").contains("子项");
    }

    @Test
    void chunkNote_editorjsExtractsTableCellsRowByRow() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"table\",\"data\":{\"content\":[[\"表头一\",\"表头二\"],[\"值一\",\"值二\"]]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("表头一").contains("值二");
    }

    @Test
    void chunkClip_htmlFormatSplitsOnParagraphTags() {
        SourceClip clip = SourceClip.builder()
                .contentFormat("html")
                .content("<p>网页正文第一段</p><p>网页正文第二段</p>")
                .build();

        List<String> chunks = service.chunkClip(clip);

        assertThat(chunks).containsExactly("网页正文第一段\n网页正文第二段");
    }

    @Test
    void chunkClip_markdownFormatSplitsOnBlankLines() {
        SourceClip clip = SourceClip.builder()
                .contentFormat("markdown")
                .content("段落一\n\n段落二")
                .build();

        List<String> chunks = service.chunkClip(clip);

        assertThat(chunks).containsExactly("段落一\n段落二");
    }

    @Test
    void mergeAndSplit_splitsUnitLongerThanHardCapBySentenceBoundary() {
        String longSentence = "这是一句很长的话。".repeat(200); // 远超过 1500 字硬上限

        List<String> chunks = service.mergeAndSplit(List.of(longSentence));

        assertThat(chunks.size()).isGreaterThan(1);
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(800 + 50)); // 单块不应远超目标上限
    }

    @Test
    void chunkNote_hardCapsPunctuationFreeOversizedContent() {
        // 粘贴进来的 base64/日志/CSV 这类内容一个句末标点都没有，按句子切分会原样返回整段，
        // 若不强制硬切就会产出远超 HARD_CAP 的超大分块，进而超出 embedding 接口输入上限
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        Note note = Note.builder().contentType("markdown").content(sb.toString()).build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).isNotEmpty();
        chunks.forEach(c -> assertThat(c.length()).isLessThanOrEqualTo(1500)); // HARD_CAP
        assertThat(String.join("", chunks)).hasSize(3000); // 没有内容丢失
    }

    @Test
    void mergeAndSplit_mergesSmallUnitsUntilTargetSizeReached() {
        List<String> tinyUnits = List.of("短句一。", "短句二。", "短句三。");

        List<String> chunks = service.mergeAndSplit(tinyUnits);

        assertThat(chunks).hasSize(1); // 三个短单元加起来远小于300字目标下限，应该合并成一块
        assertThat(chunks.get(0)).contains("短句一").contains("短句二").contains("短句三");
    }

    @Test
    void chunkNote_treatsNullContentTypeAsRichtext() {
        Note note = Note.builder()
                .contentType(null)
                .content("<p>第一段内容</p><p>第二段内容</p>")
                .build();

        List<String> chunks = service.chunkNote(note);

        // Should treat as richtext and parse HTML paragraphs without throwing NPE
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0)).contains("第一段内容").contains("第二段内容");
    }

    @Test
    void chunkNote_richtextExtractsHeadingsAndListsAsSeparateUnits() {
        Note note = Note.builder()
                .contentType("richtext")
                .content("<h2>标题</h2><ul><li>列表项一</li><li>列表项二</li></ul><br><p>正文段落</p>")
                .build();

        List<String> chunks = service.chunkNote(note);

        // Should split on heading, list items, <br>, and paragraph boundaries
        String chunkText = chunks.get(0);
        assertThat(chunkText).contains("标题").contains("列表项一").contains("列表项二").contains("正文段落");
        // Verify no stray > character leaked from incomplete <br> tag handling
        assertThat(chunkText).doesNotContain(">");
    }

    @Test
    void chunkNote_editorjsMalformedBlockDoesNotDiscardSiblings() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"第一个段落\"}},"
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":123}},"  // Malformed: number instead of string
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"第二个段落\"}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        // Should extract first and third paragraphs despite the malformed middle block
        assertThat(chunks.get(0)).contains("第一个段落").contains("第二个段落");
    }
}
