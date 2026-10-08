/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * GET /internal/ai-config：只含界面里填过的值（Key 为解密后的原文），agents 拿去与自己的环境变量合并。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InternalAiConfigResponse(String defaultProvider, Map<String, Provider> providers, Asr asr) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Provider(String apiKey, String model, String baseUrl, String chatPath) {
        @JsonIgnore
        public boolean isEmpty() {
            return apiKey == null && model == null && baseUrl == null && chatPath == null;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Asr(String appKey, String accessKey) {
        @JsonIgnore
        public boolean isEmpty() {
            return appKey == null && accessKey == null;
        }
    }
}
