/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatConversationRepository extends JpaRepository<AiChatConversation, Long> {
    List<AiChatConversation> findByOwnerOrderByLastMessageAtDesc(User owner);
}
