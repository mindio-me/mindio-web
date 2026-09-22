/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.LinkPreviewResponse;
import com.entropybits.worknotes.spring_boot.service.ClipImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供编辑器「富链接预览」块（@editorjs/link）调用：粘贴一个 URL，服务端抓取其标题/描述/封面图返回。
 * 要求登录态——这是一个"服务端替你访问任意 URL"的接口，不加鉴权等于放开一个内网探测跳板给任何人用。
 */
@RestController
@RequestMapping("/v1/link-preview")
@RequiredArgsConstructor
@Tag(name = "链接预览", description = "编辑器富链接预览块用的 URL 元信息抓取接口")
public class LinkPreviewController {

    private final ClipImportService clipImportService;

    @GetMapping
    @Operation(summary = "抓取链接预览", description = "抓取 URL 的标题/描述/封面图，供 @editorjs/link 使用")
    public ResponseEntity<LinkPreviewResponse> fetch(
            @RequestParam String url,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(clipImportService.fetchLinkPreview(url));
    }
}
