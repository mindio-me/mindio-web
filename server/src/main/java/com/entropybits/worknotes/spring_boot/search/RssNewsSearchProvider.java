/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 默认搜索后端：不需要任何 API key，用 Google News 的关键词搜索 RSS 拿真实新闻文章。
 */
@Component
@RequiredArgsConstructor
public class RssNewsSearchProvider implements WebSearchProvider {

    private final RssFeedParser rssFeedParser;

    @Override
    public List<SearchResultItem> search(String query, int limit) throws Exception {
        String url = buildSearchUrl(query, limit);
        return rssFeedParser.parse(url, limit).stream()
                .map(item -> new SearchResultItem(item.title(), item.url(), ""))
                // RSS 的 <link> 允许缺失，null/畸形 url 一路传到 ClipImportService.fetchFromUrl 会 NPE
                .filter(item -> isUsableHttpUrl(item.url()))
                .toList();
    }

    private static boolean isUsableHttpUrl(String url) {
        return url != null && !url.isBlank()
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    String buildSearchUrl(String query, int limit) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://news.google.com/rss/search?q=" + encoded + "&hl=zh-CN&gl=CN&ceid=CN:zh-Hans";
    }
}
