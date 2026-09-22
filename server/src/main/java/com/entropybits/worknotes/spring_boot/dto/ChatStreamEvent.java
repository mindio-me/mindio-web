/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 推给前端的一条SSE事件。字段是否有值取决于 type：
 * - user_message: id, content, attachments, createdAt
 * - text_delta: text
 * - tool_call: query
 * - done: id, content, citations, attachments, createdAt
 * - error: content（错误提示文字）
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
        String query
) {
    public static ChatStreamEvent userMessage(ChatMessageResponse m) {
        return new ChatStreamEvent("user_message", m.getId(), m.getContent(), null, m.getAttachments(), m.getCreatedAt(), null, null);
    }

    public static ChatStreamEvent textDelta(String text) {
        return new ChatStreamEvent("text_delta", null, null, null, null, null, text, null);
    }

    public static ChatStreamEvent toolCall(String query) {
        return new ChatStreamEvent("tool_call", null, null, null, null, null, null, query);
    }

    public static ChatStreamEvent done(ChatMessageResponse m) {
        return new ChatStreamEvent("done", m.getId(), m.getContent(), m.getCitations(), m.getAttachments(), m.getCreatedAt(), null, null);
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent("error", null, message, null, null, null, null, null);
    }
}
