/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供 agent 的图片/语音分析工具用：读笔记里的媒体 block（不需要整篇笔记内容）、拉文件
 * 字节、把分析结果窄范围写回单个字段。和 TopicBlockService（只能"追加条目"）是两条
 * 平行的窄接口，职责不同不合并：那边服务 item 数组型 block，这边服务标量字段型 block。
 * 详见 docs/superpowers/specs/2026-09-18-media-analysis-agent-tools-design.md。
 */
@Service
@RequiredArgsConstructor
public class MediaBlockService {

    private static final Set<String> MEDIA_BLOCK_TYPES = Set.of("image", "audio", "audioRecord");
    private static final Set<String> LIST_TEXT_FIELDS = Set.of("caption", "transcript", "summary");
    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024; // 25MB
    private static final Map<String, Set<String>> PATCHABLE_FIELDS = Map.of(
            "image", Set.of("caption"),
            "audio", Set.of("transcript", "summary"),
            "audioRecord", Set.of("transcript", "summary")
    );

    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;
    private final UploadPathConfig uploadPathConfig;

    public List<Map<String, Object>> listMediaBlocks(Long noteId) {
        ArrayNode blocks = loadBlocks(noteId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (JsonNode block : blocks) {
            String type = block.path("type").asText();
            if (!MEDIA_BLOCK_TYPES.contains(type) || !block.hasNonNull("id")) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("blockId", block.get("id").asText());
            entry.put("blockType", type);
            JsonNode data = block.path("data");
            String url = resolveUrl(data);
            if (!url.isBlank()) {
                entry.put("url", url);
            }
            for (String field : LIST_TEXT_FIELDS) {
                if (data.hasNonNull(field) && !data.get(field).asText().isBlank()) {
                    entry.put(field, data.get(field).asText());
                }
            }
            result.add(entry);
        }
        return result;
    }

    public Map<String, Object> getBlockFile(Long noteId, String blockId) {
        JsonNode block = findBlockOrThrow(loadBlocks(noteId), blockId);
        String url = resolveUrl(block.path("data"));
        if (url.isBlank()) {
            throw new BadRequestException("这个块还没有上传文件");
        }

        Path filePath = resolveLocalFilePath(url);
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("文件不存在: " + url);
        }
        try {
            long size = Files.size(filePath);
            if (size > MAX_FILE_SIZE_BYTES) {
                throw new BadRequestException("文件过大，暂不支持分析");
            }
            byte[] bytes = Files.readAllBytes(filePath);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mimeType", guessMimeType(filePath.getFileName().toString()));
            result.put("base64Data", Base64.getEncoder().encodeToString(bytes));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> patchBlock(Long noteId, String blockId, Map<String, Object> fields) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在或已被删除"));
        if (!"editorjs".equals(note.getContentType())) {
            throw new BadRequestException("这篇笔记不是 EditorJS 格式，无法写入分析结果");
        }

        try {
            String rawContent = note.getContent() == null || note.getContent().isBlank()
                    ? "{\"blocks\":[]}" : note.getContent();
            ObjectNode root = (ObjectNode) objectMapper.readTree(rawContent);
            ArrayNode blocks = root.has("blocks") && root.get("blocks").isArray()
                    ? (ArrayNode) root.get("blocks") : root.putArray("blocks");

            ObjectNode targetBlock = (ObjectNode) findBlockOrThrow(blocks, blockId);
            String type = targetBlock.path("type").asText();
            Set<String> allowed = PATCHABLE_FIELDS.get(type);
            if (allowed == null) {
                throw new BadRequestException("不支持写入分析结果的块类型: " + type);
            }
            for (String key : fields.keySet()) {
                if (!allowed.contains(key)) {
                    throw new BadRequestException("字段 " + key + " 不允许写入 " + type + " 块");
                }
            }

            ObjectNode data = targetBlock.has("data") && targetBlock.get("data").isObject()
                    ? (ObjectNode) targetBlock.get("data") : targetBlock.putObject("data");
            fields.forEach((key, value) -> data.put(key, value == null ? "" : String.valueOf(value)));

            note.setContent(objectMapper.writeValueAsString(root));
            noteRepository.save(note);

            return objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (BadRequestException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("写入分析结果失败: " + e.getMessage(), e);
        }
    }

    private ArrayNode loadBlocks(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在或已被删除"));
        if (!"editorjs".equals(note.getContentType())) {
            throw new BadRequestException("这篇笔记不是 EditorJS 格式");
        }
        try {
            String rawContent = note.getContent() == null || note.getContent().isBlank()
                    ? "{\"blocks\":[]}" : note.getContent();
            JsonNode root = objectMapper.readTree(rawContent);
            return root.has("blocks") && root.get("blocks").isArray()
                    ? (ArrayNode) root.get("blocks") : objectMapper.createArrayNode();
        } catch (Exception e) {
            throw new RuntimeException("解析笔记内容失败: " + e.getMessage(), e);
        }
    }

    private JsonNode findBlockOrThrow(ArrayNode blocks, String blockId) {
        for (JsonNode block : blocks) {
            if (blockId.equals(block.path("id").asText(null))) {
                return block;
            }
        }
        throw new ResourceNotFoundException("找不到这个块: " + blockId);
    }

    private Path resolveLocalFilePath(String url) {
        int idx = url.indexOf("/uploads/");
        if (idx < 0) {
            throw new BadRequestException("无法解析文件路径: " + url);
        }
        String relative = url.substring(idx + "/uploads/".length());
        Path root = Paths.get(uploadPathConfig.getUploadPath()).toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("非法的文件路径: " + url);
        }
        return resolved;
    }

    /**
     * image block 来自官方 @editorjs/image，URL 在 data.file.url；audio/audioRecord 是本
     * 项目自研 tool，URL 平铺在 data.url。两种形状都要兼容（优先嵌套，回退平铺）。
     */
    private static String resolveUrl(JsonNode data) {
        String fileUrl = data.path("file").path("url").asText("");
        if (!fileUrl.isBlank()) {
            return fileUrl;
        }
        return data.path("url").asText("");
    }

    private static String guessMimeType(String filename) {
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "heic" -> "image/heic";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "flac" -> "audio/flac";
            case "aac" -> "audio/aac";
            case "opus" -> "audio/opus";
            case "wma" -> "audio/x-ms-wma";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";
        };
    }
}
