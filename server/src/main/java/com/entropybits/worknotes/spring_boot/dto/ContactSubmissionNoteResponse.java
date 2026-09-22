/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.ContactSubmissionNote;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端 - 联系表单内部备注 DTO
 */
@Data
public class ContactSubmissionNoteResponse {

    private Long id;
    private String content;
    private String createdByUsername;
    private LocalDateTime createdAt;

    public static ContactSubmissionNoteResponse fromEntity(ContactSubmissionNote note) {
        ContactSubmissionNoteResponse dto = new ContactSubmissionNoteResponse();
        dto.setId(note.getId());
        dto.setContent(note.getContent());
        dto.setCreatedByUsername(
                note.getCreatedBy() != null ? note.getCreatedBy().getUsername() : null
        );
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }
}

