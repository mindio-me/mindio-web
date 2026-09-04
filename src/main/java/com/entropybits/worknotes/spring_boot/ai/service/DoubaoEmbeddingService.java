/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟（豆包）embedding 接口调用，走 OpenAI 兼容协议
 * （POST {model, input} → {data: [{embedding: [...]}]}）。
 * 具体字段名/路径以实施时的火山方舟 embedding API 文档为准，这里假设与 OpenAI embeddings
 * 接口同构（与现有 chat-path 的兼容处理是同一模式）。
 */
@Slf4j
@Service
public class DoubaoEmbeddingService implements EmbeddingService {

    private final AiProperties.ProviderConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DoubaoEmbeddingService(AiProperties props, ObjectMapper objectMapper) {
        this.config = props.getDoubao();
        this.objectMapper = objectMapper;
    }

    @Override
    public float[] embed(String text) throws Exception {
        String url = config.getBaseUrl() + config.getEmbeddingPath();
        Map<String, Object> body = Map.of(
                "model", config.getEmbeddingModel(),
                "input", List.of(text)
        );
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.debug("Doubao embedding response status={}", response.statusCode());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Doubao embedding API error: HTTP " + response.statusCode() + " " + response.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");

        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Doubao embedding API returned empty data");
        }

        @SuppressWarnings("unchecked")
        List<Number> embeddingNumbers = (List<Number>) data.get(0).get("embedding");

        float[] embedding = new float[embeddingNumbers.size()];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = embeddingNumbers.get(i).floatValue();
        }
        return embedding;
    }
}
