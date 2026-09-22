/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端 - 联系表单列表项 DTO
 */
@Data
public class ContactSubmissionAdminListItem {

    private Long id;
    private String name;
    private String email;
    private String organization;
    private String projectSummary;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactSubmissionAdminListItem fromEntity(ContactSubmission submission) {
        ContactSubmissionAdminListItem dto = new ContactSubmissionAdminListItem();
        dto.setId(submission.getId());
        dto.setName(submission.getName());
        dto.setEmail(submission.getEmail());
        dto.setOrganization(submission.getOrganization());
        dto.setProjectSummary(submission.getProjectSummary());
        dto.setStatus(submission.getStatus());
        dto.setCreatedAt(submission.getCreatedAt());
        dto.setUpdatedAt(submission.getUpdatedAt());
        return dto;
    }
}

