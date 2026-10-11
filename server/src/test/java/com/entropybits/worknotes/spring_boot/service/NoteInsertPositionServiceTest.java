/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteInsertPositionServiceTest {

    @Mock AgentNoteEditService noteEditService;
    @Mock AgentServiceClient agentServiceClient;
    NoteInsertPositionService service;

    @BeforeEach
    void setUp() {
        service = new NoteInsertPositionService(noteEditService, agentServiceClient);
        when(noteEditService.readBlocks("alice", 9L)).thenReturn(Map.of("contentType", "editorjs", "blocks", List.of(
                new AgentNoteBlocks.Block("h", "header", "11111111", "## 4.3 方案", true),
                new AgentNoteBlocks.Block("p", "paragraph", "22222222", "网关每 5 秒轮询一次，延迟较高", true))));
    }

    @Test
    void blockSuggestion_carriesPreviewOfTargetBlock() throws Exception {
        when(agentServiceClient.insertPosition(any(), eq("新方案"), eq("p")))
                .thenReturn(Map.of("choice", "block", "afterBlockId", "h", "reason", "属于 4.3 节"));

        Map<String, Object> s = service.suggest("alice", 9L, "新方案", "p");

        assertThat(s).containsEntry("choice", "block").containsEntry("afterBlockId", "h")
                .containsEntry("afterPreview", "## 4.3 方案").containsEntry("reason", "属于 4.3 节");
    }

    @Test
    void unknownBlockOrModelFailure_fallsBackToCursorOrEnd() throws Exception {
        when(agentServiceClient.insertPosition(any(), eq("x"), eq("p")))
                .thenReturn(Map.of("choice", "block", "afterBlockId", "nope"));
        when(agentServiceClient.insertPosition(any(), eq("y"), eq(null))).thenThrow(new RuntimeException("down"));

        assertThat(service.suggest("alice", 9L, "x", "p")).containsEntry("choice", "cursor");
        assertThat(service.suggest("alice", 9L, "y", null)).containsEntry("choice", "end");
    }
}
