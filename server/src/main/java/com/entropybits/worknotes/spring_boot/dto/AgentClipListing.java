/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import java.time.LocalDateTime;
import java.util.List;

/** agent 的 list_clips 结构化筛选结果：total 是符合条件的总数，items 只是按 limit 截取的前若干条。 */
public record AgentClipListing(long total, List<Item> items) {

    public record Item(Long id, String title, String url, List<String> tags, LocalDateTime savedAt) {}
}
