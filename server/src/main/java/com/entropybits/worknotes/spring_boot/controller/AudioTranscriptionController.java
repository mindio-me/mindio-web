/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AudioTranscriptionResponse;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.service.AudioTranscriptionService;
import com.entropybits.worknotes.spring_boot.service.AudioTranscriptionService.UtteranceEdit;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/notes/{noteId}/audio-transcriptions")
@RequiredArgsConstructor
public class AudioTranscriptionController {

    private final AudioTranscriptionService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<AudioTranscriptionResponse> list(@PathVariable Long noteId,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        service.requireOwner(noteId, userDetails.getUsername());
        return service.listForNote(noteId).stream()
                .map(t -> AudioTranscriptionResponse.from(t, objectMapper)).toList();
    }

    @PostMapping("/{blockId}")
    public ResponseEntity<AudioTranscriptionResponse> trigger(
            @PathVariable Long noteId, @PathVariable String blockId,
            @RequestParam(defaultValue = "MANUAL") AudioTranscription.TriggerType trigger,
            @RequestParam(defaultValue = "FULL") AudioTranscription.Stage stage,
            @AuthenticationPrincipal UserDetails userDetails) {
        service.requireOwner(noteId, userDetails.getUsername());
        return service.trigger(noteId, blockId, trigger, stage)
                .map(t -> ResponseEntity.ok(AudioTranscriptionResponse.from(t, objectMapper)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record PatchRequest(Map<String, String> speakerNames, String scene) {}

    @PutMapping("/{blockId}/utterances")
    public AudioTranscriptionResponse saveUtterances(
            @PathVariable Long noteId, @PathVariable String blockId,
            @RequestBody List<UtteranceEdit> edits,
            @AuthenticationPrincipal UserDetails userDetails) {
        service.requireOwner(noteId, userDetails.getUsername());
        return AudioTranscriptionResponse.from(service.saveUtterances(noteId, blockId, edits), objectMapper);
    }

    @PatchMapping("/{blockId}")
    public AudioTranscriptionResponse patch(
            @PathVariable Long noteId, @PathVariable String blockId,
            @RequestBody PatchRequest body,
            @AuthenticationPrincipal UserDetails userDetails) {
        service.requireOwner(noteId, userDetails.getUsername());
        AudioTranscription.Scene scene = null;
        if (body.scene() != null) {
            try {
                scene = AudioTranscription.Scene.valueOf(body.scene().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("不认识的场景: " + body.scene());
            }
        }
        return AudioTranscriptionResponse.from(service.patch(noteId, blockId, body.speakerNames(), scene), objectMapper);
    }
}
