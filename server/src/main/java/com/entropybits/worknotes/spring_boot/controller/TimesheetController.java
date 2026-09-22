/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryRequest;
import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryResponse;
import com.entropybits.worknotes.spring_boot.dto.TimesheetSummaryResponse;
import com.entropybits.worknotes.spring_boot.service.TimesheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/timesheet")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;

    @GetMapping("/entries")
    public ResponseEntity<List<TimesheetEntryResponse>> getEntries(
            @RequestParam String yearMonth) {
        return ResponseEntity.ok(timesheetService.getEntries(yearMonth));
    }

    @PostMapping("/entries")
    public ResponseEntity<TimesheetEntryResponse> createEntry(
            @Valid @RequestBody TimesheetEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timesheetService.createEntry(request));
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<TimesheetEntryResponse> updateEntry(
            @PathVariable Long id,
            @Valid @RequestBody TimesheetEntryRequest request) {
        return ResponseEntity.ok(timesheetService.updateEntry(id, request));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        timesheetService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<TimesheetSummaryResponse> getSummary(
            @RequestParam String yearMonth) {
        return ResponseEntity.ok(timesheetService.getSummary(yearMonth));
    }
}
