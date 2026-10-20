/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.NoteResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardBackgroundRequest;
import com.entropybits.worknotes.spring_boot.dto.ShareCardBackgroundResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardSaveRequest;
import com.entropybits.worknotes.spring_boot.service.NoteService;
import com.entropybits.worknotes.spring_boot.service.sharecard.ShareCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 笔记分享卡片（网站卡片）接口
 */
@RestController
@RequestMapping("/v1/notes")
@RequiredArgsConstructor
public class ShareCardController {

    private final ShareCardService shareCardService;
    private final NoteService noteService;

    @GetMapping("/{id}/share-card")
    public ResponseEntity<ShareCardResponse> getShareCard(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(shareCardService.getCard(id, userDetails.getUsername()));
    }

    /** AI 生成卡片背景图：存进上传目录并返回 URL，不改动笔记 */
    @PostMapping("/{id}/share-card/background")
    public ResponseEntity<ShareCardBackgroundResponse> generateBackground(
            @PathVariable Long id,
            @RequestBody(required = false) ShareCardBackgroundRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String prompt = request == null ? null : request.prompt();
        return ResponseEntity.ok(shareCardService.generateBackground(id, userDetails.getUsername(), prompt));
    }

    /** 保存卡片；body.publish=true 时同一请求里顺带公开笔记 */
    @PutMapping("/{id}/share-card")
    public ResponseEntity<NoteResponse> saveShareCard(
            @PathVariable Long id,
            @RequestBody ShareCardSaveRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(noteService.saveShareCard(id, userDetails.getUsername(), request));
    }
}
