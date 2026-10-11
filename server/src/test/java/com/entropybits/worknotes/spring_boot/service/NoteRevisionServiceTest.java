/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.EditedNoteRef;
import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AiChatConversationRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRevisionRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteRevisionServiceTest {

    @Mock NoteRevisionRepository revisionRepository;
    @Mock AiChatMessageRepository chatMessageRepository;
    @Mock AiChatConversationRepository conversationRepository;
    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock ContentIndexingService contentIndexingService;

    NoteRevisionService service;
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();

    @BeforeEach
    void setUp() {
        service = new NoteRevisionService(revisionRepository, chatMessageRepository, conversationRepository,
                noteRepository, userRepository, contentIndexingService, new ObjectMapper());
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        lenient().when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void resolveAgentTurn_isLatestUserMessageIdOfOwnedConversation() {
        AiChatConversation conv = AiChatConversation.builder().id(5L).owner(alice).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conv));
        when(chatMessageRepository.findFirstByConversationAndRoleOrderByIdDesc(conv, AiChatMessage.Role.USER))
                .thenReturn(Optional.of(AiChatMessage.builder().id(77L).build()));

        assertThat(service.resolveAgentTurn(alice, "5")).isEqualTo("77");
    }

    @Test
    void resolveAgentTurn_nullForForeignOrInvalidConversation() {
        when(conversationRepository.findById(6L))
                .thenReturn(Optional.of(AiChatConversation.builder().id(6L).owner(bob).build()));

        assertThat(service.resolveAgentTurn(alice, "6")).isNull();
        assertThat(service.resolveAgentTurn(alice, "alice")).isNull();
        assertThat(service.resolveAgentTurn(alice, null)).isNull();
    }

    @Test
    void snapshotBeforeEdit_onlyOncePerTurnAndNote() {
        Note note = Note.builder().id(9L).owner(alice).contentType("markdown").content("原文").build();
        when(revisionRepository.findByTurnIdAndNoteId("77", 9L)).thenReturn(Optional.empty());

        service.snapshotBeforeEdit(note, "77", NoteRevision.Source.AGENT);

        ArgumentCaptor<NoteRevision> saved = ArgumentCaptor.forClass(NoteRevision.class);
        verify(revisionRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("原文");
        assertThat(saved.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(saved.getValue().getSource()).isEqualTo(NoteRevision.Source.AGENT);
    }

    @Test
    void snapshotBeforeEdit_skipsWhenAlreadySnapshottedOrNoTurn() {
        Note note = Note.builder().id(9L).owner(alice).content("x").build();
        when(revisionRepository.findByTurnIdAndNoteId("77", 9L))
                .thenReturn(Optional.of(NoteRevision.builder().id(1L).build()));

        service.snapshotBeforeEdit(note, "77", NoteRevision.Source.AGENT);
        service.snapshotBeforeEdit(note, null, NoteRevision.Source.AGENT);

        verify(revisionRepository, never()).save(any());
    }

    @Test
    void undo_restoresAllNotesOfTurnAndDeletesSnapshots() {
        NoteRevision r = NoteRevision.builder().id(1L).noteId(9L).ownerId(1L).turnId("77")
                .contentType("markdown").content("改前").contentAfter("改后").build();
        Note note = Note.builder().id(9L).owner(alice).contentType("markdown").content("改后").build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("77", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        NoteRevisionService.UndoResult result = service.undo("alice", "77", false);

        assertThat(result.restoredNoteIds()).containsExactly(9L);
        assertThat(note.getContent()).isEqualTo("改前");
        verify(noteRepository).save(note);
        verify(contentIndexingService).reindexNote(9L);
        verify(revisionRepository).deleteAll(List.of(r));
    }

    @Test
    void undo_whenUserEditedAfterwards_returnsConflictsUnlessForced() {
        NoteRevision r = NoteRevision.builder().id(1L).noteId(9L).ownerId(1L).turnId("77")
                .contentType("markdown").content("改前").contentAfter("改后").build();
        Note note = Note.builder().id(9L).title("设计").owner(alice).contentType("markdown").content("改后又手改").build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("77", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        NoteRevisionService.UndoResult blocked = service.undo("alice", "77", false);
        assertThat(blocked.conflicts()).containsExactly(new EditedNoteRef(9L, "设计", true));
        assertThat(note.getContent()).isEqualTo("改后又手改");

        NoteRevisionService.UndoResult forced = service.undo("alice", "77", true);
        assertThat(forced.restoredNoteIds()).containsExactly(9L);
        assertThat(note.getContent()).isEqualTo("改前");
    }

    @Test
    void undo_editorjsSameBlocksDifferentTimestamp_isNotModified() {
        String after = "{\"time\":1,\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"x\"}}],\"version\":\"2.31.6\"}";
        String savedByEditor = "{\"time\":999,\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"x\"}}],\"version\":\"2.31.1\"}";
        NoteRevision r = NoteRevision.builder().id(1L).noteId(9L).ownerId(1L).turnId("77")
                .contentType("editorjs").content("{\"blocks\":[]}").contentAfter(after).build();
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content(savedByEditor).build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("77", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThat(service.undo("alice", "77", false).conflicts()).isEmpty();
    }

    @Test
    void undo_withoutSnapshots_throwsNotFound() {
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("88", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.undo("alice", "88", false)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void editedNotes_listsTitlesAndSkipsDeletedNotes() {
        when(revisionRepository.findByTurnIdOrderByIdAsc("77")).thenReturn(List.of(
                NoteRevision.builder().noteId(9L).contentAfter("a").build(),
                NoteRevision.builder().noteId(10L).contentAfter("b").build()));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(
                Note.builder().id(9L).title("设计").contentType("markdown").content("a").build()));
        when(noteRepository.findById(10L)).thenReturn(Optional.empty());

        assertThat(service.editedNotes("77")).containsExactly(new EditedNoteRef(9L, "设计", false));
    }

    @Test
    void undo_editorjsEditorReserialization_isNotModified() {
        String after = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"说 &quot;好&quot;\"}},"
                + "{\"id\":\"c\",\"type\":\"code\",\"data\":{\"code\":\"x\"}}]}";
        String savedByEditor = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"说 \\\"好\\\"\"}},"
                + "{\"id\":\"c\",\"type\":\"code\",\"data\":{\"code\":\"x\"},\"tunes\":{\"codeWrap\":{\"wrap\":false}}}]}";
        NoteRevision r = NoteRevision.builder().id(1L).noteId(9L).ownerId(1L).turnId("77")
                .contentType("editorjs").content("{\"blocks\":[]}").contentAfter(after).build();
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content(savedByEditor).build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("77", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThat(service.undo("alice", "77", false).conflicts()).isEmpty();
    }

    @Test
    void undo_editorjsTextChangedAfterwards_isModified() {
        String after = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"原\"}}]}";
        String edited = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"原，又改\"}}]}";
        NoteRevision r = NoteRevision.builder().id(1L).noteId(9L).ownerId(1L).turnId("77")
                .contentType("editorjs").content("{\"blocks\":[]}").contentAfter(after).build();
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content(edited).build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("77", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThat(service.undo("alice", "77", false).conflicts()).hasSize(1);
    }

    @Test
    void undo_convertRevisionRestoresMarkdownTypeAndContent() {
        String converted = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"正文\"}}]}";
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content(converted).build();
        NoteRevision r = NoteRevision.builder().noteId(9L).ownerId(1L).turnId("convert-1")
                .source(NoteRevision.Source.CONVERT).contentType("markdown").content("正文")
                .contentAfter(converted).build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("convert-1", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        NoteRevisionService.UndoResult result = service.undo("alice", "convert-1", false);

        assertThat(result.restoredNoteIds()).containsExactly(9L);
        assertThat(note.getContentType()).isEqualTo("markdown");
        assertThat(note.getContent()).isEqualTo("正文");
    }

    @Test
    void undo_restoringNonEditorjsTypeClearsStaleEditorjsSections() {
        String converted = "{\"blocks\":[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"正文\"}}]}";
        Note note = Note.builder().id(9L).owner(alice).contentType("editorjs").content(converted)
                .sectionContents(new ArrayList<>(List.of("{\"text\":\"正文\"}")))
                .sectionTypes(new ArrayList<>(List.of("paragraph"))).build();
        NoteRevision r = NoteRevision.builder().noteId(9L).ownerId(1L).turnId("convert-1")
                .source(NoteRevision.Source.CONVERT).contentType("markdown").content("正文")
                .contentAfter(converted).build();
        when(revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc("convert-1", 1L)).thenReturn(List.of(r));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        service.undo("alice", "convert-1", false);

        assertThat(note.getContentType()).isEqualTo("markdown");
        assertThat(note.getSectionContents()).isEmpty();
        assertThat(note.getSectionTypes()).isEmpty();
    }

    @Test
    void latestConvertRevision_looksUpNewestConvertSnapshotOfOwner() {
        NoteRevision r = NoteRevision.builder().turnId("convert-2").build();
        when(revisionRepository.findFirstByNoteIdAndOwnerIdAndSourceOrderByIdDesc(9L, 1L, NoteRevision.Source.CONVERT))
                .thenReturn(Optional.of(r));

        assertThat(service.latestConvertRevision(9L, 1L)).containsSame(r);
    }
}
