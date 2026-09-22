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
        String confirmProposalId;
        String confirmBlockType;
        Long confirmNoteId;
        Map<String, Object> confirmPreview;
        Long blockUpdatedNoteId;
        String blockUpdatedBlockId;
        String blockUpdatedBlockType;
        List<Map<String, Object>> blockUpdatedItems;
        Long mediaBlockUpdatedNoteId;
        String mediaBlockUpdatedBlockId;
        String mediaBlockUpdatedBlockType;
        Map<String, Object> mediaBlockUpdatedData;

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

        @Override
        public void onConfirmRequest(String proposalId, String blockType, Long noteId, Map<String, Object> preview) {
            confirmProposalId = proposalId;
            confirmBlockType = blockType;
            confirmNoteId = noteId;
            confirmPreview = preview;
        }

        @Override
        public void onBlockUpdated(Long noteId, String blockId, String blockType, List<Map<String, Object>> items) {
            blockUpdatedNoteId = noteId;
            blockUpdatedBlockId = blockId;
            blockUpdatedBlockType = blockType;
            blockUpdatedItems = items;
        }

        @Override
        public void onMediaBlockUpdated(Long noteId, String blockId, String blockType, Map<String, Object> data) {
            mediaBlockUpdatedNoteId = noteId;
            mediaBlockUpdatedBlockId = blockId;
            mediaBlockUpdatedBlockType = blockType;
            mediaBlockUpdatedData = data;
        }
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

        client.streamChat("alice", "帮我查一下", "alice", null, List.of(), null, listener);

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

        client.streamChat("alice", "问题", "alice", null, List.of(), null, listener);

        assertThat(listener.errorMessage).isEqualTo("下游模型报错了");
    }

    @Test
    void streamChat_sendsInternalTokenHeaderAndRequestBody() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"ok\",\"citations\":[]}\n");

        client.streamChat("alice", "你好", "alice", "笔记正文", List.of(), null, new RecordingListener());

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

        client.streamChat("alice", "你好", "alice", null, List.of(), null, new RecordingListener());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(body).doesNotContainKey("currentNoteContext");
    }

    @Test
    void streamChat_parsesWebCitationWithoutSourceId() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"参考网上资料\",\"citations\":"
                + "[{\"sourceType\":\"WEB\",\"sourceUrl\":\"https://example.com/a\",\"title\":\"示例文章\"}]}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "问题", "alice", null, List.of(), null, listener);

        assertThat(listener.doneCitations).hasSize(1);
        assertThat(listener.doneCitations.get(0).sourceType()).isEqualTo("WEB");
        assertThat(listener.doneCitations.get(0).sourceId()).isNull();
        assertThat(listener.doneCitations.get(0).sourceUrl()).isEqualTo("https://example.com/a");
        assertThat(listener.doneCitations.get(0).title()).isEqualTo("示例文章");
    }

    @Test
    void streamChat_sendsCurrentNoteIdWhenProvided() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"ok\",\"citations\":[]}\n");

        client.streamChat("alice", "你好", "alice", null, List.of(), 9L, new RecordingListener());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(body.get("currentNoteId")).isEqualTo(9);
    }

    @Test
    void streamChat_omitsCurrentNoteIdWhenNull() throws Exception {
        setUp(200, "{\"type\":\"done\",\"content\":\"ok\",\"citations\":[]}\n");

        client.streamChat("alice", "你好", "alice", null, List.of(), null, new RecordingListener());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
        assertThat(body).doesNotContainKey("currentNoteId");
    }

    @Test
    void streamChat_throwsOnHttpErrorStatus() throws Exception {
        setUp(500, "internal server error");

        assertThatThrownBy(() -> client.streamChat("alice", "你好", "alice", null, List.of(), null, new RecordingListener()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }

    @Test
    void visionExtract_postsImageDataUriAndReturnsExtractedText() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicReference<String> capturedBody = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/internal/vision-extract", ex -> {
            capturedBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String responseJson = "{\"text\":\"识别到的文字\"}";
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        try {
            AgentServiceClient visionClient = new AgentServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-internal-token", objectMapper);

            String text = visionClient.visionExtract("data:image/png;base64,abc");

            assertThat(text).isEqualTo("识别到的文字");
            assertThat(capturedBody.get()).contains("data:image/png;base64,abc");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamChat_parsesConfirmRequestEvent() throws Exception {
        setUp(200, "{\"type\":\"confirm_request\",\"proposalId\":\"p1\",\"blockType\":\"timeline\","
                + "\"noteId\":9,\"preview\":{\"date\":\"2024-01\",\"title\":\"事件一\"}}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "帮我加一条", "alice", null, List.of(), 9L, listener);

        assertThat(listener.confirmProposalId).isEqualTo("p1");
        assertThat(listener.confirmBlockType).isEqualTo("timeline");
        assertThat(listener.confirmNoteId).isEqualTo(9L);
        assertThat(listener.confirmPreview).containsEntry("date", "2024-01").containsEntry("title", "事件一");
    }

    @Test
    void streamChat_parsesBlockUpdatedEvent() throws Exception {
        setUp(200, "{\"type\":\"block_updated\",\"noteId\":9,\"blockId\":\"abc\",\"blockType\":\"timeline\","
                + "\"items\":[{\"date\":\"2024-01\",\"title\":\"事件一\"}]}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "帮我加一条", "alice", null, List.of(), 9L, listener);

        assertThat(listener.blockUpdatedNoteId).isEqualTo(9L);
        assertThat(listener.blockUpdatedBlockId).isEqualTo("abc");
        assertThat(listener.blockUpdatedBlockType).isEqualTo("timeline");
        assertThat(listener.blockUpdatedItems).hasSize(1);
    }

    @Test
    void streamChat_parsesMediaBlockUpdatedEvent() throws Exception {
        setUp(200, "{\"type\":\"media_block_updated\",\"noteId\":9,\"blockId\":\"b1\",\"blockType\":\"image\","
                + "\"data\":{\"url\":\"a.png\",\"caption\":\"一张图片描述\"}}\n");
        RecordingListener listener = new RecordingListener();

        client.streamChat("alice", "分析这张图", "alice", null, List.of(), 9L, listener);

        assertThat(listener.mediaBlockUpdatedNoteId).isEqualTo(9L);
        assertThat(listener.mediaBlockUpdatedBlockId).isEqualTo("b1");
        assertThat(listener.mediaBlockUpdatedBlockType).isEqualTo("image");
        assertThat(listener.mediaBlockUpdatedData).containsEntry("caption", "一张图片描述");
    }

    @Test
    void resumeChat_postsToResumeEndpointAndParsesEvents() throws Exception {
        HttpServer resumeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        resumeServer.createContext("/internal/chat/resume", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = "{\"type\":\"done\",\"content\":\"已添加\",\"citations\":[]}\n";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        resumeServer.start();
        try {
            AgentServiceClient resumeClient = new AgentServiceClient(
                    "http://127.0.0.1:" + resumeServer.getAddress().getPort(), "test-internal-token", objectMapper);
            RecordingListener listener = new RecordingListener();

            resumeClient.resumeChat("alice", "p1", "accept", listener);

            assertThat(listener.doneContent).isEqualTo("已添加");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(lastRequestBody, Map.class);
            assertThat(body.get("conversationId")).isEqualTo("alice");
            assertThat(body.get("proposalId")).isEqualTo("p1");
            assertThat(body.get("decision")).isEqualTo("accept");
        } finally {
            resumeServer.stop(0);
        }
    }
}
