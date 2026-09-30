/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ai_chat_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    private User owner;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastMessageAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastMessageAt == null) lastMessageAt = now;
    }
}
