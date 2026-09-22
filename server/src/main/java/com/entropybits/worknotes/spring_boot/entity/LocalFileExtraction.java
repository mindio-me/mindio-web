/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "local_file_extractions", uniqueConstraints = @UniqueConstraint(columnNames = "content_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalFileExtraction {

    /** 目前只有IMAGE_OCR；DOCUMENT_TEXT是给以后本地文档文本抽取预留的占位，这次不实现。 */
    public enum ExtractionType { IMAGE_OCR, DOCUMENT_TEXT }

    public enum Status { PENDING, PROCESSING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_type", nullable = false, length = 20)
    private ExtractionType extractionType;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 供对账任务判断"卡在PROCESSING太久"用，成功/失败之外的每次状态变更也会更新这个字段。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
