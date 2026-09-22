/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 笔记→图片内容hash的引用关系。按content_hash存（不按attachment_id存）——同一张图片
 * 可能通过多次独立的上传/粘贴事件进入不同笔记，各自对应不同的Attachment行，但共享同一个
 * content_hash；只有按hash查，才能一次性找到所有引用过这份内容的笔记。
 */
@Entity
@Table(
        name = "note_image_refs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"note_id", "content_hash"}),
        indexes = {
            @Index(name = "idx_note_image_refs_content_hash", columnList = "content_hash")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteImageRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoteImageRef that = (NoteImageRef) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
