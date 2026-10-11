/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.EditedNoteRef;
import com.entropybits.worknotes.spring_boot.service.NoteRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 一轮对话里 AI 对笔记的改动：查看与撤销。详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md */
@RestController
@RequestMapping("/v1/chat/turns")
@RequiredArgsConstructor
public class ChatTurnController {

    private final NoteRevisionService revisionService;

    @GetMapping("/{turnId}/revisions")
    public List<EditedNoteRef> revisions(@PathVariable String turnId, @AuthenticationPrincipal UserDetails user) {
        return revisionService.listTurn(user.getUsername(), turnId);
    }

    /** 用户在 AI 改完之后又改过某篇笔记时返回 409 + conflicts，前端确认后带 force=true 重试 */
    @PostMapping("/{turnId}/undo")
    public ResponseEntity<Map<String, Object>> undo(@PathVariable String turnId,
                                                    @RequestParam(defaultValue = "false") boolean force,
                                                    @AuthenticationPrincipal UserDetails user) {
        NoteRevisionService.UndoResult result = revisionService.undo(user.getUsername(), turnId, force);
        if (!result.conflicts().isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("conflicts", result.conflicts()));
        }
        return ResponseEntity.ok(Map.of("restoredNoteIds", result.restoredNoteIds()));
    }
}
