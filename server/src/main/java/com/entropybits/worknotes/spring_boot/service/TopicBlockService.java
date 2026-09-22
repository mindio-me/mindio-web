/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供 agent 写入工具用：往笔记里指定类型的块追加一条条目。只能追加，不能改/删已有内容——
 * 这是 spec ② 里定的安全边界，agent 的写入权限就应该只停在这一步。
 */
@Service
@RequiredArgsConstructor
public class TopicBlockService {

    private static final Set<String> KNOWN_BLOCK_TYPES = Set.of("references", "mediaGallery", "timeline", "checklist");

    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    /**
     * 笔记没有这个类型的块就在末尾新建一个空块；有多个同类型块固定加到最后一个——不做消歧，
     * 人工确认这一步已经兜住了"加错地方"的风险，不需要在这里再加一层选择逻辑。
     * 返回该块更新后的完整 items 数组，供调用方推 block_updated 事件用。
     */
    public List<Map<String, Object>> appendItem(Long noteId, String blockType, Map<String, Object> item) {
        if (!KNOWN_BLOCK_TYPES.contains(blockType)) {
            throw new BadRequestException("不支持的块类型: " + blockType);
        }
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在或已被删除"));
        if (!"editorjs".equals(note.getContentType())) {
            throw new BadRequestException("这篇笔记不是 EditorJS 格式，无法追加块内容");
        }

        try {
            String rawContent = note.getContent() == null || note.getContent().isBlank()
                    ? "{\"blocks\":[]}" : note.getContent();
            ObjectNode root = (ObjectNode) objectMapper.readTree(rawContent);
            ArrayNode blocks = root.has("blocks") && root.get("blocks").isArray()
                    ? (ArrayNode) root.get("blocks")
                    : root.putArray("blocks");

            ObjectNode targetBlock = null;
            for (int i = blocks.size() - 1; i >= 0; i--) {
                JsonNode b = blocks.get(i);
                if (blockType.equals(b.path("type").asText())) {
                    targetBlock = (ObjectNode) b;
                    break;
                }
            }
            if (targetBlock == null) {
                targetBlock = objectMapper.createObjectNode();
                targetBlock.put("type", blockType);
                targetBlock.putObject("data").putArray("items");
                blocks.add(targetBlock);
            }

            ObjectNode data = (ObjectNode) targetBlock.get("data");
            ArrayNode items = data.has("items") && data.get("items").isArray()
                    ? (ArrayNode) data.get("items")
                    : data.putArray("items");
            items.add(objectMapper.valueToTree(item));

            note.setContent(objectMapper.writeValueAsString(root));
            noteRepository.save(note);

            return objectMapper.convertValue(items, new TypeReference<List<Map<String, Object>>>() {});
        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("追加块内容失败: " + e.getMessage(), e);
        }
    }
}
