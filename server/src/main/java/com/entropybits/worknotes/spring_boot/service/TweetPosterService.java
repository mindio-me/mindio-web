/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 取 X(Twitter) 推文里视频/图片的封面地址（媒体画廊缩略图）和视频直链（画廊弹窗播放）。
 *
 * X 没有公开的封面地址规律，推文嵌入卡片自己的数据来自 cdn.syndication.twimg.com/tweet-result，
 * 但这个接口的跨域只放行 platform.twitter.com，浏览器拿不到，只能由服务端转一手。它是非官方接口，
 * 随时可能变；所以只在插入链接时调一次，拿到的地址存进笔记，任何失败都返回 null，由前端退回通用占位图。
 *
 * 国内网络直连 X 不通，可以配出站代理（worknotes.embed.outbound-proxy，默认取环境变量 HTTPS_PROXY，
 * 格式 http://[user:pass@]host:port）。用 Apache HttpClient 而不是 JDK HttpClient：JDK 默认禁止
 * 在 HTTPS 隧道上做 Basic 代理认证（jdk.http.auth.tunneling.disabledSchemes），要改就得改全局 JVM 参数。
 */
@Service
public class TweetPosterService {

    private static final Logger logger = LoggerFactory.getLogger(TweetPosterService.class);

    private static final Pattern TWEET_ID = Pattern.compile("\\d{1,25}");
    // token 由前端按嵌入卡片同款算法从推文 ID 算出（目前接口只要求带上、不校验值），这里只校验格式
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{1,32}");
    // 只接受 X 自己图床上的地址，不把接口返回的任意 URL 存进笔记
    private static final String POSTER_HOST_PREFIX = "https://pbs.twimg.com/";
    private static final String VIDEO_HOST_PREFIX = "https://video.twimg.com/";
    private static final long MAX_PREFERRED_BITRATE = 2_500_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloseableHttpClient httpClient;

    private String syndicationBaseUrl = "https://cdn.syndication.twimg.com";

    public TweetPosterService(@Value("${worknotes.embed.outbound-proxy:${HTTPS_PROXY:}}") String outboundProxy) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(8))
                .setResponseTimeout(Timeout.ofSeconds(10))
                .build();
        HttpClientBuilder builder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
        if (outboundProxy != null && !outboundProxy.isBlank()) {
            configureProxy(builder, outboundProxy.trim());
        }
        this.httpClient = builder.build();
    }

    private static void configureProxy(HttpClientBuilder builder, String proxyUrl) {
        URI proxy = URI.create(proxyUrl);
        String scheme = proxy.getScheme() == null ? "http" : proxy.getScheme();
        HttpHost proxyHost = new HttpHost(scheme, proxy.getHost(), proxy.getPort() > 0 ? proxy.getPort() : 80);
        builder.setProxy(proxyHost);
        String userInfo = proxy.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            String user = URLDecoder.decode(colon < 0 ? userInfo : userInfo.substring(0, colon), StandardCharsets.UTF_8);
            String pass = colon < 0 ? "" : URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(new AuthScope(proxyHost), new UsernamePasswordCredentials(user, pass.toCharArray()));
            builder.setDefaultCredentialsProvider(credentials);
        }
        // 日志里只记代理地址，不记账号
        logger.info("推文封面请求走出站代理 {}:{}", proxyHost.getHostName(), proxyHost.getPort());
    }

    public String fetchPosterUrl(String tweetId, String token) {
        JsonNode tweet = fetchTweet(tweetId, token);
        return tweet == null ? null : pickPosterUrl(tweet);
    }

    /**
     * 推文视频的 mp4 直链和宽高比，给媒体画廊的弹窗用原生播放器直接播放（推文卡片不支持自动播放）。
     * 直链不存进笔记，每次点开时现取，避免链接过期；取不到返回 null，前端退回推文卡片。
     */
    public TweetVideo fetchVideo(String tweetId, String token) {
        JsonNode tweet = fetchTweet(tweetId, token);
        return tweet == null ? null : pickVideo(tweet);
    }

    public record TweetVideo(String videoUrl, String aspectRatio) {}

    private JsonNode fetchTweet(String tweetId, String token) {
        if (tweetId == null || !TWEET_ID.matcher(tweetId).matches()) {
            throw new BadRequestException("推文 ID 格式不正确");
        }
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new BadRequestException("token 格式不正确");
        }
        try {
            HttpGet request = new HttpGet(syndicationBaseUrl + "/tweet-result?id=" + tweetId + "&token=" + token);
            String body = httpClient.execute(request, response -> {
                if (response.getCode() != 200) {
                    logger.info("推文数据获取失败，HTTP {}，tweetId={}", response.getCode(), tweetId);
                    return null;
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            });
            return body == null ? null : objectMapper.readTree(body);
        } catch (Exception e) {
            logger.info("推文数据获取失败，tweetId={}: {}", tweetId, e.getMessage());
            return null;
        }
    }

    /**
     * 取 mp4 变体里码率不超过 ~2.5Mbps 的最高一档（约 720p，弹窗里足够清晰又不至于太慢），
     * 都超过就取最低的一档。只接受 video.twimg.com 上的地址。
     */
    private TweetVideo pickVideo(JsonNode tweet) {
        for (JsonNode item : tweet.path("mediaDetails")) {
            JsonNode videoInfo = item.path("video_info");
            String best = null;
            long bestBitrate = -1;
            String lowest = null;
            long lowestBitrate = Long.MAX_VALUE;
            for (JsonNode variant : videoInfo.path("variants")) {
                String url = variant.path("url").asText("");
                if (!"video/mp4".equals(variant.path("content_type").asText("")) || !url.startsWith(VIDEO_HOST_PREFIX)) continue;
                long bitrate = variant.path("bitrate").asLong(0);
                if (bitrate <= MAX_PREFERRED_BITRATE && bitrate > bestBitrate) {
                    best = url;
                    bestBitrate = bitrate;
                }
                if (bitrate < lowestBitrate) {
                    lowest = url;
                    lowestBitrate = bitrate;
                }
            }
            String chosen = best != null ? best : lowest;
            if (chosen == null) continue;
            JsonNode ratio = videoInfo.path("aspect_ratio");
            String aspectRatio = ratio.size() == 2 && ratio.get(0).asInt() > 0 && ratio.get(1).asInt() > 0
                    ? ratio.get(0).asInt() + "/" + ratio.get(1).asInt()
                    : "16/9";
            return new TweetVideo(chosen, aspectRatio);
        }
        return null;
    }

    /** 优先取视频/动图的封面，没有就取第一张媒体图 */
    private String pickPosterUrl(JsonNode tweet) {
        JsonNode media = tweet.path("mediaDetails");
        String fallback = null;
        for (JsonNode item : media) {
            String url = item.path("media_url_https").asText("");
            if (!url.startsWith(POSTER_HOST_PREFIX)) continue;
            String type = item.path("type").asText("");
            if ("video".equals(type) || "animated_gif".equals(type)) return url;
            if (fallback == null) fallback = url;
        }
        if (fallback != null) return fallback;
        String videoPoster = tweet.path("video").path("poster").asText("");
        return videoPoster.startsWith(POSTER_HOST_PREFIX) ? videoPoster : null;
    }
}
