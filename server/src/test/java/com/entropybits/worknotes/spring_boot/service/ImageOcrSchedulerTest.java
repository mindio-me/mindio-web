/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageOcrSchedulerTest {

    @Mock LocalFileExtractionRepository extractionRepository;
    @Mock ImageOcrJob imageOcrJob;

    private ImageOcrScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ImageOcrScheduler(extractionRepository, imageOcrJob);
        when(extractionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatchPending_delegatesToImageOcrJob() {
        scheduler.dispatchPending();

        verify(imageOcrJob).processPendingBatch();
    }

    @Test
    void reconcile_resetsFailedRecordsUnderRetryLimitBackToPending() {
        LocalFileExtraction failed = LocalFileExtraction.builder()
                .id(1L).status(LocalFileExtraction.Status.FAILED).retryCount(1).build();
        when(extractionRepository.findByStatusAndRetryCountLessThan(
                eq(LocalFileExtraction.Status.FAILED), anyInt()))
                .thenReturn(List.of(failed));
        when(extractionRepository.findByStatusAndUpdatedAtBefore(eq(LocalFileExtraction.Status.PROCESSING), any()))
                .thenReturn(List.of());

        scheduler.reconcile();

        assertThat(failed.getStatus()).isEqualTo(LocalFileExtraction.Status.PENDING);
    }

    @Test
    void reconcile_resetsStuckProcessingRecordsBackToPending() {
        LocalFileExtraction stuck = LocalFileExtraction.builder()
                .id(2L).status(LocalFileExtraction.Status.PROCESSING).retryCount(0).build();
        when(extractionRepository.findByStatusAndRetryCountLessThan(any(), anyInt())).thenReturn(List.of());
        when(extractionRepository.findByStatusAndUpdatedAtBefore(eq(LocalFileExtraction.Status.PROCESSING), any()))
                .thenReturn(List.of(stuck));

        scheduler.reconcile();

        assertThat(stuck.getStatus()).isEqualTo(LocalFileExtraction.Status.PENDING);
    }
}
