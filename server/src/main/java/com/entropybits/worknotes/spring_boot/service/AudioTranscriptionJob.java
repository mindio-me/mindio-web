/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.ErrorKind;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Scene;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.SceneSource;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Stage;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Status;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 录音转写执行引擎：捞 PENDING → 逐段 ASR（Python 自取音频）→ 先存逐句原文 → 整理 → 写回 + 重新索引。
 * 照 ImageOcrJob 的模式：提交线程池前同步把状态翻成 PROCESSING，避免下一次调度重复提交。
 * 详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
 */
@Slf4j
@Service
public class AudioTranscriptionJob {

    static final int MAX_ATTEMPTS = 3;
    /** 重试退避：第 n 次失败后至少等 n × 30 秒再重新派发 */
    static final int RETRY_BACKOFF_SECONDS = 30;

    private final AudioTranscriptionRepository repository;
    private final MediaBlockService mediaBlockService;
    private final AgentServiceClient agentServiceClient;
    private final ContentIndexingService contentIndexingService;
    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public AudioTranscriptionJob(AudioTranscriptionRepository repository, MediaBlockService mediaBlockService,
                                 AgentServiceClient agentServiceClient, ContentIndexingService contentIndexingService,
                                 NoteRepository noteRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mediaBlockService = mediaBlockService;
        this.agentServiceClient = agentServiceClient;
        this.contentIndexingService = contentIndexingService;
        this.noteRepository = noteRepository;
        this.objectMapper = objectMapper;
    }

    public void processPendingBatch() {
        LocalDateTime now = LocalDateTime.now();
        for (AudioTranscription t : repository.findTop10ByStatusOrderByCreatedAtAsc(Status.PENDING)) {
            if (t.getAttempts() > 0 && t.getUpdatedAt() != null
                    && t.getUpdatedAt().isAfter(now.minusSeconds((long) t.getAttempts() * RETRY_BACKOFF_SECONDS))) {
                continue;
            }
            t.setStatus(Status.PROCESSING);
            repository.save(t);
            executor.submit(() -> processOne(t.getId()));
        }
    }

    void processOne(Long id) {
        AudioTranscription t = repository.findById(id).orElse(null);
        if (t == null || (t.getStatus() != Status.PENDING && t.getStatus() != Status.PROCESSING)) {
            return;
        }
        t.setStatus(Status.PROCESSING);
        repository.save(t);
        try {
            if (!noteRepository.existsById(t.getNoteId())) {
                repository.delete(t);
                return;
            }
            MediaBlockService.BlockData block;
            try {
                block = mediaBlockService.getBlockData(t.getNoteId(), t.getBlockId());
            } catch (ResourceNotFoundException | BadRequestException e) {
                fail(t, ErrorKind.FILE_MISSING, "找不到录音文件");
                return;
            }

            if (t.getStage() == Stage.FULL && !transcribe(t, block.data())) {
                return;
            }

            List<Map<String, Object>> utterances = readList(t.getUtterances());
            if (utterances.isEmpty()) {
                t.setErrorKind(ErrorKind.NO_SPEECH);
                t.setErrorMessage("没有识别出内容（可能是静音）");
                finish(t);
                return;
            }
            summarize(t, utterances, readMarks(block.data()), block.data().path("systemAudio").asBoolean(false));
            markPartialSegments(t, utterances);
            finish(t);
        } catch (AgentUnavailableException e) {
            int attempts = t.getAttempts() + 1;
            t.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                if (t.getUtterances() != null) {
                    // ASR 已经花了钱：保留逐句原文，只把整理标成失败，用户可以点"重新整理"
                    t.setErrorKind(ErrorKind.SUMMARY_FAILED);
                    t.setErrorMessage("整理失败：AI 服务暂时不可用，可以重新整理");
                    finish(t);
                } else {
                    fail(t, ErrorKind.AGENT_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试");
                }
            } else {
                t.setStatus(Status.PENDING);
                repository.save(t);
            }
        } catch (Exception e) {
            log.warn("录音转写失败 id={}", id, e);
            fail(t, ErrorKind.UNKNOWN, "处理失败：" + e.getMessage());
        }
    }

