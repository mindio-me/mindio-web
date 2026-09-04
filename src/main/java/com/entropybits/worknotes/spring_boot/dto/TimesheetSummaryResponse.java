/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetSummaryResponse {

    private String yearMonth;
    private Integer totalMinutes;
    private List<ProjectBreakdown> byProject;

    @Data
    @AllArgsConstructor
    public static class ProjectBreakdown {
        private Long projectId;
        private String name;
        private Integer minutes;
    }
}
