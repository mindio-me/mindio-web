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
import com.entropybits.worknotes.spring_boot.dto.NoteResponse;
import com.entropybits.worknotes.spring_boot.service.AgentDocumentService;
import com.entropybits.worknotes.spring_boot.service.AgentImageEditService;
import com.entropybits.worknotes.spring_boot.service.AgentNoteEditService;
import com.entropybits.worknotes.spring_boot.service.AgentTagService;
import com.entropybits.worknotes.spring_boot.service.NoteService;
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
    private final NoteService noteService;
    private final AgentTagService agentTagService;

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

    /** AI 助手新建笔记（系统默认值），之后用 edits 往里写内容 */
    @PostMapping("/notes")
    public Map<String, Object> createNote(@RequestParam String username, @RequestBody Map<String, String> body) {
        NoteResponse note = noteService.createBlankEditorjsNote(body.get("title"), username);
        return Map.of("noteId", note.getId(), "title", note.getTitle());
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

    // ===== 标签（AI 助手的 list_tags / create_tag / delete_tag / tag_note / tag_clip）=====

    @GetMapping("/tags")
    public List<Map<String, Object>> listTags(@RequestParam String username) {
        return agentTagService.listTags(username);
    }

    @PostMapping("/tags")
    public Map<String, Object> createTag(@RequestParam String username, @RequestBody Map<String, String> body) {
        return agentTagService.createTag(username, body.get("name"), body.get("scope"));
    }

    @DeleteMapping("/tags")
    public Map<String, Object> deleteTag(@RequestParam String username, @RequestParam String name) {
        return agentTagService.deleteTag(username, name);
    }

    @PostMapping("/notes/{noteId}/tags")
    public Map<String, Object> updateNoteTags(@RequestParam String username, @PathVariable Long noteId,
                                              @RequestBody Map<String, List<String>> body) {
        return agentTagService.updateNoteTags(username, noteId, body.get("add"), body.get("remove"));
    }

    @PostMapping("/clips/{clipId}/tags")
    public Map<String, Object> updateClipTags(@RequestParam String username, @PathVariable Long clipId,
                                              @RequestBody Map<String, List<String>> body) {
        return agentTagService.updateClipTags(username, clipId, body.get("add"), body.get("remove"));
    }
}
