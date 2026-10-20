/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 笔记分享卡片；没有卡片时卡片字段全为 null。aiAvailability: available | disabled | not_configured */
public record ShareCardResponse(
        Long noteId,
        boolean isPublic,
        String source,
        String imageUrl,
        String backgroundUrl,
        Integer gradient,
        String aiPrompt,
        String title,
        Boolean overlayTitle,
        String aiAvailability) {
}
