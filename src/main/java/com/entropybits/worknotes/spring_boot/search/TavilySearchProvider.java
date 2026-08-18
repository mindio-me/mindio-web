/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import com.entropybits.worknotes.spring_boot.search.config.SearchProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 部署方配置了 Tavily API key 时使用：覆盖面比新闻 RSS 更广（博客/专题站也能搜到）。
 */
@Component
@RequiredArgsConstructor
public class TavilySearchProvider implements WebSearchProvider {

    private final SearchProperties searchProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public List<SearchResultItem> search(String query, int limit) throws Exception {
        SearchProperties.ProviderConfig cfg = searchProperties.getTavily();
        String url = cfg.getBaseUrl() + "/search";

        Map<String, Object> body = Map.of(
                "api_key", cfg.getApiKey(),
                "query", query,
                "max_results", limit
        );
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Tavily API error: HTTP " + response.statusCode() + " " + response.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) parsed.getOrDefault("results", List.of());
        return results.stream()
                .limit(limit)
                .map(r -> new SearchResultItem(
                        (String) r.get("title"),
                        (String) r.get("url"),
                        (String) r.getOrDefault("content", "")))
                // null/畸形 url 一路传到 ClipImportService.fetchFromUrl 会 NPE
                .filter(item -> isUsableHttpUrl(item.url()))
                .toList();
    }

    private static boolean isUsableHttpUrl(String url) {
        return url != null && !url.isBlank()
                && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
