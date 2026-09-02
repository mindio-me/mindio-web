/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.ChatService;
import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
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
        this.aiProperties = aiProperties;
        this.anthropicChatService = anthropicChatService;
        this.openAiChatService = openAiChatService;
        this.deepseekChatService = deepseekChatService;
        this.doubaoChatService = doubaoChatService;
        this.objectMapper = objectMapper;
    }

    // 刻意不整体包一个大事务：大模型调用可能耗时数秒到数十秒，全程占一条 Hikari 连接会拖累
    // 连接池；用户消息和AI回复分别各自短事务落库，中途失败时用户那条消息本该留下。
    public List<ChatMessageResponse> sendMessage(String username, String content, Long currentNoteId) {
        User user = getUser(username);

        List<AiChatMessage> priorHistory = new ArrayList<>(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
        Collections.reverse(priorHistory);
        List<ChatService.ChatTurn> modelHistory = toModelHistory(priorHistory);

        AiChatMessage userMessage = chatMessageRepository.save(AiChatMessage.builder()
                .owner(user).role(AiChatMessage.Role.USER).content(content).build());

        String reply;
        String citationsJson = null;
        try {
            List<RetrievedChunk> retrieved = retrievalService.retrieve(user, content, RETRIEVAL_TOP_K);
            Note currentNote = loadOwnedNoteOrNull(currentNoteId, user);
            String systemPromptWithContext = SYSTEM_PROMPT + "\n\n" + buildContextBlock(currentNote, retrieved);

            reply = resolveChatService().chat(systemPromptWithContext, modelHistory, content);
            List<ChatCitation> citations = buildCitations(retrieved);
            citationsJson = citations.isEmpty() ? null : writeJson(citations);
        } catch (Exception e) {
            log.error("global chat pipeline failed for user {}", username, e);
            reply = "抱歉，这次没能回复，换个说法试试？";
        }

        AiChatMessage assistantMessage = chatMessageRepository.save(AiChatMessage.builder()
                .owner(user).role(AiChatMessage.Role.ASSISTANT).content(reply).citationsJson(citationsJson).build());

        return List.of(toResponse(userMessage), toResponse(assistantMessage));
    }

    public List<ChatMessageResponse> listHistory(String username, int limit) {
        User user = getUser(username);
        List<AiChatMessage> messages = new ArrayList<>(chatMessageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
        Collections.reverse(messages);
        if (messages.size() > limit) {
            messages = messages.subList(messages.size() - limit, messages.size());
        }
        return messages.stream().map(this::toResponse).toList();
    }

    private List<ChatService.ChatTurn> toModelHistory(List<AiChatMessage> history) {
        int fromIndex = Math.max(0, history.size() - HISTORY_TURNS_FOR_MODEL * 2);
        return history.subList(fromIndex, history.size()).stream()
                .map(m -> new ChatService.ChatTurn(
                        m.getRole() == AiChatMessage.Role.USER ? "user" : "assistant", m.getContent()))
                .toList();
    }

    private Note loadOwnedNoteOrNull(Long noteId, User user) {
        if (noteId == null) return null;
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElse(null);
    }

    private String buildContextBlock(Note currentNote, List<RetrievedChunk> retrieved) {
        StringBuilder sb = new StringBuilder();
        if (currentNote != null) {
            sb.append("【当前正在编辑的笔记】\n标题：").append(currentNote.getTitle())
              .append("\n正文：\n").append(currentNote.getContent()).append("\n\n");
        }
        if (!retrieved.isEmpty()) {
            sb.append("【可能相关的笔记/收藏片段】\n");
            for (int i = 0; i < retrieved.size(); i++) {
                RetrievedChunk chunk = retrieved.get(i);
                String title = lookupTitle(chunk.sourceType(), chunk.sourceId());
                sb.append(i + 1).append(".（来自").append(chunk.sourceType() == ContentChunk.SourceType.NOTE ? "笔记" : "收藏")
                  .append("《").append(title).append("》）").append(chunk.chunkText()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<ChatCitation> buildCitations(List<RetrievedChunk> retrieved) {
        Map<String, ChatCitation> deduped = new LinkedHashMap<>();
        for (RetrievedChunk chunk : retrieved) {
            String key = chunk.sourceType() + ":" + chunk.sourceId();
            deduped.putIfAbsent(key, new ChatCitation(
                    chunk.sourceType().name(), chunk.sourceId(), lookupTitle(chunk.sourceType(), chunk.sourceId())));
        }
        return List.copyOf(deduped.values());
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
