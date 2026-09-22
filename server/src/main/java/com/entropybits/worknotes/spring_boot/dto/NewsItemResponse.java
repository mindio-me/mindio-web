/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.NewsItem;

import java.time.LocalDate;

public record NewsItemResponse(
        Long id,
        String sourceKey,
        Integer rankOrder,
        String title,
        String url,
        LocalDate fetchDate
) {
    public static NewsItemResponse from(NewsItem item) {
        return new NewsItemResponse(
                item.getId(),
                item.getSourceKey(),
                item.getRankOrder(),
                item.getTitle(),
                item.getUrl(),
                item.getFetchDate()
        );
    }
}
