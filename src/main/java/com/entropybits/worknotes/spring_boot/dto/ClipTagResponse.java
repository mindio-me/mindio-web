/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.ClipTagLink;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClipTagResponse {

    private Long id;
    private String name;
    private Boolean manuallyAdded;
    private Boolean aiSuggested;

    public static ClipTagResponse fromEntity(ClipTagLink link) {
        return ClipTagResponse.builder()
                .id(link.getTag().getId())
                .name(link.getTag().getName())
                .manuallyAdded(link.getManuallyAdded())
                .aiSuggested(link.getAiSuggested())
                .build();
    }
}
