/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RssNewsSearchProviderTest {

    @Test
    void buildSearchUrl_encodesQueryAndUsesGoogleNewsSearchEndpoint() {
        RssNewsSearchProvider provider = new RssNewsSearchProvider(mock(RssFeedParser.class));

        String url = provider.buildSearchUrl("AI 深度报道", 5);

        assertThat(url).startsWith("https://news.google.com/rss/search?q=");
        assertThat(url).contains(URLEncoder.encode("AI 深度报道", StandardCharsets.UTF_8));
    }

    @Test
    void search_mapsRssItemsToSearchResultItemsWithEmptyExcerpt() throws Exception {
        RssFeedParser parser = mock(RssFeedParser.class);
        when(parser.parse(anyString(), eq(5))).thenReturn(List.of(
                new NewsItemData(1, "Title A", "https://a.example.com"),
                new NewsItemData(2, "Title B", "https://b.example.com")
        ));
        RssNewsSearchProvider provider = new RssNewsSearchProvider(parser);

        List<SearchResultItem> results = provider.search("AI", 5);

        assertThat(results).containsExactly(
                new SearchResultItem("Title A", "https://a.example.com", ""),
                new SearchResultItem("Title B", "https://b.example.com", "")
        );
    }

    @Test
    void search_dropsItemsWithNullBlankOrNonHttpUrl() throws Exception {
        // RssFeedParser 允许 <link> 缺失；这类条目一路传到 ClipImportService.fetchFromUrl 会 NPE
        RssFeedParser parser = mock(RssFeedParser.class);
        when(parser.parse(anyString(), eq(5))).thenReturn(List.of(
                new NewsItemData(1, "No link", null),
                new NewsItemData(2, "Blank link", "   "),
                new NewsItemData(3, "Javascript link", "javascript:alert(1)"),
                new NewsItemData(4, "Title B", "https://b.example.com")
        ));
        RssNewsSearchProvider provider = new RssNewsSearchProvider(parser);

        List<SearchResultItem> results = provider.search("AI", 5);

        assertThat(results).containsExactly(
                new SearchResultItem("Title B", "https://b.example.com", "")
        );
    }
}
