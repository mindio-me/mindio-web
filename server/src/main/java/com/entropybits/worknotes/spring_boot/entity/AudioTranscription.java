/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 一个音频块的转写记录：逐句原文 / AI 优化版 / 结构化总结都是 JSON 字符串，原样透传给前端。
 * 详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
 */
@Entity
@Table(name = "audio_transcriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"note_id", "block_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioTranscription {

    public enum Status { PENDING, PROCESSING, DONE, FAILED }

    public enum TriggerType { AUTO, MANUAL }

    /** FULL = 转写 + 整理；SUMMARY = 逐句原文已有，只重新整理（不再花 ASR 的钱） */
    public enum Stage { FULL, SUMMARY }

    public enum ErrorKind {
        PARTIAL_SEGMENTS, SUMMARY_FAILED, NO_SPEECH, ASR_NOT_CONFIGURED,
        FILE_MISSING, ASR_FAILED, AGENT_UNAVAILABLE, UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "block_id", nullable = false, length = 64)
    private String blockId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Stage stage;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_kind", length = 30)
    private ErrorKind errorKind;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(columnDefinition = "LONGTEXT")
    private String utterances;

    @Column(columnDefinition = "LONGTEXT")
    private String polished;

    @Column(columnDefinition = "LONGTEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
