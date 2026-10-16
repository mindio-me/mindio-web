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
    @Mock ContentIndexingService indexing;
    AudioTranscriptionService service;
    final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AudioTranscriptionService(repository, mediaBlockService, aiSettingsService, noteRepository, indexing, om);
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
    void triggerFromAgent_returnsCleanDoneRecordUnchanged() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = existing(AudioTranscription.Status.DONE, "[{\"start\":0}]");
        t.setErrorKind(null);
        assertThat(service.triggerFromAgent(1L, "b")).isSameAs(t);
        assertThat(t.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(t.getUtterances()).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    void triggerFromAgent_retriggersIncompleteDoneAndNewRecords() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = existing(AudioTranscription.Status.DONE, "[{\"start\":0}]");
        assertThat(service.triggerFromAgent(1L, "b")).isSameAs(t);
        assertThat(t.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(t.getTriggerType()).isEqualTo(AudioTranscription.TriggerType.MANUAL);

        when(repository.findByNoteIdAndBlockId(1L, "b")).thenReturn(Optional.empty());
        assertThat(service.triggerFromAgent(1L, "b").getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
    }

    @Test
    void trigger_rejectsNonAudioBlock() throws Exception {
        block("paragraph", 0);
        assertThatThrownBy(() -> service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL))
                .isInstanceOf(BadRequestException.class);
    }

    private static final String UTTS = "[{\"start\":0,\"end\":1000,\"text\":\"甲\",\"speaker\":1},"
            + "{\"start\":1200,\"end\":2000,\"text\":\"乙\",\"speaker\":2}]";

    private AudioTranscription done(String utterances) {
        AudioTranscription t = existing(AudioTranscription.Status.DONE, utterances);
        t.setErrorKind(null);
        return t;
    }

    @Test
    void saveUtterances_replacesOnlyTextThenQueuesSummaryAndReindexes() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done(UTTS);

        AudioTranscription saved = service.saveUtterances(1L, "b",
                java.util.List.of(new AudioTranscriptionService.UtteranceEdit(1, " 乙改 ")));

        var arr = om.readTree(saved.getUtterances());
        assertThat(arr.get(0).get("text").asText()).isEqualTo("甲");
        assertThat(arr.get(1).get("text").asText()).isEqualTo("乙改");
        assertThat(arr.get(1).get("start").asLong()).isEqualTo(1200);
        assertThat(arr.get(1).get("speaker").asInt()).isEqualTo(2);
        assertThat(saved.getEditedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(saved.getStage()).isEqualTo(AudioTranscription.Stage.SUMMARY);
        verify(indexing).reindexNote(1L);
    }

    @Test
    void saveUtterances_rejectsWhenNotDone() throws Exception {
        block("audioRecord", 60);
        existing(AudioTranscription.Status.PROCESSING, UTTS);
        assertThatThrownBy(() -> service.saveUtterances(1L, "b",
                java.util.List.of(new AudioTranscriptionService.UtteranceEdit(0, "x"))))
                .isInstanceOf(com.entropybits.worknotes.spring_boot.exception.ConflictException.class);
    }

    @Test
    void saveUtterances_rejectsFailedSegmentPlaceholder() throws Exception {
        block("audioRecord", 60);
        done("[{\"start\":0,\"end\":0,\"text\":\"[第 1 段转写失败]\"},{\"start\":5,\"end\":9,\"text\":\"乙\"}]");
        assertThatThrownBy(() -> service.saveUtterances(1L, "b",
                java.util.List.of(new AudioTranscriptionService.UtteranceEdit(1, "x"))))
                .isInstanceOf(com.entropybits.worknotes.spring_boot.exception.ConflictException.class);
    }

    @Test
    void saveUtterances_rejectsOutOfRangeOrDuplicateIndex() throws Exception {
        block("audioRecord", 60);
        done(UTTS);
        assertThatThrownBy(() -> service.saveUtterances(1L, "b",
                java.util.List.of(new AudioTranscriptionService.UtteranceEdit(2, "x"))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveUtterances(1L, "b", java.util.List.of(
                new AudioTranscriptionService.UtteranceEdit(0, "x"), new AudioTranscriptionService.UtteranceEdit(0, "y"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void patch_renameDoesNotRequeue() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done(UTTS);
        AudioTranscription saved = service.patch(1L, "b", java.util.Map.of("1", "张总"), null);
        assertThat(om.readTree(saved.getSpeakerNames()).get("1").asText()).isEqualTo("张总");
        assertThat(saved.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
    }

    @Test
    void patch_sceneRequeuesSummaryAsUserScene() throws Exception {
        block("audioRecord", 60);
        done(UTTS);
        AudioTranscription saved = service.patch(1L, "b", null, AudioTranscription.Scene.LECTURE);
        assertThat(saved.getScene()).isEqualTo(AudioTranscription.Scene.LECTURE);
        assertThat(saved.getSceneSource()).isEqualTo(AudioTranscription.SceneSource.USER);
        assertThat(saved.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(saved.getStage()).isEqualTo(AudioTranscription.Stage.SUMMARY);
    }

    @Test
    void patch_sceneWorksOnPhase1RecordWithoutScene() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done("[{\"start\":0,\"end\":1000,\"text\":\"甲\"}]");
        t.setScene(null);
        AudioTranscription saved = service.patch(1L, "b", null, AudioTranscription.Scene.MEETING);
        assertThat(saved.getScene()).isEqualTo(AudioTranscription.Scene.MEETING);
        assertThat(saved.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
    }

    @Test
    void patch_sceneRejectedWhileProcessing() throws Exception {
        block("audioRecord", 60);
        existing(AudioTranscription.Status.PROCESSING, UTTS);
        assertThatThrownBy(() -> service.patch(1L, "b", null, AudioTranscription.Scene.MEETING))
                .isInstanceOf(com.entropybits.worknotes.spring_boot.exception.ConflictException.class);
    }

    @Test
    void manualFull_clearsEditsAndSpeakerNamesAndAutoScene() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done(UTTS);
        t.setEditedAt(java.time.LocalDateTime.now());
        t.setSpeakerNames("{\"1\":\"张总\"}");
        t.setScene(AudioTranscription.Scene.MEETING);
        t.setSceneSource(AudioTranscription.SceneSource.AUTO);
        AudioTranscription saved = service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL).orElseThrow();
        assertThat(saved.getEditedAt()).isNull();
        assertThat(saved.getSpeakerNames()).isNull();
        assertThat(saved.getScene()).isNull();
        assertThat(saved.getSceneSource()).isNull();
    }

    @Test
    void manualFull_keepsUserScene() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done(UTTS);
        t.setScene(AudioTranscription.Scene.LECTURE);
        t.setSceneSource(AudioTranscription.SceneSource.USER);
        AudioTranscription saved = service.trigger(1L, "b", AudioTranscription.TriggerType.MANUAL, AudioTranscription.Stage.FULL).orElseThrow();
        assertThat(saved.getScene()).isEqualTo(AudioTranscription.Scene.LECTURE);
        assertThat(saved.getSceneSource()).isEqualTo(AudioTranscription.SceneSource.USER);
    }

    @Test
    void agentRetriggerOfSummaryFailedOnlyResummarizes() throws Exception {
        block("audioRecord", 60);
        AudioTranscription t = done(UTTS);
        t.setErrorKind(AudioTranscription.ErrorKind.SUMMARY_FAILED);
        AudioTranscription saved = service.triggerFromAgent(1L, "b");
        assertThat(saved.getStage()).isEqualTo(AudioTranscription.Stage.SUMMARY);
        assertThat(saved.getUtterances()).isEqualTo(UTTS);
    }
}
