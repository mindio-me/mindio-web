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

class AnthropicChatServiceTest {

    private HttpServer httpServer;
    private AnthropicChatService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String lastRequestBody;

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    private void setUpStreaming(String sseBody) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/messages", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        AiProperties props = new AiProperties();
        props.getAnthropic().setApiKey("test-key");
        props.getAnthropic().setModel("test-model");
        props.getAnthropic().setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());

        service = new AnthropicChatService(props, objectMapper);
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
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1","role":"assistant","content":[]}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好，"}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"有什么可以帮你？"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":10}}

            event: message_stop
            data: {"type":"message_stop"}

            """;

    private static final String TOOL_USE_SSE = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_2","role":"assistant","content":[]}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_abc","name":"search_workspace","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"query\\": \\""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"用户增长\\"}"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":5}}

            event: message_stop
            data: {"type":"message_stop"}

            """;

    private static final String NARRATE_THEN_TOOL_USE_SSE = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_3","role":"assistant","content":[]}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"我先搜一下你的笔记"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: content_block_start
            data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_xyz","name":"search_workspace","input":{}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"query\\": \\"用户增长\\"}"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":1}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":8}}

            event: message_stop
            data: {"type":"message_stop"}

            """;

    @Test
    void chatStream_forwardsNarrationTextBeforeToolCallInSameResponse() throws Exception {
        setUpStreaming(NARRATE_THEN_TOOL_USE_SSE);
        RecordingListener listener = new RecordingListener();
        ChatService.ToolDefinition tool = new ChatService.ToolDefinition(
                "search_workspace", "搜索工作区", Map.of("type", "object"));

        service.chatStream("system prompt", List.of(), List.of(tool), listener);

        assertThat(listener.textDeltas).containsExactly("我先搜一下你的笔记");
        assertThat(listener.toolCalls).hasSize(1);
        assertThat(listener.toolCalls.get(0).input()).containsEntry("query", "用户增长");
        assertThat(listener.done).isTrue();
    }

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
        assertThat(call.id()).isEqualTo("toolu_abc");
        assertThat(call.name()).isEqualTo("search_workspace");
        assertThat(call.input()).containsEntry("query", "用户增长");
        assertThat(listener.textDeltas).isEmpty();
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
    void chatStream_omitsToolsFieldWhenToolListEmpty() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();

        service.chatStream("system prompt", List.of(), List.of(), listener);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(requestBody).doesNotContainKey("tools");
    }

    @Test
    void chatStream_buildsMultimodalContentBlocksWhenAttachmentPresent() throws Exception {
        setUpStreaming(TEXT_ONLY_SSE);
        RecordingListener listener = new RecordingListener();
        ChatService.ChatTurn turnWithImage = new ChatService.ChatTurn("user", "这张图是什么",
                List.of(new ChatService.Attachment("image", "image/png", "aGVsbG8=")));

        service.chatStream("system prompt", List.of(turnWithImage), List.of(), listener);

        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = objectMapper.readValue(lastRequestBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
        Object content = messages.get(0).get("content");
        assertThat(content).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).containsEntry("type", "text").containsEntry("text", "这张图是什么");
        assertThat(blocks.get(1)).containsEntry("type", "image");
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) blocks.get(1).get("source");
        assertThat(source).containsEntry("media_type", "image/png").containsEntry("data", "aGVsbG8=");
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
        assertThat(messages.get(0).get("content")).isInstanceOf(String.class).isEqualTo("普通问题");
    }
}
