/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryRequest;
import com.entropybits.worknotes.spring_boot.dto.TimesheetEntryResponse;
import com.entropybits.worknotes.spring_boot.dto.TimesheetSummaryResponse;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.TimesheetEntry;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.TimesheetEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetEntryRepository repository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<TimesheetEntryResponse> getEntries(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        return repository
                .findByEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .map(TimesheetEntryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public TimesheetEntryResponse createEntry(TimesheetEntryRequest request) {
        validate(request);
        Project project = resolveProject(request.getProjectId());
        TimesheetEntry entry = TimesheetEntry.builder()
                .project(project)
                .label(request.getLabel())
                .entryDate(request.getEntryDate())
                .durationMinutes(request.getDurationMinutes())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .note(request.getNote())
                .build();
        return TimesheetEntryResponse.fromEntity(repository.save(entry));
    }

    @Transactional
    public TimesheetEntryResponse updateEntry(Long id, TimesheetEntryRequest request) {
        TimesheetEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工时记录不存在，ID: " + id));
        validate(request);
        Project project = resolveProject(request.getProjectId());
        entry.setProject(project);
        entry.setLabel(request.getLabel());
        entry.setEntryDate(request.getEntryDate());
        entry.setDurationMinutes(request.getDurationMinutes());
        entry.setStartTime(request.getStartTime());
        entry.setEndTime(request.getEndTime());
        entry.setNote(request.getNote());
        return TimesheetEntryResponse.fromEntity(repository.save(entry));
    }

    @Transactional
    public void deleteEntry(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("工时记录不存在，ID: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TimesheetSummaryResponse getSummary(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<TimesheetEntry> entries = repository
                .findByEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(ym.atDay(1), ym.atEndOfMonth());

        int totalMinutes = entries.stream().mapToInt(TimesheetEntry::getDurationMinutes).sum();

        Map<String, Integer> minutesByKey = new LinkedHashMap<>();
        Map<String, String> nameByKey = new LinkedHashMap<>();
        Map<String, Long> projectIdByKey = new LinkedHashMap<>();

        for (TimesheetEntry e : entries) {
            String key;
            String displayName;
            Long projectId;
            if (e.getProject() != null) {
                key = "P:" + e.getProject().getId();
                displayName = e.getProject().getName();
                projectId = e.getProject().getId();
            } else {
                key = "L:" + e.getLabel();
                displayName = e.getLabel();
                projectId = null;
            }
            minutesByKey.merge(key, e.getDurationMinutes(), Integer::sum);
            nameByKey.putIfAbsent(key, displayName);
            projectIdByKey.putIfAbsent(key, projectId);
        }

        List<TimesheetSummaryResponse.ProjectBreakdown> breakdown = minutesByKey.entrySet().stream()
                .map(me -> new TimesheetSummaryResponse.ProjectBreakdown(
                        projectIdByKey.get(me.getKey()),
                        nameByKey.get(me.getKey()),
                        me.getValue()))
                .sorted(Comparator.comparingInt(TimesheetSummaryResponse.ProjectBreakdown::getMinutes).reversed())
                .collect(Collectors.toList());

        return TimesheetSummaryResponse.builder()
                .yearMonth(yearMonth)
                .totalMinutes(totalMinutes)
                .byProject(breakdown)
                .build();
    }

    private void validate(TimesheetEntryRequest request) {
        boolean hasProject = request.getProjectId() != null;
        boolean hasLabel = request.getLabel() != null && !request.getLabel().isBlank();
        if (!hasProject && !hasLabel) {
            throw new BadRequestException("请关联项目或填写描述标签");
        }
        if (request.getStartTime() != null && request.getEndTime() != null) {
            long grossMinutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            if (grossMinutes < request.getDurationMinutes()) {
                throw new BadRequestException("开始到结束时间跨度不得小于净时长");
            }
        }
    }

    private Project resolveProject(Long projectId) {
        if (projectId == null) return null;
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在，ID: " + projectId));
    }
}
