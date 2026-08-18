/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private ProviderConfig tavily = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl = "https://api.tavily.com";
    }
}
