/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.TimesheetEntry;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TimesheetEntryResponse {

    private Long id;
    private Long projectId;
    private String projectName;
    private String label;
    private LocalDate entryDate;
    private Integer durationMinutes;
    private LocalTime startTime;
    private LocalTime endTime;
    private String note;
    private LocalDateTime createdAt;

    public static TimesheetEntryResponse fromEntity(TimesheetEntry e) {
        TimesheetEntryResponse r = new TimesheetEntryResponse();
        r.setId(e.getId());
        r.setProjectId(e.getProject() != null ? e.getProject().getId() : null);
        r.setProjectName(e.getProject() != null ? e.getProject().getName() : null);
        r.setLabel(e.getLabel());
        r.setEntryDate(e.getEntryDate());
        r.setDurationMinutes(e.getDurationMinutes());
        r.setStartTime(e.getStartTime());
        r.setEndTime(e.getEndTime());
        r.setNote(e.getNote());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }
}
