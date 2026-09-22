/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Service 实体 - 服务项目表
 */
@Entity
@Table(name = "services")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 200, message = "服务名称长度不能超过200个字符")
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank(message = "服务描述不能为空")
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private String description;

    @Column(length = Integer.MAX_VALUE)
    private String detailedDescription; // 详细描述

    @Column(length = 100)
    private String icon; // Element UI 图标名称

    @Column(length = 100)
    private String category; // 服务分类: development, consulting, maintenance

    @Column(length = Integer.MAX_VALUE)
    private String features; // 服务特性，JSON格式

    @Column(length = Integer.MAX_VALUE)
    private String pricing; // 定价信息，JSON格式

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true; // 是否启用

    @Column(nullable = false)
    @Builder.Default
    private Boolean isFeatured = false; // 是否为核心服务

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0; // 显示顺序

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime modifiedAt;
}
