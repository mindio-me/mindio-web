/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TimesheetEntryRequest {

    private Long projectId;

    @Size(max = 200)
    private String label;

    @NotNull(message = "日期不能为空")
    private LocalDate entryDate;

    @NotNull(message = "净时长不能为空")
    @Min(value = 1, message = "净时长必须大于0")
    private Integer durationMinutes;

    private LocalTime startTime;

    private LocalTime endTime;

    private String note;
}
