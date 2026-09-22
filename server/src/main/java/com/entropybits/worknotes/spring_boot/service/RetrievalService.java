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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义检索：把用户问题也 embedding 一下，取该用户全部分块暴力算余弦相似度，
 * 返回按相似度降序排列、高于阈值的 top-K。这是子项目1对外暴露的唯一公共接口。
 * <p>
 * 同一篇文档最多贡献 {@link #MAX_CHUNKS_PER_SOURCE} 个分块进最终结果：不加这个上限的话，
 * 一篇内容多、跟查询词相关度高的长文档能把 topK 名额全占满，把其他也相关但内容更短的文档
 * 挤出结果（例如标题完全命中查询词、但正文很短只有一个分块的笔记）。名额优先保证多样性，
 * 只有分完之后还有空位，才用被挤掉的高分分块回填，不浪费 topK 名额。
 */
@Slf4j
@Service
public class RetrievalService {

    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final int MAX_CHUNKS_PER_SOURCE = 2;

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
        if (queryText == null || queryText.isBlank()) {
            log.debug("retrieve called with blank query, skipping embedding call");
            return List.of();
        }

        float[] queryVector = embeddingService.embed(queryText);
        List<ContentChunk> allChunks = chunkRepository.findByOwner(user);

        List<ScoredChunk> aboveThreshold = allChunks.stream()
                .map(c -> new ScoredChunk(c, cosineSimilarity(queryVector, parseEmbedding(c.getEmbeddingJson()))))
                .filter(sc -> sc.score() >= SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();

        if (log.isDebugEnabled()) {
            log.debug("retrieve query={} topK={} chunksAboveThreshold={}: {}", queryText, topK, aboveThreshold.size(),
                    aboveThreshold.stream()
                            .map(sc -> sc.chunk().getSourceId() + "#" + sc.chunk().getChunkIndex() + "=" + String.format("%.4f", sc.score()))
                            .toList());
        }

        List<ScoredChunk> diverse = new ArrayList<>();
        List<ScoredChunk> overflow = new ArrayList<>();
        Map<String, Integer> perSourceCount = new HashMap<>();
        for (ScoredChunk sc : aboveThreshold) {
            String sourceKey = sc.chunk().getSourceType() + ":" + sc.chunk().getSourceId();
            int count = perSourceCount.getOrDefault(sourceKey, 0);
            if (count < MAX_CHUNKS_PER_SOURCE) {
                perSourceCount.put(sourceKey, count + 1);
                diverse.add(sc);
            } else {
                overflow.add(sc);
            }
        }

        // 分完名额还有空位，说明相关文档不够多，用被挤掉的分块（多是同一篇文档的更多分块）回填，
        // 不浪费 topK 名额；重新按分数排一遍，因为 diverse/overflow 分桶拼接后顺序不再是全局分数降序。
        List<ScoredChunk> selected = new ArrayList<>(diverse);
        if (selected.size() < topK) {
            selected.addAll(overflow);
        }
        selected.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        return selected.stream()
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
