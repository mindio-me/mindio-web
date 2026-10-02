/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatStreamEvent;
import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AgentConversationStateRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatConversationRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GlobalChatService 现在按会话（AiChatConversation）而不是按用户存取历史；agent推理循环
 * 本身在独立的Python/LangGraph服务，这里mock的是 AgentServiceClient。
 */
@ExtendWith(MockitoExtension.class)
class GlobalChatServiceTest {

    @Mock AiChatMessageRepository chatMessageRepository;
    @Mock AiChatConversationRepository conversationRepository;
    @Mock AgentConversationStateRepository agentConversationStateRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock ProjectRepository projectRepository;
    @Mock SourceClipRepository sourceClipRepository;
    @Mock ContentChunkingService chunkingService;
    @Mock AgentServiceClient agentServiceClient;
    @Mock LocalFileExtractionService extractionService;
    @Mock AiTranslationService anthropicService;
    @Mock AiTranslationService openAiService;
    @Mock AiTranslationService deepseekService;
    @Mock AiTranslationService doubaoService;

    private GlobalChatService service;
    private final User user = User.builder().id(1L).username("alice").build();

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setProvider("anthropic");
        service = new GlobalChatService(chatMessageRepository, conversationRepository, agentConversationStateRepository,
                userRepository, noteRepository, projectRepository,
                sourceClipRepository, chunkingService, agentServiceClient, extractionService, new ObjectMapper(),
                aiProperties, anthropicService, openAiService, deepseekService, doubaoService);
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        lenient().when(chatMessageRepository.save(any())).thenAnswer(inv -> {
            AiChatMessage m = inv.getArgument(0);
            // null防御：某些测试方法内额外用 argThat(...) 给 save(...) 注册更具体的stub时，
            // Mockito在注册那一刻会用argThat的null占位符实际"调用"一次mock，如果这个调用落到
            // 了这里（因为any()也匹配null），thenAnswer就会拿到null参数——不加保护会在stub
            // 注册阶段就NPE，而不是在真正的业务调用时才失败。
            if (m != null && m.getId() == null) m.setId(System.nanoTime());
            return m;
        });
        lenient().when(conversationRepository.save(any())).thenAnswer(inv -> {
            AiChatConversation c = inv.getArgument(0);
            if (c != null && c.getId() == null) c.setId(System.nanoTime());
            return c;
        });
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
    void sendMessageStream_withNullConversationIdCreatesNewConversation() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("你好呀", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "你好", null, null, null, List.of(), captureEmitter(recorder));

        ArgumentCaptor<AiChatConversation> captor = ArgumentCaptor.forClass(AiChatConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getOwner()).isEqualTo(user);

