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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentChunkingServiceTest {

    private final LocalFileExtractionService extractionService = mock(LocalFileExtractionService.class);
    private final ContentChunkingService service = new ContentChunkingService(new ObjectMapper(), extractionService);

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
    void chunkNote_editorjsExtractsReferencesTitleAndNote() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"references\",\"data\":{\"items\":["
                        + "{\"kind\":\"link\",\"title\":\"参考文章一\",\"url\":\"https://example.com\",\"note\":\"很可靠\"},"
                        + "{\"kind\":\"note\",\"title\":\"另一篇笔记\",\"noteId\":42}"
                        + "]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("参考文章一").contains("很可靠").contains("另一篇笔记");
        assertThat(chunks.get(0)).doesNotContain("https://example.com");
    }

    @Test
    void chunkNote_editorjsExtractsGalleryCaptionsOnly() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"mediaGallery\",\"data\":{\"items\":["
                        + "{\"type\":\"image\",\"url\":\"https://example.com/a.png\",\"caption\":\"截图说明\"},"
                        + "{\"type\":\"video\",\"embedUrl\":\"https://youtube.com/embed/x\"}"
                        + "]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).isEqualTo("截图说明");
    }

    @Test
    void chunkNote_editorjsExtractsTimelineDateTitleDescription() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"timeline\",\"data\":{\"items\":["
                        + "{\"date\":\"2024-01\",\"title\":\"事件一\",\"description\":\"详细说明\"}"
                        + "]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("2024-01").contains("事件一").contains("详细说明");
    }

    @Test
    void chunkNote_editorjsExtractsChecklistItemsWithCheckedState() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"checklist\",\"data\":{\"items\":["
                        + "{\"text\":\"买菜\",\"checked\":false},"
                        + "{\"text\":\"洗车\",\"checked\":true}"
                        + "]}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("[ ] 买菜").contains("[x] 洗车");
    }

    @Test
    void chunkNote_editorjsExtractsWarningTitleAndMessage() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"warning\",\"data\":{\"title\":\"注意\",\"message\":\"别忘了带钥匙\"}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("注意").contains("别忘了带钥匙");
    }

    @Test
    void chunkNote_editorjsExtractsLinkToolMetaTitleAndDescription() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"linkTool\",\"data\":{\"link\":\"https://example.com\","
                        + "\"meta\":{\"title\":\"示例站点\",\"description\":\"一个例子\"}}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).contains("示例站点").contains("一个例子");
        assertThat(chunks.get(0)).doesNotContain("https://example.com");
    }

    @Test
    void chunkNote_editorjsExtractsAttachesTitle() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"attaches\",\"data\":{\"title\":\"报告.pdf\","
                        + "\"file\":{\"url\":\"/uploads/a.pdf\"}}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).isEqualTo("报告.pdf");
    }

    @Test
    void chunkNote_editorjsExtractsEmbedVideoAudioCaptions() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"embed\",\"data\":{\"sourceUrl\":\"https://youtube.com/x\",\"caption\":\"嵌入说明\"}},"
                        + "{\"type\":\"video\",\"data\":{\"url\":\"/uploads/a.mp4\",\"caption\":\"视频说明\"}},"
                        + "{\"type\":\"audio\",\"data\":{\"url\":\"/uploads/a.mp3\",\"caption\":\"音频说明\"}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(String.join("\n", chunks)).contains("嵌入说明").contains("视频说明").contains("音频说明");
    }

    @Test
    void chunkNote_editorjsSkipsAudioRecordWithNoTextField() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"paragraph\",\"data\":{\"text\":\"正文段落\"}},"
                        + "{\"type\":\"audioRecord\",\"data\":{\"url\":\"/uploads/a.webm\",\"duration\":5}}"
                        + "]}")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("正文段落");
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
    void chunkPlainText_splitsOnBlankLinesLikeMarkdown() {
        List<String> chunks = service.chunkPlainText("第一段文字。\n\n第二段文字。");

        assertThat(chunks).containsExactly("第一段文字。\n第二段文字。");
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

    @Test
    void chunkNote_richtextFoldsInOcrTextForReferencedImages() {
        when(extractionService.findExtractedTextForImageUrl("/uploads/public/note/abc.png"))
                .thenReturn("图片里的文字");
        Note note = Note.builder()
                .contentType("richtext")
                .content("<p>笔记正文</p><img src=\"/uploads/public/note/abc.png\" alt=\"截图\" />")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(String.join("\n", chunks)).contains("图片里的文字");
    }

    @Test
    void chunkNote_markdownFoldsInOcrTextForReferencedImages() {
        when(extractionService.findExtractedTextForImageUrl("/uploads/public/note/abc.png"))
                .thenReturn("图片里的文字");
        Note note = Note.builder()
                .contentType("markdown")
                .content("正文内容\n\n![截图](/uploads/public/note/abc.png)")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(String.join("\n", chunks)).contains("图片里的文字");
    }

    @Test
    void chunkNote_doesNotFoldInAnythingWhenImageHasNoOcrTextYet() {
        Note note = Note.builder()
                .contentType("richtext")
                .content("<p>笔记正文</p><img src=\"/uploads/public/note/pending.png\" />")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("笔记正文");
    }

    @Test
    void extractImageUrls_findsMultipleHtmlImageUrls() {
        Note note = Note.builder()
                .contentType("richtext")
                .content("<p>正文</p><img src=\"/uploads/a.png\"><img src='/uploads/b.png'>")
                .build();

        assertThat(service.extractImageUrls(note)).containsExactly("/uploads/a.png", "/uploads/b.png");
    }

    @Test
    void extractImageUrls_findsMarkdownImageUrls() {
        Note note = Note.builder()
                .contentType("markdown")
                .content("正文\n\n![截图](/uploads/a.png)")
                .build();

        assertThat(service.extractImageUrls(note)).containsExactly("/uploads/a.png");
    }

    @Test
    void extractImageUrls_returnsEmptyForEditorjsContentType() {
        Note note = Note.builder()
                .contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"/uploads/a.png\"}}}]}")
                .build();

        assertThat(service.extractImageUrls(note)).isEmpty();
    }

    @Test
    void extractImageUrls_returnsEmptyWhenNoteHasNoImages() {
        Note note = Note.builder().contentType("markdown").content("纯文字笔记，没有图片。").build();

        assertThat(service.extractImageUrls(note)).isEmpty();
    }

    @Test
    void chunkNote_prependsTitleAsOwnLeadingChunk() {
        Note note = Note.builder()
                .title("竞品分析 - XX产品")
                .contentType("markdown")
                .content("正文完全没提到这几个字，只是随便写点别的东西凑数。")
                .build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks.get(0)).isEqualTo("竞品分析 - XX产品");
        assertThat(chunks).hasSize(2);
    }

    @Test
    void chunkNote_omitsTitleChunkWhenTitleBlank() {
        Note note = Note.builder().title("   ").contentType("markdown").content("正文内容").build();

        List<String> chunks = service.chunkNote(note);

        assertThat(chunks).containsExactly("正文内容");
    }

    @Test
    void chunkClip_prependsTitleAsOwnLeadingChunk() {
        SourceClip clip = SourceClip.builder()
                .title("行业竞品分析报告")
                .contentFormat("markdown")
                .content("网页正文")
                .build();

        List<String> chunks = service.chunkClip(clip);

        assertThat(chunks.get(0)).isEqualTo("行业竞品分析报告");
        assertThat(chunks).hasSize(2);
    }
}
