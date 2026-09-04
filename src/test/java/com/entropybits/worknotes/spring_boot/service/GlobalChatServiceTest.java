/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.ChatService;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatStreamEvent;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
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
import java.util.Map;
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
    @Mock ContentChunkingService chunkingService;
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
                retrievalService, chunkingService, aiProperties, anthropicChatService, openAiChatService, deepseekChatService,
                doubaoChatService, new ObjectMapper());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        // lenient：listHistory 这类只读方法不会调用 save，避免 Mockito 严格桩报 UnnecessaryStubbing
        lenient().when(chatMessageRepository.save(any())).thenAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(System.nanoTime());
            return m;
        });
    }

    @Test
    void listHistory_returnsChronologicalOrderTruncatedToLimit() {
        // 按真实时间顺序构造（index 0 最早，index 4 最新）
        List<AiChatMessage> chronological = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            chronological.add(AiChatMessage.builder().owner(user)
                    .role(i % 2 == 0 ? AiChatMessage.Role.USER : AiChatMessage.Role.ASSISTANT)
                    .content("历史消息" + i).build());
        }
        // findTop50ByOwnerOrderByCreatedAtDesc 真实返回的是按时间倒序（最新的在前）
        List<AiChatMessage> descendingOrder = new java.util.ArrayList<>(chronological);
        java.util.Collections.reverse(descendingOrder);
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(descendingOrder);

        List<ChatMessageResponse> result = service.listHistory("alice", 3);

        // 期望：恢复为时间正序，且只保留最近 3 条（历史消息2/3/4），而不是最早的3条或倒序排列
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
    void sendMessageStream_answersDirectlyWithoutToolCallWhenNotNeeded() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("你好呀");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "你好", null, List.of(), emitter);

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "text_delta", "done");
        assertThat(recorder.events.get(1).text()).isEqualTo("你好呀");
        assertThat(recorder.events.get(2).content()).isEqualTo("你好呀");
        verify(retrievalService, org.mockito.Mockito.never()).retrieve(any(), anyString(), anyInt());
    }

    @Test
    void sendMessageStream_callsSearchToolThenAnswersWithCitations() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        Note relatedNote = Note.builder().id(7L).owner(user).title("相关笔记").build();
        when(noteRepository.findById(7L)).thenReturn(Optional.of(relatedNote));
        when(retrievalService.retrieve(eq(user), eq("用户增长"), eq(5))).thenReturn(
                List.of(new RetrievedChunk(ContentChunk.SourceType.NOTE, 7L, "片段正文", 0.9)));

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onToolCallStart(new ChatService.ToolCall("call_1", "search_workspace", Map.of("query", "用户增长")));
            listener.onDone();
            return null;
        }).doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("根据你的笔记，核心观点是留存优先");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "帮我看看用户增长的笔记", null, List.of(), emitter);

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "tool_call", "text_delta", "done");
        assertThat(recorder.events.get(1).query()).isEqualTo("用户增长");
        ChatStreamEvent done = recorder.events.get(3);
        assertThat(done.content()).isEqualTo("根据你的笔记，核心观点是留存优先");
        assertThat(done.citations()).hasSize(1);
        assertThat(done.citations().get(0).title()).isEqualTo("相关笔记");
    }

    @Test
    void sendMessageStream_forcesTextOnlyAnswerAfterThreeToolCalls() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onToolCallStart(new ChatService.ToolCall("call_x", "search_workspace", Map.of("query", "继续搜")));
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "一直搜不到就一直搜", null, List.of(), emitter);

        // 最多3次工具调用 + 第4次(index=3)强制不带tools只能给文字——但因为mock每次都返回工具调用，
        // 第4次调用listener依然会触发onToolCallStart，只是这次传的tools列表是空的（用ArgumentCaptor验证）。
        org.mockito.ArgumentCaptor<List> toolsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(anthropicChatService, org.mockito.Mockito.times(4))
                .chatStream(anyString(), any(), toolsCaptor.capture(), any());
        List<List> allToolLists = toolsCaptor.getAllValues();
        assertThat(allToolLists.get(0)).hasSize(1);
        assertThat(allToolLists.get(1)).hasSize(1);
        assertThat(allToolLists.get(2)).hasSize(1);
        assertThat(allToolLists.get(3)).isEmpty();
    }

    @Test
    void sendMessageStream_continuesWhenSearchToolThrows() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenThrow(new RuntimeException("向量库挂了"));

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onToolCallStart(new ChatService.ToolCall("call_1", "search_workspace", Map.of("query", "问题")));
            listener.onDone();
            return null;
        }).doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("虽然搜索失败，但我可以基于已有信息回答");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "问个问题", null, List.of(), emitter);

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).isEqualTo("虽然搜索失败，但我可以基于已有信息回答");
    }

    @Test
    void sendMessageStream_includesCurrentUserMessageInHistoryPassedToProvider() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of(
                AiChatMessage.builder().owner(user).role(AiChatMessage.Role.ASSISTANT).content("上一轮回答").build(),
                AiChatMessage.builder().owner(user).role(AiChatMessage.Role.USER).content("上一轮问题").build()));

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("收到");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "这一轮的新问题", null, List.of(), emitter);

        org.mockito.ArgumentCaptor<List<ChatService.ChatTurn>> historyCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(anthropicChatService).chatStream(anyString(), historyCaptor.capture(), any(), any());
        List<ChatService.ChatTurn> history = historyCaptor.getValue();
        assertThat(history).extracting(ChatService.ChatTurn::content)
                .containsExactly("上一轮问题", "上一轮回答", "这一轮的新问题");
        assertThat(history.get(history.size() - 1).role()).isEqualTo("user");
    }

    @Test
    void sendMessageStream_stillPersistsAssistantMessageWhenClientDisconnectsMidStream() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("回复的第一部分");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

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
    void sendMessageStream_streamsNarrationTextBeforeToolCallEvent() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(retrievalService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("我先搜一下");
            listener.onToolCallStart(new ChatService.ToolCall("call_1", "search_workspace", Map.of("query", "笔记")));
            listener.onDone();
            return null;
        }).doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("找到了相关内容");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "帮我搜一下", null, List.of(), emitter);

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "text_delta", "tool_call", "text_delta", "done");
        assertThat(recorder.events.get(1).text()).isEqualTo("我先搜一下");
        assertThat(recorder.events.get(4).content()).isEqualTo("我先搜一下找到了相关内容");
    }

    @Test
    void sendMessageStream_preservesPartialTextWhenGenerationFailsMidStream() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("这是已经生成了一半的");
            throw new RuntimeException("模拟生成中途失败");
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        service.sendMessageStream("alice", "问个问题", null, List.of(), emitter);

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).contains("这是已经生成了一半的");
        assertThat(done.content()).contains("生成中断");
    }

    @Test
    void sendMessageStream_attachesImageOnlyToCurrentTurnNotHistory() throws Exception {
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of());

        org.mockito.Mockito.doAnswer(inv -> {
            ChatService.StreamListener listener = inv.getArgument(3);
            listener.onTextDelta("这张图是一只猫");
            listener.onDone();
            return null;
        }).when(anthropicChatService).chatStream(anyString(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        SseEmitter emitter = captureEmitter(recorder);

        List<com.entropybits.worknotes.spring_boot.dto.AttachmentPayload> attachments = List.of(
                new com.entropybits.worknotes.spring_boot.dto.AttachmentPayload(
                        "image", "image/png", "aGVsbG8=", "https://cdn.example.com/a.png", "a.png"));

        service.sendMessageStream("alice", "这是什么", null, attachments, emitter);

        org.mockito.ArgumentCaptor<List<ChatService.ChatTurn>> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(anthropicChatService).chatStream(anyString(), historyCaptor.capture(), any(), any());
        ChatService.ChatTurn lastTurn = historyCaptor.getValue().get(historyCaptor.getValue().size() - 1);
        assertThat(lastTurn.attachments()).hasSize(1);
        assertThat(lastTurn.attachments().get(0).base64Data()).isEqualTo("aGVsbG8=");

        // user_message 事件里应该带着落库的附件引用（ChatAttachmentRef 本身就不含base64字段，
        // 所以这里天然验证了"不含原始数据，只有引用"）
        assertThat(recorder.events.get(0).attachments()).hasSize(1);
        assertThat(recorder.events.get(0).attachments().get(0).url()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(recorder.events.get(0).attachments().get(0).fileName()).isEqualTo("a.png");
    }

}
