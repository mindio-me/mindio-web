/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

public record AudioTranscriptionResponse(
        String blockId, String status, String stage, String errorKind, String errorMessage,
        JsonNode utterances, JsonNode polished, JsonNode summary, LocalDateTime updatedAt,
        String scene, String sceneSource, JsonNode speakerNames, LocalDateTime editedAt) {

    public static AudioTranscriptionResponse from(AudioTranscription t, ObjectMapper om) {
        return new AudioTranscriptionResponse(
                t.getBlockId(), t.getStatus().name(), t.getStage().name(),
                t.getErrorKind() == null ? null : t.getErrorKind().name(), t.getErrorMessage(),
                parse(om, t.getUtterances()), parse(om, t.getPolished()), parse(om, t.getSummary()),
                t.getUpdatedAt(),
                t.getScene() == null ? null : t.getScene().name(),
                t.getSceneSource() == null ? null : t.getSceneSource().name(),
                parse(om, t.getSpeakerNames()), t.getEditedAt());
    }

    /** 公开展示用：去掉内部失败详情（errorKind / errorMessage），其余字段原样保留 */
    public AudioTranscriptionResponse withoutErrorDetail() {
        return new AudioTranscriptionResponse(blockId, status, stage, null, null,
                utterances, polished, summary, updatedAt, scene, sceneSource, speakerNames, editedAt);
    }

    private static JsonNode parse(ObjectMapper om, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
