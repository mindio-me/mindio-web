/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.dto.ShareCardBackgroundResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardResponse;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.service.AgentCallException;
import com.entropybits.worknotes.spring_boot.service.AgentServiceClient;
import com.entropybits.worknotes.spring_boot.service.UploadService;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareCardServiceTest {

    /** 记录"当前是否在事务里"，用来证明 agent 调用和上传不在事务内 */
    static class TrackingTxManager extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        boolean active;
        int begun;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition d) { active = true; begun++; }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) { active = false; }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) { active = false; }
    }

    NoteRepository noteRepository = mock(NoteRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AiSettingsService aiSettings = mock(AiSettingsService.class);
    AgentServiceClient agent = mock(AgentServiceClient.class);
    UploadService uploadService = mock(UploadService.class);
    TrackingTxManager txManager = new TrackingTxManager();
    AiProperties props = new AiProperties();
    User owner = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        when(aiSettings.current()).thenReturn(props);
        when(aiSettings.imageConfig()).thenReturn(Map.of());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
    }

    private ShareCardService service(boolean aiEnabled) {
        return new ShareCardService(noteRepository, userRepository, aiSettings, agent, uploadService, txManager, aiEnabled);
    }

    @Test
    void disabledWhenFlagOff() {
        when(aiSettings.imageConfig()).thenReturn(Map.of("ai.image.provider", "openai"));
        props.getOpenai().setApiKey("k");
        assertThat(service(false).aiAvailability()).isEqualTo("disabled");
    }

    @Test
    void notConfiguredWhenProviderBlank() {
        assertThat(service(true).aiAvailability()).isEqualTo("not_configured");
    }

    @Test
    void notConfiguredWhenProviderKeyMissing() {
        when(aiSettings.imageConfig()).thenReturn(Map.of("ai.image.provider", "openai"));
        props.getOpenai().setApiKey("");
        assertThat(service(true).aiAvailability()).isEqualTo("not_configured");
    }

    @Test
    void availableWhenProviderAndKeyPresent() {
        when(aiSettings.imageConfig()).thenReturn(Map.of("ai.image.provider", "doubao"));
        props.getDoubao().setApiKey("k");
        assertThat(service(true).aiAvailability()).isEqualTo("available");
    }

    @Test
    void getCardReturnsNullCardFieldsWhenNone() {
        when(noteRepository.findById(7L)).thenReturn(Optional.of(
                Note.builder().id(7L).owner(owner).isPublic(true).build()));
        ShareCardResponse r = service(true).getCard(7L, "alice");
        assertThat(r.noteId()).isEqualTo(7L);
        assertThat(r.isPublic()).isTrue();
        assertThat(r.source()).isNull();
        assertThat(r.imageUrl()).isNull();
        assertThat(r.gradient()).isNull();
        assertThat(r.title()).isNull();
        assertThat(r.overlayTitle()).isNull();
        assertThat(r.aiAvailability()).isEqualTo("not_configured");
    }

    @Test
    void getCardReturnsStoredCard() {
        when(noteRepository.findById(7L)).thenReturn(Optional.of(Note.builder().id(7L).owner(owner)
                .isPublic(false).shareCardSource("ai").shareCardImageUrl("/api/uploads/a.jpg")
                .shareCardBgUrl("/api/uploads/b.jpg").shareCardTitle("T").shareCardOverlay(true).build()));
        ShareCardResponse r = service(true).getCard(7L, "alice");
        assertThat(r.source()).isEqualTo("ai");
        assertThat(r.imageUrl()).isEqualTo("/api/uploads/a.jpg");
        assertThat(r.backgroundUrl()).isEqualTo("/api/uploads/b.jpg");
        assertThat(r.overlayTitle()).isTrue();
    }

    @Test
    void getCardRejectsNonOwner() {
        when(noteRepository.findById(7L)).thenReturn(Optional.of(
                Note.builder().id(7L).owner(User.builder().id(2L).build()).build()));
        assertThatThrownBy(() -> service(true).getCard(7L, "alice")).isInstanceOf(UnauthorizedException.class);
    }

    private void configureAvailable() {
        when(aiSettings.imageConfig()).thenReturn(Map.of("ai.image.provider", "openai"));
        props.getOpenai().setApiKey("k");
    }

    private Note ownedNote() {
        Note n = Note.builder().id(3L).owner(owner).title("My title")
                .content("<p>hello world</p>").contentType("richtext").build();
        when(noteRepository.findById(3L)).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    void generateBackgroundRejectsWhenDisabled() {
        configureAvailable();
        ownedNote();
        assertThatThrownBy(() -> service(false).generateBackground(3L, "alice", null))
                .isInstanceOf(BadRequestException.class);
        verify(agent, never()).shareCardBackground(any(), any(), any());
    }

    @Test
    void generateBackgroundRejectsWhenNotConfigured() {
        ownedNote();
        assertThatThrownBy(() -> service(true).generateBackground(3L, "alice", null))
                .isInstanceOf(BadRequestException.class);
        verify(agent, never()).shareCardBackground(any(), any(), any());
    }

    @Test
    void generateBackgroundRejectsNonOwner() {
        configureAvailable();
        when(noteRepository.findById(3L)).thenReturn(Optional.of(
                Note.builder().id(3L).owner(User.builder().id(2L).build()).build()));
        assertThatThrownBy(() -> service(true).generateBackground(3L, "alice", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(agent, never()).shareCardBackground(any(), any(), any());
    }

    @Test
    void generateBackgroundSavesImageAndReturnsUrl() {
        configureAvailable();
        ownedNote();
        when(agent.shareCardBackground(any(), any(), any())).thenReturn(
                Map.of("prompt", "p", "imageBase64", "AAA", "mimeType", "image/png"));
        FileResultVo saved = new FileResultVo();
        saved.setUrl("/api/uploads/x.jpg");
        when(uploadService.base64Upload("data:image/png;base64,AAA", "share-card", 3)).thenReturn(saved);
        ShareCardBackgroundResponse r = service(true).generateBackground(3L, "alice", "user prompt");
        assertThat(r).isEqualTo(new ShareCardBackgroundResponse("p", "/api/uploads/x.jpg"));
        verify(agent).shareCardBackground(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("user prompt"));
    }

    @Test
    void generateBackgroundPassesTitleAndPlainText() {
        configureAvailable();
        Note n = ownedNote();
        when(agent.shareCardBackground(any(), any(), any())).thenReturn(
                Map.of("prompt", "p", "imageBase64", "AAA", "mimeType", "image/png"));
        when(uploadService.base64Upload(anyString(), anyString(), any())).thenReturn(new FileResultVo());
        service(true).generateBackground(3L, "alice", null);
        verify(agent).shareCardBackground("My title", NotePlainText.extract(n, 1500), null);
        assertThat(NotePlainText.extract(n, 1500)).isEqualTo("hello world");
    }

    @Test
    void generateBackgroundSurfacesAgentDetail() {
        configureAvailable();
        ownedNote();
        when(agent.shareCardBackground(any(), any(), any()))
                .thenThrow(new AgentCallException("UNKNOWN", "image provider rejected the request"));
        assertThatThrownBy(() -> service(true).generateBackground(3L, "alice", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image provider rejected the request");
    }

    @Test
    void generateBackgroundCallsAgentAndUploadOutsideTransaction() {
        configureAvailable();
        ownedNote();
        java.util.concurrent.atomic.AtomicBoolean agentInTx = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean uploadInTx = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(agent.shareCardBackground(any(), any(), any())).thenAnswer(inv -> {
            agentInTx.set(txManager.active);
            return Map.of("prompt", "p", "imageBase64", "AAA", "mimeType", "image/png");
        });
        when(uploadService.base64Upload(anyString(), anyString(), any())).thenAnswer(inv -> {
            uploadInTx.set(txManager.active);
            return new FileResultVo();
        });
        service(true).generateBackground(3L, "alice", null);
        assertThat(txManager.begun).isEqualTo(1);
        assertThat(agentInTx).isFalse();
        assertThat(uploadInTx).isFalse();
    }
}
