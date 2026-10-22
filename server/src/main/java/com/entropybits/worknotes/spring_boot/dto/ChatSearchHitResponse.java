/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** 聊天记录搜索的一条命中：哪个会话的哪条消息，附命中处前后的一小段文字 */
@Data
@Builder
public class ChatSearchHitResponse {
    private Long conversationId;
    private String conversationTitle;
    private Long messageId;
    private String role;
    private String snippet;
    private Instant createdAt;
}
