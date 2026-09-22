/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 独立Python/LangGraph Agent服务的checkpointer持久化后端。stateBlob 对Java来说是不透明数据——
 * 结构和演变逻辑完全由agent自己定义，这里只负责按 conversationId 存取。conversationId 目前
 * 等同于 username（延续"单一连续会话"的既有决定），归属信息已经承载在这个字符串里，不需要
 * 关联 User 外键。
 */
@Entity
@Table(name = "agent_conversation_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private String conversationId;

    @Column(name = "state_blob", columnDefinition = "LONGTEXT")
    private String stateBlob;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }
}
