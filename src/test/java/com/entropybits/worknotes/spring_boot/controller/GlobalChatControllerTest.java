/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.SendChatMessageRequest;
import com.entropybits.worknotes.spring_boot.service.GlobalChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalChatControllerTest {

    @Mock GlobalChatService chatService;
    @Mock UserDetails principal;

    @Test
    void sendMessage_returnsEmitterImmediatelyAndDelegatesToServiceOnBackgroundThread() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");

        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setContent("你好");
        request.setCurrentNoteId(42L);

        SseEmitter emitter = controller.sendMessage(request, principal);

        assertThat(emitter).isNotNull();
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.timeout(2000))
                .sendMessageStream(
                        org.mockito.ArgumentMatchers.eq("alice"),
                        org.mockito.ArgumentMatchers.eq("你好"),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(SseEmitter.class));
    }

    @Test
    void listMessages_defaultsLimitTo50WhenNotProvided() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");
        when(chatService.listHistory("alice", 50)).thenReturn(List.of());

        controller.listMessages(50, principal);

        org.mockito.Mockito.verify(chatService).listHistory("alice", 50);
    }
}
