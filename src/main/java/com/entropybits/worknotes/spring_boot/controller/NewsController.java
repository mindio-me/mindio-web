/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.NewsFeedResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsHistoryResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsSourceConfigResponse;
import com.entropybits.worknotes.spring_boot.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<List<NewsFeedResponse>> getNews(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(newsService.getNewsByDate(targetDate));
    }

    @GetMapping("/history")
    public ResponseEntity<NewsHistoryResponse> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(newsService.getNewsHistory(page, size));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<NewsSourceConfigResponse>> getSources(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(newsService.getAllSources());
    }

    @PutMapping("/sources/{key}")
    public ResponseEntity<NewsSourceConfigResponse> toggleSource(
            @PathVariable String key,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(newsService.toggleSource(key));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshAll(@AuthenticationPrincipal UserDetails userDetails) {
        new Thread(newsService::fetchAll).start();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/refresh/{key}")
    public ResponseEntity<Void> refreshOne(
            @PathVariable String key,
            @AuthenticationPrincipal UserDetails userDetails) {
        new Thread(() -> newsService.fetchSource(key)).start();
        return ResponseEntity.accepted().build();
    }
}
