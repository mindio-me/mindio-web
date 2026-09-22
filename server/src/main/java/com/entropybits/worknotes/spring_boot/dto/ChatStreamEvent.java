/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 推给前端的一条SSE事件。字段是否有值取决于 type：
 * - user_message: id, content, attachments, createdAt
 * - text_delta: text
 * - tool_call: query
 * - done: id, content, citations, attachments, createdAt
 * - error: content（错误提示文字）
 * - confirm_request: proposalId, blockType, noteId, preview
 * - block_updated: noteId, blockId, blockType, items（专题块的items数组型更新）
 * - media_block_updated: noteId, blockId, blockType, data（图片/音频block的标量字段更新）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        String type,
        Long id,
        String content,
        List<ChatCitation> citations,
        List<ChatAttachmentRef> attachments,
        Instant createdAt,
        String text,
        String query,
        String proposalId,
        String blockType,
        Long noteId,
        Map<String, Object> preview,
        String blockId,
        List<Map<String, Object>> items,
        Map<String, Object> data
) {
    public static ChatStreamEvent userMessage(ChatMessageResponse m) {
        return new ChatStreamEvent("user_message", m.getId(), m.getContent(), null, m.getAttachments(), m.getCreatedAt(), null, null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent textDelta(String text) {
        return new ChatStreamEvent("text_delta", null, null, null, null, null, text, null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent toolCall(String query) {
        return new ChatStreamEvent("tool_call", null, null, null, null, null, null, query, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent done(ChatMessageResponse m) {
        return new ChatStreamEvent("done", m.getId(), m.getContent(), m.getCitations(), m.getAttachments(), m.getCreatedAt(), null, null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, message, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent confirmRequest(String proposalId, String blockType, Long noteId, Map<String, Object> preview) {
        return new ChatStreamEvent("confirm_request", null, null, null, null, null, null, null, proposalId, blockType, noteId, preview, null, null, null);
    }

    public static ChatStreamEvent blockUpdated(Long noteId, String blockId, String blockType, List<Map<String, Object>> items) {
        return new ChatStreamEvent("block_updated", null, null, null, null, null, null, null, null, blockType, noteId, null, blockId, items, null);
    }

    public static ChatStreamEvent mediaBlockUpdated(Long noteId, String blockId, String blockType, Map<String, Object> data) {
        return new ChatStreamEvent("media_block_updated", null, null, null, null, null, null, null, null, blockType, noteId, null, blockId, null, data);
    }
}
