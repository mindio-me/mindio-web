/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 收藏与标签的关联，区分人工添加和 AI 建议两种来源。
 * manuallyAdded、aiSuggested 任一为 true 这条关联就生效（对用户可见、参与筛选）；
 * 两个都为 false 时应当删除这行记录，不保留 false/false 的僵尸行。
 */
@Entity
@Table(
        name = "clip_tag_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"clip_id", "tag_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipTagLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clip_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SourceClip clip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Tag tag;

    @Column(nullable = false)
    @Builder.Default
    private Boolean manuallyAdded = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aiSuggested = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClipTagLink that = (ClipTagLink) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
