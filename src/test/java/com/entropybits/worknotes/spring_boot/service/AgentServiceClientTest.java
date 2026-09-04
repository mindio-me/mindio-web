/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServiceClientTest {

    private HttpServer httpServer;
    private AgentServiceClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String lastRequestBody;
    private String lastAuthHeader;

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    private void setUp(int statusCode, String responseBody) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/internal/chat/stream", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthHeader = ex.getRequestHeaders().getFirst("X-Internal-Token");
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(statusCode, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        client = new AgentServiceClient(
                "http://127.0.0.1:" + httpServer.getAddress().getPort(),
                "test-internal-token",
                objectMapper);
    }

    private static class RecordingListener implements AgentServiceClient.StreamListener {
        final List<String> textDeltas = new java.util.ArrayList<>();
        final List<String> toolCalls = new java.util.ArrayList<>();
        String doneContent;
        List<ChatCitation> doneCitations;
        String errorMessage;

        @Override
        public void onTextDelta(String text) { textDeltas.add(text); }

        @Override
        public void onToolCall(String query) { toolCalls.add(query); }

        @Override
        public void onDone(String content, List<ChatCitation> citations) {
            doneContent = content;
            doneCitations = citations;
        }

        @Override
        public void onError(String message) { errorMessage = message; }
    }

    @Test
    void streamChat_parsesTextDeltaToolCallAndDoneEventsInOrder() throws Exception {
        String ndjson = """
                {"type":"text_delta","text":"你好，"}
                {"type":"tool_call","query":"用户增长"}
                {"type":"text_delta","text":"根据笔记"}
                {"type":"done","content":"根据笔记的回答","citations":[{"sourceType":"NOTE","sourceId":7}]}
                """;
        setUp(200, ndjson);
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "帮我查一下", "alice", null, List.of(), listener);

        assertThat(listener.textDeltas).containsExactly("你好，", "根据笔记");
        assertThat(listener.toolCalls).containsExactly("用户增长");
        assertThat(listener.doneContent).isEqualTo("根据笔记的回答");
        assertThat(listener.doneCitations).hasSize(1);
        assertThat(listener.doneCitations.get(0).sourceType()).isEqualTo("NOTE");
        assertThat(listener.doneCitations.get(0).sourceId()).isEqualTo(7L);
        assertThat(listener.doneCitations.get(0).title()).isNull();
    }

    @Test
    void streamChat_parsesErrorEvent() throws Exception {
        setUp(200, "{\"type\":\"error\",\"content\":\"下游模型报错了\"}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "问题", "alice", null, List.of(), listener);

        assertThat(listener.errorMessage).isEqualTo("下游模型报错了");
    }

    @Test
    void streamChat_sendsInternalTokenHeaderAndRequestBody() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"ok\",\"citations\":[]}\n");

        client.streamChat("alice", "你好", "alice", "笔记正文", List.of(), new RecordingListener());

        assertThat(lastAuthHeader).isEqualTo("test-internal-token");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(body.get("username")).isEqualTo("alice");
        assertThat(body.get("content")).isEqualTo("你好");
        assertThat(body.get("conversationId")).isEqualTo("alice");
        assertThat(body.get("currentNoteContext")).isEqualTo("笔记正文");
    }

    @Test
    void streamChat_omitsCurrentNoteContextWhenNull() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"ok\",\"citations\":[]}\n");

        client.streamChat("alice", "你好", "alice", null, List.of(), new RecordingListener());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(body).doesNotContainKey("currentNoteContext");
    }

    @Test
    void streamChat_parsesWebCitationWithoutSourceId() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"参考网上资料\",\"citations\":"
                + "[{\"sourceType\":\"WEB\",\"sourceUrl\":\"https://example.com/a\",\"title\":\"示例文章\"}]}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "问题", "alice", null, List.of(), listener);

        assertThat(listener.doneCitations).hasSize(1);
        assertThat(listener.doneCitations.get(0).sourceType()).isEqualTo("WEB");
        assertThat(listener.doneCitations.get(0).sourceId()).isNull();
        assertThat(listener.doneCitations.get(0).sourceUrl()).isEqualTo("https://example.com/a");
        assertThat(listener.doneCitations.get(0).title()).isEqualTo("示例文章");
    }

    @Test
    void streamChat_throwsOnHttpErrorStatus() throws Exception {
        setUp(500, "internal server error");

        assertThatThrownBy(() -> client.streamChat("alice", "你好", "alice", null, List.of(), new RecordingListener()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }
}
