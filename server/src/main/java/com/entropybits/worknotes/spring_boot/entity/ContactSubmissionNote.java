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

import java.time.LocalDateTime;

/**
 * ContactSubmissionNote 实体 - 联系表单内部备注
 */
@Entity
@Table(name = "contact_submission_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactSubmissionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的联系表单提交
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ContactSubmission submission;

    /**
     * 备注创建人（管理员）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    /**
     * 备注内容
     */
    @NotBlank(message = "备注内容不能为空")
    @Size(max = 2000, message = "备注内容不能超过2000个字符")
    @Column(nullable = false, length = 2000)
    private String content;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

