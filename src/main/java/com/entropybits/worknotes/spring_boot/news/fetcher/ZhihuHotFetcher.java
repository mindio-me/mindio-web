/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news.fetcher;

import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ZhihuHotFetcher implements NewsFetcher {

    private static final Logger log = LoggerFactory.getLogger(ZhihuHotFetcher.class);
    private static final String ZHIHU_HOT_URL = "https://www.zhihu.com/hot";
    private static final String ZHIHU_API_URL = "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=50&desktop=true";
    private static final String VVHAN_API_URL = "https://api.vvhan.com/api/hotlist/zhihuHot";
    private static final int RESPONSE_PREVIEW_LIMIT = 2000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSourceKey() {
        return "zhihu_hot";
    }

    @Override
    public List<NewsItemData> fetch() throws Exception {
        List<String> errors = new ArrayList<>();

        try {
            List<NewsItemData> items = parseZhihuApi(fetchJson(ZHIHU_API_URL, ZHIHU_HOT_URL));
            if (!items.isEmpty()) {
                return items;
            }
            errors.add("official API returned no items");
        } catch (Exception e) {
            log.warn("Zhihu hot: official API fetch failed: {}", e.getMessage());
            errors.add("official API: " + e.getMessage());
        }

        try {
            List<NewsItemData> items = parseZhihuPage(fetchZhihuHotPage());
            if (!items.isEmpty()) {
                return items;
            }
            errors.add("hot page returned no items");
        } catch (Exception e) {
            log.warn("Zhihu hot: page parse failed: {}", e.getMessage());
            errors.add("hot page: " + e.getMessage());
        }

        try {
            List<NewsItemData> fallbackItems = parseVvhanApi(fetchJson(VVHAN_API_URL, "https://www.zhihu.com/"));
            if (!fallbackItems.isEmpty()) {
                return fallbackItems;
            }
            errors.add("vvhan API returned no items");
        } catch (Exception e) {
            log.warn("Zhihu hot: vvhan API fetch failed: {}", e.getMessage());
            errors.add("vvhan API: " + e.getMessage());
        }

        throw new IOException("Zhihu hot fetch failed; " + String.join("; ", errors));
    }

    private List<NewsItemData> parseZhihuApi(JsonNode root) {
        List<NewsItemData> result = new ArrayList<>();
        JsonNode data = root.path("data");
        int rank = 1;
        for (JsonNode item : data) {
            JsonNode target = item.path("target");
            String title = target.path("title").asText();
            if (title.isBlank()) continue;

            long id = target.path("id").asLong(0);
            String url = id > 0
                    ? "https://www.zhihu.com/question/" + id
                    : target.path("url").asText("https://www.zhihu.com/hot");

            result.add(new NewsItemData(rank++, title, url));
            if (rank > 10) break;
        }
        return result;
    }

    private List<NewsItemData> parseZhihuPage(Document doc) {
        Element initialDataEl = doc.select("script#js-initialData").first();
        if (initialDataEl != null) {
            try {
                List<NewsItemData> items = parseZhihuEmbeddedData(objectMapper.readTree(initialDataEl.html()));
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception e) {
                log.warn("Zhihu hot: js-initialData parse failed: {}", e.getMessage());
            }
        }

        Element nextDataEl = doc.select("script#__NEXT_DATA__").first();
        if (nextDataEl != null) {
            try {
                List<NewsItemData> items = parseZhihuEmbeddedData(objectMapper.readTree(nextDataEl.html()));
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (Exception e) {
                log.warn("Zhihu hot: __NEXT_DATA__ parse failed: {}", e.getMessage());
            }
        }

        return parseHtmlElements(doc);
    }

    private List<NewsItemData> parseZhihuEmbeddedData(JsonNode root) {
        JsonNode hotList = root.at("/initialState/topstory/hotList");
        if (hotList.isMissingNode() || !hotList.isArray()) {
            hotList = root.at("/initialState/topstory/hotBoard/data");
        }
        if (hotList.isMissingNode() || !hotList.isArray()) {
            hotList = root.at("/props/pageProps/hotList");
        }
        if (hotList.isMissingNode() || !hotList.isArray()) {
            hotList = root.at("/props/initialReduxState/topstory/hotBoard/data");
        }
        return parseZhihuItems(hotList);
    }

    private List<NewsItemData> parseZhihuItems(JsonNode items) {
        List<NewsItemData> result = new ArrayList<>();
        if (items.isMissingNode() || !items.isArray()) {
            return result;
        }

        int rank = 1;
        for (JsonNode item : items) {
            JsonNode target = item.path("target");
            String title = target.path("title").asText();
            if (title.isBlank()) title = item.path("title").asText();
            if (title.isBlank()) continue;

            long id = target.path("id").asLong(0);
            if (id == 0) id = item.path("id").asLong(0);
            String url = id > 0
                    ? "https://www.zhihu.com/question/" + id
                    : target.path("url").asText("https://www.zhihu.com/hot");

            result.add(new NewsItemData(rank++, title, url));
            if (rank > 10) break;
        }
        return result;
    }

    private List<NewsItemData> parseHtmlElements(Document doc) {
        List<NewsItemData> result = new ArrayList<>();
        Elements sections = doc.select("section.HotItem, div.HotItem, [class*=HotItem]:not([class*=HotItem-])");
        int rank = 1;
        for (Element section : sections) {
            Element titleEl = section.select("h2, [class*=HotItem-title], a[href*='/question/']").first();
            if (titleEl == null) continue;
            String title = titleEl.text().trim();
            if (title.isBlank()) continue;

            Element linkEl = section.select("a[href*='/question/']").first();
            String url = "https://www.zhihu.com/hot";
            if (linkEl != null) {
                String href = linkEl.attr("href");
                url = href.startsWith("http") ? href : "https://www.zhihu.com" + href;
            }

            result.add(new NewsItemData(rank++, title, url));
            if (rank > 10) break;
        }
        return result;
    }

    private List<NewsItemData> parseVvhanApi(JsonNode root) {
        List<NewsItemData> result = new ArrayList<>();
        JsonNode data = root.path("data");
        int rank = 1;
        for (JsonNode item : data) {
            String title = item.path("title").asText();
            if (title.isBlank()) continue;

            String url = item.path("url").asText("https://www.zhihu.com/hot");
            result.add(new NewsItemData(rank++, title, url));
            if (rank > 10) break;
        }
        return result;
    }

    private Document fetchZhihuHotPage() throws IOException {
        Connection.Response response = Jsoup.connect(ZHIHU_HOT_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.zhihu.com/")
                .timeout(15000)
                .ignoreHttpErrors(true)
                .execute();

        String body = response.body();
        logResponse(
                "Zhihu hot page",
                response.statusCode(),
                response.contentType(),
                response.header("Server"),
                response.header("X-Backend-Response"),
                body
        );
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " from " + ZHIHU_HOT_URL
                    + "; response=" + preview(body));
        }
        return response.parse();
    }

    private JsonNode fetchJson(String url, String referer) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("Referer", referer);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int status = conn.getResponseCode();
        String contentType = conn.getHeaderField("Content-Type");
        String server = conn.getHeaderField("Server");
        String backendResponse = conn.getHeaderField("X-Backend-Response");
        if (status >= 400) {
            String body = readBody(conn.getErrorStream());
            logResponse("JSON endpoint " + url, status, contentType, server, backendResponse, body);
            conn.disconnect();
            throw new IOException("HTTP " + status + " from " + url + "; response=" + preview(body));
        }

        try (InputStream input = conn.getInputStream()) {
            String body = readBody(input);
            logResponse("JSON endpoint " + url, status, contentType, server, backendResponse, body);
            return objectMapper.readTree(body);
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void logResponse(
            String source,
            int status,
            String contentType,
            String server,
            String backendResponse,
            String body
    ) {
        String message = "{} response: status={}, contentType={}, server={}, backendResponse={}, body={}";
        if (status >= 400) {
            log.warn(message, source, status, contentType, server, backendResponse, preview(body));
        } else {
            log.debug(message, source, status, contentType, server, backendResponse, preview(body));
        }
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= RESPONSE_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, RESPONSE_PREVIEW_LIMIT) + "...";
    }
}
