/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ContentChunkRepository extends JpaRepository<ContentChunk, Long> {
    List<ContentChunk> findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType type, Long sourceId);
    /** 批量按 sourceId 查，供知识地图语义聚类一次性取一批 clip 的分块向量，避免逐条 clip 各查一次 */
    List<ContentChunk> findBySourceTypeAndSourceIdIn(ContentChunk.SourceType type, Collection<Long> sourceIds);
    List<ContentChunk> findByOwner(User owner);
    void deleteBySourceTypeAndSourceId(ContentChunk.SourceType type, Long sourceId);
}
