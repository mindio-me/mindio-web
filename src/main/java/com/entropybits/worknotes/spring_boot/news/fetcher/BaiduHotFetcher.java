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
public class BaiduHotFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(BaiduHotFetcher.class);
    private static final String URL = "https://top.baidu.com/api/board?tab=realtime";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceKey() {
        return "baidu_hot";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        List<NewsItemData> result = new ArrayList<>();
        HttpURLConnection conn = (HttpURLConnection) URI.create(URL).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Referer", "https://top.baidu.com/");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        JsonNode root = objectMapper.readTree(conn.getInputStream());
        JsonNode cards = root.path("data").path("cards");
        if (cards.isArray() && !cards.isEmpty()) {
            JsonNode content = cards.get(0).path("content");
            int rank = 1;
            for (JsonNode item : content) {
                String word = item.path("word").asText();
                String url = item.path("url").asText(null);
                if (!word.isBlank()) {
                    if (url == null || url.isBlank()) {
                        url = "https://www.baidu.com/s?wd=" + word;
                    }
                    result.add(new NewsItemData(rank++, word, url));
                    if (rank > 10) break;
                }
            }
        }
        return result;
    }
}
