/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
    List<AiChatMessage> findByConversationOrderByCreatedAtAsc(AiChatConversation conversation);

    // fallbackTitle()专用：只需要第一条消息的内容做截断展示，findFirst让Spring Data在SQL层
    // 加LIMIT 1，避免为了读一行就把整个会话的消息（含citationsJson/attachmentsJson等TEXT列）
    // 全部查出来——这个方法在GET /v1/chat/conversations里对每个无标题会话都会跑一次。
    Optional<AiChatMessage> findFirstByConversationOrderByCreatedAtAsc(AiChatConversation conversation);
}
