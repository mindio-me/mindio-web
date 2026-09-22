/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryRequest;
import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryResponse;
import com.entropybits.worknotes.spring_boot.entity.TimesheetEntry;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.TimesheetEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimesheetServiceTest {

    @Mock
    private TimesheetEntryRepository repository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private TimesheetService service;

    @Test
    void createEntry_whenNoProjectAndNoLabel_throwsBadRequest() {
        TimesheetEntryRequest req = new TimesheetEntryRequest();
        req.setEntryDate(LocalDate.now());
        req.setDurationMinutes(60);
        // projectId = null, label = null

        assertThrows(BadRequestException.class, () -> service.createEntry(req));
    }

    @Test
    void createEntry_whenGrossTimeLessThanNet_throwsBadRequest() {
        TimesheetEntryRequest req = new TimesheetEntryRequest();
        req.setEntryDate(LocalDate.now());
        req.setDurationMinutes(90);           // 1h30m net
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(11, 0));  // only 1h gross — invalid
        req.setLabel("Client A");

        assertThrows(BadRequestException.class, () -> service.createEntry(req));
    }

    @Test
    void createEntry_whenGrossTimeEqualsNet_succeeds() {
        TimesheetEntryRequest req = new TimesheetEntryRequest();
        req.setEntryDate(LocalDate.now());
        req.setDurationMinutes(60);
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(11, 0));  // exactly 1h — valid
        req.setLabel("Client A");

        TimesheetEntry saved = TimesheetEntry.builder()
                .id(1L).label("Client A").entryDate(LocalDate.now())
                .durationMinutes(60).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .build();
        when(repository.save(any())).thenReturn(saved);

        TimesheetEntryResponse result = service.createEntry(req);
        assertNotNull(result);
        assertEquals(60, result.getDurationMinutes());
    }

    @Test
    void createEntry_whenOnlyStartTimeProvided_succeeds() {
        TimesheetEntryRequest req = new TimesheetEntryRequest();
        req.setEntryDate(LocalDate.now());
        req.setDurationMinutes(90);
        req.setStartTime(LocalTime.of(10, 0));
        // endTime = null — no gross validation
        req.setLabel("Client A");

        TimesheetEntry saved = TimesheetEntry.builder()
                .id(2L).label("Client A").entryDate(LocalDate.now()).durationMinutes(90).build();
        when(repository.save(any())).thenReturn(saved);

        assertDoesNotThrow(() -> service.createEntry(req));
    }
}
