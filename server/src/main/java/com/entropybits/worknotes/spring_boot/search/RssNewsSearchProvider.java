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
 * 默认搜索后端：不需要任何 API key，用 Bing 的关键词搜索 RSS 拿真实网页结果。
 * 原来用的是 news.google.com——在没有代理的国内网络环境直连会连接超时（HttpURLConnection
 * 不会像 curl 那样自动读 HTTPS_PROXY 环境变量，Windows 系统级代理也没配），实测 Bing 的
 * 同类 RSS 端点无需代理即可直连，跟 BingNewsFetcher 里"热点新闻"功能用的是同一个端点模式。
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
        return "https://www.bing.com/search?q=" + encoded + "&format=rss&setlang=zh-CN&mkt=zh-CN";
    }
}
