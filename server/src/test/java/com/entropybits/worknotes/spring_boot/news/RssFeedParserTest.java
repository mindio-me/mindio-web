/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RssFeedParserTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesRssItems() throws Exception {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item><title>First story</title><link>https://example.com/1</link></item>
                    <item><title>Second story</title><link>https://example.com/2</link></item>
                  </channel>
                </rss>
                """;
        String url = startServer(200, rss);

        List<NewsItemData> items = new RssFeedParser().parse(url, 10);

        assertThat(items).containsExactly(
                new NewsItemData(1, "First story", "https://example.com/1"),
                new NewsItemData(2, "Second story", "https://example.com/2")
        );
    }

    @Test
    void exposesHttpFailureInsteadOfReturningEmptyList() throws Exception {
        String url = startServer(403, "Forbidden");

        assertThatThrownBy(() -> new RssFeedParser().parse(url, 10))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 403");
    }

    private String startServer(int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/feed", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/rss+xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/feed";
    }
}
