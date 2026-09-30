/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatResumeRequest;
import com.entropybits.worknotes.spring_boot.dto.SendChatMessageRequest;
import com.entropybits.worknotes.spring_boot.service.GlobalChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
        request.setConversationId(7L);
        request.setCurrentNoteId(42L);

        SseEmitter emitter = controller.sendMessage(request, principal);

        assertThat(emitter).isNotNull();
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.timeout(2000))
                .sendMessageStream(
                        org.mockito.ArgumentMatchers.eq("alice"),
                        org.mockito.ArgumentMatchers.eq("你好"),
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(SseEmitter.class));
    }

    @Test
    void sendMessage_withNullConversationIdDelegatesNullThrough() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");

        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setContent("新话题");

        controller.sendMessage(request, principal);

        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.timeout(2000))
                .sendMessageStream(
                        org.mockito.ArgumentMatchers.eq("alice"),
                        org.mockito.ArgumentMatchers.eq("新话题"),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.any(SseEmitter.class));
    }

    @Test
    void getConversationMessages_delegatesToServiceWithConversationId() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");
        when(chatService.getConversationMessages("alice", 9L)).thenReturn(List.of());

        ResponseEntity<List<ChatMessageResponse>> response = controller.getConversationMessages(9L, principal);

        assertThat(response.getBody()).isEmpty();
        verify(chatService).getConversationMessages("alice", 9L);
    }

    @Test
    void resume_returnsEmitterImmediatelyAndDelegatesToServiceOnBackgroundThread() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");

        ChatResumeRequest request = new ChatResumeRequest();
        request.setConversationId(7L);
        request.setProposalId("p1");
        request.setDecision("accept");

        SseEmitter emitter = controller.resume(request, principal);

        assertThat(emitter).isNotNull();
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.timeout(2000))
                .resumeStream(
                        org.mockito.ArgumentMatchers.eq("alice"),
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.eq("p1"),
                        org.mockito.ArgumentMatchers.eq("accept"),
                        org.mockito.ArgumentMatchers.any(SseEmitter.class));
    }

    @Test
    void listConversations_delegatesToService() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");
        when(chatService.listConversations("alice")).thenReturn(List.of());

        ResponseEntity<List<com.entropybits.worknotes.spring_boot.dto.ConversationResponse>> response =
                controller.listConversations(principal);

        assertThat(response.getBody()).isEmpty();
        verify(chatService).listConversations("alice");
    }

    @Test
    void renameConversation_delegatesToServiceWithNewTitle() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");
        com.entropybits.worknotes.spring_boot.dto.UpdateConversationTitleRequest request =
                new com.entropybits.worknotes.spring_boot.dto.UpdateConversationTitleRequest();
        request.setTitle("新标题");
        when(chatService.renameConversation("alice", 5L, "新标题"))
                .thenReturn(com.entropybits.worknotes.spring_boot.dto.ConversationResponse.builder()
                        .id(5L).title("新标题").build());

        ResponseEntity<com.entropybits.worknotes.spring_boot.dto.ConversationResponse> response =
                controller.renameConversation(5L, request, principal);

        assertThat(response.getBody().getTitle()).isEqualTo("新标题");
    }

    @Test
    void deleteConversation_delegatesToServiceAndReturnsNoContent() {
        GlobalChatController controller = new GlobalChatController(chatService);
        when(principal.getUsername()).thenReturn("alice");

        ResponseEntity<Void> response = controller.deleteConversation(5L, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(chatService).deleteConversation("alice", 5L);
    }
}
