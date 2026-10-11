/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteConversionServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRevisionService revisionService;
    @Mock ContentIndexingService contentIndexingService;

    final ObjectMapper om = new ObjectMapper();
    NoteConversionService service;
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();

    @BeforeEach
    void setUp() {
        service = new NoteConversionService(noteRepository, userRepository, revisionService, contentIndexingService, om);
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        lenient().when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void convertsMarkdownNoteInPlaceAfterSnapshottingOriginal() throws Exception {
        Note note = Note.builder().id(9L).owner(alice).contentType("markdown").content("# 标题\n\n正文").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));
        List<String> typeAtSnapshot = new ArrayList<>();
        doAnswer(inv -> typeAtSnapshot.add(((Note) inv.getArgument(0)).getContentType()))
                .when(revisionService).snapshotBeforeEdit(any(), anyString(), eq(NoteRevision.Source.CONVERT));

        NoteConversionService.Result result = service.convertMarkdownToEditorjs("alice", 9L);

        assertThat(result.turnId()).startsWith("convert-");
        assertThat(typeAtSnapshot).containsExactly("markdown");
        assertThat(note.getContentType()).isEqualTo("editorjs");
        JsonNode root = om.readTree(note.getContent());
        assertThat(root.path("blocks")).extracting(b -> b.path("type").asText()).containsExactly("header", "paragraph");
        verify(revisionService).recordAfter(result.turnId(), 9L, note.getContent());
        verify(contentIndexingService).reindexNote(9L);
    }

    @Test
    void fillsSectionsLikeTheEditorSaveDoes() throws Exception {
        Note note = Note.builder().id(9L).owner(alice).contentType("markdown").content("# 标题\n\n正文").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        service.convertMarkdownToEditorjs("alice", 9L);

        JsonNode blocks = om.readTree(note.getContent()).path("blocks");
        assertThat(note.getSectionTypes()).containsExactly("header", "paragraph");
        assertThat(note.getSectionContents()).hasSize(2);
        for (int i = 0; i < blocks.size(); i++) {
            assertThat(om.readTree(note.getSectionContents().get(i))).isEqualTo(blocks.get(i).path("data"));
        }
    }

    @Test
    void rejectsNonMarkdownNote() {
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content("{\"blocks\":[]}").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.convertMarkdownToEditorjs("alice", 9L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("只有 Markdown 笔记可以转换");
        verifyNoInteractions(revisionService);
    }

    @Test
    void rejectsForeignNote() {
        Note note = Note.builder().id(9L).owner(bob).contentType("markdown").content("x").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.convertMarkdownToEditorjs("alice", 9L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(revisionService);
    }
}
