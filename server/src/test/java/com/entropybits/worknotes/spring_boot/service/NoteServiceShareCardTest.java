/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.NoteResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardSaveRequest;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuDocumentSnapshotRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuImageMappingRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuWikiImportMappingRepository;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteImageRefRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRevisionRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteServiceShareCardTest {

    static final String IMG = "/api/uploads/share-card/a.jpg";

    NoteRepository noteRepository = mock(NoteRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    NoteService service;
    User owner = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        service = new NoteService(noteRepository, userRepository, mock(TagRepository.class),
                mock(ProjectRepository.class), mock(FeishuWikiImportMappingRepository.class),
                mock(FeishuDocumentSnapshotRepository.class), mock(FeishuImageMappingRepository.class),
                mock(ContentIndexingService.class), mock(NoteImageRefRepository.class),
                mock(AudioTranscriptionRepository.class), mock(NoteRevisionRepository.class));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Note note(boolean isPublic) {
        Note n = Note.builder().id(42L).owner(owner).isPublic(isPublic).build();
        when(noteRepository.findById(42L)).thenReturn(Optional.of(n));
        return n;
    }

    private ShareCardSaveRequest req(String source, String imageUrl, String title, String prompt, boolean publish) {
        return new ShareCardSaveRequest(source, imageUrl, null, 3, prompt, title, false, publish);
    }

    @Test
    void savesCardAndPublishesWhenPublishTrue() {
        Note n = note(false);
        NoteResponse resp = service.saveShareCard(42L, "alice", req("gradient", IMG, "T", null, true));

        assertThat(n.getIsPublic()).isTrue();
        assertThat(n.getShareCardSource()).isEqualTo("gradient");
        assertThat(n.getShareCardGradient()).isEqualTo(3);
        assertThat(n.getShareCardImageUrl()).isEqualTo(IMG);
        assertThat(n.getShareCardTitle()).isEqualTo("T");
        assertThat(n.getShareCardOverlay()).isTrue();
        assertThat(resp).isNotNull();
    }

    @Test
    void savesCardWithoutChangingVisibilityWhenPublishFalse() {
        Note pub = note(true);
        service.saveShareCard(42L, "alice", req("upload", IMG, "T", null, false));
        assertThat(pub.getIsPublic()).isTrue();
        assertThat(pub.getShareCardOverlay()).isFalse();

        Note priv = note(false);
        service.saveShareCard(42L, "alice", req("upload", IMG, "T", null, false));
        assertThat(priv.getIsPublic()).isFalse();
        assertThat(priv.getShareCardSource()).isEqualTo("upload");
    }

    @Test
    void rejectsNonOwner() {
        Note n = Note.builder().id(42L).owner(User.builder().id(2L).build()).isPublic(false).build();
        when(noteRepository.findById(42L)).thenReturn(Optional.of(n));
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("upload", IMG, "T", null, true)))
                .isInstanceOf(UnauthorizedException.class);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownSource() {
        note(false);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("video", IMG, "T", null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMissingImageUrl() {
        note(false);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("upload", " ", "T", null, false)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("upload", null, "T", null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsTitleLongerThan200() {
        note(false);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("upload", IMG, "x".repeat(201), null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsPromptLongerThan2000() {
        note(false);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice", req("ai", IMG, "T", "x".repeat(2001), false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsExternalImageUrl() {
        note(false);
        assertThatThrownBy(() -> service.saveShareCard(42L, "alice",
                req("upload", "https://evil.example/x.png", "T", null, false)))
                .isInstanceOf(BadRequestException.class);
    }
}
