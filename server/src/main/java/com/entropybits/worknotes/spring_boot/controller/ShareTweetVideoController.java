/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.service.TweetPosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 公开笔记页上的推文视频直链（匿名可访问）。
 * <p>
 * /v1/embed/tweet-video 需要登录，匿名访客用不了；这里只对「公开笔记里确实引用了这条推文」的请求放行，
 * 避免被当成任意推文的匿名代理。读笔记走仓库自带的短事务，调 X 的慢请求在事务之外。
 */
@RestController
@RequestMapping("/v1/share")
@RequiredArgsConstructor
public class ShareTweetVideoController {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,25}");

    private final NoteRepository noteRepository;
    private final TweetPosterService tweetPosterService;

    @GetMapping("/notes/{noteId}/tweet-video")
    public ResponseEntity<Map<String, String>> tweetVideo(@PathVariable Long noteId,
                                                          @RequestParam String id,
                                                          @RequestParam String token) {
        if (!DIGITS.matcher(id).matches()) {
            return ResponseEntity.notFound().build();
        }
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || !Boolean.TRUE.equals(note.getIsPublic()) || !referencesTweet(note.getContent(), id)) {
            return ResponseEntity.notFound().build();
        }
        TweetPosterService.TweetVideo video = tweetPosterService.fetchVideo(id, token);
        Map<String, String> body = new HashMap<>();
        body.put("videoUrl", video == null ? null : video.videoUrl());
        body.put("aspectRatio", video == null ? null : video.aspectRatio());
        return ResponseEntity.ok(body);
    }

    /**
     * 笔记内容（JSON 字符串）里是否有这条推文：嵌入地址 Tweet.html?id=<id>，或 x.com/twitter.com 的 status/<id> 链接。
     * id 后面不能再跟数字，避免前缀相同的另一条推文被误匹配；status 前后的斜杠兼容 JSON 的 \/ 转义。
     */
    static boolean referencesTweet(String content, String tweetId) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        Pattern p = Pattern.compile("(?:Tweet\\.html\\?id=|status\\\\?/)" + tweetId + "(?!\\d)");
        return p.matcher(content).find();
    }
}
