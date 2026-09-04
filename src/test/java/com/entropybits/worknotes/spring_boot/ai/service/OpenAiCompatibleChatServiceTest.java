/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleChatServiceTest {

    private HttpServer httpServer;
    private OpenAiCompatibleChatService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String lastRequestBody;

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    private void setUpStreaming(String sseBody) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/chat/completions", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        AiProperties.ProviderConfig config = new AiProperties.ProviderConfig();
        config.setApiKey("test-key");
        config.setModel("test-model");
        config.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        config.setChatPath("/v1/chat/completions");

        service = new OpenAiCompatibleChatService(config, "TestProvider", objectMapper);
    }

    private static class RecordingListener implements ChatService.StreamListener {
        final List<String> textDeltas = new java.util.ArrayList<>();
        final List<ChatService.ToolCall> toolCalls = new java.util.ArrayList<>();
        boolean done = false;

        @Override
        public void onTextDelta(String delta) { textDeltas.add(delta); }

        @Override
        public void onToolCallStart(ChatService.ToolCall call) { toolCalls.add(call); }

        @Override
        public void onDone() { done = true; }
    }

    private static final String TEXT_ONLY_SSE = """
            data: {"choices":[{"delta":{"content":"你好，"}}]}

            data: {"choices":[{"delta":{"content":"有什么可以帮你？"}}]}

            data: [DONE]

            """;

    private static final String TOOL_USE_SSE = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","function":{"name":"search_workspace","arguments":"{\\"query\\": \\""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"用户增长\\"}"}}]}}]}

            data: [DONE]

            """;

    @Test
    void chatStream_forwardsTextDeltasInOrderAndCallsOnDone() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();

        service.chatStream("system prompt", List.of(), List.of(), listener);

        assertThat(listener.textDeltas).containsExactly("你好，", "有什么可以帮你？");
        assertThat(listener.toolCalls).isEmpty();
        assertThat(listener.done).isTrue();
    }

    @Test
    void chatStream_detectsToolCallWithAccumulatedJsonInput() throws Exception {
        setUpStreaming(TOOL_USE_SSE);
        RecordingListener listener = new RecordingListener();
        ChatService.ToolDefinition tool = new ChatService.ToolDefinition(
                "search_workspace", "搜索工作区", Map.of("type", "object"));

        service.chatStream("system prompt", List.of(), List.of(tool), listener);

        assertThat(listener.toolCalls).hasSize(1);
        ChatService.ToolCall call = listener.toolCalls.get(0);
        assertThat(call.id()).isEqualTo("call_abc");
        assertThat(call.name()).isEqualTo("search_workspace");
        assertThat(call.input()).containsEntry("query", "用户增长");
        assertThat(listener.done).isTrue();
    }

    @Test
    void chatStream_includesToolsAndStreamFlagInRequestBodyWhenToolsProvided() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();
        ChatService.ToolDefinition tool = new ChatService.ToolDefinition(
                "search_workspace", "搜索工作区", Map.of("type", "object", "properties", Map.of()));

        service.chatStream("system prompt", List.of(), List.of(tool), listener);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(requestBody.get("stream")).isEqualTo(true);
        assertThat(requestBody).containsKey("tools");
    }

    @Test
    void chatStream_buildsImageUrlContentBlockWhenAttachmentPresent() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();
        ChatService.ChatTurn turnWithImage = new ChatService.ChatTurn("user", "这张图是什么",
                List.of(new ChatService.Attachment("image", "image/png", "aGVsbG8=")));

        service.chatStream("system prompt", List.of(turnWithImage), List.of(), listener);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = objectMapper.readValue(lastRequestBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
        // index 0 是 system 消息，index 1 才是这条 user 消息
        Object content = messages.get(1).get("content");
        assertThat(content).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(1)).containsEntry("type", "image_url");
    }

    @Test
    void chatStream_keepsPlainStringContentWhenNoAttachments() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();

        service.chatStream("system prompt", List.of(new ChatService.ChatTurn("user", "普通问题")), List.of(), listener);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = objectMapper.readValue(lastRequestBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
        assertThat(messages.get(1).get("content")).isInstanceOf(String.class).isEqualTo("普通问题");
    }
}