        ChatStreamEvent userMessageEvent = recorder.events.get(0);
        assertThat(userMessageEvent.type()).isEqualTo("user_message");
        assertThat(userMessageEvent.conversationId()).isNotNull();
    }

    @Test
    void sendMessageStream_withExistingConversationIdAppendsToIt() throws Exception {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("继续聊", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "接着上次说", 5L, null, null, List.of(), captureEmitter(recorder));

        assertThat(recorder.events.get(0).conversationId()).isEqualTo(5L);
        verify(agentServiceClient).streamChat(eq("alice"), eq("接着上次说"), eq("5"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendMessageStream_withConversationIdOwnedByAnotherUserThrows() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.sendMessageStream(
                "alice", "偷看一下", 5L, null, null, List.of(), captureEmitter(new RecordingEmitterListener())))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(agentServiceClient);
    }

    @Test
    void sendMessageStream_withNonExistentConversationIdThrows() {
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessageStream(
                "alice", "问个问题", 99L, null, null, List.of(), captureEmitter(new RecordingEmitterListener())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendMessageStream_firstTurnTriggersTitleGenerationAndPersistsIt() throws Exception {
        when(anthropicService.generateConversationTitle(anyString(), anyString())).thenReturn("生成的标题");
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("回复内容", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<AiChatConversation> savedConversation = ArgumentCaptor.forClass(AiChatConversation.class);
        AiChatConversation created = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.save(argThat(c -> c.getId() == null))).thenReturn(created);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(created));

        service.sendMessageStream("alice", "问题", null, null, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        // 标题生成在独立线程里异步跑，给它一点时间完成再断言
        verify(anthropicService, timeout(2000)).generateConversationTitle(eq("问题"), eq("回复内容"));
        verify(conversationRepository, timeout(2000).atLeastOnce()).save(argThat(c -> "生成的标题".equals(c.getTitle())));
    }

    @Test
    void sendMessageStream_titleGenerationFailureDoesNotBreakMainFlow() throws Exception {
        when(anthropicService.generateConversationTitle(anyString(), anyString()))
                .thenThrow(new RuntimeException("模型调用超时"));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("回复内容", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "问题", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).isEqualTo("回复内容");
    }

    @Test
    void sendMessageStream_secondTurnDoesNotTriggerTitleGeneration() throws Exception {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).title("已有标题").build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("第二轮回复", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        service.sendMessageStream("alice", "第二轮问题", 5L, null, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(anthropicService, never()).generateConversationTitle(anyString(), anyString());
    }

    @Test
    void sendMessageStream_existingConversationWithNullTitleTriggersTitleGenerationOnThisTurn() throws Exception {
        // 覆盖finding#1的核心bug：conversationId不为空（不是这次请求里新建的会话——比如
        // V20迁移出来的存量会话，或者上一轮标题生成失败过）但title仍是null，按spec要求
        // 任意一轮问答后title还是null都要重试生成，老代码用"conversationId==null"当门槛，
        // 这种场景永远不会触发。
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).title(null).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(anthropicService.generateConversationTitle(anyString(), anyString())).thenReturn("补生成的标题");
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("这轮的回复", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        service.sendMessageStream("alice", "这轮的问题", 5L, null, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(anthropicService, timeout(2000)).generateConversationTitle(eq("这轮的问题"), eq("这轮的回复"));
        verify(conversationRepository, timeout(2000).atLeastOnce()).save(argThat(c -> "补生成的标题".equals(c.getTitle())));
    }

    @Test
    void sendMessageStream_touchConversationUsesTargetedUpdateAndNeverFullSavesConversation() throws Exception {
        // 覆盖finding#1里touchConversation的竞态：旧实现touchConversation()对
        // conversationRepository调用save(conversation)，把整个内存实体（可能还带着过时的
        // title=null）存盘——如果同一会话有并发的标题生成线程已经写库成功，这次save()会把
        // 刚生成好的标题覆盖回null。新实现只应该对lastMessageAt做定向update，全程不应该
        // 对这条会话调用save(conversation)，这样不管title在DB里实际是什么，都不会被这次
        // "只是想更新lastMessageAt"的操作意外清空。这里用title已存在（非null）的会话，
        // 让标题生成分支不触发，从而能单独断言touchConversation自己的行为。
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).title("已有标题").build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("回复内容", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        service.sendMessageStream("alice", "问题", 5L, null, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(conversationRepository).updateLastMessageAt(eq(5L), any());
        verify(conversationRepository, never()).save(any(AiChatConversation.class));
    }

    @Test
    void sendMessageStream_passesCurrentNoteContext() throws Exception {
        Note currentNote = Note.builder().id(9L).owner(user).title("我的笔记").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(currentNote));
        when(chunkingService.chunkNote(currentNote)).thenReturn(List.of("正文内容"));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("好的", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        service.sendMessageStream("alice", "问题", null, 9L, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(agentServiceClient).streamChat(
                eq("alice"), eq("问题"), anyString(),
                argThat(ctx -> ctx != null && ctx.contains("我的笔记") && ctx.contains("正文内容")),
                any(), any(), any(), any(), any());
    }

    @Test
    void sendMessageStream_neverThreadsUnownedNoteIdToAgentService() throws Exception {
        User bob = User.builder().id(2L).username("bob").build();
        Note othersNote = Note.builder().id(9L).owner(bob).title("别人的笔记").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(othersNote));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("好的", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        service.sendMessageStream("alice", "关联的资料", null, 9L, null, List.of(), captureEmitter(new RecordingEmitterListener()));

        verify(agentServiceClient).streamChat(
                eq("alice"), eq("关联的资料"), anyString(), any(), any(), any(), isNull(), any(), any());
    }

    @Test
    void sendMessageStream_forwardsToolCallEventFromAgentService() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onToolCall("用户增长");
            listener.onDone("根据笔记回答", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我看看笔记", null, null, null, List.of(), captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "tool_call", "done");
        assertThat(recorder.events.get(1).query()).isEqualTo("用户增长");
    }

    @Test
    void sendMessageStream_resolvesCitationTitlesBySourceTypeAndId() throws Exception {
        Note relatedNote = Note.builder().id(7L).owner(user).title("相关笔记").build();
        when(noteRepository.findById(7L)).thenReturn(Optional.of(relatedNote));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("根据你的笔记，核心观点是留存优先",
                    List.of(new ChatCitation("NOTE", 7L, null, null)));
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我看看用户增长的笔记", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.citations()).hasSize(1);
        assertThat(done.citations().get(0).title()).isEqualTo("相关笔记");
    }

    @Test
    void sendMessageStream_passesThroughWebCitationsWithoutLookup() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("参考了一篇网文",
                    List.of(new ChatCitation("WEB", null, "示例标题", "https://example.com/a")));
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我查点资料", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.citations()).hasSize(1);
        assertThat(done.citations().get(0).title()).isEqualTo("示例标题");
        verifyNoInteractions(sourceClipRepository);
    }

    @Test
    void sendMessageStream_resolvesLocalMediaCitationTitleViaExtractionService() throws Exception {
        when(extractionService.findDisplayNameForExtraction(42L)).thenReturn("screenshot.png");
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("这张截图显示了登录页面",
                    List.of(new ChatCitation("LOCAL_MEDIA", 42L, null, null)));
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "这张截图说了什么", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.citations()).hasSize(1);
        assertThat(done.citations().get(0).title()).isEqualTo("screenshot.png");
    }

    @Test
    void sendMessageStream_preservesPartialTextWhenAgentServiceReportsErrorMidStream() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onTextDelta("这是已经生成了一半的");
            listener.onError("下游模型报错了");
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "问个问题", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).contains("这是已经生成了一半的");
        assertThat(done.content()).contains("生成中断");
    }

    @Test
    void sendMessageStream_fallsBackToGenericMessageWhenAgentServiceUnreachable() throws Exception {
        doThrow(new RuntimeException("connection refused"))
                .when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "你好", null, null, null, List.of(), captureEmitter(recorder));

        ChatStreamEvent done = recorder.events.get(recorder.events.size() - 1);
        assertThat(done.type()).isEqualTo("done");
        assertThat(done.content()).isEqualTo("抱歉，这次没能回复，换个说法试试？");
    }

    @Test
    void sendMessageStream_stillPersistsAssistantMessageWhenClientDisconnectsMidStream() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onTextDelta("回复的第一部分");
            listener.onDone("回复的第一部分", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("client gone"))
                .when(emitter).send(org.mockito.ArgumentMatchers.anyString());

        service.sendMessageStream("alice", "你好", null, null, null, List.of(), emitter);

        org.mockito.ArgumentCaptor<AiChatMessage> captor = org.mockito.ArgumentCaptor.forClass(AiChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        AiChatMessage assistantMsg = captor.getAllValues().get(1);
        assertThat(assistantMsg.getRole()).isEqualTo(AiChatMessage.Role.ASSISTANT);
        assertThat(assistantMsg.getContent()).isEqualTo("回复的第一部分");
    }

    @Test
    void sendMessageStream_passesAttachmentsThroughToAgentService() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("这张图是一只猫", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        List<com.entropybits.worknotes.spring_boot.dto.AttachmentPayload> attachments = List.of(
                new com.entropybits.worknotes.spring_boot.dto.AttachmentPayload(
                        "image", "image/png", "aGVsbG8=", "https://cdn.example.com/a.png", "a.png"));

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "这是什么", null, null, null, attachments, captureEmitter(recorder));

        verify(agentServiceClient).streamChat(eq("alice"), eq("这是什么"), anyString(), any(), any(), eq(attachments), any(), any(), any());
        assertThat(recorder.events.get(0).attachments()).hasSize(1);
        assertThat(recorder.events.get(0).attachments().get(0).url()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(recorder.events.get(0).attachments().get(0).fileName()).isEqualTo("a.png");
    }

    @Test
    void sendMessageStream_confirmRequestEndsStreamWithoutPersistingAssistantMessage() throws Exception {
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onConfirmRequest("p1", "timeline", 9L, Map.of("date", "2024-01", "title", "事件一"));
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "加一条", null, 9L, null, List.of(), captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type)
                .containsExactly("user_message", "confirm_request");
        verify(chatMessageRepository, times(1)).save(any());
    }

    @Test
    void sendMessageStream_withOwnedProjectId_passesProjectContextAndId() throws Exception {
        Project project = Project.builder().id(7L).owner(user).name("Mindio").category("SaaS")
                .technologies("Vue, Spring Boot").description("desc").descriptionZh("描述").build();
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("好的", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我写简介", null, null, 7L, List.of(), captureEmitter(recorder));

        ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentServiceClient).streamChat(eq("alice"), eq("帮我写简介"), anyString(), any(),
                contextCaptor.capture(), any(), any(), projectIdCaptor.capture(), any());
        assertThat(projectIdCaptor.getValue()).isEqualTo(7L);
        assertThat(contextCaptor.getValue()).contains("Mindio").contains("SaaS");
    }

    @Test
    void sendMessageStream_withProjectIdNotOwnedByUser_passesNullProjectId() throws Exception {
        User otherOwner = User.builder().id(2L).username("bob").build();
        Project project = Project.builder().id(7L).owner(otherOwner).build();
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(8);
            listener.onDone("好的", List.of());
            return null;
        }).when(agentServiceClient).streamChat(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.sendMessageStream("alice", "帮我写简介", null, null, 7L, List.of(), captureEmitter(recorder));

        ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(agentServiceClient).streamChat(eq("alice"), eq("帮我写简介"), anyString(), any(), any(), any(), any(),
                projectIdCaptor.capture(), any());
        assertThat(projectIdCaptor.getValue()).isNull();
    }

    @Test
    void resumeStream_acceptPersistsAssistantMessageAndSendsDone() throws Exception {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.USER).content("加一条").build()));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(3);
            listener.onDone("已经加好了", List.of());
            return null;
        }).when(agentServiceClient).resumeChat(anyString(), anyString(), anyString(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.resumeStream("alice", 5L, "p1", "accept", captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type).containsExactly("done");
        assertThat(recorder.events.get(0).content()).isEqualTo("已经加好了");
        verify(chatMessageRepository, times(1)).save(any());
        verify(agentServiceClient).resumeChat(eq("5"), eq("p1"), eq("accept"), any());
    }

    @Test
    void resumeStream_titleStillNullTriggersTitleGenerationEvenWithPriorAssistantMessages() throws Exception {
        // 覆盖finding#1里resumeStream那部分：旧的isFirstTurn用"这条会话此前是否已经有过
        // ASSISTANT消息"来判断是不是第一轮，对title仍是null但已经有过往消息的会话（比如
        // 上一次标题生成失败过）不会重试。新门槛统一为"title是否还是null"。
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).title(null).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.USER).content("最早的问题").build(),
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.ASSISTANT).content("最早的回复").build(),
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.USER).content("加一条").build()));
        when(anthropicService.generateConversationTitle(anyString(), anyString())).thenReturn("补生成的标题");
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(3);
            listener.onDone("已经加好了", List.of());
            return null;
        }).when(agentServiceClient).resumeChat(anyString(), anyString(), anyString(), any());

        service.resumeStream("alice", 5L, "p1", "accept", captureEmitter(new RecordingEmitterListener()));

        verify(anthropicService, timeout(2000)).generateConversationTitle(eq("最早的问题"), eq("已经加好了"));
        verify(conversationRepository, timeout(2000).atLeastOnce()).save(argThat(c -> "补生成的标题".equals(c.getTitle())));
    }

    @Test
    void resumeStream_withConversationIdOwnedByAnotherUserThrowsBeforeCallingAgentService() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.resumeStream(
                "alice", 5L, "p1", "accept", captureEmitter(new RecordingEmitterListener())))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(agentServiceClient);
    }

    @Test
    void resumeStream_withNonExistentConversationIdThrowsBeforeCallingAgentService() {
        when(conversationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resumeStream(
                "alice", 404L, "p1", "accept", captureEmitter(new RecordingEmitterListener())))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(agentServiceClient);
    }

    @Test
    void resumeStream_blockUpdatedEventIsForwardedToClient() throws Exception {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.USER).content("加一条").build()));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(3);
            listener.onBlockUpdated(9L, "block-1", "timeline", List.of(Map.of("date", "2024-01", "title", "事件一")));
            listener.onDone("已经加好了", List.of());
            return null;
        }).when(agentServiceClient).resumeChat(anyString(), anyString(), anyString(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.resumeStream("alice", 5L, "p1", "accept", captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type).containsExactly("block_updated", "done");
        assertThat(recorder.events.get(0).noteId()).isEqualTo(9L);
        assertThat(recorder.events.get(0).blockId()).isEqualTo("block-1");
    }

    @Test
    void resumeStream_mediaBlockUpdatedEventIsForwardedToClient() throws Exception {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(
                AiChatMessage.builder().conversation(conversation).role(AiChatMessage.Role.USER).content("这张图").build()));
        doAnswer(inv -> {
            AgentServiceClient.StreamListener listener = inv.getArgument(3);
            listener.onMediaBlockUpdated(9L, "b1", "image", Map.of("url", "a.png", "caption", "一张图片描述"));
            listener.onDone("已经分析好了", List.of());
            return null;
        }).when(agentServiceClient).resumeChat(anyString(), anyString(), anyString(), any());

        RecordingEmitterListener recorder = new RecordingEmitterListener();
        service.resumeStream("alice", 5L, "p1", "accept", captureEmitter(recorder));

        assertThat(recorder.events).extracting(ChatStreamEvent::type).containsExactly("media_block_updated", "done");
        assertThat(recorder.events.get(0).noteId()).isEqualTo(9L);
        assertThat(recorder.events.get(0).blockId()).isEqualTo("b1");
        assertThat(recorder.events.get(0).data()).containsEntry("caption", "一张图片描述");
    }

    @Test
    void getConversationMessages_returnsChronologicalOrder() {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)).thenReturn(List.of(
                AiChatMessage.builder().id(1L).conversation(conversation).role(AiChatMessage.Role.USER).content("第一条").build(),
                AiChatMessage.builder().id(2L).conversation(conversation).role(AiChatMessage.Role.ASSISTANT).content("第一条回复").build()));

        List<ChatMessageResponse> result = service.getConversationMessages("alice", 5L);

        assertThat(result).extracting(ChatMessageResponse::getContent).containsExactly("第一条", "第一条回复");
    }

    @Test
    void getConversationMessages_withConversationOwnedByAnotherUserThrows() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.getConversationMessages("alice", 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listConversations_returnsNewestFirstWithFallbackTitleWhenTitleIsNull() {
        AiChatConversation withTitle = AiChatConversation.builder().id(1L).owner(user)
                .title("已有标题").lastMessageAt(java.time.Instant.parse("2026-02-01T00:00:00Z")).build();
        AiChatConversation withoutTitle = AiChatConversation.builder().id(2L).owner(user)
                .title(null).lastMessageAt(java.time.Instant.parse("2026-03-01T00:00:00Z")).build();
        when(conversationRepository.findByOwnerOrderByLastMessageAtDesc(user))
                .thenReturn(List.of(withoutTitle, withTitle));
        when(chatMessageRepository.findFirstByConversationOrderByCreatedAtAsc(withoutTitle)).thenReturn(Optional.of(
                AiChatMessage.builder().conversation(withoutTitle).role(AiChatMessage.Role.USER)
                        .content("这是一条比较长需要截断展示的第一条消息内容示例文本").build()));

        List<com.entropybits.worknotes.spring_boot.dto.ConversationResponse> result = service.listConversations("alice");

        assertThat(result).extracting(com.entropybits.worknotes.spring_boot.dto.ConversationResponse::getTitle)
                .containsExactly("这是一条比较长需要截断展示的第一条消息内…", "已有标题");
        // 覆盖finding#3：fallbackTitle只需要第一条消息，不应该为了读一行就把整个会话的
        // 消息历史都查出来。
        verify(chatMessageRepository, never()).findByConversationOrderByCreatedAtAsc(any());
    }

    @Test
    void listConversations_fallbackTitleReturnsDefaultWhenConversationHasNoMessagesYet() {
        // 懒创建但还没发第一条消息的会话——findFirstByConversationOrderByCreatedAtAsc
        // 应该返回empty而不是抛异常，fallbackTitle兜底成"新对话"。
        AiChatConversation empty = AiChatConversation.builder().id(3L).owner(user).title(null)
                .lastMessageAt(null).build();
        when(conversationRepository.findByOwnerOrderByLastMessageAtDesc(user)).thenReturn(List.of(empty));
        when(chatMessageRepository.findFirstByConversationOrderByCreatedAtAsc(empty)).thenReturn(Optional.empty());

        List<com.entropybits.worknotes.spring_boot.dto.ConversationResponse> result = service.listConversations("alice");

        assertThat(result).extracting(com.entropybits.worknotes.spring_boot.dto.ConversationResponse::getTitle)
                .containsExactly("新对话");
    }

    @Test
    void renameConversation_persistsNewTitleAndReturnsIt() {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).title("旧标题").build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        com.entropybits.worknotes.spring_boot.dto.ConversationResponse result =
                service.renameConversation("alice", 5L, "新标题");

        assertThat(result.getTitle()).isEqualTo("新标题");
        verify(conversationRepository).save(argThat(c -> "新标题".equals(c.getTitle())));
    }

    @Test
    void renameConversation_withConversationOwnedByAnotherUserThrows() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.renameConversation("alice", 5L, "新标题"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void deleteConversation_deletesOwnedConversation() {
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        service.deleteConversation("alice", 5L);

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void deleteConversation_alsoDeletesMatchingAgentConversationStateRow() {
        // 覆盖finding#4：agent_conversation_state不受AiChatConversation删除时的FK级联影响，
        // 必须显式按conversationId字符串清理，否则LangGraph checkpointer序列化的对话内容
        // 副本会一直留在这张表里。
        AiChatConversation conversation = AiChatConversation.builder().id(5L).owner(user).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        service.deleteConversation("alice", 5L);

        verify(agentConversationStateRepository).deleteByConversationId("5");
    }

    @Test
    void deleteConversation_withConversationOwnedByAnotherUserDoesNotDeleteAgentConversationState() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.deleteConversation("alice", 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(agentConversationStateRepository);
    }

    @Test
    void deleteConversation_withConversationOwnedByAnotherUserThrowsAndDoesNotDelete() {
        User bob = User.builder().id(2L).username("bob").build();
        AiChatConversation bobsConversation = AiChatConversation.builder().id(5L).owner(bob).build();
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(bobsConversation));

        assertThatThrownBy(() -> service.deleteConversation("alice", 5L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(conversationRepository, never()).delete(any());
    }
}
