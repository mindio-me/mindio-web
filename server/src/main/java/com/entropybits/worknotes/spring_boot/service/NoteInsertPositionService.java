/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回复上「插入到笔记」按钮：让模型判断内容该插到光标处、文末还是某一节之后。模型失败或给出
 * 不存在的块时退回光标处（没有光标则文末）——这一步只是建议，不能卡住插入。
 */
@Slf4j
@Service
public class NoteInsertPositionService {

    private static final int PREVIEW_CHARS = 40;

    private final AgentNoteEditService noteEditService;
    private final AgentServiceClient agentServiceClient;

    public NoteInsertPositionService(AgentNoteEditService noteEditService, AgentServiceClient agentServiceClient) {
        this.noteEditService = noteEditService;
        this.agentServiceClient = agentServiceClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> suggest(String username, Long noteId, String content, String cursorBlockId) {
        List<AgentNoteBlocks.Block> blocks = (List<AgentNoteBlocks.Block>) noteEditService.readBlocks(username, noteId)
                .get("blocks");
        List<Map<String, Object>> outline = new ArrayList<>();
        Map<String, String> previews = new HashMap<>();
        for (AgentNoteBlocks.Block b : blocks) {
            String preview = b.text().replace("\n", " ");
            if (preview.length() > PREVIEW_CHARS) preview = preview.substring(0, PREVIEW_CHARS) + "…";
            previews.put(b.id(), preview);
            outline.add(Map.of("id", b.id(), "type", b.type(), "preview", preview));
        }
        String cursor = cursorBlockId != null && previews.containsKey(cursorBlockId) ? cursorBlockId : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("choice", cursor != null ? "cursor" : "end");
        result.put("reason", "");
        try {
            Map<String, Object> answer = agentServiceClient.insertPosition(outline, content, cursor);
            Object choice = answer.get("choice");
            Object after = answer.get("afterBlockId");
            if ("block".equals(choice) && after != null && previews.containsKey(after.toString())) {
                result.put("choice", "block");
                result.put("afterBlockId", after.toString());
                result.put("afterPreview", previews.get(after.toString()));
            } else if ("end".equals(choice)) {
                result.put("choice", "end");
            }
            if (answer.get("reason") != null) result.put("reason", answer.get("reason").toString());
        } catch (Exception e) {
            log.warn("insert-position suggestion failed for note {}, falling back", noteId, e);
        }
        return result;
    }
}
