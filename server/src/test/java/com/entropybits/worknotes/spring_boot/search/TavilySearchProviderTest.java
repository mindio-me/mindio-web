/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import com.entropybits.worknotes.spring_boot.search.config.SearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TavilySearchProviderTest {

    private HttpServer httpServer;
    private TavilySearchProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String lastRequestBody;

    private void setUp(String responseJson) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/search", ex -> {
            lastRequestBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        SearchProperties props = new SearchProperties();
        props.getTavily().setApiKey("test-key");
        props.getTavily().setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());

        provider = new TavilySearchProvider(props, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    @Test
    void search_mapsTavilyResponseToSearchResultItems() throws Exception {
        setUp("{\"results\":[" +
                "{\"title\":\"Title A\",\"url\":\"https://a.example.com\",\"content\":\"Excerpt A\"}," +
                "{\"title\":\"Title B\",\"url\":\"https://b.example.com\",\"content\":\"Excerpt B\"}]}");

        List<SearchResultItem> results = provider.search("AI 深度报道", 5);

        assertThat(results).containsExactly(
                new SearchResultItem("Title A", "https://a.example.com", "Excerpt A"),
                new SearchResultItem("Title B", "https://b.example.com", "Excerpt B")
        );
        assertThat(lastRequestBody).contains("\"api_key\":\"test-key\"").contains("AI 深度报道");
    }

    @Test
    void search_dropsResultsWithNullOrNonHttpUrl() throws Exception {
        setUp("{\"results\":[" +
                "{\"title\":\"No url\",\"content\":\"Excerpt\"}," +
                "{\"title\":\"Null url\",\"url\":null,\"content\":\"Excerpt\"}," +
                "{\"title\":\"Ftp url\",\"url\":\"ftp://a.example.com\",\"content\":\"Excerpt\"}," +
                "{\"title\":\"Title B\",\"url\":\"https://b.example.com\",\"content\":\"Excerpt B\"}]}");

        List<SearchResultItem> results = provider.search("AI 深度报道", 5);

        assertThat(results).containsExactly(
                new SearchResultItem("Title B", "https://b.example.com", "Excerpt B")
        );
    }

    @Test
    void search_throwsWithStatusCodeOnHttpError() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/search", ex -> {
            byte[] bytes = "unauthorized".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(401, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();
        SearchProperties props = new SearchProperties();
        props.getTavily().setApiKey("bad-key");
        props.getTavily().setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        provider = new TavilySearchProvider(props, objectMapper);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> provider.search("AI", 5));
    }
}
