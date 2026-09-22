/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端 - 联系表单详情 DTO（含备注列表）
 */
@Data
public class ContactSubmissionAdminDetail {

    private Long id;
    private String name;
    private String email;
    private String organization;
    private String projectSummary;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 附件信息（如有）
    private Long attachmentId;
    private String attachmentName;
    private String attachmentUrl;

    // 内部备注
    private List<ContactSubmissionNoteResponse> notes;

    public static ContactSubmissionAdminDetail fromEntity(
            ContactSubmission submission,
            Attachment attachment,
            String attachmentUrl,
            List<ContactSubmissionNoteResponse> notes
    ) {
        ContactSubmissionAdminDetail dto = new ContactSubmissionAdminDetail();
        dto.setId(submission.getId());
        dto.setName(submission.getName());
        dto.setEmail(submission.getEmail());
        dto.setOrganization(submission.getOrganization());
        dto.setProjectSummary(submission.getProjectSummary());
        dto.setStatus(submission.getStatus());
        dto.setCreatedAt(submission.getCreatedAt());
        dto.setUpdatedAt(submission.getUpdatedAt());

        if (attachment != null) {
            dto.setAttachmentId(attachment.getAttId());
            dto.setAttachmentName(attachment.getName());
            dto.setAttachmentUrl(attachmentUrl);
        }

        dto.setNotes(notes);
        return dto;
    }
}

