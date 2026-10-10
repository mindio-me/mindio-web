/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioTranscriptionJobTest {

    @Mock AudioTranscriptionRepository repository;
    @Mock MediaBlockService mediaBlockService;
    @Mock AgentServiceClient agent;
    @Mock ContentIndexingService indexing;
    @Mock NoteRepository noteRepository;
    final ObjectMapper om = new ObjectMapper();
    AudioTranscriptionJob job;
    AudioTranscription record;

    @BeforeEach
    void setUp() throws Exception {
        job = new AudioTranscriptionJob(repository, mediaBlockService, agent, indexing, noteRepository, om);
        record = AudioTranscription.builder().id(9L).noteId(1L).blockId("b")
                .status(AudioTranscription.Status.PROCESSING).triggerType(AudioTranscription.TriggerType.AUTO)
                .stage(AudioTranscription.Stage.FULL).attempts(0).build();
        when(repository.findById(9L)).thenReturn(Optional.of(record));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.existsById(1L)).thenReturn(true);
        when(repository.existsById(9L)).thenReturn(true);
        blockData("{\"url\":\"/uploads/a.webm\",\"duration\":3700,\"marks\":[5000]," +
                "\"segments\":[{\"url\":\"/uploads/s0.webm\",\"start\":0},{\"url\":\"/uploads/s1.webm\",\"start\":1800}]}");
        when(agent.audioSummarize(any(), any())).thenReturn(Map.of(
                "polished", List.of(Map.of("start", 0, "text", "整理后")),
                "summary", Map.of("overview", "概述")));
    }

    private void blockData(String json) throws Exception {
        when(mediaBlockService.getBlockData(1L, "b"))
                .thenReturn(new MediaBlockService.BlockData("audioRecord", om.readTree(json)));
    }

    private static Map<String, Object> u(long start, String text) {
        return Map.of("start", start, "end", start + 1000, "text", text);
    }

    @Test
    void fullRun_transcribesEachSegmentWithOffsetThenSummarizes() {
        when(agent.asrTranscribe(1L, "b", 0, 0L)).thenReturn(List.of(u(0, "甲")));
        when(agent.asrTranscribe(1L, "b", 1, 1_800_000L)).thenReturn(List.of(u(1_800_000, "乙")));

        job.processOne(9L);

        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(record.getUtterances()).contains("甲").contains("乙");
        assertThat(record.getPolished()).contains("整理后");
        assertThat(record.getSummary()).contains("概述");
        assertThat(record.getErrorKind()).isNull();
        assertThat(record.getCompletedAt()).isNotNull();
        verify(agent).audioSummarize(any(), eq(List.of(5000L)));
        verify(indexing).reindexNote(1L);
    }

    @Test
    void singleFileWithoutSegments_usesNullSegmentAndZeroOffset() throws Exception {
        blockData("{\"url\":\"/uploads/a.webm\",\"duration\":60}");
        when(agent.asrTranscribe(1L, "b", null, 0L)).thenReturn(List.of(u(0, "甲")));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        verify(agent).audioSummarize(any(), eq(List.of()));
    }

    @Test
    void partialSegmentFailure_insertsPlaceholderAndMarksPartial() {
        when(agent.asrTranscribe(1L, "b", 0, 0L)).thenThrow(new AgentCallException("ASR_FAILED", "超时"));
        when(agent.asrTranscribe(1L, "b", 1, 1_800_000L)).thenReturn(List.of(u(1_800_000, "乙")));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(record.getUtterances()).contains("[第 1 段转写失败]");
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.PARTIAL_SEGMENTS);
        assertThat(record.getErrorMessage()).contains("第 1 段");
    }

    @Test
    void allSegmentsFail_marksFailed() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenThrow(new AgentCallException("ASR_FAILED", "超时"));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.FAILED);
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.ASR_FAILED);
        verify(agent, never()).audioSummarize(any(), any());
    }

    @Test
    void asrNotConfigured_failsImmediatelyWithKind() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong()))
                .thenThrow(new AgentCallException("ASR_NOT_CONFIGURED", "请到 设置 → AI 服务 填写语音识别 Key"));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.FAILED);
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.ASR_NOT_CONFIGURED);
        verify(agent, times(1)).asrTranscribe(any(), any(), any(), anyLong());
    }

    @Test
    void noSpeech_doneWithoutSummary() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of());
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.NO_SPEECH);
        verify(agent, never()).audioSummarize(any(), any());
    }

    @Test
    void summaryFailure_keepsUtterancesAndMarksSummaryFailed() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of(u(0, "甲")));
        when(agent.audioSummarize(any(), any())).thenThrow(new AgentCallException("SUMMARY_FAILED", "坏 JSON"));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(record.getUtterances()).contains("甲");
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.SUMMARY_FAILED);
        verify(indexing).reindexNote(1L);
    }

    @Test
    void agentUnavailable_requeuesUntilMaxAttempts() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenThrow(new AgentUnavailableException("down", null));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(record.getAttempts()).isEqualTo(1);

        record.setStatus(AudioTranscription.Status.PROCESSING);
        record.setAttempts(2);
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.FAILED);
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.AGENT_UNAVAILABLE);
    }

    @Test
    void retriesOnlySummaryWhenAgentUnavailableAfterAsr() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of(u(0, "甲")));
        when(agent.audioSummarize(any(), any())).thenThrow(new AgentUnavailableException("down", null));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.PENDING);
        assertThat(record.getStage()).isEqualTo(AudioTranscription.Stage.SUMMARY);
        assertThat(record.getUtterances()).contains("甲");
    }

    @Test
    void summaryStage_skipsAsr() {
        record.setStage(AudioTranscription.Stage.SUMMARY);
        record.setUtterances("[{\"start\":0,\"end\":1000,\"text\":\"甲\"}]");
        job.processOne(9L);
        verify(agent, never()).asrTranscribe(any(), any(), any(), anyLong());
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(record.getSummary()).contains("概述");
    }

    @Test
    void missingBlockOrFile_marksFileMissing() {
        when(mediaBlockService.getBlockData(1L, "b")).thenThrow(new ResourceNotFoundException("找不到这个块: b"));
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.FAILED);
        assertThat(record.getErrorKind()).isEqualTo(AudioTranscription.ErrorKind.FILE_MISSING);
    }

    @Test
    void noteDeletedDuringAsr_stopsWithoutSavingOrSummarizing() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of(u(0, "甲")));
        when(noteRepository.existsById(1L)).thenReturn(true, false);
        job.processOne(9L);
        verify(repository).deleteByNoteId(1L);
        assertThat(record.getUtterances()).isNull();
        verify(agent, never()).audioSummarize(any(), any());
        verify(indexing, never()).reindexNote(any());
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.PROCESSING);
    }

    @Test
    void recordDeletedDuringAsr_doesNotSaveAgain() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of(u(0, "甲")));
        when(repository.existsById(9L)).thenReturn(false);
        job.processOne(9L);
        // 只有开头翻 PROCESSING 的那一次 save
        verify(repository, times(1)).save(any());
        verify(agent, never()).audioSummarize(any(), any());
        verify(indexing, never()).reindexNote(any());
    }

    @Test
    void reindexFailure_keepsRecordDone() {
        when(agent.asrTranscribe(any(), any(), any(), anyLong())).thenReturn(List.of(u(0, "甲")));
        doThrow(new RuntimeException("index down")).when(indexing).reindexNote(1L);
        job.processOne(9L);
        assertThat(record.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
    }
}
