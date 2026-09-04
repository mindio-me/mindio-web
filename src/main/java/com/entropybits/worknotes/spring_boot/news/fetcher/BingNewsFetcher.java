/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.entropybits.worknotes.spring_boot.news.RssFeedParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BingNewsFetcher implements NewsFetcher {

    private static final String NEWS_URL =
            "https://www.bing.com/news/search?q=top+news&mkt=en-US&setlang=en-US";
    private static final String FALLBACK_RSS_URL =
            "https://www.bing.com/search?q=breaking+news&format=rss&setlang=en-US";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; Notecast/1.0)";
    private final RssFeedParser rssFeedParser;

    @Override
    public String getSourceKey() {
        return "bing_news";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        try {
            Document document = Jsoup.connect(NEWS_URL)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            List<NewsItemData> items = parseNewsPage(document, 10);
            if (!items.isEmpty()) {
                return items;
            }
        } catch (IOException ignored) {
            // Bing's Web Search RSS remains a useful fallback when the News page is unavailable.
        }
        return rssFeedParser.parse(FALLBACK_RSS_URL, 10);
    }

    List<NewsItemData> parseNewsPage(Document document, int maxItems) {
        Map<String, String> uniqueItems = new LinkedHashMap<>();
        for (Element card : document.select("div.news-card[data-title][data-url]")) {
            String title = card.attr("data-title").trim();
            String url = card.attr("data-url").trim();
            if (!title.isBlank() && !url.isBlank()) {
                uniqueItems.putIfAbsent(url, title);
            }
            if (uniqueItems.size() >= maxItems) {
                break;
            }
        }

        List<NewsItemData> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, String> item : uniqueItems.entrySet()) {
            result.add(new NewsItemData(rank++, item.getValue(), item.getKey()));
        }
        return result;
    }
}
