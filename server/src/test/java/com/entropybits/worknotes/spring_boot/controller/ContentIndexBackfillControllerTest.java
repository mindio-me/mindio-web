/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.ContentIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentIndexBackfillControllerTest {

    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository clipRepository;
    @Mock UserRepository userRepository;
    @Mock ContentIndexingService contentIndexingService;

    private final UserDetails principal =
            org.springframework.security.core.userdetails.User.withUsername("alice").password("x").authorities("USER").build();

    @Test
    void backfill_reindexesAllNotesAndClipsForCurrentUser() {
        ContentIndexBackfillController controller =
                new ContentIndexBackfillController(noteRepository, clipRepository, userRepository, contentIndexingService);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(noteRepository.findByOwner(user)).thenReturn(List.of(
                Note.builder().id(10L).build(), Note.builder().id(11L).build()));
        when(clipRepository.findByOwner(user)).thenReturn(List.of(
                SourceClip.builder().id(20L).build()));

        ResponseEntity<Void> response = controller.backfill(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(contentIndexingService).reindexNote(10L);
        verify(contentIndexingService).reindexNote(11L);
        verify(contentIndexingService).reindexClip(20L);
    }
}
