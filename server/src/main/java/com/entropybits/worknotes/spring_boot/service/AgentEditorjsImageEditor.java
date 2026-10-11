/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor.js 笔记的图片插入/删除/改图注。事件里带 block id，前端据此在已打开的编辑器里做
 * 同样的操作——编辑器的自动保存是整篇写回，只改库不改编辑器的话会被下一次保存覆盖。
 */
final class AgentEditorjsImageEditor {

    private static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper om;

    AgentEditorjsImageEditor(ObjectMapper om) {
        this.om = om;
    }

    AgentImageEditResult insert(String content, String url, String caption, String anchorText) {
        ObjectNode root = parse(content);
        ArrayNode blocks = blocks(root);

        String blockId = newBlockId();
        ObjectNode block = om.createObjectNode();
        block.put("id", blockId);
        block.put("type", "image");
        ObjectNode data = block.putObject("data");
        data.putObject("file").put("url", url);
        data.put("caption", caption == null ? "" : caption);
        data.put("withBorder", false);
        data.put("withBackground", false);
        data.put("stretched", false);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("op", "insert");
        event.put("blockId", blockId);
        if (anchorText == null || anchorText.isBlank()) {
            if (!blocks.isEmpty()) {
                event.put("afterBlockId", blocks.get(blocks.size() - 1).path("id").asText(null));
            }
            blocks.add(block);
        } else {
            String anchor = anchorText.trim();
            int i = findAnchor(blocks, anchor);
            if (i < 0) {
                throw new BadRequestException("找不到包含「" + anchor + "」的段落");
            }
            String anchorId = blocks.get(i).path("id").asText(null);
            if (plainText(blocks.get(i)).equals(anchor)) {
                blocks.set(i, block);
                event.put("replacedBlockId", anchorId);
            } else {
                blocks.insert(i + 1, block);
                event.put("afterBlockId", anchorId);
            }
        }
        event.put("data", om.convertValue(data, MAP_TYPE));
        return new AgentImageEditResult(write(root), event);
    }

    AgentImageEditResult delete(String content, AgentImageLocator.LocatedImage image) {
        requireStandalone(image);
        ObjectNode root = parse(content);
        ArrayNode blocks = blocks(root);
        blocks.remove(indexOfBlock(blocks, image.blockId()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("op", "delete");
        event.put("blockId", image.blockId());
        return new AgentImageEditResult(write(root), event);
    }

    AgentImageEditResult setCaption(String content, AgentImageLocator.LocatedImage image, String caption) {
        requireStandalone(image);
        ObjectNode root = parse(content);
        ArrayNode blocks = blocks(root);
        ObjectNode block = (ObjectNode) blocks.get(indexOfBlock(blocks, image.blockId()));
        ObjectNode data = block.has("data") && block.get("data").isObject()
                ? (ObjectNode) block.get("data") : block.putObject("data");
        data.put("caption", caption == null ? "" : caption);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("op", "caption");
        event.put("blockId", image.blockId());
        event.put("data", om.convertValue(data, MAP_TYPE));
        return new AgentImageEditResult(write(root), event);
    }

    /** 锚点只在文本类块里找（图片块的图注不算），取第一个包含锚点文字的块 */
    private static int findAnchor(ArrayNode blocks, String anchor) {
        for (int i = 0; i < blocks.size(); i++) {
            JsonNode block = blocks.get(i);
            if (!"image".equals(block.path("type").asText()) && plainText(block).contains(anchor)) {
                return i;
            }
        }
        return -1;
    }

    private static String plainText(JsonNode block) {
        List<String> texts = new ArrayList<>();
        AgentImageLocator.collectTextValues(block.path("data"), texts::add);
        return AgentImageLocator.stripHtml(String.join(" ", texts));
    }

    private static int indexOfBlock(ArrayNode blocks, String blockId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blockId != null && blockId.equals(blocks.get(i).path("id").asText(null))) return i;
        }
        throw new ResourceNotFoundException("找不到这个图片块: " + blockId);
    }

    private static void requireStandalone(AgentImageLocator.LocatedImage image) {
        if (!image.standalone()) {
            throw new BadRequestException("这张图嵌在文本块里，不支持单独修改");
        }
    }

    private ObjectNode parse(String content) {
        try {
            String raw = content == null || content.isBlank() ? "{\"blocks\":[]}" : content;
            return (ObjectNode) om.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("笔记内容不是合法的 EditorJS JSON");
        }
    }

    private static ArrayNode blocks(ObjectNode root) {
        return root.has("blocks") && root.get("blocks").isArray()
                ? (ArrayNode) root.get("blocks") : root.putArray("blocks");
    }

    private String write(ObjectNode root) {
        try {
            return om.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 和 Editor.js 自己生成的 id 同长度同字符集，前端按这个 id 插入后两边一致 */
    private static String newBlockId() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        return sb.toString();
    }
}
