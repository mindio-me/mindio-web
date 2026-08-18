/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.ai.service.CuratedSearchResult;
import com.entropybits.worknotes.spring_boot.dto.ClipImportUrlRequest;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchResultItem;
import com.entropybits.worknotes.spring_boot.dto.SourceClipDraft;
import com.entropybits.worknotes.spring_boot.dto.SourceClipRequest;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.entity.ClipSearchMessage;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.ClipSearchMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.search.SearchResultItem;
import com.entropybits.worknotes.spring_boot.search.WebSearchProviderResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClipSearchService {

    private static final int MAX_CANDIDATES = 10;

    private final ClipSearchMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiProperties aiProperties;
    private final AiTranslationService anthropicService;
    private final AiTranslationService openAiService;
    private final AiTranslationService deepseekService;
    private final AiTranslationService doubaoService;
    private final WebSearchProviderResolver searchProviderResolver;
    private final ObjectMapper objectMapper;
    private final ClipImportService clipImportService;
    private final SourceClipService sourceClipService;

    public ClipSearchService(
            ClipSearchMessageRepository messageRepository,
            UserRepository userRepository,
            AiProperties aiProperties,
            @Qualifier("anthropicTranslationService") AiTranslationService anthropicService,
            @Qualifier("openAiTranslationService") AiTranslationService openAiService,
            @Qualifier("deepseekTranslationService") AiTranslationService deepseekService,
            @Qualifier("doubaoTranslationService") AiTranslationService doubaoService,
            WebSearchProviderResolver searchProviderResolver,
            ObjectMapper objectMapper,
            ClipImportService clipImportService,
            SourceClipService sourceClipService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.aiProperties = aiProperties;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.deepseekService = deepseekService;
        this.doubaoService = doubaoService;
        this.searchProviderResolver = searchProviderResolver;
        this.objectMapper = objectMapper;
        this.clipImportService = clipImportService;
        this.sourceClipService = sourceClipService;
    }

    @Transactional(readOnly = true)
    public List<ClipSearchMessageResponse> listHistory(String username) {
        User user = getUser(username);
        List<ClipSearchMessage> messages = new ArrayList<>(messageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
        Collections.reverse(messages);
        return messages.stream().map(this::toResponse).toList();
    }

    // 刻意不加 @Transactional：整条流水线要跑最长约 270 秒的外部 HTTP（两次 AI 调用 + 一次搜索），
    // 全程占着一条 Hikari 连接会把连接池耗尽。每次 save 各自开自己的短事务即可，这里也不需要跨语句原子性
    // ——流水线中途失败时用户自己那条消息本就应该留下来，而不是被一起回滚。
    public List<ClipSearchMessageResponse> sendMessage(String username, String content) {
        User user = getUser(username);

        List<ClipSearchMessage> priorHistory = new ArrayList<>(messageRepository.findTop50ByOwnerOrderByCreatedAtDesc(user));
        Collections.reverse(priorHistory);

        ClipSearchMessage userMessage = messageRepository.save(ClipSearchMessage.builder()
                .owner(user).role(ClipSearchMessage.Role.USER).content(content).build());

        String assistantReply;
        List<ClipSearchResultItem> assistantResults = null;
        try {
            AiTranslationService ai = resolveAi();
            List<String> historyLines = priorHistory.stream()
                    .map(m -> (m.getRole() == ClipSearchMessage.Role.USER ? "用户: " : "助手: ") + m.getContent())
                    .toList();

            String query = ai.planSearchQuery(historyLines, content);

            List<SearchResultItem> candidates;
            if (query != null && !query.isBlank()) {
                candidates = searchProviderResolver.resolve().search(query, MAX_CANDIDATES);
            } else {
                candidates = previousCandidates(priorHistory);
            }

            if (candidates.isEmpty()) {
                assistantReply = "没有找到相关的文章，换个说法试试？";
            } else {
                CuratedSearchResult curated = ai.curateSearchResults(content, candidates);

                // 防止 AI 编造/篡改链接：只保留 url 确实出现在真实候选池 candidates 中的结果。
                // title/excerpt 允许 AI 改写，但 url 必须是可验证的真实搜索结果。
                Set<String> candidateUrls = candidates.stream()
                        .map(SearchResultItem::url)
                        .collect(Collectors.toSet());
                List<SearchResultItem> verified = curated.results().stream()
                        .filter(r -> candidateUrls.contains(r.url()))
                        .toList();

                if (verified.isEmpty()) {
                    assistantReply = "没有找到相关的文章，换个说法试试？";
                } else {
                    assistantReply = curated.reply();
                    assistantResults = verified.stream()
                            .map(r -> new ClipSearchResultItem(r.title(), r.url(), r.excerpt(), null))
                            .toList();
                }
            }
        } catch (Exception e) {
            log.error("clip search pipeline failed for user {}", username, e);
            assistantReply = "抱歉，这次没查到，换个说法试试？";
        }

        ClipSearchMessage assistantMessage = messageRepository.save(ClipSearchMessage.builder()
                .owner(user).role(ClipSearchMessage.Role.ASSISTANT).content(assistantReply)
                .resultsJson(assistantResults == null ? null : writeJson(assistantResults))
                .build());

        return List.of(toResponse(userMessage), toResponse(assistantMessage));
    }

    // 同上：中间夹着 clipImportService.fetchFromUrl 的十几秒外部抓取，不能占着事务连接等。
    public Long saveResult(Long messageId, int resultIndex, String username) {
        User user = getUser(username);
        ClipSearchMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("对话消息不存在"));
        if (!message.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权操作该消息");
        }
        List<ClipSearchResultItem> results = readResults(message.getResultsJson());
        if (resultIndex < 0 || resultIndex >= results.size()) {
            throw new ResourceNotFoundException("搜索结果不存在");
        }
        ClipSearchResultItem item = results.get(resultIndex);
        if (item.sourceClipId() != null) {
            return item.sourceClipId();
        }

        ClipImportUrlRequest importRequest = new ClipImportUrlRequest();
        importRequest.setUrl(item.url());
        SourceClipDraft draft = clipImportService.fetchFromUrl(importRequest);

        SourceClipRequest createRequest = new SourceClipRequest();
        createRequest.setSourceType(draft.getSourceType());
        createRequest.setSourceUrl(draft.getSourceUrl());
        createRequest.setSourceTitle(draft.getSourceTitle());
        createRequest.setSourceAuthor(draft.getSourceAuthor());
        createRequest.setExtractionMode(draft.getExtractionMode());
        createRequest.setExtractionStatus(draft.getExtractionStatus());
        // SourceClip.title 有 @NotBlank/@Size(max=200)，而这里绕过了 controller 的 @Valid，
        // 必须自己兜底：挑第一个非空候选标题并截断到 200 字符，否则会在 flush 时炸成 500。
        String candidateTitle = draft.getSuggestedTitle();
        if (candidateTitle == null || candidateTitle.isBlank()) candidateTitle = item.title();
        if (candidateTitle == null || candidateTitle.isBlank()) candidateTitle = item.url();
        createRequest.setTitle(candidateTitle.length() > 200 ? candidateTitle.substring(0, 200) : candidateTitle);
        createRequest.setContent(draft.getContent());
        createRequest.setContentFormat(draft.getContentFormat());

        SourceClipResponse created = sourceClipService.createClip(createRequest, username);

        List<ClipSearchResultItem> updated = new ArrayList<>(results);
        updated.set(resultIndex, new ClipSearchResultItem(item.title(), item.url(), item.excerpt(), created.getId()));
        message.setResultsJson(writeJson(updated));
        messageRepository.save(message);

        return created.getId();
    }

    private List<SearchResultItem> previousCandidates(List<ClipSearchMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ClipSearchMessage m = history.get(i);
            if (m.getRole() == ClipSearchMessage.Role.ASSISTANT && m.getResultsJson() != null) {
                return readResults(m.getResultsJson()).stream()
                        .map(r -> new SearchResultItem(r.title(), r.url(), r.excerpt()))
                        .toList();
            }
        }
        return List.of();
    }

    private AiTranslationService resolveAi() {
        return switch (aiProperties.getProvider().toLowerCase()) {
            case "openai" -> openAiService;
            case "deepseek" -> deepseekService;
            case "doubao" -> doubaoService;
            default -> anthropicService;
        };
    }

    private ClipSearchMessageResponse toResponse(ClipSearchMessage m) {
        return ClipSearchMessageResponse.builder()
                .id(m.getId())
                .role(m.getRole().name())
                .content(m.getContent())
                .results(m.getResultsJson() == null ? null : readResults(m.getResultsJson()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private List<ClipSearchResultItem> readResults(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ClipSearchResultItem>>() {});
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
