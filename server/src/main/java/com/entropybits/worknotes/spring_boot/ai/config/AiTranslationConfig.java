/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.ai.config;

import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.ai.service.OpenAiCompatibleTranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiTranslationConfig {

    @Bean("openAiTranslationService")
    AiTranslationService openAiTranslationService(AiConfigSource source, ObjectMapper objectMapper) {
        return new OpenAiCompatibleTranslationService(() -> source.current().getOpenai(), "OpenAI", objectMapper);
    }

    @Bean("deepseekTranslationService")
    AiTranslationService deepseekTranslationService(AiConfigSource source, ObjectMapper objectMapper) {
        return new OpenAiCompatibleTranslationService(() -> source.current().getDeepseek(), "DeepSeek", objectMapper);
    }

    @Bean("doubaoTranslationService")
    AiTranslationService doubaoTranslationService(AiConfigSource source, ObjectMapper objectMapper) {
        return new OpenAiCompatibleTranslationService(() -> source.current().getDoubao(), "Doubao", objectMapper);
    }
}
