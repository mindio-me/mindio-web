/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.SendChatMessageRequest;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.service.GlobalChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class GlobalChatController {

    private final GlobalChatService chatService;

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageResponse>> listMessages(
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.listHistory(user.getUsername(), limit));
    }

    @GetMapping("/notes/{noteId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessagesForNote(
            @PathVariable Long noteId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.getMessagesForNote(user.getUsername(), noteId));
    }

    @PostMapping(value = "/messages", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal UserDetails user) {
        boolean noContent = request.getContent() == null || request.getContent().isBlank();
        boolean noAttachments = request.getAttachments() == null || request.getAttachments().isEmpty();
        if (noContent && noAttachments) {
            throw new BadRequestException("消息内容和附件不能同时为空");
        }
        SseEmitter emitter = new SseEmitter(0L);
        String username = user.getUsername();
        new Thread(() -> chatService.sendMessageStream(
                username, request.getContent(), request.getCurrentNoteId(), request.getAttachments(), emitter)).start();
        return emitter;
    }
}
