/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ClipSearchMessageResponse {
    private Long id;
    private String role;
    private String content;
    private List<ClipSearchResultItem> results;
    private Instant createdAt;
}
