/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatResumeRequest {
    @NotBlank
    private String proposalId;
    @NotBlank
    private String decision;
}
