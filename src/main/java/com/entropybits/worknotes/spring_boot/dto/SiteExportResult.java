/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SiteExportResult {
    private boolean success;
    private String targetPath;
    private int exportedNoteCount;
    private int exportedProjectCount;
    private int skippedImageCount;
    private List<String> warnings;
}
