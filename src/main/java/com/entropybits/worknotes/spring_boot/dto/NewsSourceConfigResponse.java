/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.NewsSourceConfig;

import java.time.LocalDateTime;

public record NewsSourceConfigResponse(
        Long id,
        String sourceKey,
        String nameZh,
        String nameEn,
        String category,
        Boolean enabled,
        Integer sortOrder,
        LocalDateTime lastFetchedAt,
        String lastFetchStatus,
        String lastFetchError
) {
    public static NewsSourceConfigResponse from(NewsSourceConfig config) {
        return new NewsSourceConfigResponse(
                config.getId(),
                config.getSourceKey(),
                config.getNameZh(),
                config.getNameEn(),
                config.getCategory(),
                config.getEnabled(),
                config.getSortOrder(),
                config.getLastFetchedAt(),
                config.getLastFetchStatus(),
                config.getLastFetchError()
        );
    }
}
