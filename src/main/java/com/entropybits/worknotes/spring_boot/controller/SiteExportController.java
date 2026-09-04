/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.SiteExportRequest;
import com.entropybits.worknotes.spring_boot.dto.SiteExportResult;
import com.entropybits.worknotes.spring_boot.service.SiteExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桌面版专用：导出公开网站。仅在 desktop profile 下启用。
 */
@RestController
@RequestMapping("/v1/site-export")
@RequiredArgsConstructor
@Profile("desktop")
public class SiteExportController {

    private final SiteExportService siteExportService;

    @PostMapping
    public ResponseEntity<SiteExportResult> exportSite(@Valid @RequestBody SiteExportRequest request) {
        SiteExportResult result = siteExportService.export(
                request.getTargetPath(), request.getTemplateId(), request.getLocale());
        return ResponseEntity.ok(result);
    }
}
