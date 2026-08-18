/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ClipSearchMessage;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClipSearchMessageRepository extends JpaRepository<ClipSearchMessage, Long> {
    List<ClipSearchMessage> findTop50ByOwnerOrderByCreatedAtDesc(User owner);
}
