/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionAdminDetail;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionAdminListItem;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionNoteResponse;
import com.entropybits.worknotes.spring_boot.service.ContactSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 联系表单管理端接口（仅管理员）
 */
@RestController
@RequestMapping("/v1/admin/contact-submissions")
@RequiredArgsConstructor
@Tag(name = "联系表单管理", description = "联系表单 Collaboration Request 管理接口（仅管理员）")
@SuppressWarnings("null")
public class ContactAdminController {

    private final ContactSubmissionService contactSubmissionService;

    /**
     * 分页获取联系表单提交列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取联系表单提交列表（管理端）")
    public ResponseEntity<Page<ContactSubmissionAdminListItem>> listSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<ContactSubmissionAdminListItem> result =
                contactSubmissionService.getSubmissionsForAdmin(status, keyword, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单条提交详情（含内部备注）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取联系表单提交详情（管理端）")
    public ResponseEntity<ContactSubmissionAdminDetail> getSubmissionDetail(
            @PathVariable Long id
    ) {
        ContactSubmissionAdminDetail detail = contactSubmissionService.getSubmissionDetailForAdmin(id);
        return ResponseEntity.ok(detail);
    }

    /**
     * 为提交记录添加内部备注
     */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "添加内部备注（管理端）")
    public ResponseEntity<ContactSubmissionNoteResponse> addInternalNote(
            @PathVariable Long id,
            @RequestBody String content,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        ContactSubmissionNoteResponse note =
                contactSubmissionService.addInternalNote(id, content, username);
        return ResponseEntity.ok(note);
    }

    /**
     * 更新提交状态
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新联系表单提交状态（管理端）")
    public ResponseEntity<ContactSubmissionAdminDetail> updateStatus(
            @PathVariable Long id,
            @RequestParam @NotBlank String status
    ) {
        ContactSubmissionAdminDetail detail = contactSubmissionService.updateStatus(id, status);
        return ResponseEntity.ok(detail);
    }
}

