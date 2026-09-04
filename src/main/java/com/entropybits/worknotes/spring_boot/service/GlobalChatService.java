/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

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

    private final AiChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final SourceClipRepository sourceClipRepository;
    private final ContentChunkingService chunkingService;
    private final AgentServiceClient agentServiceClient;
    private final ObjectMapper objectMapper;

    public GlobalChatService(AiChatMessageRepository chatMessageRepository,
                              UserRepository userRepository,
                              NoteRepository noteRepository,
                              SourceClipRepository sourceClipRepository,
                              ContentChunkingService chunkingService,
                              AgentServiceClient agentServiceClient,
                              ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.sourceClipRepository = sourceClipRepository;
        this.chunkingService = chunkingService;
        this.agentServiceClient = agentServiceClient;
        this.objectMapper = objectMapper;
    }

    // agent推理逻辑整体搬到独立的Python/LangGraph服务（AgentServiceClient），这里只做：
    // 持久化用户消息 -> 调用Agent服务并把事件原样转发给前端 -> 持久化最终回复。
    // conversationId 目前等同于username（延续"单一连续会话"的既有决定）。
    public void sendMessageStream(String username, String content, Long currentNoteId,
                                   List<AttachmentPayload> attachments, SseEmitter emitter) {
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            User user = getUser(username);

            List<ChatAttachmentRef> attachmentRefs = attachments == null ? List.of() : attachments.stream()
                    .map(a -> new ChatAttachmentRef(a.type(), a.url(), a.fileName()))
                    .toList();
            String attachmentsJson = attachmentRefs.isEmpty() ? null : writeJson(attachmentRefs);

            AiChatMessage userMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .owner(user).role(AiChatMessage.Role.USER).content(content)
                    .attachmentsJson(attachmentsJson).noteId(currentNoteId).build());
            sendEvent(emitter, ChatStreamEvent.userMessage(toResponse(userMessage)), disconnected);

            Note currentNote = loadOwnedNoteOrNull(currentNoteId, user);
            String currentNoteContext = currentNote == null ? null
                    : "标题：" + currentNote.getTitle() + "\n正文：\n" + currentNoteBodyText(currentNote);

            StringBuilder finalReplyText = new StringBuilder();
            List<ChatCitation>[] resolvedCitations = new List[]{List.of()};
            boolean[] chatSucceeded = {false};

            try {
                agentServiceClient.streamChat(username, content, username, currentNoteContext, attachments,
                        new AgentServiceClient.StreamListener() {
                            @Override
                            public void onTextDelta(String text) {
                                finalReplyText.append(text);
                                sendEvent(emitter, ChatStreamEvent.textDelta(text), disconnected);
                            }

                            @Override
                            public void onToolCall(String query) {
                                sendEvent(emitter, ChatStreamEvent.toolCall(query == null ? "" : query), disconnected);
                            }

                            @Override
                            public void onDone(String finalContent, List<ChatCitation> citations) {
                                // Agent服务已经给出权威的最终文本（不是靠拼接text_delta），
                                // 用它覆盖，避免因为某个provider不支持逐token流式而拼不出完整内容。
                                finalReplyText.setLength(0);
                                finalReplyText.append(finalContent);
                                resolvedCitations[0] = resolveCitationTitles(citations);
                                chatSucceeded[0] = true;
                            }

                            @Override
                            public void onError(String message) {
                                // 保留已经流出去的部分文本（用户已经在界面上看到了），只在后面
                                // 追加一句提示，而不是整段替换掉——和迁移前"生成中途失败"的
                                // 行为保持一致。
                                if (finalReplyText.length() == 0) {
                                    finalReplyText.append(message);
                                } else {
                                    finalReplyText.append("\n\n（生成中断，请重新提问）");
                                }
                            }
                        });
            } catch (Exception e) {
                log.error("agent service call failed for user {}", username, e);
                if (finalReplyText.length() == 0) {
                    finalReplyText.append("抱歉，这次没能回复，换个说法试试？");
                } else {
                    finalReplyText.append("\n\n（生成中断，请重新提问）");
                }
            }

            String reply = finalReplyText.length() > 0 ? finalReplyText.toString() : "抱歉，这次没能回复，换个说法试试？";
            String citationsJson = chatSucceeded[0] && !resolvedCitations[0].isEmpty()
                    ? writeJson(resolvedCitations[0]) : null;

            AiChatMessage assistantMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .owner(user).role(AiChatMessage.Role.ASSISTANT).content(reply)
                    .citationsJson(citationsJson).noteId(currentNoteId).build());

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

    // Agent服务只知道sourceType/sourceId，人类可读的标题按ID反查（复用现有lookupTitle逻辑）。
    private List<ChatCitation> resolveCitationTitles(List<ChatCitation> citations) {
        Map<String, String> titleCache = new LinkedHashMap<>();
        return citations.stream()
                .map(c -> {
                    if ("WEB".equals(c.sourceType())) {
                        return c; // Python已经给好了title，网络资料不需要反查
                    }
                    ContentChunk.SourceType sourceType = ContentChunk.SourceType.valueOf(c.sourceType());
                    String key = titleKey(sourceType, c.sourceId());
                    String title = titleCache.computeIfAbsent(key, k -> lookupTitle(sourceType, c.sourceId()));
                    return new ChatCitation(c.sourceType(), c.sourceId(), title, c.sourceUrl());
                })
                .toList();
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

    public List<ChatMessageResponse> getMessagesForNote(String username, Long noteId) {
        User user = getUser(username);
        return chatMessageRepository.findByOwnerAndNoteIdOrderByCreatedAtAsc(user, noteId)
                .stream().map(this::toResponse).toList();
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
