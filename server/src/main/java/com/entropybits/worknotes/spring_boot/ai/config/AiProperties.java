/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "anthropic";
    private ProviderConfig anthropic = new ProviderConfig();
    private ProviderConfig openai = new ProviderConfig();
    private ProviderConfig deepseek = new ProviderConfig();
    private ProviderConfig doubao = new ProviderConfig();
    /** Embedding 目前固定走 doubao 的 api-key/base-url，认证不单独配置，只有模型和路径独立 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String model;
        private String baseUrl;
        /** Chat Completions 路径，默认 /v1/chat/completions，豆包等特殊服务商可覆盖 */
        private String chatPath = "/v1/chat/completions";
    }

    @Data
    public static class EmbeddingConfig {
        private String model;
        /** Embeddings 接口路径，默认 /v1/embeddings，豆包等特殊服务商可覆盖 */
        private String path = "/v1/embeddings";
    }
}
