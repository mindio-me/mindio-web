/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import com.entropybits.worknotes.spring_boot.entity.ContactSubmissionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ContactSubmissionNote Repository
 */
@Repository
public interface ContactSubmissionNoteRepository extends JpaRepository<ContactSubmissionNote, Long> {

    /**
     * 根据提交记录获取备注列表（按创建时间倒序）
     */
    List<ContactSubmissionNote> findBySubmissionOrderByCreatedAtDesc(ContactSubmission submission);
}

