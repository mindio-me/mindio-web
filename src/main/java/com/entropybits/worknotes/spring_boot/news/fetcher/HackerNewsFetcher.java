/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsFetcher.class);
    private static final String TOP_STORIES_URL = "https://hacker-news.firebaseio.com/v0/topstories.json";
    private static final String ITEM_URL = "https://hacker-news.firebaseio.com/v0/item/%d.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceKey() {
        return "hacker_news";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        List<NewsItemData> result = new ArrayList<>();

        JsonNode ids = objectMapper.readTree(URI.create(TOP_STORIES_URL).toURL());
        int count = Math.min(ids.size(), 10);

        for (int i = 0; i < count; i++) {
            long id = ids.get(i).asLong();
            try {
                JsonNode item = objectMapper.readTree(URI.create(String.format(ITEM_URL, id)).toURL());
                String title = item.path("title").asText();
                String url = item.path("url").asText(null);
                if (!title.isBlank()) {
                    if (url == null || url.isBlank()) {
                        url = "https://news.ycombinator.com/item?id=" + id;
                    }
                    result.add(new NewsItemData(i + 1, title, url));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch HN item {}: {}", id, e.getMessage());
            }
        }
        return result;
    }
}
