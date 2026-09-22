/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ClipTagLink;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClipTagLinkRepository extends JpaRepository<ClipTagLink, Long> {

    @Query("SELECT l FROM ClipTagLink l JOIN FETCH l.tag WHERE l.clip = :clip")
    List<ClipTagLink> findByClip(@Param("clip") SourceClip clip);

    Optional<ClipTagLink> findByClipAndTag(SourceClip clip, Tag tag);

    List<ClipTagLink> findByTag(Tag tag);

    boolean existsByTag(Tag tag);

    /** 一次查询算出该用户每个标签当前挂着几条生效链接，避免逐个标签单独查询（N+1） */
    @Query("SELECT l.tag.id AS tagId, COUNT(l) AS clipCount FROM ClipTagLink l " +
           "WHERE l.tag.owner = :owner AND (l.manuallyAdded = true OR l.aiSuggested = true) " +
           "GROUP BY l.tag.id")
    List<TagClipCount> countActiveLinksByOwnerGroupedByTag(@Param("owner") User owner);

    interface TagClipCount {
        Long getTagId();
        Long getClipCount();
    }
}
