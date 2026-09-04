/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AttachmentPayload;
import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 调用独立的Python/LangGraph Agent服务（/internal/chat/stream）的客户端，取代之前
 * 手写HTTP+SSE解析的 ChatService 实现。Agent服务返回NDJSON事件流（每行一个完整
 * JSON），type取值 text_delta/tool_call/done/error，和Java对外的 ChatStreamEvent
 * 语义一一对应，转发逻辑不需要额外翻译。详见
 * docs/superpowers/specs/2026-09-04-agent-service-langgraph-design.md。
 */
@Slf4j
@Component
public class AgentServiceClient {

    public interface StreamListener {
        void onTextDelta(String text);
        void onToolCall(String query);
        /** citations里的title字段还没解析——Agent服务只知道sourceType/sourceId，
         * 人类可读的标题要调用方自己按ID反查（复用现有lookupTitle逻辑）。 */
        void onDone(String content, List<ChatCitation> citations);
        void onError(String message);
    }

    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public AgentServiceClient(@Value("${agent-service.base-url}") String baseUrl,
                               @Value("${agent.internal-token}") String internalToken,
                               ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    public void streamChat(String username, String content, String conversationId,
                            String currentNoteContext, List<AttachmentPayload> attachments,
                            StreamListener listener) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("content", content);
        body.put("conversationId", conversationId);
        if (currentNoteContext != null) {
            body.put("currentNoteContext", currentNoteContext);
        }
        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }

        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/chat/stream"))
                .header("X-Internal-Token", internalToken)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() >= 400) {
            String errorBody;
            try (Stream<String> errorLines = response.body()) {
                errorBody = errorLines.collect(Collectors.joining("\n"));
            }
            throw new RuntimeException("agent service error: HTTP " + response.statusCode() + " " + errorBody);
        }

        try (Stream<String> lines = response.body()) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line.isBlank()) continue;
                Map<String, Object> event = objectMapper.readValue(line, new TypeReference<>() {});
                dispatch(event, listener);
            }
        }
    }

    private void dispatch(Map<String, Object> event, StreamListener listener) {
        String type = (String) event.get("type");
        if (type == null) {
            log.warn("agent service event missing type: {}", event);
            return;
        }
        switch (type) {
            case "text_delta" -> listener.onTextDelta((String) event.get("text"));
            case "tool_call" -> listener.onToolCall((String) event.get("query"));
            case "done" -> listener.onDone((String) event.get("content"), parseCitations(event.get("citations")));
            case "error" -> listener.onError((String) event.get("content"));
            default -> log.warn("unknown agent service event type: {}", type);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ChatCitation> parseCitations(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<ChatCitation> citations = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> m = (Map<String, Object>) item;
            String sourceType = (String) m.get("sourceType");
            if (sourceType == null) continue;
            if ("WEB".equals(sourceType)) {
                String sourceUrl = (String) m.get("sourceUrl");
                if (sourceUrl == null) continue;
                citations.add(new ChatCitation(sourceType, null, (String) m.get("title"), sourceUrl));
            } else {
                Number sourceId = (Number) m.get("sourceId");
                if (sourceId == null) continue;
                citations.add(new ChatCitation(sourceType, sourceId.longValue(), null, null));
            }
        }
        return citations;
    }
}
