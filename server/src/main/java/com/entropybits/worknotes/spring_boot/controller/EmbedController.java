/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.service.TweetPosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 编辑器嵌入媒体用的辅助接口。要求登录态——服务端替调用方访问外部接口，不对匿名开放。
 */
@RestController
@RequestMapping("/v1/embed")
@RequiredArgsConstructor
@Tag(name = "嵌入媒体", description = "编辑器嵌入第三方媒体时的辅助接口")
public class EmbedController {

    private final TweetPosterService tweetPosterService;

    @GetMapping("/tweet-poster")
    @Operation(summary = "获取推文封面", description = "返回推文里视频/图片的封面地址，取不到时 posterUrl 为 null")
    public ResponseEntity<Map<String, String>> tweetPoster(@RequestParam String id, @RequestParam String token) {
        return ResponseEntity.ok(Collections.singletonMap("posterUrl", tweetPosterService.fetchPosterUrl(id, token)));
    }
}
