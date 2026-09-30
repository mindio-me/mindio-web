/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AgentConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentConversationStateRepository extends JpaRepository<AgentConversationState, Long> {
    Optional<AgentConversationState> findByConversationId(String conversationId);

    // GlobalChatService#deleteConversation专用：LangGraph checkpointer的序列化状态是独立
    // 一张表、按conversationId字符串（=AiChatConversation.id的字符串形式）单独存的，不受
    // AiChatConversation删除时的外键级联影响，必须在这里显式清理，否则用户删会话后这张表
    // 里还留着内容副本。
    void deleteByConversationId(String conversationId);
}
