/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TweetPosterServiceTest {

    private final TweetPosterService service = new TweetPosterService("");
    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    private void serve(int status, String body, AtomicReference<String> query) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/tweet-result", ex -> {
            if (query != null) query.set(ex.getRequestURI().getQuery());
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();
        ReflectionTestUtils.setField(service, "syndicationBaseUrl", "http://127.0.0.1:" + httpServer.getAddress().getPort());
    }

    @Test
    void returnsVideoThumbnailAndForwardsIdAndToken() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        serve(200, "{\"mediaDetails\":[" +
                "{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/photo.jpg\"}," +
                "{\"type\":\"video\",\"media_url_https\":\"https://pbs.twimg.com/amplify_video_thumb/1/img/v.jpg\"}]}", query);

        String poster = service.fetchPosterUrl("2107377858951995641", "4abc");

        assertThat(poster).isEqualTo("https://pbs.twimg.com/amplify_video_thumb/1/img/v.jpg");
        assertThat(query.get()).isEqualTo("id=2107377858951995641&token=4abc");
    }

    @Test
    void fallsBackToFirstImageWhenNoVideo() throws Exception {
        serve(200, "{\"mediaDetails\":[{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/photo.jpg\"}]}", null);

        assertThat(service.fetchPosterUrl("123", "abc")).isEqualTo("https://pbs.twimg.com/media/photo.jpg");
    }

    @Test
    void ignoresUrlsOutsideTwitterImageHost() throws Exception {
        serve(200, "{\"mediaDetails\":[{\"type\":\"video\",\"media_url_https\":\"https://evil.example.com/x.jpg\"}]}", null);

        assertThat(service.fetchPosterUrl("123", "abc")).isNull();
    }

    @Test
    void returnsNullOnEmptyOrFailedResponse() throws Exception {
        serve(200, "{}", null);
        assertThat(service.fetchPosterUrl("123", "abc")).isNull();
        httpServer.stop(0);

        serve(404, "not found", null);
        assertThat(service.fetchPosterUrl("123", "abc")).isNull();
    }

    @Test
    void goesThroughOutboundProxyWithBasicAuth() throws Exception {
        // 本地起一个要求 Basic 认证的代理：先回 407，带上正确账号后才转发出结果
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("user:p@ss".getBytes(StandardCharsets.UTF_8));
        AtomicReference<String> proxiedUri = new AtomicReference<>();
        byte[] body = "{\"mediaDetails\":[{\"type\":\"video\",\"media_url_https\":\"https://pbs.twimg.com/v.jpg\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", ex -> {
            if (!expectedAuth.equals(ex.getRequestHeaders().getFirst("Proxy-Authorization"))) {
                ex.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"test\"");
                ex.sendResponseHeaders(407, -1);
                ex.close();
                return;
            }
            proxiedUri.set(ex.getRequestURI().toString());
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        httpServer.start();
        TweetPosterService proxied = new TweetPosterService("http://user:p%40ss@127.0.0.1:" + httpServer.getAddress().getPort());
        ReflectionTestUtils.setField(proxied, "syndicationBaseUrl", "http://syndication.test");

        assertThat(proxied.fetchPosterUrl("123", "abc")).isEqualTo("https://pbs.twimg.com/v.jpg");
        assertThat(proxiedUri.get()).isEqualTo("http://syndication.test/tweet-result?id=123&token=abc");
    }

    @Test
    void picksMp4UnderPreferredBitrateWithAspectRatio() throws Exception {
        serve(200, "{\"mediaDetails\":[{\"type\":\"video\",\"video_info\":{\"aspect_ratio\":[9,16],\"variants\":[" +
                "{\"content_type\":\"application/x-mpegURL\",\"url\":\"https://video.twimg.com/pl/a.m3u8\"}," +
                "{\"content_type\":\"video/mp4\",\"bitrate\":632000,\"url\":\"https://video.twimg.com/vid/320.mp4\"}," +
                "{\"content_type\":\"video/mp4\",\"bitrate\":2176000,\"url\":\"https://video.twimg.com/vid/720.mp4\"}," +
                "{\"content_type\":\"video/mp4\",\"bitrate\":10368000,\"url\":\"https://video.twimg.com/vid/1080.mp4\"}," +
                "{\"content_type\":\"video/mp4\",\"bitrate\":900000,\"url\":\"https://evil.example.com/x.mp4\"}]}}]}", null);

        TweetPosterService.TweetVideo video = service.fetchVideo("123", "abc");

        assertThat(video.videoUrl()).isEqualTo("https://video.twimg.com/vid/720.mp4");
        assertThat(video.aspectRatio()).isEqualTo("9/16");
    }

    @Test
    void fallsBackToLowestBitrateWhenAllAboveCap() throws Exception {
        serve(200, "{\"mediaDetails\":[{\"type\":\"video\",\"video_info\":{\"variants\":[" +
                "{\"content_type\":\"video/mp4\",\"bitrate\":10368000,\"url\":\"https://video.twimg.com/vid/1080.mp4\"}," +
                "{\"content_type\":\"video/mp4\",\"bitrate\":5000000,\"url\":\"https://video.twimg.com/vid/900.mp4\"}]}}]}", null);

        TweetPosterService.TweetVideo video = service.fetchVideo("123", "abc");

        assertThat(video.videoUrl()).isEqualTo("https://video.twimg.com/vid/900.mp4");
        assertThat(video.aspectRatio()).isEqualTo("16/9");
    }

    @Test
    void returnsNullVideoForPhotoOnlyTweet() throws Exception {
        serve(200, "{\"mediaDetails\":[{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/p.jpg\"}]}", null);

        assertThat(service.fetchVideo("123", "abc")).isNull();
    }

    @Test
    void rejectsMalformedIdOrToken() {
        assertThatThrownBy(() -> service.fetchPosterUrl("123&x=1", "abc")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.fetchPosterUrl("123", "a/b")).isInstanceOf(BadRequestException.class);
    }
}
