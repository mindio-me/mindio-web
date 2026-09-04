/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
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
 * ContactSubmission 实体 - 联系表单提交表
 */
@Entity
@Table(name = "contact_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 200, message = "姓名长度不能超过200个字符")
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 200, message = "邮箱长度不能超过200个字符")
    @Column(nullable = false, length = 200)
    private String email;

    @Size(max = 200, message = "组织名称长度不能超过200个字符")
    @Column(length = 200)
    private String organization;

    @NotBlank(message = "项目摘要不能为空")
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private String projectSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private Attachment attachment;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending"; // pending, reviewed, replied

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}





