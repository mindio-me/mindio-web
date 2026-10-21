/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AgentClipListing;
import com.entropybits.worknotes.spring_boot.dto.AgentDocumentContent;
import com.entropybits.worknotes.spring_boot.dto.AgentDocumentSummary;
import com.entropybits.worknotes.spring_boot.dto.AgentImageRef;
import com.entropybits.worknotes.spring_boot.dto.AgentInsertImageRequest;
import com.entropybits.worknotes.spring_boot.service.AgentDocumentService;
import com.entropybits.worknotes.spring_boot.service.AgentImageEditService;
import com.entropybits.worknotes.spring_boot.service.AgentNoteEditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 只给独立的Python/LangGraph Agent服务回调用的内部接口（鉴权见 InternalServiceAuthFilter，
 * 不是给终端用户/前端直接调用的）：agent 按 ID/标题读取用户任意笔记或收藏的全文和其中的图片。
 */
@RestController
@RequestMapping("/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final AgentDocumentService agentDocumentService;
    private final AgentImageEditService agentImageEditService;
    private final AgentNoteEditService agentNoteEditService;

    @GetMapping("/notes")
    public List<AgentDocumentSummary> findNotes(@RequestParam String username, @RequestParam String title) {
        return agentDocumentService.findNotesByTitle(username, title);
    }

    @GetMapping("/notes/{noteId}")
    public AgentDocumentContent readNote(@RequestParam String username, @PathVariable Long noteId) {
        return agentDocumentService.readNote(username, noteId);
    }

    @GetMapping("/clips")
    public AgentClipListing listClips(@RequestParam String username,
                                      @RequestParam(required = false) String domain,
                                      @RequestParam(required = false) String tag,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer limit) {
        return agentDocumentService.listClips(username, domain, tag, keyword, limit);
    }

    @GetMapping("/clips/{clipId}")
    public AgentDocumentContent readClip(@RequestParam String username, @PathVariable Long clipId) {
        return agentDocumentService.readClip(username, clipId);
    }

    @GetMapping("/notes/{noteId}/images")
    public List<AgentImageRef> noteImages(@RequestParam String username, @PathVariable Long noteId) {
        return agentDocumentService.listNoteImages(username, noteId);
    }

    @GetMapping("/clips/{clipId}/images")
    public List<AgentImageRef> clipImages(@RequestParam String username, @PathVariable Long clipId) {
        return agentDocumentService.listClipImages(username, clipId);
    }

    /** 本地图片回 {mimeType, base64Data}，外链图片回 {url} */
    @GetMapping("/notes/{noteId}/images/{index}")
    public Map<String, Object> noteImage(@RequestParam String username, @PathVariable Long noteId,
                                         @PathVariable int index) {
        return agentDocumentService.loadNoteImage(username, noteId, index);
    }

    @GetMapping("/clips/{clipId}/images/{index}")
    public Map<String, Object> clipImage(@RequestParam String username, @PathVariable Long clipId,
                                         @PathVariable int index) {
        return agentDocumentService.loadClipImage(username, clipId, index);
    }

    @PostMapping("/notes/{noteId}/images")
    public Map<String, Object> insertNoteImage(@RequestParam String username, @PathVariable Long noteId,
                                               @RequestParam(required = false) String conversationId,
                                               @RequestBody AgentInsertImageRequest request) {
        return agentImageEditService.insertImage(username, noteId, request.source(), request.caption(),
                request.anchorText(), conversationId);
    }

    @DeleteMapping("/notes/{noteId}/images/{index}")
    public Map<String, Object> deleteNoteImage(@RequestParam String username, @PathVariable Long noteId,
                                               @PathVariable int index,
                                               @RequestParam(required = false) String conversationId) {
        return agentImageEditService.deleteImage(username, noteId, index, conversationId);
    }

    @PatchMapping("/notes/{noteId}/images/{index}")
    public Map<String, Object> setNoteImageCaption(@RequestParam String username, @PathVariable Long noteId,
                                                   @PathVariable int index,
                                                   @RequestParam(required = false) String conversationId,
                                                   @RequestBody Map<String, String> body) {
        return agentImageEditService.setImageCaption(username, noteId, index, body.get("caption"), conversationId);
    }

    @GetMapping("/notes/{noteId}/blocks")
    public Map<String, Object> noteBlocks(@RequestParam String username, @PathVariable Long noteId) {
        return agentNoteEditService.readBlocks(username, noteId);
    }

    @PostMapping("/notes/{noteId}/edits")
    public Map<String, Object> editNote(@RequestParam String username, @PathVariable Long noteId,
                                        @RequestParam(required = false) String conversationId,
                                        @RequestParam(defaultValue = "true") boolean snapshot,
                                        @RequestBody Map<String, List<Map<String, Object>>> body) {
        return agentNoteEditService.applyAsAgent(username, noteId, conversationId, body.get("operations"), snapshot);
    }
}
