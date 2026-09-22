/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionRequest;
import com.entropybits.worknotes.spring_boot.dto.ContactSubmissionResponse;
import com.entropybits.worknotes.spring_boot.service.ContactSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 联系表单控制器
 */
@RestController
@RequestMapping("/v1/contact")
@RequiredArgsConstructor
@Tag(name = "联系表单", description = "联系表单提交相关接口")
public class ContactController {

    private final ContactSubmissionService contactSubmissionService;

    /**
     * 提交联系表单
     * POST /v1/contact/submit
     */
    @PostMapping("/submit")
    @Operation(summary = "提交联系表单", description = "提交需求文档和联系信息")
    public ResponseEntity<ContactSubmissionResponse> submitContactForm(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam(value = "organization", required = false) String organization,
            @RequestParam("projectSummary") String projectSummary,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        // 构建请求对象
        ContactSubmissionRequest request = new ContactSubmissionRequest();
        request.setName(name);
        request.setEmail(email);
        request.setOrganization(organization);
        request.setProjectSummary(projectSummary);

        // 创建提交记录
        ContactSubmissionResponse response = contactSubmissionService.createSubmission(request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

