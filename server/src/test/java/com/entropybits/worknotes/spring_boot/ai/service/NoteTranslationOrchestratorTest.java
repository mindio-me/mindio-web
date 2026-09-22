/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.dto.TranslationRequest;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.integration.wechat.EditorJsToHtmlConverter;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoteTranslationOrchestratorTest {

    private final AiProperties aiProperties = new AiProperties();
    private final AiTranslationService anthropicService = mock(AiTranslationService.class);
    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final NoteTranslationOrchestrator orchestrator = new NoteTranslationOrchestrator(
            aiProperties, anthropicService, anthropicService, anthropicService, anthropicService,
            noteRepository, new ObjectMapper(), new EditorJsToHtmlConverter(new ObjectMapper()));

    @Test
    void faithfulTranslate_referencesBlockTranslatesTitleAndNoteKeepsUrl() throws Exception {
        aiProperties.setProvider("anthropic");
        Note source = Note.builder()
                .title("原标题")
                .contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                        + "{\"kind\":\"link\",\"title\":\"参考文章\",\"url\":\"https://example.com\",\"note\":\"很可靠\"}"
                        + "]}}]}")
                .build();
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 顺序：[title, item0.title, item0.note]
        when(anthropicService.translateTexts(any(), eq("en")))
                .thenReturn(List.of("Translated Title", "Reference Article", "Very reliable"));

        Note translated = orchestrator.translate(source, translationRequest());

        assertThat(translated.getContent()).contains("\"title\":\"Reference Article\"");
        assertThat(translated.getContent()).contains("\"note\":\"Very reliable\"");
        assertThat(translated.getContent()).contains("\"url\":\"https://example.com\""); // url不翻译
    }

    @Test
    void faithfulTranslate_mediaGalleryBlockTranslatesCaptionOnly() throws Exception {
        aiProperties.setProvider("anthropic");
        Note source = Note.builder()
                .title("原标题")
                .contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"mediaGallery\",\"data\":{\"items\":["
                        + "{\"type\":\"image\",\"url\":\"https://example.com/a.png\",\"caption\":\"截图说明\"}"
                        + "]}}]}")
                .build();
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(anthropicService.translateTexts(any(), eq("en")))
                .thenReturn(List.of("Translated Title", "Screenshot caption"));

        Note translated = orchestrator.translate(source, translationRequest());

        assertThat(translated.getContent()).contains("\"caption\":\"Screenshot caption\"");
        assertThat(translated.getContent()).contains("\"url\":\"https://example.com/a.png\"");
    }

    @Test
    void faithfulTranslate_timelineBlockTranslatesTitleAndDescriptionKeepsDate() throws Exception {
        aiProperties.setProvider("anthropic");
        Note source = Note.builder()
                .title("原标题")
                .contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":["
                        + "{\"date\":\"2024-01\",\"title\":\"事件一\",\"description\":\"详情\"}"
                        + "]}}]}")
                .build();
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(anthropicService.translateTexts(any(), eq("en")))
                .thenReturn(List.of("Translated Title", "Event One", "Details"));

        Note translated = orchestrator.translate(source, translationRequest());

        assertThat(translated.getContent()).contains("\"title\":\"Event One\"");
        assertThat(translated.getContent()).contains("\"description\":\"Details\"");
        assertThat(translated.getContent()).contains("\"date\":\"2024-01\"");
    }

    @Test
    void faithfulTranslate_referencesBlockWithTwoItemsDoesNotCrossItemFields() throws Exception {
        aiProperties.setProvider("anthropic");
        Note source = Note.builder()
                .title("原标题")
                .contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"references\",\"data\":{\"items\":["
                        + "{\"kind\":\"link\",\"title\":\"标题一\",\"url\":\"https://example.com/1\",\"note\":\"备注一\"},"
                        + "{\"kind\":\"link\",\"title\":\"标题二\",\"url\":\"https://example.com/2\",\"note\":\"备注二\"}"
                        + "]}}]}")
                .build();
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 收集顺序：[title, item0.title(subIndex=0), item0.note(subIndex=1),
        //           item1.title(subIndex=2), item1.note(subIndex=3)]
        when(anthropicService.translateTexts(any(), eq("en")))
                .thenReturn(List.of("Translated Title", "Title One", "Note One", "Title Two", "Note Two"));

        Note translated = orchestrator.translate(source, translationRequest());

        JsonNode items = new ObjectMapper().readTree(translated.getContent())
                .path("blocks").get(0).path("data").path("items");
        assertThat(items.get(0).path("title").asText()).isEqualTo("Title One");
        assertThat(items.get(0).path("note").asText()).isEqualTo("Note One");
        assertThat(items.get(0).path("url").asText()).isEqualTo("https://example.com/1");
        assertThat(items.get(1).path("title").asText()).isEqualTo("Title Two");
        assertThat(items.get(1).path("note").asText()).isEqualTo("Note Two");
        assertThat(items.get(1).path("url").asText()).isEqualTo("https://example.com/2");
    }

    private TranslationRequest request() {
        return translationRequest();
    }

    private TranslationRequest translationRequest() {
        TranslationRequest r = new TranslationRequest();
        r.setTargetLanguage("en");
        return r;
    }
}
