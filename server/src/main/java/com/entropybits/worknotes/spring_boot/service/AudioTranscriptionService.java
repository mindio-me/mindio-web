/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.ErrorKind;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Scene;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.SceneSource;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Stage;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Status;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.TriggerType;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ConflictException;
import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 录音转写的登记入口：决定一次触发要不要登记 / 重置任务，真正的处理在 AudioTranscriptionJob。
 * 规则见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md "触发接口" 一节。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AudioTranscriptionService {

    public static final int MIN_AUTO_TRANSCRIBE_SECONDS = 10;

    private final AudioTranscriptionRepository repository;
    private final MediaBlockService mediaBlockService;
    private final AiSettingsService aiSettingsService;
    private final NoteRepository noteRepository;
    private final ContentIndexingService contentIndexingService;
    private final ObjectMapper objectMapper;

    public Optional<AudioTranscription> trigger(Long noteId, String blockId, TriggerType trigger, Stage stage) {
        MediaBlockService.BlockData block = mediaBlockService.getBlockData(noteId, blockId);
        if (!"audio".equals(block.type()) && !"audioRecord".equals(block.type())) {
            throw new BadRequestException("这个块不是音频: " + block.type());
        }
        Optional<AudioTranscription> existing = repository.findByNoteIdAndBlockId(noteId, blockId);
        if (existing.isPresent() && isInFlight(existing.get())) {
            return existing;
        }

        if (trigger == TriggerType.AUTO) {
            if (!aiSettingsService.autoTranscribeEnabled()
                    || !"audioRecord".equals(block.type())
                    || block.data().path("duration").asDouble(0) < MIN_AUTO_TRANSCRIBE_SECONDS) {
                return Optional.empty();
            }
            if (existing.isPresent()) {
                return existing;
            }
            stage = Stage.FULL;
        }

        if (stage == Stage.SUMMARY && (existing.isEmpty() || existing.get().getUtterances() == null)) {
            throw new BadRequestException("还没有转写原文，请先转写");
        }

        AudioTranscription record = existing.orElseGet(() -> AudioTranscription.builder()
                .noteId(noteId).blockId(blockId).build());
        record.setStatus(Status.PENDING);
        record.setTriggerType(trigger);
        record.setStage(stage);
        record.setAttempts(0);
        record.setErrorKind(null);
        record.setErrorMessage(null);
        record.setCompletedAt(null);
        record.setPolished(null);
        record.setSummary(null);
        if (stage == Stage.FULL) {
            record.setUtterances(null);
            // 重新转写后说话人编号可能变、用户改过的原文会被覆盖：一并清掉；用户选过的场景保留
            record.setEditedAt(null);
            record.setSpeakerNames(null);
            if (record.getSceneSource() != SceneSource.USER) {
                record.setScene(null);
                record.setSceneSource(null);
            }
        }
        return Optional.of(repository.save(record));
    }

    /**
     * agent 的 transcribe_audio 工具入口：已经转写完成的结果不能被静默清掉重来，
     * 只有"未完整完成"的 DONE（部分失败 / 无语音 / 整理失败）才允许重新触发。
     */
    public AudioTranscription triggerFromAgent(Long noteId, String blockId) {
        Optional<AudioTranscription> existing = repository.findByNoteIdAndBlockId(noteId, blockId);
        if (existing.isPresent() && existing.get().getStatus() == Status.DONE) {
            ErrorKind kind = existing.get().getErrorKind();
            if (kind == ErrorKind.SUMMARY_FAILED) {
                // 逐句原文已经付过 ASR 的钱：只重新整理
                return trigger(noteId, blockId, TriggerType.MANUAL, Stage.SUMMARY).orElseThrow();
            }
            if (kind != ErrorKind.PARTIAL_SEGMENTS && kind != ErrorKind.NO_SPEECH) {
                return existing.get();
            }
        }
        return trigger(noteId, blockId, TriggerType.MANUAL, Stage.FULL).orElseThrow();
    }

    public record UtteranceEdit(int index, String text) {}

    /** 用户改错：只覆盖对应句的 text，保存后立即重建索引，再按 SUMMARY 重新整理 */
    public AudioTranscription saveUtterances(Long noteId, String blockId, List<UtteranceEdit> edits) {
        if (edits == null) throw new BadRequestException("没有要保存的修改");
        AudioTranscription t = requireDone(noteId, blockId);
        ArrayNode arr = readUtterances(t);
        for (JsonNode u : arr) {
            if (u.path("text").asText("").startsWith("[第 ")) {
                throw new ConflictException("这段录音有转写失败的片段，请先重新转写");
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (UtteranceEdit e : edits) {
            if (e.index() < 0 || e.index() >= arr.size() || !seen.add(e.index())) {
                throw new BadRequestException("句子序号不对: " + e.index());
            }
            // 以"[第 "开头会被当成转写失败的占位句，之后整段都不能再编辑
            if (e.text() != null && e.text().trim().startsWith("[第 ")) {
                throw new BadRequestException("句子不能以“[第 ”开头");
            }
        }
        edits.forEach(e -> ((ObjectNode) arr.get(e.index())).put("text", e.text() == null ? "" : e.text().trim()));
        t.setUtterances(arr.toString());
        t.setEditedAt(LocalDateTime.now());
        // 索引优先用优化版：旧优化版不清掉的话，下面重建索引进去的还是改之前的文字（SUMMARY 重新整理本来也会清）
        t.setPolished(null);
        repository.save(t);
        try {
            contentIndexingService.reindexNote(noteId);
        } catch (Exception ex) {
            // 索引失败不影响保存；整理完成后 Job 还会再索引一次
            log.warn("原文修改已保存但重新索引失败 noteId={}", noteId, ex);
        }
        return trigger(noteId, blockId, TriggerType.MANUAL, Stage.SUMMARY).orElseThrow();
    }

    /** 改说话人名字（不重新整理）/ 改场景（按新模板重新整理） */
    public AudioTranscription patch(Long noteId, String blockId, Map<String, String> speakerNames, Scene scene) {
        AudioTranscription t = repository.findByNoteIdAndBlockId(noteId, blockId)
                .orElseThrow(() -> new ResourceNotFoundException("这段录音还没有转写记录"));
        String namesJson = null;
        if (speakerNames != null) {
            namesJson = cleanSpeakerNames(speakerNames);
        }
        if (scene != null && t.getStatus() != Status.DONE) {
            throw new ConflictException("录音正在处理，稍后再改场景");
        }
        // 处理中的 Job 握着整条记录，整理完会整体保存，这时改的名字会被悄悄冲掉
        if (speakerNames != null && isInFlight(t)) {
            throw new ConflictException("录音正在处理，稍后再改名字");
        }
        if (speakerNames != null) {
            t.setSpeakerNames(namesJson);
        }
        if (scene != null) {
            t.setScene(scene);
            t.setSceneSource(SceneSource.USER);
        }
        if (speakerNames == null && scene == null) {
            return t;
        }
        t = repository.save(t);
        if (scene != null) {
            return trigger(noteId, blockId, TriggerType.MANUAL, Stage.SUMMARY).orElseThrow();
        }
        return t;
    }

    /** 说话人编号只能是 1-99，最多 20 个，名字去空白后至多 50 字，空名字丢弃；全空则返回 null */
    private String cleanSpeakerNames(Map<String, String> raw) {
        if (raw.size() > 20) {
            throw new BadRequestException("说话人名字太多");
        }
        Map<String, String> cleaned = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || !e.getKey().matches("[1-9][0-9]?")) {
                throw new BadRequestException("说话人编号不对: " + e.getKey());
            }
            String name = e.getValue() == null ? "" : e.getValue().trim();
            if (name.length() > 50) {
                throw new BadRequestException("说话人名字不能超过 50 个字");
            }
            if (!name.isEmpty()) {
                cleaned.put(e.getKey(), name);
            }
        }
        if (cleaned.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new BadRequestException("说话人名字格式不对");
        }
    }

    private AudioTranscription requireDone(Long noteId, String blockId) {
        AudioTranscription t = repository.findByNoteIdAndBlockId(noteId, blockId)
                .orElseThrow(() -> new ResourceNotFoundException("这段录音还没有转写记录"));
        if (t.getStatus() != Status.DONE || t.getUtterances() == null) {
            throw new ConflictException("录音正在处理，修改未保存");
        }
        return t;
    }

    private ArrayNode readUtterances(AudioTranscription t) {
        try {
            JsonNode node = objectMapper.readTree(t.getUtterances());
            if (node instanceof ArrayNode arr) return arr;
        } catch (Exception ignored) {
            // 落到下面统一报错
        }
        throw new ConflictException("转写原文数据损坏，请重新转写");
    }

    public List<AudioTranscription> listForNote(Long noteId) {
        return repository.findByNoteId(noteId);
    }

    /** Note.owner 是 LAZY 且 open-in-view=false：读 owner 的用户名必须在事务里，否则 LazyInitializationException */
    @Transactional(readOnly = true)
    public void requireOwner(Long noteId, String username) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在或已被删除"));
        if (note.getOwner() == null || !note.getOwner().getUsername().equals(username)) {
            throw new ForbiddenException("无权访问这篇笔记");
        }
    }

    private static boolean isInFlight(AudioTranscription t) {
        return t.getStatus() == Status.PENDING || t.getStatus() == Status.PROCESSING;
    }
}
