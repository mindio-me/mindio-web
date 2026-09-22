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
 * 火山方舟（豆包）多模态向量化接口调用（/api/v3/embeddings/multimodal）。
 * 请求体 input 是带 type 字段的内容对象数组（本服务只用 {"type":"text","text":...}），
 * 不是纯文本 embedding 接口那种字符串数组，两者协议不同，参见火山方舟 embeddings/multimodal 文档。
 * 响应是 {data: {embedding: [...]}}（单个对象，一次调用的多模态输入只产出一个联合向量，
 * 跟纯文本接口按数组返回逐条向量不同）。
 */
@Slf4j
@Service
public class DoubaoEmbeddingService implements EmbeddingService {

    private final AiProperties.ProviderConfig doubaoConfig;
    private final AiProperties.EmbeddingConfig embeddingConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DoubaoEmbeddingService(AiProperties props, ObjectMapper objectMapper) {
        this.doubaoConfig = props.getDoubao();
        this.embeddingConfig = props.getEmbedding();
        this.objectMapper = objectMapper;
    }

    @Override
    public float[] embed(String text) throws Exception {
        String url = doubaoConfig.getBaseUrl() + embeddingConfig.getPath();
        Map<String, Object> body = Map.of(
                "model", embeddingConfig.getModel(),
                "encoding_format", "float",
                "input", List.of(Map.of("type", "text", "text", text))
        );
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + doubaoConfig.getApiKey())
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.debug("Doubao embedding response status={} body={}", response.statusCode(), response.body());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Doubao embedding API error: HTTP " + response.statusCode() + " " + response.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");

        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Doubao embedding API returned empty data");
        }

        @SuppressWarnings("unchecked")
        List<Number> embeddingNumbers = (List<Number>) data.get("embedding");

        if (embeddingNumbers == null) {
            throw new RuntimeException("Doubao embedding API response missing data.embedding, data keys=" + data.keySet());
        }

        float[] embedding = new float[embeddingNumbers.size()];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = embeddingNumbers.get(i).floatValue();
        }
        return embedding;
    }
}
