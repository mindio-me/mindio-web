/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.service.AgentNoteEditService;
import com.entropybits.worknotes.spring_boot.service.NoteInsertPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 聊天回复上「插入到笔记」按钮用的两个接口。详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md */
@RestController
@RequestMapping("/v1/notes")
@RequiredArgsConstructor
public class NoteAiInsertController {

    private final NoteInsertPositionService insertPositionService;
    private final AgentNoteEditService noteEditService;

    @PostMapping("/{noteId}/insert-position")
    public Map<String, Object> insertPosition(@PathVariable Long noteId, @RequestBody Map<String, String> body,
                                              @AuthenticationPrincipal UserDetails user) {
        return insertPositionService.suggest(user.getUsername(), noteId, body.get("content"), body.get("cursorBlockId"));
    }

    @PostMapping("/{noteId}/insert-content")
    public Map<String, Object> insertContent(@PathVariable Long noteId, @RequestBody Map<String, String> body,
                                             @AuthenticationPrincipal UserDetails user) {
        return noteEditService.applyAsReplyInsert(user.getUsername(), noteId, body.get("markdown"), body.get("after"));
    }
}
