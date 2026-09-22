/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {

    List<TimesheetEntry> findByEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            LocalDate start, LocalDate end);
}
