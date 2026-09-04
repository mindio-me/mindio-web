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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock ContentChunkRepository chunkRepository;
    @Mock EmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final User user = User.builder().id(1L).build();

    private ContentChunk chunkWithEmbedding(long id, ContentChunk.SourceType type, long sourceId, float[] embedding) throws Exception {
        return ContentChunk.builder()
                .id(id).owner(user).sourceType(type).sourceId(sourceId).chunkIndex(0)
                .chunkText("chunk-" + id).contentHash("hash-" + id)
                .embeddingJson(objectMapper.writeValueAsString(embedding))
                .build();
    }

    @Test
    void retrieve_returnsTopKSortedByDescendingSimilarity() throws Exception {
        RetrievalService retrievalService = new RetrievalService(chunkRepository, embeddingService, objectMapper);
        when(embeddingService.embed("查询问题")).thenReturn(new float[]{1f, 0f});
        // 与查询向量[1,0]的余弦相似度：chunk A 完全同向(1.0)，chunk B 接近正交(约0.0)
        when(chunkRepository.findByOwner(user)).thenReturn(List.of(
                chunkWithEmbedding(1L, ContentChunk.SourceType.NOTE, 100L, new float[]{1f, 0f}),
                chunkWithEmbedding(2L, ContentChunk.SourceType.NOTE, 101L, new float[]{0f, 1f})
        ));

        List<RetrievedChunk> result = retrievalService.retrieve(user, "查询问题", 5);

        assertThat(result).hasSize(1); // 只有 chunk A 的相似度超过阈值
        assertThat(result.get(0).sourceId()).isEqualTo(100L);
    }

    @Test
    void cosineSimilarity_returnsOneForIdenticalVectors() {
        assertThat(RetrievalService.cosineSimilarity(new float[]{1f, 2f, 3f}, new float[]{1f, 2f, 3f}))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void cosineSimilarity_returnsZeroForOrthogonalVectors() {
        assertThat(RetrievalService.cosineSimilarity(new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void cosineSimilarity_returnsZeroForMismatchedLengthOrEmptyVectors() {
        assertThat(RetrievalService.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f})).isEqualTo(0.0);
        assertThat(RetrievalService.cosineSimilarity(new float[0], new float[]{1f})).isEqualTo(0.0);
    }

    @Test
    void retrieve_limitsResultsToTopK() throws Exception {
        RetrievalService retrievalService = new RetrievalService(chunkRepository, embeddingService, objectMapper);
        when(embeddingService.embed("查询问题")).thenReturn(new float[]{1f, 0f});
        when(chunkRepository.findByOwner(user)).thenReturn(List.of(
                chunkWithEmbedding(1L, ContentChunk.SourceType.NOTE, 100L, new float[]{1f, 0f}),
                chunkWithEmbedding(2L, ContentChunk.SourceType.NOTE, 101L, new float[]{0.99f, 0.01f}),
                chunkWithEmbedding(3L, ContentChunk.SourceType.NOTE, 102L, new float[]{0.98f, 0.02f})
        ));

        List<RetrievedChunk> result = retrievalService.retrieve(user, "查询问题", 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void retrieve_sortsResultsByDescendingSimilarity() throws Exception {
        RetrievalService retrievalService = new RetrievalService(chunkRepository, embeddingService, objectMapper);
        when(embeddingService.embed("查询问题")).thenReturn(new float[]{1f, 0f});
        // 构造三个chunk，插入顺序是 lowest -> middle -> highest
        // 但期望的结果应该是按相似度降序排列：highest -> middle -> lowest
        when(chunkRepository.findByOwner(user)).thenReturn(List.of(
                // lowest: 相似度约 0.7071 (刚好接近0.7阈值)
                chunkWithEmbedding(1L, ContentChunk.SourceType.NOTE, 100L, new float[]{0.71f, 0.71f}),
                // middle: 相似度约 0.8944
                chunkWithEmbedding(2L, ContentChunk.SourceType.NOTE, 101L, new float[]{0.89f, 0.45f}),
                // highest: 相似度 1.0 (完全同向)
                chunkWithEmbedding(3L, ContentChunk.SourceType.NOTE, 102L, new float[]{1f, 0f})
        ));

        List<RetrievedChunk> result = retrievalService.retrieve(user, "查询问题", 5);

        // 验证结果按相似度降序排列：102(highest) -> 101(middle) -> 100(lowest)
        assertThat(result).hasSize(3);
        assertThat(result).extracting(RetrievedChunk::sourceId).containsExactly(102L, 101L, 100L);
        // 进一步验证scores确实是递减的
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
        assertThat(result.get(1).score()).isGreaterThan(result.get(2).score());
    }

    @Test
    void retrieve_excludesChunkWithMalformedEmbeddingJsonWithoutFailing() throws Exception {
        RetrievalService retrievalService = new RetrievalService(chunkRepository, embeddingService, objectMapper);
        when(embeddingService.embed("查询问题")).thenReturn(new float[]{1f, 0f});

        // 直接构造一个embeddingJson为垃圾数据的chunk，而不通过helper
        ContentChunk corruptChunk = ContentChunk.builder()
                .id(1L).owner(user).sourceType(ContentChunk.SourceType.NOTE).sourceId(100L).chunkIndex(0)
                .chunkText("corrupt-chunk").contentHash("hash-corrupt")
                .embeddingJson("not-valid-json")
                .build();

        // 混合一个有效chunk
        ContentChunk validChunk = chunkWithEmbedding(2L, ContentChunk.SourceType.NOTE, 101L, new float[]{1f, 0f});

        when(chunkRepository.findByOwner(user)).thenReturn(List.of(corruptChunk, validChunk));

        List<RetrievedChunk> result = retrievalService.retrieve(user, "查询问题", 5);

        // 期望：
        // 1. 没有异常抛出（retrieve正常返回）
        // 2. 结果只包含有效chunk，corrupt chunk被静默排除（不是因为阈值过低，而是因为parseEmbedding返回空数组导致相似度0.0）
        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceId()).isEqualTo(101L);
    }
}
