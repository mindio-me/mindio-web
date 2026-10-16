/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import com.entropybits.worknotes.spring_boot.service.AudioTranscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class AudioTranscriptionControllerTest {

    AudioTranscriptionService service = mock(AudioTranscriptionService.class);
    AudioTranscriptionController controller = new AudioTranscriptionController(service, new ObjectMapper());
    UserDetails alice = User.withUsername("alice").password("x").roles("USER").build();
    AudioTranscription record = AudioTranscription.builder().noteId(1L).blockId("b")
            .status(AudioTranscription.Status.PENDING).stage(AudioTranscription.Stage.SUMMARY)
            .triggerType(AudioTranscription.TriggerType.MANUAL).build();

    @BeforeEach
    void setUp() {
        when(service.saveUtterances(any(), any(), any())).thenReturn(record);
        when(service.patch(any(), any(), any(), any())).thenReturn(record);
    }

    @Test
    void saveUtterances_checksOwnerThenDelegates() {
        var res = controller.saveUtterances(1L, "b", List.of(new AudioTranscriptionService.UtteranceEdit(0, "x")), alice);
        verify(service).requireOwner(1L, "alice");
        verify(service).saveUtterances(eq(1L), eq("b"), eq(List.of(new AudioTranscriptionService.UtteranceEdit(0, "x"))));
        assertThat(res.status()).isEqualTo("PENDING");
    }

    @Test
    void saveUtterances_forbiddenForOtherUser() {
        doThrow(new ForbiddenException("无权访问这篇笔记")).when(service).requireOwner(1L, "alice");
        assertThatThrownBy(() -> controller.saveUtterances(1L, "b", List.of(), alice))
                .isInstanceOf(ForbiddenException.class);
        verify(service, never()).saveUtterances(any(), any(), any());
    }

    @Test
    void patch_parsesSceneAndNames() {
        controller.patch(1L, "b", new AudioTranscriptionController.PatchRequest(Map.of("1", "张总"), "lecture"), alice);
        verify(service).requireOwner(1L, "alice");
        verify(service).patch(1L, "b", Map.of("1", "张总"), AudioTranscription.Scene.LECTURE);
    }

    @Test
    void patch_withoutSceneLeavesItNull() {
        controller.patch(1L, "b", new AudioTranscriptionController.PatchRequest(Map.of("1", "张总"), null), alice);
        verify(service).patch(eq(1L), eq("b"), eq(Map.of("1", "张总")), isNull());
    }

    @Test
    void patch_rejectsUnknownScene() {
        assertThatThrownBy(() -> controller.patch(1L, "b", new AudioTranscriptionController.PatchRequest(null, "PARTY"), alice))
                .isInstanceOf(BadRequestException.class);
    }
}
