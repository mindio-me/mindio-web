/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClipSearchMessageRequest {

    @NotBlank
    private String content;
}
