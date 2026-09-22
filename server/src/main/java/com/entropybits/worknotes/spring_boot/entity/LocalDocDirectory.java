/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
// (owner_id, dir_path) 的唯一性由 Flyway 迁移强制：mysql/ 建在生成列 (owner_id,
// dir_path_hash) 上（dir_path 太长，整列复合唯一索引会超过 InnoDB 3072 字节上限），
// h2/ 建在 (owner_id, dir_path) 上。这里不再用 @UniqueConstraint 声明，避免 ddl-auto
// 按整列去重建一个 MySQL 建不出来的索引。
@Table(name = "local_doc_directories",
    indexes = {
        @Index(name = "idx_ldd_owner", columnList = "owner_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalDocDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "dir_path", nullable = false, length = 1000)
    private String dirPath;

    @Size(max = 200)
    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "scan_status", nullable = false, length = 20)
    @Builder.Default
    private String scanStatus = "IDLE";

    @Column(name = "last_scan_at")
    private LocalDateTime lastScanAt;

    @Column(name = "last_scan_error", length = Integer.MAX_VALUE)
    private String lastScanError;

    @Column(name = "document_count", nullable = false)
    @Builder.Default
    private Integer documentCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "directory", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LocalDocument> documents = new ArrayList<>();
}
