/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ContentChunkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 语义检索：把用户问题也 embedding 一下，取该用户全部分块暴力算余弦相似度，
 * 返回按相似度降序排列、高于阈值的 top-K。这是子项目1对外暴露的唯一公共接口。
 */
@Service
public class RetrievalService {

    private static final double SIMILARITY_THRESHOLD = 0.7;

    private final ContentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public RetrievalService(ContentChunkRepository chunkRepository,
                             EmbeddingService embeddingService,
                             ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(User user, String queryText, int topK) throws Exception {
        float[] queryVector = embeddingService.embed(queryText);
        List<ContentChunk> allChunks = chunkRepository.findByOwner(user);

        return allChunks.stream()
                .map(c -> new ScoredChunk(c, cosineSimilarity(queryVector, parseEmbedding(c.getEmbeddingJson()))))
                .filter(sc -> sc.score() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .map(sc -> new RetrievedChunk(sc.chunk().getSourceType(), sc.chunk().getSourceId(),
                        sc.chunk().getChunkText(), sc.score()))
                .toList();
    }

    private float[] parseEmbedding(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            return new float[0]; // 脏数据：跳过这一块，不影响整体检索
        }
    }

    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(ContentChunk chunk, double score) {}
}
