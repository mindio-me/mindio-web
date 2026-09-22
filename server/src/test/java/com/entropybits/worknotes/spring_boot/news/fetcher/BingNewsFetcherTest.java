/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BingNewsFetcherTest {

    @Test
    void parsesAndDeduplicatesServerRenderedNewsCards() {
        String html = """
                <html><body>
                  <div class="news-card newsitem" data-title="First headline"
                       data-url="https://example.com/first"></div>
                  <div class="news-card" data-title="Duplicate headline"
                       data-url="https://example.com/first"></div>
                  <div class="news-card" data-title="Second headline"
                       data-url="https://example.com/second"></div>
                  <div class="news-card" data-title="" data-url="https://example.com/empty"></div>
                </body></html>
                """;
        BingNewsFetcher fetcher = new BingNewsFetcher(null);

        List<NewsItemData> items = fetcher.parseNewsPage(Jsoup.parse(html), 10);

        assertThat(items).containsExactly(
                new NewsItemData(1, "First headline", "https://example.com/first"),
                new NewsItemData(2, "Second headline", "https://example.com/second")
        );
    }

    @Test
    void respectsMaximumItemCount() {
        String html = """
                <div class="news-card" data-title="First" data-url="https://example.com/1"></div>
                <div class="news-card" data-title="Second" data-url="https://example.com/2"></div>
                """;
        BingNewsFetcher fetcher = new BingNewsFetcher(null);

        List<NewsItemData> items = fetcher.parseNewsPage(Jsoup.parse(html), 1);

        assertThat(items).containsExactly(
                new NewsItemData(1, "First", "https://example.com/1")
        );
    }
}
