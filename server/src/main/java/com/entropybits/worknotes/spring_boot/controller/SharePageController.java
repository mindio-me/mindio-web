/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.service.sharecard.PublicBaseUrl;
import com.entropybits.worknotes.spring_boot.service.sharecard.SharePageRenderer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给链接预览爬虫的 Open Graph meta 页（匿名可访问）。
 */
@RestController
@RequestMapping("/v1/share")
@RequiredArgsConstructor
public class SharePageController {

    private final NoteRepository noteRepository;

    @Value("${worknotes.frontend.base-url:http://localhost:10822}")
    private String frontendBaseUrl;

    @GetMapping(value = "/notes/{id}", produces = "text/html;charset=UTF-8")
    @Transactional(readOnly = true)
    public ResponseEntity<String> notePage(@PathVariable Long id, HttpServletRequest request) {
        Note note = noteRepository.findById(id).orElse(null);
        if (note == null || !Boolean.TRUE.equals(note.getIsPublic())) {
            return ResponseEntity.notFound().build();
        }
        String html = SharePageRenderer.render(note, PublicBaseUrl.resolve(frontendBaseUrl, request));
        return ResponseEntity.ok(html);
    }
}
