/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Stage;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.Status;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription.TriggerType;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 录音转写的登记入口：决定一次触发要不要登记 / 重置任务，真正的处理在 AudioTranscriptionJob。
 * 规则见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md "触发接口" 一节。
 */
@Service
@RequiredArgsConstructor
public class AudioTranscriptionService {

    public static final int MIN_AUTO_TRANSCRIBE_SECONDS = 10;

    private final AudioTranscriptionRepository repository;
    private final MediaBlockService mediaBlockService;
    private final AiSettingsService aiSettingsService;
    private final NoteRepository noteRepository;

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
        }
        return Optional.of(repository.save(record));
    }

    public List<AudioTranscription> listForNote(Long noteId) {
        return repository.findByNoteId(noteId);
    }

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
