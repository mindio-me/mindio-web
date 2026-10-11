/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Markdown 笔记原地转为 Editor.js 块文档。转换前存一份 CONVERT 快照，撤销/"恢复为 Markdown 原文"
 * 直接走 AI 改笔记的 turn 撤销接口（/v1/chat/turns/{turnId}/undo），那里已处理"转换后又改过"的冲突确认。
 */
@Service
public class NoteConversionService {

    public record Result(String turnId) {}

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NoteRevisionService revisionService;
    private final ContentIndexingService contentIndexingService;
    private final ObjectMapper objectMapper;
    private final MarkdownToEditorjsConverter converter;

    public NoteConversionService(NoteRepository noteRepository, UserRepository userRepository,
                                 NoteRevisionService revisionService, ContentIndexingService contentIndexingService,
                                 ObjectMapper objectMapper) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.revisionService = revisionService;
        this.contentIndexingService = contentIndexingService;
        this.objectMapper = objectMapper;
        this.converter = new MarkdownToEditorjsConverter(objectMapper);
    }

    @Transactional
    public Result convertMarkdownToEditorjs(String username, Long noteId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        Note note = noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在"));
        if (!"markdown".equals(note.getContentType())) {
            throw new BadRequestException("只有 Markdown 笔记可以转换");
        }
        String turnId = "convert-" + UUID.randomUUID();
        revisionService.snapshotBeforeEdit(note, turnId, NoteRevision.Source.CONVERT);
        ArrayNode blocks = objectMapper.createArrayNode();
        converter.convert(note.getContent()).forEach(blocks::add);
        String content = toEditorjsJson(blocks);
        note.setContent(content);
        note.setContentType("editorjs");
        // 与前端编辑器保存 editorjs 笔记时一致：每块一条 section，内容是 JSON.stringify(block.data)，类型是 block.type
        List<String> sectionContents = new ArrayList<>();
        List<String> sectionTypes = new ArrayList<>();
        for (JsonNode block : blocks) {
            sectionContents.add(writeJson(block.path("data")));
            sectionTypes.add(block.path("type").asText());
        }
        note.setSectionContents(sectionContents);
        note.setSectionTypes(sectionTypes);
        noteRepository.save(note);
        revisionService.recordAfter(turnId, note.getId(), content);
        contentIndexingService.reindexNote(note.getId());
        return new Result(turnId);
    }

    private String toEditorjsJson(ArrayNode blocks) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("time", System.currentTimeMillis());
        root.set("blocks", blocks);
        root.put("version", "2.31.1");
        return writeJson(root);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
