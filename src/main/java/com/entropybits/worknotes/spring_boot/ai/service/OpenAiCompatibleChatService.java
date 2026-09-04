/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

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
import java.util.stream.Stream;

/**
 * 通用 OpenAI Chat Completions 兼容实现，支持 OpenAI、DeepSeek、豆包（火山引擎）。
 * 与 {@link OpenAiCompatibleTranslationService} 是姊妹类：那个是单发任务型 prompt，
 * 这个是真正的多轮消息数组 + system prompt。
 */
@Slf4j
public class OpenAiCompatibleChatService implements ChatService {

    private final AiProperties.ProviderConfig config;
    private final String providerName;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    public OpenAiCompatibleChatService(AiProperties.ProviderConfig config,
                                        String providerName,
                                        ObjectMapper objectMapper) {
        this.config = config;
        this.providerName = providerName;
        this.objectMapper = objectMapper;
    }

    @Override
    public void chatStream(String systemPrompt, List<ChatTurn> history, List<ToolDefinition> tools,
                            StreamListener listener) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", turn.role(), "content", buildMessageContent(turn)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolDefs = new ArrayList<>();
            for (ToolDefinition t : tools) {
                toolDefs.add(Map.of(
                        "type", "function",
                        "function", Map.of("name", t.name(), "description", t.description(), "parameters", t.inputSchema())
                ));
            }
            body.put("tools", toolDefs);
            body.put("parallel_tool_calls", false);
        }

        String url = config.getBaseUrl() + config.getChatPath();
        String json = objectMapper.writeValueAsString(body);
        log.debug("{} chatStream request: model={}", providerName, config.getModel());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + config.getApiKey())
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(120))
            .build();

        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() >= 400) {
            String errorBody;
            try (Stream<String> errorLines = response.body()) {
                errorBody = errorLines.collect(java.util.stream.Collectors.joining("\n"));
            }
            throw new RuntimeException(providerName + " chatStream API error: HTTP " + response.statusCode() + " " + errorBody);
        }

        String toolCallId = null;
        String toolCallName = null;
        StringBuilder toolArgsJson = new StringBuilder();
        boolean sawToolCall = false;

        try (Stream<String> responseBody = response.body()) {
            Iterator<String> lines = responseBody.iterator();
            while (lines.hasNext()) {
                String line = lines.next();
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6);
                if ("[DONE]".equals(data.trim())) break;

                Map<String, Object> evt = objectMapper.readValue(data, new TypeReference<>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) evt.get("choices");
                if (choices == null || choices.isEmpty()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta == null) continue;

                String content = (String) delta.get("content");
                if (content != null) {
                    listener.onTextDelta(content);
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
                if (toolCalls != null) {
                    for (Map<String, Object> tc : toolCalls) {
                        sawToolCall = true;
                        if (tc.get("id") != null) toolCallId = (String) tc.get("id");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> function = (Map<String, Object>) tc.get("function");
                        if (function != null) {
                            if (function.get("name") != null) toolCallName = (String) function.get("name");
                            if (function.get("arguments") != null) toolArgsJson.append((String) function.get("arguments"));
                        }
                    }
                }
            }
        }

        if (sawToolCall) {
            Map<String, Object> input = toolArgsJson.length() > 0
                    ? objectMapper.readValue(toolArgsJson.toString(), new TypeReference<>() {})
                    : Map.of();
            listener.onToolCallStart(new ToolCall(toolCallId, toolCallName, input));
        }
        listener.onDone();
    }

    private Object buildMessageContent(ChatTurn turn) {
        if (turn.attachments() == null || turn.attachments().isEmpty()) {
            return turn.content();
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (turn.content() != null && !turn.content().isBlank()) {
            blocks.add(Map.of("type", "text", "text", turn.content()));
        }
        for (Attachment att : turn.attachments()) {
            if ("image".equals(att.type())) {
                blocks.add(Map.of("type", "image_url", "image_url",
                        Map.of("url", "data:" + att.mimeType() + ";base64," + att.base64Data())));
            } else {
                blocks.add(Map.of("type", "file", "file",
                        Map.of("filename", "attachment", "file_data",
                                "data:" + att.mimeType() + ";base64," + att.base64Data())));
            }
        }
        return blocks;
    }
}
