/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一次性存量回填：把当前用户已有的全部笔记/收藏都索引一遍。不做进度追踪/断点续传——
 * 数据量级下一次性同步遍历触发（每条内部仍是 @Async）足够，详见设计文档"范围之外"一节。
 */
@RestController
@RequestMapping("/v1/content-index")
@RequiredArgsConstructor
public class ContentIndexBackfillController {

    private final NoteRepository noteRepository;
    private final SourceClipRepository clipRepository;
    private final UserRepository userRepository;
    private final ContentIndexingService contentIndexingService;

    @PostMapping("/backfill")
    public ResponseEntity<Void> backfill(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        for (Note note : noteRepository.findByOwner(user)) {
            contentIndexingService.reindexNote(note.getId());
        }
        for (SourceClip clip : clipRepository.findByOwner(user)) {
            contentIndexingService.reindexClip(clip.getId());
        }

        return ResponseEntity.ok().build();
    }
}
