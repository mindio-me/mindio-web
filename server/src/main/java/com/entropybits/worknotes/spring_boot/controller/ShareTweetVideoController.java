/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.service.TweetPosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公开笔记页上的推文视频直链（匿名可访问）。
 * <p>
 * /v1/embed/tweet-video 需要登录，匿名访客用不了；这里只对「公开笔记里确实引用了这条推文」的请求放行，
 * 避免被当成任意推文的匿名代理。笔记引用校验每次都做（一次廉价的读库），只有对 X 的请求按推文 ID 缓存，
 * 防止匿名请求被放大成对 X 的请求、把服务器出口限流后连累登录用户的功能。
 */
@RestController
@RequestMapping("/v1/share")
@RequiredArgsConstructor
public class ShareTweetVideoController {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,25}");
    // 与 TweetPosterService 的 token 格式一致；这里先拦掉，免得它的 400 提示泄露给匿名调用方
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{1,32}");
    // 嵌入地址 Tweet.html?id=<id>，或 x.com/twitter.com 的 status/<id>；id 后不能再跟数字，status 后的斜杠兼容 JSON 的 \/ 转义
    private static final Pattern TWEET_REF = Pattern.compile("(?:Tweet\\.html\\?id=|status\\\\?/)(\\d{1,25})(?!\\d)");

    private static final long HIT_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long MISS_TTL_MS = TimeUnit.SECONDS.toMillis(60);
    private static final int CACHE_MAX = 500;

    /** video 为 null 表示没取到（也缓存，时间更短）。 */
    private record Cached(TweetPosterService.TweetVideo video, long expiresAt) {}

    private final NoteRepository noteRepository;
    private final TweetPosterService tweetPosterService;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    @GetMapping("/notes/{noteId}/tweet-video")
    public ResponseEntity<Map<String, String>> tweetVideo(@PathVariable Long noteId,
                                                          @RequestParam String id,
                                                          @RequestParam String token) {
        if (!DIGITS.matcher(id).matches() || !TOKEN.matcher(token).matches()) {
            return ResponseEntity.notFound().build();
        }
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || !Boolean.TRUE.equals(note.getIsPublic()) || !referencesTweet(note.getContent(), id)) {
            return ResponseEntity.notFound().build();
        }
        TweetPosterService.TweetVideo video = fetchCached(id, token);
        Map<String, String> body = new HashMap<>();
        body.put("videoUrl", video == null ? null : video.videoUrl());
        body.put("aspectRatio", video == null ? null : video.aspectRatio());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(body);
    }

    private TweetPosterService.TweetVideo fetchCached(String id, String token) {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(id);
        if (hit != null && hit.expiresAt() > now) {
            return hit.video();
        }
        TweetPosterService.TweetVideo video;
        try {
            video = tweetPosterService.fetchVideo(id, token);
        } catch (RuntimeException e) {
            video = null;
        }
        if (cache.size() >= CACHE_MAX) {
            cache.values().removeIf(c -> c.expiresAt() <= now);
            if (cache.size() >= CACHE_MAX) {
                cache.clear();
            }
        }
        cache.put(id, new Cached(video, now + (video == null ? MISS_TTL_MS : HIT_TTL_MS)));
        return video;
    }

    static boolean referencesTweet(String content, String tweetId) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        Matcher m = TWEET_REF.matcher(content);
        while (m.find()) {
            if (m.group(1).equals(tweetId)) {
                return true;
            }
        }
        return false;
    }
}
