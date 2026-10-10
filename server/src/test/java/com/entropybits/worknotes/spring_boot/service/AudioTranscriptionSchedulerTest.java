/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioTranscriptionSchedulerTest {

    @Mock AudioTranscriptionRepository repository;
    @Mock AudioTranscriptionJob job;

    private AudioTranscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AudioTranscriptionScheduler(repository, job);
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recoverOnStartup_resetsProcessingToPending() {
        AudioTranscription stuck = AudioTranscription.builder().status(AudioTranscription.Status.PROCESSING).build();
        when(repository.findByStatus(AudioTranscription.Status.PROCESSING)).thenReturn(List.of(stuck));
        scheduler.recoverOnStartup();
        assertThat(stuck.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        verify(repository).saveAll(List.of(stuck));
    }

    @Test
    void dispatchPending_doesNotCallJobBeforeRecovery() {
        scheduler.dispatchPending();
        verify(job, never()).processPendingBatch();
    }

    @Test
    void dispatchPending_delegatesToJobAfterRecovery() {
        when(repository.findByStatus(AudioTranscription.Status.PROCESSING)).thenReturn(List.of());
        scheduler.recoverOnStartup();
        scheduler.dispatchPending();
        verify(job).processPendingBatch();
    }
}
