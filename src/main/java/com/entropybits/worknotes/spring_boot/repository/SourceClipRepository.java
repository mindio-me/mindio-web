/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SourceClipRepository extends JpaRepository<SourceClip, Long> {

    Page<SourceClip> findByOwner(User owner, Pageable pageable);

    Page<SourceClip> findByOwnerAndSourceType(User owner, SourceClip.SourceType sourceType, Pageable pageable);

    @Query("SELECT c FROM SourceClip c WHERE c.owner = :owner " +
           "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.excerpt LIKE %:keyword% OR c.sourceTitle LIKE %:keyword%)")
    Page<SourceClip> searchByOwner(@Param("owner") User owner,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    @Query("SELECT c FROM SourceClip c WHERE c.owner = :owner " +
           "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.excerpt LIKE %:keyword% OR c.sourceTitle LIKE %:keyword%) " +
           "AND (:sourceType IS NULL OR c.sourceType = :sourceType)")
    Page<SourceClip> searchByOwnerAndType(@Param("owner") User owner,
                                          @Param("keyword") String keyword,
                                          @Param("sourceType") SourceClip.SourceType sourceType,
                                          Pageable pageable);

    // 命中任意一个所选标签即可（OR 语义，与 NoteRepository.findByOwnerAndTagIds 的多标签筛选行为一致）。
    // SELECT DISTINCT 避免同一个 clip 因命中多个标签而在 JOIN 结果里重复出现；
    // Spring Data 能为“SELECT DISTINCT c ...”正确派生 COUNT(DISTINCT c) 计数查询，不需要显式 countQuery。
    @Query("SELECT DISTINCT c FROM SourceClip c JOIN c.clipTagLinks l WHERE c.owner = :owner " +
           "AND l.tag.id IN :tagIds AND (l.manuallyAdded = true OR l.aiSuggested = true) " +
           "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.excerpt LIKE %:keyword% OR c.sourceTitle LIKE %:keyword%) " +
           "AND (:sourceType IS NULL OR c.sourceType = :sourceType)")
    Page<SourceClip> searchByOwnerAndTypeAndTags(@Param("owner") User owner,
                                                 @Param("keyword") String keyword,
                                                 @Param("sourceType") SourceClip.SourceType sourceType,
                                                 @Param("tagIds") List<Long> tagIds,
                                                 Pageable pageable);

    // 一条标签都没有的收藏（既没有 AI 分类过，也没被手动打过标签）
    @Query("SELECT c FROM SourceClip c WHERE c.owner = :owner " +
           "AND NOT EXISTS (SELECT 1 FROM ClipTagLink l WHERE l.clip = c AND (l.manuallyAdded = true OR l.aiSuggested = true)) " +
           "AND (:keyword IS NULL OR c.title LIKE %:keyword% OR c.excerpt LIKE %:keyword% OR c.sourceTitle LIKE %:keyword%) " +
           "AND (:sourceType IS NULL OR c.sourceType = :sourceType)")
    Page<SourceClip> searchByOwnerAndTypeWithNoTags(@Param("owner") User owner,
                                                    @Param("keyword") String keyword,
                                                    @Param("sourceType") SourceClip.SourceType sourceType,
                                                    Pageable pageable);

    // 从未打开过详情的收藏没有 lastAccessedAt，排序时回退用 createdAt
    @Query("SELECT c FROM SourceClip c WHERE c.owner = :owner " +
           "ORDER BY COALESCE(c.lastAccessedAt, c.createdAt) DESC")
    List<SourceClip> findRecentlyAccessedByOwner(@Param("owner") User owner, Pageable pageable);
}
