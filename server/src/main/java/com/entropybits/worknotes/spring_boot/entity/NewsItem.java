/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_item",
    indexes = {
        @Index(name = "idx_ni_source_date", columnList = "source_key, fetch_date"),
        @Index(name = "idx_ni_fetch_date", columnList = "fetch_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, length = 50)
    private String sourceKey;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Size(max = 500)
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Size(max = 1000)
    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "fetch_date", nullable = false)
    private LocalDate fetchDate;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
