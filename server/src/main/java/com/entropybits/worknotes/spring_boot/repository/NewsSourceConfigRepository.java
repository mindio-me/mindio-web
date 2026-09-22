/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.NewsSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsSourceConfigRepository extends JpaRepository<NewsSourceConfig, Long> {

    Optional<NewsSourceConfig> findBySourceKey(String sourceKey);

    List<NewsSourceConfig> findAllByOrderBySortOrderAsc();

    List<NewsSourceConfig> findByEnabledTrueOrderBySortOrderAsc();
}
