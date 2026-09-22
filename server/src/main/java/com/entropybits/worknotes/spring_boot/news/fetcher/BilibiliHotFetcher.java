/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class BilibiliHotFetcher implements NewsFetcher {

    private static final String API_URL =
            "https://api.bilibili.com/x/web-interface/popular?pn=1&ps=20";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceKey() {
        return "bilibili_hot";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int status = conn.getResponseCode();
        if (status >= 400) {
            conn.disconnect();
            throw new IOException("HTTP " + status + " from " + API_URL);
        }

        try (InputStream input = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new IOException("Bilibili API error " + code + ": "
                        + root.path("message").asText("unknown error"));
            }
            return parseItems(root.path("data").path("list"));
        } finally {
            conn.disconnect();
        }
    }

    private List<NewsItemData> parseItems(JsonNode items) {
        List<NewsItemData> result = new ArrayList<>();
        int rank = 1;
        for (JsonNode item : items) {
            String title = item.path("title").asText();
            String bvid = item.path("bvid").asText();
            if (title.isBlank() || bvid.isBlank()) {
                continue;
            }

            String url = item.path("short_link_v2").asText();
            if (url.isBlank()) {
                url = "https://www.bilibili.com/video/" + bvid;
            }
            result.add(new NewsItemData(rank++, title, url));
            if (rank > 10) {
                break;
            }
        }
        return result;
    }
}
