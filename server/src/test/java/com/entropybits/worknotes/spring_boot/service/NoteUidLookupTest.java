/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteUidLookupTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @InjectMocks NoteService noteService;

    User alice = User.builder().id(1L).username("alice").build();
    static final String UID = "11111111-1111-1111-1111-111111111111";

    @Test
    void findsOwnNoteByUid() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(noteRepository.findByOwnerAndUidIn(alice, List.of(UID)))
                .thenReturn(List.of(Note.builder().id(42L).owner(alice).uid(UID).title("t").build()));

        assertThat(noteService.findIdByUid("alice", UID)).isEqualTo(42L);
    }

    @Test
    void missingNoteIs404() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(noteRepository.findByOwnerAndUidIn(alice, List.of(UID))).thenReturn(List.of());

        assertThatThrownBy(() -> noteService.findIdByUid("alice", UID))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("这篇笔记不在本机");
    }
}
