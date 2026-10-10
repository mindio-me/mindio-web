/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AudioTranscriptionResponse;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.service.AudioTranscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
