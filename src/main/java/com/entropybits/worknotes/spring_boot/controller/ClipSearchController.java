/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageRequest;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchResultSaveResponse;
import com.entropybits.worknotes.spring_boot.service.ClipSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/clip-search")
@RequiredArgsConstructor
public class ClipSearchController {

    private final ClipSearchService clipSearchService;

    @GetMapping("/messages")
    public ResponseEntity<List<ClipSearchMessageResponse>> listMessages(
            @RequestParam Long noteId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(clipSearchService.listHistory(user.getUsername(), noteId));
    }

    @PostMapping("/messages")
    public ResponseEntity<List<ClipSearchMessageResponse>> sendMessage(
            @Valid @RequestBody ClipSearchMessageRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(clipSearchService.sendMessage(user.getUsername(), request.getContent(), request.getNoteId()));
    }

    @PostMapping("/messages/{messageId}/results/{resultIndex}/save")
    public ResponseEntity<ClipSearchResultSaveResponse> saveResult(
            @PathVariable Long messageId,
            @PathVariable int resultIndex,
            @AuthenticationPrincipal UserDetails user) {
        Long sourceClipId = clipSearchService.saveResult(messageId, resultIndex, user.getUsername());
        return ResponseEntity.ok(new ClipSearchResultSaveResponse(sourceClipId));
    }
}
