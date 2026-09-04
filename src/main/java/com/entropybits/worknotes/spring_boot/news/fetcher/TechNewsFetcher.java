/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.entropybits.worknotes.spring_boot.news.RssFeedParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TechNewsFetcher implements NewsFetcher {

    private static final String TECHCRUNCH_RSS = "https://techcrunch.com/feed/";
    private static final String THEVERGE_RSS = "https://www.theverge.com/rss/index.xml";
    private final RssFeedParser rssFeedParser;

    @Override
    public String getSourceKey() {
        return "tech_news";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        List<NewsItemData> combined = new ArrayList<>();
        List<NewsItemData> tc = rssFeedParser.parse(TECHCRUNCH_RSS, 5);
        List<NewsItemData> tv = rssFeedParser.parse(THEVERGE_RSS, 5);
        combined.addAll(tc);
        combined.addAll(tv);
        // re-rank sequentially
        List<NewsItemData> result = new ArrayList<>();
        for (int i = 0; i < combined.size(); i++) {
            NewsItemData item = combined.get(i);
            result.add(new NewsItemData(i + 1, item.title(), item.url()));
        }
        return result;
    }
}
