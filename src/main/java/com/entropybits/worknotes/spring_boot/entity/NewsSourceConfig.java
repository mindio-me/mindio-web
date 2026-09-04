/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_source_config",
    indexes = {
        @Index(name = "idx_nsc_category", columnList = "category"),
        @Index(name = "idx_nsc_sort", columnList = "sort_order")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, unique = true, length = 50)
    private String sourceKey;

    @Column(name = "name_zh", nullable = false, length = 100)
    private String nameZh;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "category", nullable = false, length = 10)
    private String category;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @Column(name = "last_fetch_status", length = 20)
    private String lastFetchStatus;

    @Column(name = "last_fetch_error", length = 500)
    private String lastFetchError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
