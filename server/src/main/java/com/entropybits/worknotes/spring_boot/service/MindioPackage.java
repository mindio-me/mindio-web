/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * .mindio 笔记包的数据结构。包里只放笔记自身内容；本机 id、owner、标签、项目、language、isPublic
 * 不出包（覆盖导入时这些都保持本机的值）。详见 docs/superpowers/specs/2026-10-12-editorjs-note-export-import-design.md。
 */
public final class MindioPackage {

    public static final String FORMAT = "mindio-notes";
    public static final int FORMAT_VERSION = 1;

    public record MissingAsset(String noteUid, String url) {}

    public record Manifest(String format, int formatVersion, String exportedAt, String exportedFrom,
                           int noteCount, List<MissingAsset> missingAssets) {}

    public record Section(String type, String content) {}

    public record PackagedTranscription(String blockId, String stage, String utterances, String polished,
                                        String summary, String createdAt, String completedAt) {}

    public record PackagedNote(String uid, String title, String summary, String createdAt, String modifiedAt,
                               String contentType, String content, List<Section> sections,
                               List<PackagedTranscription> transcriptions) {
        @JsonCreator
        public PackagedNote {
            // 只把 null 规范化为空列表；元素为 null 的列表原样保留，交给读包校验拒绝
            transcriptions = transcriptions == null ? List.of() : transcriptions;
        }

        /** 不带转写记录 */
        public PackagedNote(String uid, String title, String summary, String createdAt, String modifiedAt,
                            String contentType, String content, List<Section> sections) {
            this(uid, title, summary, createdAt, modifiedAt, contentType, content, sections, List.of());
        }
    }

    private MindioPackage() {}

    /** 实体里的 LocalDateTime 按 UTC 存（见 Note.prePersist），包里写成带 Z 的 Instant 字符串 */
    public static String toIso(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC).toString();
    }

    public static LocalDateTime fromIso(String iso) {
        if (iso == null || iso.isBlank()) return null;
        return OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static List<Section> sectionsOf(Note note) {
        List<String> types = note.getSectionTypes();
        List<String> contents = note.getSectionContents();
        int n = Math.min(types.size(), contents.size());
        List<Section> sections = new ArrayList<>(n);
        for (int i = 0; i < n; i++) sections.add(new Section(types.get(i), contents.get(i)));
        return sections;
    }

    public static PackagedNote fromNote(Note note, String uid, String content, List<Section> sections) {
        return fromNote(note, uid, content, sections, List.of());
    }

    public static PackagedNote fromNote(Note note, String uid, String content, List<Section> sections,
                                        List<PackagedTranscription> transcriptions) {
        return new PackagedNote(uid, note.getTitle(), note.getSummary(), toIso(note.getCreatedAt()),
                toIso(note.getModifiedAt()), note.getContentType(), content, sections, transcriptions);
    }

    /** 取 editorjs 正文 blocks[].id；JSON 不合法时返回空集 */
    public static Set<String> blockIds(ObjectMapper om, String editorjsJson) {
        Set<String> ids = new LinkedHashSet<>();
        if (editorjsJson == null || editorjsJson.isBlank()) return ids;
        try {
            JsonNode root = om.readTree(editorjsJson);
            if (root == null) return ids;
            for (JsonNode block : root.path("blocks")) {
                JsonNode id = block.get("id");
                if (id != null && id.isTextual()) ids.add(id.asText());
            }
        } catch (JsonProcessingException e) {
            return ids;
        }
        return ids;
    }
}
