/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 助手改笔记前的快照：一轮对话（turnId）里某篇笔记第一次被改动之前的正文，供"撤销本轮修改"。
 * contentAfter 是本轮最后一次改动后的正文，用来判断用户之后是否又改过。
 * 详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md。
 */
@Entity
@Table(name = "note_revisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"turn_id", "note_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteRevision {

    public enum Source { AGENT, REPLY_INSERT, CONVERT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "turn_id", nullable = false, length = 64)
    private String turnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "content_type", length = 20)
    private String contentType;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "content_after", columnDefinition = "LONGTEXT")
    private String contentAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
