/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
    List<AiChatMessage> findByConversationOrderByCreatedAtAsc(AiChatConversation conversation);

    // fallbackTitle()专用：只需要第一条消息的内容做截断展示，findFirst让Spring Data在SQL层
    // 加LIMIT 1，避免为了读一行就把整个会话的消息（含citationsJson/attachmentsJson等TEXT列）
    // 全部查出来——这个方法在GET /v1/chat/conversations里对每个无标题会话都会跑一次。
    Optional<AiChatMessage> findFirstByConversationOrderByCreatedAtAsc(AiChatConversation conversation);

    Optional<AiChatMessage> findFirstByConversationAndRoleOrderByIdDesc(AiChatConversation conversation,
                                                                        AiChatMessage.Role role);

    // 聊天记录搜索：只搜该用户自己的会话，忽略大小写的子串匹配，新的在前。
    // pattern 由调用方把 ! % _ 用 ! 转义好并在两头加上 %；转义符不用反斜杠，MySQL 字符串字面量里
    // 反斜杠本身就是转义符，'\' 会把后面的引号吃掉。
    @Query("SELECT m FROM AiChatMessage m JOIN FETCH m.conversation c WHERE c.owner = :owner"
            + " AND LOWER(m.content) LIKE LOWER(:pattern) ESCAPE '!' ORDER BY m.createdAt DESC, m.id DESC")
    List<AiChatMessage> searchByOwner(@Param("owner") User owner, @Param("pattern") String pattern, Pageable pageable);
}
