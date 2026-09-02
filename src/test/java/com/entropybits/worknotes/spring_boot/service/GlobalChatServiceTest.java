/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.ChatService;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalChatServiceTest {

    @Mock AiChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository sourceClipRepository;
    @Mock RetrievalService retrievalService;
    @Mock AiProperties aiProperties;
    @Mock ChatService anthropicChatService;
    @Mock ChatService openAiChatService;
    @Mock ChatService deepseekChatService;
    @Mock ChatService doubaoChatService;

    private GlobalChatService service;
    private final User user = User.builder().id(1L).username("alice").build();

    @BeforeEach
    void setUp() {
        service = new GlobalChatService(chatMessageRepository, userRepository, noteRepository, sourceClipRepository,
                retrievalService, aiProperties, anthropicChatService, openAiChatService, deepseekChatService, doubaoChatService,
                new ObjectMapper());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(chatMessageRepository.save(any())).thenAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(System.nanoTime());
            return m;
        });
    }

    @Test
    void sendMessage_alwaysCallsRetrievalRegardlessOfContent() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(eq(user), eq("你好"), eq(5))).thenReturn(List.of());
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenReturn("你好呀");

        service.sendMessage("alice", "你好", null);

        verify(retrievalService).retrieve(user, "你好", 5);
    }

    @Test
    void sendMessage_loadsCurrentNoteFullTextWhenOwnedByCaller() throws Exception {
        Note note = Note.builder().id(42L).owner(user).title("我的笔记").content("笔记正文").build();
        when(noteRepository.findById(42L)).thenReturn(Optional.of(note));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenAnswer(inv -> {
            String systemPrompt = inv.getArgument(0);
            assertThat(systemPrompt).contains("我的笔记").contains("笔记正文");
            return "收到";
        });

        service.sendMessage("alice", "帮我总结一下", 42L);

        verify(anthropicChatService).chat(anyString(), any(), eq("帮我总结一下"));
    }

    @Test
    void sendMessage_ignoresCurrentNoteIdWhenNotOwnedByCaller() throws Exception {
        User otherUser = User.builder().id(2L).username("bob").build();
        Note othersNote = Note.builder().id(99L).owner(otherUser).title("别人的笔记").content("别人的正文").build();
        when(noteRepository.findById(99L)).thenReturn(Optional.of(othersNote));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenAnswer(inv -> {
            String systemPrompt = inv.getArgument(0);
            assertThat(systemPrompt).doesNotContain("别人的笔记").doesNotContain("别人的正文");
            return "收到";
        });

        service.sendMessage("alice", "帮我总结一下", 99L);
    }

    @Test
    void sendMessage_buildsCitationsFromRetrievedChunksByLookingUpTitles() throws Exception {
        Note relatedNote = Note.builder().id(7L).owner(user).title("相关笔记").build();
        when(noteRepository.findById(7L)).thenReturn(Optional.of(relatedNote));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(
                List.of(new RetrievedChunk(ContentChunk.SourceType.NOTE, 7L, "片段正文", 0.9)));
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenReturn("参考了一篇笔记");

        List<ChatMessageResponse> result = service.sendMessage("alice", "问个问题", null);

        ChatMessageResponse assistantMsg = result.get(1);
        assertThat(assistantMsg.getCitations()).hasSize(1);
        assertThat(assistantMsg.getCitations().get(0).sourceId()).isEqualTo(7L);
        assertThat(assistantMsg.getCitations().get(0).title()).isEqualTo("相关笔记");
    }

    @Test
    void sendMessage_returnsFallbackReplyAndStillSavesUserMessageWhenChatServiceThrows() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenThrow(new RuntimeException("模拟API失败"));

        List<ChatMessageResponse> result = service.sendMessage("alice", "问个问题", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("问个问题");
        assertThat(result.get(1).getContent()).isNotBlank();

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getContent()).isEqualTo("问个问题");
    }

    @Test
    void sendMessage_truncatesHistoryToLast10TurnsBeforeCallingChatService() throws Exception {
        // 按真实时间顺序构造（index 0 最早，index 29 最新）
        List<AiChatMessage> chronological = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            chronological.add(AiChatMessage.builder().owner(user)
                    .role(i % 2 == 0 ? AiChatMessage.Role.USER : AiChatMessage.Role.ASSISTANT)
                    .content("消息" + i).build());
        }
        // findTop50ByOwnerOrderByCreatedAtDesc 真实返回的是按时间倒序（最新的在前）
        List<AiChatMessage> descendingOrder = new java.util.ArrayList<>(chronological);
        java.util.Collections.reverse(descendingOrder);
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(descendingOrder);
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());
        when(anthropicChatService.chat(anyString(), any(), anyString())).thenReturn("好的");

        service.sendMessage("alice", "新问题", null);

        ArgumentCaptor<List<ChatService.ChatTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(anthropicChatService).chat(anyString(), historyCaptor.capture(), eq("新问题"));
        assertThat(historyCaptor.getValue()).hasSize(20);
    }
}
