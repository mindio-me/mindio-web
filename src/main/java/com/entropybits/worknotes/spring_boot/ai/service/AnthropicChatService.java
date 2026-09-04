/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * Anthropic Messages API 的多轮对话实现。与 {@link AnthropicTranslationService} 是姊妹类：
 * 那个是单发任务型 prompt，这个是真正的多轮消息数组 + 独立的 system 字段
 * （Anthropic Messages API 把 system prompt 作为顶层字段，不是 messages 数组里的一条）。
 */
@Slf4j
@Service("anthropicChatService")
@RequiredArgsConstructor
public class AnthropicChatService implements ChatService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    public void chatStream(String systemPrompt, List<ChatTurn> history, List<ToolDefinition> tools,
                            StreamListener listener) throws Exception {
        AiProperties.ProviderConfig cfg = aiProperties.getAnthropic();
        String url = cfg.getBaseUrl() + "/v1/messages";

        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", turn.role(), "content", buildMessageContent(turn)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("max_tokens", 4096);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolDefs = new ArrayList<>();
            for (ToolDefinition t : tools) {
                toolDefs.add(Map.of("name", t.name(), "description", t.description(), "input_schema", t.inputSchema()));
            }
            body.put("tools", toolDefs);
        }

        String json = objectMapper.writeValueAsString(body);
        log.debug("Anthropic chatStream request: model={}", cfg.getModel());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-api-key", cfg.getApiKey())
            .header("anthropic-version", "2023-06-01")
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
            throw new RuntimeException("Anthropic chatStream API error: HTTP " + response.statusCode() + " " + errorBody);
        }

        String currentEvent = null;
        boolean inToolUseBlock = false;
        String toolUseId = null;
        String toolUseName = null;
        StringBuilder toolInputJson = new StringBuilder();

        try (Stream<String> responseBody = response.body()) {
            Iterator<String> lines = responseBody.iterator();
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.startsWith("event: ")) {
                    currentEvent = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("content_block_start".equals(currentEvent)) {
                        Map<String, Object> evt = objectMapper.readValue(data, new TypeReference<>() {});
                        @SuppressWarnings("unchecked")
                        Map<String, Object> block = (Map<String, Object>) evt.get("content_block");
                        if (block != null && "tool_use".equals(block.get("type"))) {
                            inToolUseBlock = true;
                            toolUseId = (String) block.get("id");
                            toolUseName = (String) block.get("name");
                            toolInputJson.setLength(0);
                        }
                    } else if ("content_block_delta".equals(currentEvent)) {
                        Map<String, Object> evt = objectMapper.readValue(data, new TypeReference<>() {});
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) evt.get("delta");
                        if (delta != null && "text_delta".equals(delta.get("type"))) {
                            listener.onTextDelta((String) delta.get("text"));
                        } else if (delta != null && "input_json_delta".equals(delta.get("type"))) {
                            toolInputJson.append((String) delta.get("partial_json"));
                        }
                    } else if ("content_block_stop".equals(currentEvent)) {
                        if (inToolUseBlock) {
                            Map<String, Object> input = toolInputJson.length() > 0
                                    ? objectMapper.readValue(toolInputJson.toString(), new TypeReference<>() {})
                                    : Map.of();
                            listener.onToolCallStart(new ToolCall(toolUseId, toolUseName, input));
                            inToolUseBlock = false;
                        }
                    }
                }
            }
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
            String blockType = "image".equals(att.type()) ? "image" : "document";
            blocks.add(Map.of(
                    "type", blockType,
                    "source", Map.of("type", "base64", "media_type", att.mimeType(), "data", att.base64Data())
            ));
        }
        return blocks;
    }
}
