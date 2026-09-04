/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatStreamEvent;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GlobalChatService 现在只做"持久化用户消息 -> 调用AgentServiceClient并转发事件 ->
 * 持久化最终回复"，agent推理循环本身已经搬到独立的Python/LangGraph服务，所以这里
 * mock的是 AgentServiceClient，不再是四个 ChatService provider bean。
 */
@ExtendWith(MockitoExtension.class)
class GlobalChatServiceTest {

    @Mock AiChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository sourceClipRepository;
    @Mock ContentChunkingService chunkingService;
    @Mock AgentServiceClient agentServiceClient;

    private GlobalChatService service;
    private final User user = User.builder().id(1L).username("alice").build();

    @BeforeEach
    void setUp() {
        service = new GlobalChatService(chatMessageRepository, userRepository, noteRepository, sourceClipRepository,
                chunkingService, agentServiceClient, new ObjectMapper());
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        // lenient：listHistory 这类只读方法不会调用 save，避免 Mockito 严格桩报 UnnecessaryStubbing
        lenient().when(chatMessageRepository.save(any())).thenAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(System.nanoTime());
            return m;
        });
    }

    @Test
    void listHistory_returnsChronologicalOrderTruncatedToLimit() {
        List<AiChatMessage> chronological = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            chronological.add(AiChatMessage.builder().owner(user)
                    .role(i % 2 == 0 ? AiChatMessage.Role.USER : AiChatMessage.Role.ASSISTANT)
                    .content("历史消息" + i).build());
        }
        List<AiChatMessage> descendingOrder = new java.util.ArrayList<>(chronological);
        java.util.Collections.reverse(descendingOrder);
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(descendingOrder);

        List<ChatMessageResponse> result = service.listHistory("alice", 3);

        assertThat(result).extracting(ChatMessageResponse::getContent)
                .containsExactly("历史消息2", "历史消息3", "历史消息4");
    }

    @Test
    void listHistory_withNegativeLimitReturnsEmptyListWithoutThrowing() {
        List<AiChatMessage> descendingOrder = List.of(
                AiChatMessage.builder().owner(user).role(AiChatMessage.Role.USER).content("消息").build());
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(descendingOrder);

        List<ChatMessageResponse> result = service.listHistory("alice", -1);

        assertThat(result).isEmpty();
    }

    private static class RecordingEmitterListener {
        final List<ChatStreamEvent> events = new java.util.ArrayList<>();
    }

    private SseEmitter captureEmitter(RecordingEmitterListener recorder) {
        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        try {
            org.mockito.Mockito.doAnswer(inv -> {
                String json = inv.getArgument(0);
                recorder.events.add(new ObjectMapper().readValue(json, ChatStreamEvent.class));
                return null;
            }).when(emitter).send(org.mockito.ArgumentMatchers.anyString());
        } catch (Exception ignored) {
        }
        return emitter;
    }

    @Test
    void sendMessageStream_persistsAndForwardsPlainTextReply() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onTextDelta("你好呀");
            listener.onDone("你好呀", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "你好", null, List.of(), emitter);

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "text_delta", "done");
        assertThat(recorder.events.get(1).text()).isEqualTo("你好呀");
        assertThat(recorder.events.get(2).content()).isEqualTo("你好呀");
    }

    @Test
    void sendMessageStream_passesConversationIdEqualToUsernameAndCurrentNoteContext() throws Exception {
        Note currentNote = Note.builder().id(9L).owner(user).title("我的笔记").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(currentNote));
        when(chunkingService.chunkNote(currentNote)).thenReturn(List.of("正文内容"));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onDone("好的", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        service.sendMessageStream("alice", "问题", 9L, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(agentServiceClient).streamChat(
                eq("alice"), eq("问题"), eq("alice"),
                argThat(ctx -> ctx != null && ctx.contains("我的笔记") && ctx.contains("正文内容")),
                any(), any());
    }

    @Test
    void sendMessageStream_forwardsToolCallEventFromAgentService() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onToolCall("用户增长");
            listener.onDone("根据笔记回答", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我看看笔记", null, List.of(), captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "tool_call", "done");
        assertThat(recorder.events.get(1).query()).isEqualTo("用户增长");
    }

    @Test
    void sendMessageStream_resolvesCitationTitlesBySourceTypeAndId() throws Exception {
        Note relatedNote = Note.builder().id(7L).owner(user).title("相关笔记").build();
        when(noteRepository.findById(7L)).thenReturn(Optional.of(relatedNote));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onDone("根据你的笔记，核心观点是留存优先",
                    List.of(new ChatCitation("NOTE", 7L, null)));
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我看看用户增长的笔记", null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.citations()).hasSize(1);
        assertThat(done.citations().get(0).title()).isEqualTo("相关笔记");
    }

    @Test
    void sendMessageStream_preservesPartialTextWhenAgentServiceReportsErrorMidStream() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onTextDelta("这是已经生成了一半的");
            listener.onError("下游模型报错了");
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "问个问题", null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).contains("这是已经生成了一半的");
        assertThat(done.content()).contains("生成中断");
    }

    @Test
    void sendMessageStream_fallsBackToGenericMessageWhenAgentServiceUnreachable() throws Exception {
        doThrow(new RuntimeException("connection refused"))
                .when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "你好", null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).isEqualTo("抱歉，这次没能回复，换个说法试试？");
    }

    @Test
    void sendMessageStream_stillPersistsAssistantMessageWhenClientDisconnectsMidStream() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onTextDelta("回复的第一部分");
            listener.onDone("回复的第一部分", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("client gone"))
                .when(emitter).send(org.mockito.ArgumentMatchers.anyString());

        service.sendMessageStream("alice", "你好", null, List.of(), emitter);

        org.mockito.ArgumentCaptor<AiChatMessage> captor = org.mockito.ArgumentCaptor.forClass(AiChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        AiChatMessage assistantMsg = captor.getAllValues().get(1);
        assertThat(assistantMsg.getRole()).isEqualTo(AiChatMessage.Role.ASSISTANT);
        assertThat(assistantMsg.getContent()).isEqualTo("回复的第一部分");
    }

    @Test
    void sendMessageStream_passesAttachmentsThroughToAgentService() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(5);
            listener.onDone("这张图是一只猫", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any());

        List<com.entropybits.worknotes.spring_boot.dto.AttachmentPayload> attachments = List.of(
                new com.entropybits.worknotes.spring_boot.dto.AttachmentPayload(
                        "image", "image/png", "aGVsbG8=", "https://cdn.example.com/a.png", "a.png"));

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "这是什么", null, attachments, captureEmitter(recorder));

        verify(agentServiceClient).streamChat(eq("alice"), eq("这是什么"), eq("alice"), any(), eq(attachments), any());

        // user_message 事件里应该带着落库的附件引用（ChatAttachmentRef 本身就不含base64字段）
        assertThat(recorder.events.get(0).attachments()).hasSize(1);
        assertThat(recorder.events.get(0).attachments().get(0).url()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(recorder.events.get(0).attachments().get(0).fileName()).isEqualTo("a.png");
    }
}
