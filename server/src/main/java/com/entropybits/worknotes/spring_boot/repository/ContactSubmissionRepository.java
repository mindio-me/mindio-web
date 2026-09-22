/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ContactSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ContactSubmission Repository
 */
@Repository
public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, Long> {

    /**
     * 按状态查找提交记录
     */
    List<ContactSubmission> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 按邮箱查找提交记录
     */
    List<ContactSubmission> findByEmailOrderByCreatedAtDesc(String email);

    /**
     * 管理端分页搜索：按状态和关键词（姓名 / 邮箱 / 组织）筛选
     */
    @Query("SELECT c FROM ContactSubmission c " +
            "WHERE (:status IS NULL OR c.status = :status) " +
            "AND (" +
            "  :keyword IS NULL OR " +
            "  LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(c.organization) LIKE LOWER(CONCAT('%', :keyword, '%'))" +
            ") " +
            "ORDER BY c.createdAt DESC")
    Page<ContactSubmission> searchForAdmin(@Param("status") String status,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}




