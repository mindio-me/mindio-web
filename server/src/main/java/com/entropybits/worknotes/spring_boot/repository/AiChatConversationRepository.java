/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface AiChatConversationRepository extends JpaRepository<AiChatConversation, Long> {
    List<AiChatConversation> findByOwnerOrderByLastMessageAtDesc(User owner);

    // touchConversation()用的定向update：只碰lastMessageAt列，不会把调用方手里可能已经过时
    // 的整个entity（尤其是title字段）存盘——避免和异步标题生成线程的save()发生"谁后写谁赢，
    // 但赢的可能是过时的null"竞态。风格对齐ImportJobRepository#incrementCheckedCount。
    // clearAutomatically=true：bulk update默认不会让一级缓存里已经加载过的同一个conversation
    // 实体失效，同一事务/persistence context里后续再findById会读到update前的旧值（比如刚好
    // 是这次touchConversation之前加载的那个实例）。清掉缓存强制下次读取回源，不会把这次的
    // 定向update白做。
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AiChatConversation c SET c.lastMessageAt = :lastMessageAt WHERE c.id = :id")
    void updateLastMessageAt(@Param("id") Long id, @Param("lastMessageAt") Instant lastMessageAt);
}
