/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.ai.dto.AiSettingsResponse;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设置页"AI 服务"：全站一份，仅管理员。Key 永远不回传原文。
 * PUT 的 body 是 {配置键: 值}：值为 null/键不出现 = 不变，空字符串 = 清除（回到环境变量），其他 = 替换。
 */
@RestController
@RequestMapping("/v1/settings/ai")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AiSettingsResponse get() {
        return aiSettingsService.view();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> update(@RequestBody Map<String, String> changes) {
        aiSettingsService.update(changes);
        return ResponseEntity.noContent().build();
    }
}
