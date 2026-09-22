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

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeiboHotFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(WeiboHotFetcher.class);
    private static final String URL = "https://weibo.com/ajax/side/hotSearch";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceKey() {
        return "weibo_hot";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        List<NewsItemData> result = new ArrayList<>();
        HttpURLConnection conn = (HttpURLConnection) URI.create(URL).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Referer", "https://weibo.com/");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        JsonNode root = objectMapper.readTree(conn.getInputStream());
        JsonNode realtime = root.path("data").path("realtime");
        int rank = 1;
        for (JsonNode item : realtime) {
            String word = item.path("word").asText();
            String scheme = item.path("scheme").asText(null);
            if (!word.isBlank()) {
                String url = (scheme != null && !scheme.isBlank()) ? scheme : "https://s.weibo.com/weibo?q=" + word;
                result.add(new NewsItemData(rank++, word, url));
                if (rank > 10) break;
            }
        }
        return result;
    }
}
