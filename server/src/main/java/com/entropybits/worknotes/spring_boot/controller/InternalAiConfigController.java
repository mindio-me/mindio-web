/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.ai.dto.InternalAiConfigResponse;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * agents 每处理一次请求取一次界面配置的 AI 设置（只含库里填过的值，Key 为原文）。
 * /internal/** 由 InternalServiceAuthFilter 用共享密钥保护。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalAiConfigController {

    private final AiSettingsService aiSettingsService;

    @GetMapping("/ai-config")
    public InternalAiConfigResponse aiConfig() {
        return aiSettingsService.internalConfig();
    }
}
