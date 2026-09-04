/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.config;

import com.entropybits.worknotes.spring_boot.ai.service.ChatService;
import com.entropybits.worknotes.spring_boot.ai.service.OpenAiCompatibleChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatConfig {

    @Bean("openAiChatService")
    ChatService openAiChatService(AiProperties props, ObjectMapper objectMapper) {
        return new OpenAiCompatibleChatService(props.getOpenai(), "OpenAI", objectMapper);
    }

    @Bean("deepseekChatService")
    ChatService deepseekChatService(AiProperties props, ObjectMapper objectMapper) {
        return new OpenAiCompatibleChatService(props.getDeepseek(), "DeepSeek", objectMapper);
    }

    @Bean("doubaoChatService")
    ChatService doubaoChatService(AiProperties props, ObjectMapper objectMapper) {
        return new OpenAiCompatibleChatService(props.getDoubao(), "Doubao", objectMapper);
    }
}
