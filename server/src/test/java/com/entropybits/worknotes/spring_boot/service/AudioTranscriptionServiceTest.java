/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioTranscriptionServiceTest {

    @Mock AudioTranscriptionRepository repository;
    @Mock MediaBlockService mediaBlockService;
    @Mock AiSettingsService aiSettingsService;
    @Mock NoteRepository noteRepository;
    AudioTranscriptionService service;
    final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AudioTranscriptionService(repository, mediaBlockService, aiSettingsService, noteRepository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiSettingsService.autoTranscribeEnabled()).thenReturn(true);
        when(repository.findByNoteIdAndBlockId(any(), any())).thenReturn(Optional.empty());
    }

    private void block(String type, int duration) throws Exception {
        when(mediaBlockService.getBlockData(1L, "b")).thenReturn(new MediaBlockService.BlockData(
                type, om.readTree("{\"url\":\"/uploads/a.webm\",\"duration\":" + duration + "}")));
    }

    private AudioTranscription existing(AudioTranscription.Status status, String utterances) {
        AudioTranscription t = AudioTranscription.builder().noteId(1L).blockId("b").status(status)
                .triggerType(AudioTranscription.TriggerType.AUTO).stage(AudioTranscription.Stage.FULL)
                .utterances(utterances).polished("[]").summary("{}").attempts(2)
                .errorKind(AudioTranscription.ErrorKind.SUMMARY_FAILED).build();
        when(repository.findByNoteIdAndBlockId(1L, "b")).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void auto_registersPendingForLongRecording() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = service.trigger(1L, "b", AudioTranscription.TriggerType.AUTO, AudioTranscription.Stage.FULL).orElseThrow();
        assertThat(t.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(t.getTriggerType()).isEqualTo(AudioTranscription.TriggerType.AUTO);
    }

    @Test
    void auto_skipsWhenDisabled() throws Exception {
        block("audioRecord", 60);
        when(aiSettingsService.autoTranscribeEnabled()).thenReturn(false);
        assertThat(service.trigger(1L, "b", AudioTranscription.TriggerType.AUTO, AudioTranscription.Stage.FULL)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void auto_skipsUploadedAudioShortRecordingAndExistingRecord() throws Exception {
        block("audio", 60);
        assertThat(service.trigger(1L, "b", AudioTranscription.TriggerType.AUTO, AudioTranscription.Stage.FULL)).isEmpty();
        block("audioRecord", 9);
        assertThat(service.trigger(1L, "b", AudioTranscription.TriggerType.AUTO, AudioTranscription.Stage.FULL)).isEmpty();
        block("audioRecord", 60);
        AudioTranscription done = existing(AudioTranscription.Status.DONE, "[]");
        assertThat(service.trigger(1L, "b", AudioTranscription.TriggerType.AUTO, AudioTranscription.Stage.FULL)).containsSame(done);
        assertThat(done.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
    }

    @Test
    void anyTrigger_returnsInFlightRecordWithoutResetting() throws Exception {
        block("audioRecord", 60);
        AudioTranscription running = existing(AudioTranscription.Status.PROCESSING, null);
        assertThat(service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL)).containsSame(running);
        verify(repository, never()).save(any());
    }

    @Test
    void manualFull_resetsDoneRecord() throws Exception {
        block("audio", 5);
        AudioTranscription t = existing(AudioTranscription.Status.DONE, "[{\"start\":0}]");
        service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL);
        assertThat(t.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(t.getTriggerType()).isEqualTo(AudioTranscription.TriggerType.MANUAL);
        assertThat(t.getAttempts()).isZero();
        assertThat(t.getUtterances()).isNull();
        assertThat(t.getSummary()).isNull();
        assertThat(t.getErrorKind()).isNull();
    }

    @Test
    void manualSummary_keepsUtterancesAndRequiresThem() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = existing(AudioTranscription.Status.DONE, "[{\"start\":0}]");
        service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.SUMMARY);
        assertThat(t.getStage()).isEqualTo(AudioTranscription.Stage.SUMMARY);
        assertThat(t.getUtterances()).isEqualTo("[{\"start\":0}]");
        assertThat(t.getPolished()).isNull();

        existing(AudioTranscription.Status.FAILED, null);
        assertThatThrownBy(() -> service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.SUMMARY))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void trigger_rejectsNonAudioBlock() throws Exception {
        block("paragraph", 0);
        assertThatThrownBy(() -> service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL))
                .isInstanceOf(BadRequestException.class);
    }
}
