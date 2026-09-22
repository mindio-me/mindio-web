/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicBlockServiceTest {

    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final TopicBlockService service = new TopicBlockService(noteRepository, new ObjectMapper());

    @Test
    void appendItem_createsNewBlockWhenNoneExists() {
        Note note = Note.builder().id(1L).contentType("editorjs").content("{\"blocks\":[]}").build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> items = service.appendItem(1L, "timeline",
                Map.of("date", "2024-01", "title", "事件一", "description", "", "link", ""));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("title")).isEqualTo("事件一");
        assertThat(note.getContent()).contains("\"type\":\"timeline\"");
    }

    @Test
    void appendItem_appendsToExistingBlockOfSameType() {
        Note note = Note.builder().id(1L).contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"timeline\",\"data\":{\"items\":[{\"date\":\"2023\",\"title\":\"旧事件\"}]}}]}")
                .build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> items = service.appendItem(1L, "timeline",
                Map.of("date", "2024-01", "title", "新事件", "description", "", "link", ""));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("title")).isEqualTo("旧事件");
        assertThat(items.get(1).get("title")).isEqualTo("新事件");
    }

    @Test
    void appendItem_appendsToLastBlockWhenMultipleSameTypeBlocksExist() {
        Note note = Note.builder().id(1L).contentType("editorjs")
                .content("{\"blocks\":["
                        + "{\"type\":\"mediaGallery\",\"data\":{\"items\":[{\"type\":\"image\",\"url\":\"a.png\"}]}},"
                        + "{\"type\":\"mediaGallery\",\"data\":{\"items\":[{\"type\":\"image\",\"url\":\"b.png\"}]}}"
                        + "]}")
                .build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.appendItem(1L, "mediaGallery", Map.of("type", "image", "url", "c.png", "caption", ""));

        String content = note.getContent();
        int lastGalleryIdx = content.lastIndexOf("mediaGallery");
        assertThat(content.substring(0, lastGalleryIdx)).doesNotContain("c.png");
        assertThat(content.substring(lastGalleryIdx)).contains("b.png").contains("c.png");
    }

    @Test
    void appendItem_appendsChecklistItem() {
        Note note = Note.builder().id(1L).contentType("editorjs").content("{\"blocks\":[]}").build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> items = service.appendItem(1L, "checklist",
                Map.of("text", "买菜", "checked", false));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("text")).isEqualTo("买菜");
        assertThat(note.getContent()).contains("\"type\":\"checklist\"");
    }

    @Test
    void appendItem_rejectsUnknownBlockType() {
        Note note = Note.builder().id(1L).contentType("editorjs").content("{\"blocks\":[]}").build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.appendItem(1L, "notARealBlockType", Map.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void appendItem_rejectsNonEditorjsNote() {
        Note note = Note.builder().id(1L).contentType("markdown").content("正文").build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.appendItem(1L, "timeline", Map.of()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void appendItem_throwsWhenNoteNotFound() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.appendItem(99L, "timeline", Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
