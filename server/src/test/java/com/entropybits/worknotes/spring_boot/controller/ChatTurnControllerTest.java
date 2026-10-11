/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.EditedNoteRef;
import com.entropybits.worknotes.spring_boot.service.NoteRevisionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatTurnControllerTest {

    final NoteRevisionService revisionService = mock(NoteRevisionService.class);
    final ChatTurnController controller = new ChatTurnController(revisionService);
    final UserDetails alice = User.withUsername("alice").password("x").authorities(List.of()).build();

    @Test
    void undo_returns409WithConflictsWhenUserEditedAfterwards() {
        EditedNoteRef conflict = new EditedNoteRef(9L, "设计", true);
        when(revisionService.undo("alice", "77", false))
                .thenReturn(new NoteRevisionService.UndoResult(List.of(conflict), List.of()));

        ResponseEntity<Map<String, Object>> response = controller.undo("77", false, alice);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("conflicts", List.of(conflict));
    }

    @Test
    void undo_returnsRestoredNoteIds() {
        when(revisionService.undo("alice", "77", true))
                .thenReturn(new NoteRevisionService.UndoResult(List.of(), List.of(9L)));

        ResponseEntity<Map<String, Object>> response = controller.undo("77", true, alice);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("restoredNoteIds", List.of(9L));
    }
}
