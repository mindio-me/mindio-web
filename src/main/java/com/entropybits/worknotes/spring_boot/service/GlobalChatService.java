/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.ChatService;
import com.entropybits.worknotes.spring_boot.dto.AttachmentPayload;
import com.entropybits.worknotes.spring_boot.dto.ChatAttachmentRef;
import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatStreamEvent;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GlobalChatService {

    private static final int RETRIEVAL_TOP_K = 5;
    private static final int HISTORY_TURNS_FOR_MODEL = 10;
    private static final String SYSTEM_PROMPT =
            "你是这款笔记应用内置的AI助手，可以自由对话、协助写作和总结。" +
            "如果下面提供了笔记/收藏的相关内容，可以参考它们来回答，但不要虚构未提供的内容。";

    private final AiChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final SourceClipRepository sourceClipRepository;
    private final RetrievalService retrievalService;
    private final ContentChunkingService chunkingService;
    private final AiProperties aiProperties;
    private final ChatService anthropicChatService;
    private final ChatService openAiChatService;
    private final ChatService deepseekChatService;
    private final ChatService doubaoChatService;
    private final ObjectMapper objectMapper;

    public GlobalChatService(AiChatMessageRepository chatMessageRepository,
                              UserRepository userRepository,
                              NoteRepository noteRepository,
                              SourceClipRepository sourceClipRepository,
                              RetrievalService retrievalService,
                              ContentChunkingService chunkingService,
                              AiProperties aiProperties,
                              @Qualifier("anthropicChatService") ChatService anthropicChatService,
                              @Qualifier("openAiChatService") ChatService openAiChatService,
                              @Qualifier("deepseekChatService") ChatService deepseekChatService,
                              @Qualifier("doubaoChatService") ChatService doubaoChatService,
                              ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.sourceClipRepository = sourceClipRepository;
        this.retrievalService = retrievalService;
        this.chunkingService = chunkingService;
        this.aiProperties = aiProperties;
        this.anthropicChatService = anthropicChatService;
        this.openAiChatService = openAiChatService;
        this.deepseekChatService = deepseekChatService;
        this.doubaoChatService = doubaoChatService;
        this.objectMapper = objectMapper;
    }

    private static final int MAX_TOOL_CALLS = 3;
    private static final ChatService.ToolDefinition SEARCH_WORKSPACE_TOOL = new ChatService.ToolDefinition(
            "search_workspace",
            "在用户的笔记和收藏里做语义搜索，返回最相关的片段。当用户的问题可能需要参考他们自己写过的笔记或"
                    + "收藏过的内容时调用；如果只是常规聊天或问题已经能从当前对话/当前笔记回答，不需要调用。",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "搜索关键词或问题，用于语义检索")
                    ),
                    "required", List.of("query")
            )
    );

    // agentic版本：不再固定每轮自动检索，而是把检索包装成一个工具，由大模型自己判断
    // 要不要调用、调用几次（硬顶3次），并把整个过程通过SSE实时推给前端。
    public void sendMessageStream(String username, String content, Long currentNoteId,
                                   List<AttachmentPayload> attachments, SseEmitter emitter) {
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            User user = getUser(username);

            List<AiChatMessage> priorHistory = new ArrayList<>(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
            Collections.reverse(priorHistory);
            List<ChatService.ChatTurn> modelHistory = new ArrayList<>(toModelHistory(priorHistory));

            List<ChatService.Attachment> chatAttachments = attachments == null ? List.of() : attachments.stream()
                    .map(a -> new ChatService.Attachment(a.type(), a.mimeType(), a.base64Data()))
                    .toList();
            modelHistory.add(new ChatService.ChatTurn("user", content, chatAttachments));

            List<ChatAttachmentRef> attachmentRefs = attachments == null ? List.of() : attachments.stream()
                    .map(a -> new ChatAttachmentRef(a.type(), a.url(), a.fileName()))
                    .toList();
            String attachmentsJson = attachmentRefs.isEmpty() ? null : writeJson(attachmentRefs);

            AiChatMessage userMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .owner(user).role(AiChatMessage.Role.USER).content(content).attachmentsJson(attachmentsJson).build());
            sendEvent(emitter, ChatStreamEvent.userMessage(toResponse(userMessage)), disconnected);

            Note currentNote = loadOwnedNoteOrNull(currentNoteId, user);
            StringBuilder systemPromptBuilder = new StringBuilder(SYSTEM_PROMPT);
            if (currentNote != null) {
                systemPromptBuilder.append("\n\n【当前正在编辑的笔记】\n标题：").append(currentNote.getTitle())
                        .append("\n正文：\n").append(currentNoteBodyText(currentNote));
            }

            List<ChatCitation> allCitations = new ArrayList<>();
            Map<String, String> titleCache = new LinkedHashMap<>();
            StringBuilder finalReplyText = new StringBuilder();
            boolean chatSucceeded = false;

            try {
                for (int attempt = 0; attempt <= MAX_TOOL_CALLS; attempt++) {
                    List<ChatService.ToolDefinition> toolsForThisAttempt =
                            attempt < MAX_TOOL_CALLS ? List.of(SEARCH_WORKSPACE_TOOL) : List.of();

                    java.util.concurrent.atomic.AtomicReference<ChatService.ToolCall> toolCallHolder =
                            new java.util.concurrent.atomic.AtomicReference<>();

                    resolveChatService().chatStream(systemPromptBuilder.toString(), modelHistory, toolsForThisAttempt,
                            new ChatService.StreamListener() {
                                @Override
                                public void onTextDelta(String delta) {
                                    finalReplyText.append(delta);
                                    sendEvent(emitter, ChatStreamEvent.textDelta(delta), disconnected);
                                }

                                @Override
                                public void onToolCallStart(ChatService.ToolCall call) {
                                    toolCallHolder.set(call);
                                    Object query = call.input().get("query");
                                    sendEvent(emitter, ChatStreamEvent.toolCall(query == null ? "" : query.toString()), disconnected);
                                }

                                @Override
                                public void onDone() {
                                }
                            });

                    ChatService.ToolCall toolCall = toolCallHolder.get();
                    if (toolCall == null) {
                        chatSucceeded = true;
                        break;
                    }

                    if (attempt == 0 && !chatAttachments.isEmpty()) {
                        // 工具调用意味着这轮对话还要至少再请求模型一次；附件底图已经在第一次
                        // 请求里让模型"看过"了，后续几次重复调用不需要再重发一遍原始数据
                        // （省流量，也省每次都要重新计费的多模态输入token）。
                        modelHistory.set(modelHistory.size() - 1,
                                new ChatService.ChatTurn("user", nonBlankOrPlaceholder(content)));
                    }

                    Object queryObj = toolCall.input().get("query");
                    String query = queryObj == null ? "" : queryObj.toString();
                    try {
                        List<RetrievedChunk> retrieved = retrievalService.retrieve(user, query, RETRIEVAL_TOP_K);
                        for (RetrievedChunk chunk : retrieved) {
                            String key = titleKey(chunk.sourceType(), chunk.sourceId());
                            String title = titleCache.computeIfAbsent(key, k -> lookupTitle(chunk.sourceType(), chunk.sourceId()));
                            allCitations.add(new ChatCitation(chunk.sourceType().name(), chunk.sourceId(), title));
                        }
                        systemPromptBuilder.append("\n\n【工具调用结果：search_workspace(\"").append(query).append("\")】\n");
                        if (retrieved.isEmpty()) {
                            systemPromptBuilder.append("没有搜索到相关内容。");
                        } else {
                            for (RetrievedChunk chunk : retrieved) {
                                systemPromptBuilder.append("- ").append(chunk.chunkText()).append("\n");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("workspace search tool failed for user {}, continuing without results", username, e);
                        systemPromptBuilder.append("\n\n【工具调用结果：search_workspace(\"").append(query)
                                .append("\")】\n检索失败，请基于已有信息回答。");
                    }
                }
            } catch (Exception e) {
                log.error("global chat pipeline failed for user {}", username, e);
                String interruption = finalReplyText.length() > 0
                        ? "\n\n（生成中断，请重新提问）"
                        : "抱歉，这次没能回复，换个说法试试？";
                finalReplyText.append(interruption);
                sendEvent(emitter, ChatStreamEvent.textDelta(interruption), disconnected);
            }

            String reply = finalReplyText.length() > 0 ? finalReplyText.toString() : "抱歉，这次没能回复，换个说法试试？";
            List<ChatCitation> dedupedCitations = dedupeCitations(allCitations);
            String citationsJson = chatSucceeded && !dedupedCitations.isEmpty() ? writeJson(dedupedCitations) : null;

            AiChatMessage assistantMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .owner(user).role(AiChatMessage.Role.ASSISTANT).content(reply).citationsJson(citationsJson).build());

            sendEvent(emitter, ChatStreamEvent.done(toResponse(assistantMessage)), disconnected);
            // 无论disconnected与否都调用：Spring对已经complete/error过的emitter再次complete()是安全的no-op，
            // 这样即使sendEvent是因为非断连原因（而非真实的客户端断开）设置的disconnected，emitter也不会
            // 因为SseEmitter(0L)没有超时而永远挂起。
            emitter.complete();
        } catch (Exception e) {
            log.error("unexpected error in sendMessageStream for user {}", username, e);
            sendEvent(emitter, ChatStreamEvent.error("抱歉，这次没能回复，换个说法试试？"), disconnected);
            emitter.completeWithError(e);
        }
    }

    private List<ChatCitation> dedupeCitations(List<ChatCitation> citations) {
        Map<String, ChatCitation> deduped = new LinkedHashMap<>();
        for (ChatCitation c : citations) {
            deduped.putIfAbsent(c.sourceType() + ":" + c.sourceId(), c);
        }
        return List.copyOf(deduped.values());
    }

    private void sendEvent(SseEmitter emitter, ChatStreamEvent event, java.util.concurrent.atomic.AtomicBoolean disconnected) {
        if (disconnected.get()) return;
        try {
            emitter.send(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            disconnected.set(true);
            log.warn("SSE send failed (client likely disconnected), continuing pipeline without further sends", e);
        }
    }

    public List<ChatMessageResponse> listHistory(String username, int limit) {
        int safeLimit = Math.max(0, limit);
        User user = getUser(username);
        List<AiChatMessage> messages = new ArrayList<>(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
        Collections.reverse(messages);
        if (messages.size() > safeLimit) {
            messages = messages.subList(messages.size() - safeLimit, messages.size());
        }
        return messages.stream().map(this::toResponse).toList();
    }

    private List<ChatService.ChatTurn> toModelHistory(List<AiChatMessage> history) {
        int fromIndex = Math.max(0, history.size() - HISTORY_TURNS_FOR_MODEL * 2);
        return history.subList(fromIndex, history.size()).stream()
                .map(m -> new ChatService.ChatTurn(
                        m.getRole() == AiChatMessage.Role.USER ? "user" : "assistant",
                        nonBlankOrPlaceholder(m.getContent())))
                .toList();
    }

    // 附件在当前轮之外不重放（避免重复传底图），历史里那一轮的content可能是空字符串
    // （只发了图/文档、没打字）。空字符串作为纯文本轮次传给Anthropic等厂商会被拒绝
    // （要求content非空），所以历史回放时用占位符顶上。
    private String nonBlankOrPlaceholder(String content) {
        return (content == null || content.isBlank()) ? "（发送了图片/文档）" : content;
    }

    private Note loadOwnedNoteOrNull(Long noteId, User user) {
        if (noteId == null) return null;
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElse(null);
    }

    // 当前笔记的正文经由 ContentChunkingService 归一化为纯文本（去掉 EditorJS/HTML 结构噪音），
    // 并做长度硬截断，避免超长笔记把系统提示词撑爆模型的上下文窗口。
    private static final int CURRENT_NOTE_BODY_CHAR_CAP = 4000;

    private String currentNoteBodyText(Note note) {
        String text = String.join("\n", chunkingService.chunkNote(note));
        return text.length() > CURRENT_NOTE_BODY_CHAR_CAP
                ? text.substring(0, CURRENT_NOTE_BODY_CHAR_CAP) + "…（内容过长，已截断）"
                : text;
    }

    private String titleKey(ContentChunk.SourceType sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }

    private String lookupTitle(ContentChunk.SourceType sourceType, Long sourceId) {
        if (sourceType == ContentChunk.SourceType.NOTE) {
            return noteRepository.findById(sourceId).map(Note::getTitle).orElse("（已删除的笔记）");
        }
        return sourceClipRepository.findById(sourceId).map(SourceClip::getTitle).orElse("（已删除的收藏）");
    }

    private ChatService resolveChatService() {
        return switch (aiProperties.getProvider().toLowerCase()) {
            case "openai" -> openAiChatService;
            case "deepseek" -> deepseekChatService;
            case "doubao" -> doubaoChatService;
            default -> anthropicChatService;
        };
    }

    private ChatMessageResponse toResponse(AiChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .role(m.getRole().name())
                .content(m.getContent())
                .citations(readCitations(m.getCitationsJson()))
                .attachments(readAttachments(m.getAttachmentsJson()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private List<ChatCitation> readCitations(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatCitation>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ChatAttachmentRef> readAttachments(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatAttachmentRef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
