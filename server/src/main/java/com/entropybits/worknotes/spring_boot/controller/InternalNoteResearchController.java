/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.InternalSearchWebRequest;
import com.entropybits.worknotes.spring_boot.dto.NoteReferenceItem;
import com.entropybits.worknotes.spring_boot.search.SearchResultItem;
import com.entropybits.worknotes.spring_boot.search.WebSearchProviderResolver;
import com.entropybits.worknotes.spring_boot.service.NoteClipRefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 只给独立的Python/LangGraph Agent服务回调用的内部接口（鉴权见 InternalServiceAuthFilter，
 * 不是给终端用户/前端直接调用的）。详见
 * docs/superpowers/specs/2026-09-04-note-research-workflow-design.md。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalNoteResearchController {

    private final WebSearchProviderResolver searchProviderResolver;
    private final NoteClipRefService noteClipRefService;

    @PostMapping("/search-web")
    public List<SearchResultItem> searchWeb(@RequestBody InternalSearchWebRequest request) throws Exception {
        return searchProviderResolver.resolve().search(request.query(), request.limit());
    }

    @GetMapping("/note-references/{noteId}")
    public List<NoteReferenceItem> noteReferences(@PathVariable Long noteId) {
        return noteClipRefService.getFullContentForNote(noteId);
    }
}
