/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.dto.ShareCardResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareCardServiceTest {

    NoteRepository noteRepository = mock(NoteRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    AiSettingsService aiSettings = mock(AiSettingsService.class);
    AiProperties props = new AiProperties();
    User owner = User.builder().id(1L).build();

    @BeforeEach
    void setUp() {
        when(aiSettings.current()).thenReturn(props);
        when(aiSettings.imageConfig()).thenReturn(Map.of());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
    }

    private ShareCardService service(boolean aiEnabled) {
        return new ShareCardService(noteRepository, userRepository, aiSettings, aiEnabled);
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
}