    /** 逐段 ASR 并先存逐句原文；返回 false 表示已经判失败、不用继续 */
    private boolean transcribe(AudioTranscription t, JsonNode data) throws Exception {
        List<Integer> segmentIndexes = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();
        JsonNode segments = data.path("segments");
        if (segments.isArray() && !segments.isEmpty()) {
            for (int i = 0; i < segments.size(); i++) {
                segmentIndexes.add(i);
                offsets.add(Math.round(segments.get(i).path("start").asDouble(0) * 1000));
            }
        } else {
            segmentIndexes.add(null);
            offsets.add(0L);
        }

        List<Map<String, Object>> all = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        String lastError = null;
        for (int i = 0; i < segmentIndexes.size(); i++) {
            try {
                all.addAll(agentServiceClient.asrTranscribe(t.getNoteId(), t.getBlockId(), segmentIndexes.get(i), offsets.get(i)));
            } catch (AgentCallException e) {
                if ("ASR_NOT_CONFIGURED".equals(e.getCode())) {
                    fail(t, ErrorKind.ASR_NOT_CONFIGURED, e.getMessage());
                    return false;
                }
                failed.add(i + 1);
                lastError = e.getMessage();
                Map<String, Object> placeholder = new LinkedHashMap<>();
                placeholder.put("start", offsets.get(i));
                placeholder.put("end", offsets.get(i));
                placeholder.put("text", "[第 " + (i + 1) + " 段转写失败]");
                all.add(placeholder);
            }
        }
        if (failed.size() == segmentIndexes.size()) {
            fail(t, ErrorKind.ASR_FAILED, "语音识别失败：" + lastError);
            return false;
        }
        // 转写期间笔记可能被删（deleteNote 会清掉这条记录）：此时再 save 会把行"复活"成孤儿
        if (!stillExists(t)) {
            return false;
        }
        boolean hasSpeech = all.stream().anyMatch(u -> !String.valueOf(u.get("text")).startsWith("[第 "));
        t.setUtterances(objectMapper.writeValueAsString(hasSpeech ? all : List.of()));
        if (!failed.isEmpty()) {
            t.setErrorKind(ErrorKind.PARTIAL_SEGMENTS);
            t.setErrorMessage(failed.stream().map(n -> "第 " + n + " 段").reduce((a, b) -> a + "、" + b).orElse("")
                    + "转写失败，可以重新转写");
        }
        // ASR 已经花了钱：后面整理阶段如果因为服务不可达要重试，只重做整理
        t.setStage(Stage.SUMMARY);
        repository.save(t);
        return true;
    }

    private void summarize(AudioTranscription t, List<Map<String, Object>> utterances, List<Long> marks,
                           boolean systemAudio) throws Exception {
        try {
            // 用户选过的场景照用；否则交给模型判断（内录由 Python 直接定为 SYSTEM_AUDIO）
            String scene = t.getSceneSource() == SceneSource.USER && t.getScene() != null ? t.getScene().name() : null;
            Map<String, Object> result = agentServiceClient.audioSummarize(utterances, marks, scene, systemAudio);
            t.setPolished(objectMapper.writeValueAsString(result.get("polished")));
            t.setSummary(objectMapper.writeValueAsString(result.get("summary")));
            if (t.getSceneSource() != SceneSource.USER) {
                t.setScene(parseScene(result.get("scene")));
                t.setSceneSource(SceneSource.AUTO);
            }
        } catch (AgentCallException e) {
            t.setErrorKind(ErrorKind.SUMMARY_FAILED);
            t.setErrorMessage("整理失败：" + e.getMessage());
        } catch (AgentUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // 逐句原文已经存好：整理阶段的任何意外都不该把整条记录判成失败
            log.warn("录音整理失败 id={}", t.getId(), e);
            t.setErrorKind(ErrorKind.SUMMARY_FAILED);
            t.setErrorMessage("整理失败：" + e.getMessage());
        }
    }

    /**
     * 只重新整理（改场景 / 改原文后）时 trigger 会清掉 errorKind，但失败占位句还在：
     * 不补回 PARTIAL_SEGMENTS 的话"重新转写"入口就没了，而编辑又因占位句被禁用
     */
    private static void markPartialSegments(AudioTranscription t, List<Map<String, Object>> utterances) {
        if (t.getErrorKind() != null) return;
        List<String> failed = new ArrayList<>();
        for (Map<String, Object> u : utterances) {
            String text = String.valueOf(u.get("text"));
            if (!text.startsWith("[第 ")) continue;
            int end = text.indexOf(" 段");
            failed.add(end > 0 ? text.substring(1, end + 2) : null);
        }
        if (failed.isEmpty()) return;
        t.setErrorKind(ErrorKind.PARTIAL_SEGMENTS);
        t.setErrorMessage(failed.contains(null)
                ? "部分片段转写失败，可以重新转写"
                : String.join("、", failed) + "转写失败，可以重新转写");
    }

    private static Scene parseScene(Object value) {
        try {
            return Scene.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Scene.MEMO;
        }
    }

    private void finish(AudioTranscription t) {
        if (!stillExists(t)) {
            return;
        }
        t.setStatus(Status.DONE);
        t.setCompletedAt(LocalDateTime.now());
        repository.save(t);
        try {
            contentIndexingService.reindexNote(t.getNoteId());
        } catch (Exception e) {
            log.warn("录音转写完成但重新索引失败 noteId={}", t.getNoteId(), e);
        }
    }

    /** 笔记或记录已被删除则不能再 save（detached 实体 merge 会重新插入行）；笔记已删时顺手清掉残留 */
    private boolean stillExists(AudioTranscription t) {
        if (!noteRepository.existsById(t.getNoteId())) {
            repository.deleteByNoteId(t.getNoteId());
            return false;
        }
        return repository.existsById(t.getId());
    }

    private void fail(AudioTranscription t, ErrorKind kind, String message) {
        if (!stillExists(t)) {
            return;
        }
        t.setStatus(Status.FAILED);
        t.setErrorKind(kind);
        t.setErrorMessage(message);
        t.setCompletedAt(LocalDateTime.now());
        repository.save(t);
    }

    private List<Map<String, Object>> readList(String json) throws Exception {
        if (json == null || json.isBlank()) return List.of();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private static List<Long> readMarks(JsonNode data) {
        List<Long> marks = new ArrayList<>();
        JsonNode node = data.path("marks");
        if (node.isArray()) node.forEach(m -> { if (m.canConvertToLong()) marks.add(m.asLong()); });
        return marks;
    }
}
