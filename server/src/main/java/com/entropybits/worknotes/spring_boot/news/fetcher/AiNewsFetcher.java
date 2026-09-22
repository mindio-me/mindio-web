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

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiNewsFetcher implements NewsFetcher {

    private static final String RSS_URL = "https://www.qbitai.com/feed";
    private final RssFeedParser rssFeedParser;

    @Override
    public String getSourceKey() {
        return "ai_news";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        return rssFeedParser.parse(RSS_URL, 10);
    }
}
