/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 联系表单提交响应 DTO
 */
@Data
public class ContactSubmissionResponse {

    private Long id;
    private String name;
    private String email;
    private String organization;
    private String projectSummary;
    private Long attachmentId;
    private String status;
    private LocalDateTime createdAt;

    public static ContactSubmissionResponse fromEntity(ContactSubmission submission) {
        ContactSubmissionResponse response = new ContactSubmissionResponse();
        response.setId(submission.getId());
        response.setName(submission.getName());
        response.setEmail(submission.getEmail());
        response.setOrganization(submission.getOrganization());
        response.setProjectSummary(submission.getProjectSummary());
        response.setAttachmentId(submission.getAttachment() != null ? submission.getAttachment().getAttId() : null);
        response.setStatus(submission.getStatus());
        response.setCreatedAt(submission.getCreatedAt());
        return response;
    }
}





