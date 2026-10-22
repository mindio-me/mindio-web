/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AudioTranscriptionResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公开笔记页上的录音/音频转写结果（匿名只读）。
 * <p>
 * /v1/notes/{id}/audio-transcriptions 需要登录且要求是笔记主人，匿名访客看不到转写。这里只对公开笔记放行，
 * 并且只返回笔记当前正文里仍然存在的 audio / audioRecord 块对应的记录（块被删后遗留的转写不外泄）。
 * 响应形状与登录版一致，但 errorKind / errorMessage 置空：它是内部失败原因，展示用不到（前端有通用兜底文案）。
 */
@RestController
@RequestMapping("/v1/share")
@RequiredArgsConstructor
@Slf4j
public class ShareAudioTranscriptionController {

    private final NoteRepository noteRepository;
    private final AudioTranscriptionRepository transcriptionRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/notes/{noteId}/audio-transcriptions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AudioTranscriptionResponse>> list(@PathVariable Long noteId) {
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || !Boolean.TRUE.equals(note.getIsPublic())) {
            return ResponseEntity.notFound().build();
        }
        Set<String> blockIds = audioBlockIds(note.getContent());
        if (blockIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<AudioTranscriptionResponse> body = transcriptionRepository.findByNoteId(noteId).stream()
                .filter(t -> blockIds.contains(t.getBlockId()))
                .map(t -> AudioTranscriptionResponse.from(t, objectMapper))
                .map(AudioTranscriptionResponse::withoutErrorDetail)
                .toList();
        return ResponseEntity.ok(body);
    }

    private Set<String> audioBlockIds(String content) {
        Set<String> ids = new HashSet<>();
        if (content == null || content.isBlank()) {
            return ids;
        }
        try {
            JsonNode blocks = objectMapper.readTree(content).path("blocks");
            for (JsonNode block : blocks) {
                String type = block.path("type").asText();
                if (("audio".equals(type) || "audioRecord".equals(type)) && block.hasNonNull("id")) {
                    ids.add(block.get("id").asText());
                }
            }
        } catch (Exception e) {
            // 正文不是合法 JSON（非 editorjs 笔记等）：没有可展示的音频块
            log.debug("share audio transcriptions: content not parseable, noteContent ignored: {}", e.toString());
        }
        return ids;
    }
}
