/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 保存笔记分享卡片；publish=true 时同一请求里顺带把笔记设为公开（"确认公开"） */
public record ShareCardSaveRequest(
        String source,
        String imageUrl,
        String backgroundUrl,
        Integer gradient,
        String aiPrompt,
        String title,
        Boolean overlayTitle,
        boolean publish) {
}
