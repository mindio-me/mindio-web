/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .mindio 包里笔记之间的引用：本机整数 id 换个安装就失效，包里改用 uid。
 * 两处引用：参考文献块 kind=note 条目的 noteId；文本里的 /workspace/notes/&lt;id&gt; 链接（可带本机 origin）。
 * 导出 toPackage：id → noteUid / /workspace/notes/uid/&lt;uid&gt;；导入 fromPackage：能解析的换回本机 id，
 * 解析不了的保留 uid 形式，由前端 /workspace/notes/uid/:uid 路由在点击时再找。
 * 详见 spec 第 8.1 节。
 */
public class NoteRefRewriter {

    private static final String LOOPBACK_ORIGIN = "https?://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?";
    private static final String NOT_AFTER_HOST = "(?<![\\w.:/-])";
    private static final Pattern UID_LINK =
            Pattern.compile(NOT_AFTER_HOST + "/workspace/notes/uid/(" + MindioPackageReader.UUID_RE + ")");

    private final ObjectMapper om;
    private final Pattern idLink;

    public NoteRefRewriter(ObjectMapper om, Collection<String> extraOrigins) {
        this.om = om;
        StringBuilder origins = new StringBuilder(LOOPBACK_ORIGIN);
        for (String origin : extraOrigins) {
            if (origin != null && !origin.isBlank()) {
                String o = origin.trim();
                origins.append('|').append(Pattern.quote(o.endsWith("/") ? o.substring(0, o.length() - 1) : o));
            }
        }
        this.idLink = Pattern.compile("(?:(?:" + origins + ")|" + NOT_AFTER_HOST + ")/workspace/notes/(\\d{1,18})(?![\\w-])");
    }

    public Set<Long> collectNoteIds(String text, String sectionType) {
        Set<Long> ids = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return ids;
        editNoteItems(text, sectionType, item -> {
            Long id = numericNoteId(item);
            if (id != null) ids.add(id);
            return false;
        });
        Matcher m = idLink.matcher(text);
        while (m.find()) ids.add(Long.parseLong(m.group(1)));
        return ids;
    }

    public String toPackage(String text, String sectionType, Map<Long, String> idToUid) {
        if (text == null || text.isBlank() || idToUid.isEmpty()) return text;
        String out = editNoteItems(text, sectionType, item -> {
            Long id = numericNoteId(item);
            String uid = id == null ? null : idToUid.get(id);
            if (uid == null) return false;
            item.remove("noteId");
            item.put("noteUid", uid);
            return true;
        });
        Matcher m = idLink.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String uid = idToUid.get(Long.parseLong(m.group(1)));
            m.appendReplacement(sb, Matcher.quoteReplacement(uid == null ? m.group() : "/workspace/notes/uid/" + uid));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Set<String> collectNoteUids(String text, String sectionType) {
        Set<String> uids = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return uids;
        editNoteItems(text, sectionType, item -> {
            JsonNode uid = item.get("noteUid");
            if (uid != null && uid.isTextual()) uids.add(uid.asText());
            return false;
        });
        Matcher m = UID_LINK.matcher(text);
        while (m.find()) uids.add(m.group(1));
        return uids;
    }

    public String fromPackage(String text, String sectionType, Map<String, Long> uidToId) {
        if (text == null || text.isBlank() || uidToId.isEmpty()) return text;
        String out = editNoteItems(text, sectionType, item -> {
            JsonNode uid = item.get("noteUid");
            Long id = uid == null ? null : uidToId.get(uid.asText());
            if (id == null) return false;
            item.remove("noteUid");
            item.put("noteId", id);
            return true;
        });
        Matcher m = UID_LINK.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Long id = uidToId.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(id == null ? m.group() : "/workspace/notes/" + id));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Long numericNoteId(ObjectNode item) {
        JsonNode id = item.get("noteId");
        if (id == null) return null;
        if (id.isIntegralNumber()) return id.asLong();
        if (id.isTextual() && id.asText().matches("\\d{1,18}")) return Long.valueOf(id.asText());
        return null;
    }

    /**
     * 对参考文献里 kind=note 的条目逐个调用 edit；edit 返回 true 表示改了。有改动时返回重新序列化的 JSON，
     * 否则原样返回 text（不重排、不改格式）。text 不是合法 JSON 时跳过结构化部分。
     */
    private String editNoteItems(String text, String sectionType, Predicate<ObjectNode> edit) {
        boolean doc = sectionType == null;
        if (!doc && !"references".equals(sectionType)) return text;
        if (!text.contains("\"kind\"")) return text;
        JsonNode root;
        try {
            root = om.readTree(text);
        } catch (JsonProcessingException e) {
            return text;
        }
        if (root == null) return text;
        boolean changed = false;
        if (doc) {
            for (JsonNode block : root.path("blocks")) {
                if ("references".equals(block.path("type").asText())) changed |= editItems(block.path("data"), edit);
            }
        } else {
            changed = editItems(root, edit);
        }
        if (!changed) return text;
        try {
            return om.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean editItems(JsonNode data, Predicate<ObjectNode> edit) {
        boolean changed = false;
        for (JsonNode item : data.path("items")) {
            if (item instanceof ObjectNode o && "note".equals(o.path("kind").asText())) changed |= edit.test(o);
        }
        return changed;
    }
}
