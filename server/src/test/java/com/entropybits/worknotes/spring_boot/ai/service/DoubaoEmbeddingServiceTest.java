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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubaoEmbeddingServiceTest {

    private HttpServer httpServer;
    private DoubaoEmbeddingService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setUp(int statusCode, String responseBody) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/embeddings", ex -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(statusCode, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        AiProperties props = new AiProperties();
        props.getDoubao().setApiKey("test-key");
        props.getDoubao().setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        props.getEmbedding().setModel("test-embedding-model");
        props.getEmbedding().setPath("/embeddings");

        service = new DoubaoEmbeddingService(props, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    @Test
    void embed_parsesFloatArrayFromResponse() throws Exception {
        setUp(200, "{\"data\":{\"embedding\":[0.1,0.2,0.3]}}");

        float[] result = service.embed("测试文本");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void embed_throwsWhenApiReturnsError() throws Exception {
        setUp(500, "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> service.embed("测试文本"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }

    @Test
    void embed_throwsWhenResponseDataIsEmpty() throws Exception {
        setUp(200, "{\"data\":{}}");

        assertThatThrownBy(() -> service.embed("测试文本"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty data");
    }
}
