/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把一条录音转写记录变成纯文本单元，两种用途：
 * - indexUnits：进向量检索。概述 + 优化版段落，换人说话处加"名字："前缀；不带时间戳（只会给向量添噪音）。
 * - readingUnits：给 AI 助手读全文。场景、概述、要点、章节、待办 + 逐句原文按说话人分轮次，
 *   每行带 [mm:ss 名字]，AI 才答得了"谁说了什么""第几分钟讲到"。用原文而不是优化版：
 *   优化版段落不区分说话人，归属可能错。
 * JSON 坏了的字段跳过，不影响其余部分。
 */
class TranscriptTextFormatter {

    /** 同一说话人连续句子合成一行，超过这个长度另起一行并重新标时间 */
    static final int TURN_MAX_CHARS = 300;

    private static final Pattern SPEAKER_REF = Pattern.compile("\\{\\{S(\\d+)\\}\\}");
    private static final Map<String, String> SCENE_NAMES = Map.of(
            "MEETING", "会议", "LECTURE", "课堂", "INTERVIEW", "访谈", "MEMO", "闪念", "SYSTEM_AUDIO", "内录");

    private final ObjectMapper objectMapper;

    TranscriptTextFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<String> indexUnits(AudioTranscription t) {
        Map<String, String> names = speakerNames(t);
        List<String> units = new ArrayList<>();
        JsonNode summary = read(t.getSummary());
        String overview = summary.path("overview").asText("");
        if (!overview.isBlank()) units.add(replaceSpeakerRefs(overview, names));

        List<JsonNode> utts = sentences(read(t.getUtterances()));
        boolean multi = multiSpeaker(utts);
        JsonNode polished = read(t.getPolished());
        if (polished.isArray() && !polished.isEmpty()) {
            Map<Long, Integer> speakerByStart = new HashMap<>();
            utts.forEach(u -> { if (u.hasNonNull("speaker")) speakerByStart.putIfAbsent(u.path("start").asLong(), u.path("speaker").asInt()); });
            Integer last = null;
            for (JsonNode p : polished) {
                String text = p.path("text").asText("").trim();
                if (text.isEmpty() || text.startsWith("[第 ")) continue;
                Integer sp = multi ? speakerByStart.get(p.path("start").asLong()) : null;
                units.add(sp != null && !sp.equals(last) ? speakerName(sp, names) + "：" + text : text);
                if (sp != null) last = sp;
            }
        } else {
            for (Turn turn : turns(utts)) {
                units.add(multi && turn.speaker != null ? speakerName(turn.speaker, names) + "：" + turn.text : turn.text);
            }
        }
        return units;
    }

    List<String> readingUnits(AudioTranscription t) {
        Map<String, String> names = speakerNames(t);
        List<String> units = new ArrayList<>();
        units.add(t.getScene() == null ? "【录音转写】"
                : "【录音转写】场景：" + SCENE_NAMES.getOrDefault(t.getScene().name(), t.getScene().name()));

        JsonNode summary = read(t.getSummary());
        String overview = summary.path("overview").asText("");
        if (!overview.isBlank()) units.add("概述：" + replaceSpeakerRefs(overview, names));
        for (JsonNode kp : summary.path("keyPoints")) {
            String topic = kp.path("topic").asText("").trim();
            for (JsonNode p : kp.path("points")) {
                String point = replaceSpeakerRefs(p.asText("").replace("**", ""), names).trim();
                if (!point.isEmpty()) units.add("要点：" + (topic.isEmpty() ? "" : topic + "：") + point);
            }
        }
        for (JsonNode c : summary.path("chapters")) {
            String title = c.path("title").asText("").trim();
            if (!title.isEmpty()) units.add("章节：[" + fmt(c.path("start").asLong()) + "] " + replaceSpeakerRefs(title, names));
        }
        for (JsonNode todo : summary.path("todos")) {
            String text = replaceSpeakerRefs(todo.asText(""), names).trim();
            if (!text.isEmpty()) units.add("待办：" + text);
        }

        List<JsonNode> utts = sentences(read(t.getUtterances()));
        boolean multi = multiSpeaker(utts);
        if (!utts.isEmpty()) units.add("原文：");
        for (Turn turn : turns(utts)) {
            String who = multi && turn.speaker != null ? " " + speakerName(turn.speaker, names) : "";
            units.add("[" + fmt(turn.start) + who + "] " + turn.text);
        }
        return units;
    }

    /** 总结里的 {{S1}} 换成说话人名字，没改过名的写"说话人 1"（与前端 speakerText 一致） */
    String replaceSpeakerRefs(String text, Map<String, String> names) {
        Matcher m = SPEAKER_REF.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(speakerName(Integer.parseInt(m.group(1)), names)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    Map<String, String> speakerNames(AudioTranscription t) {
        Map<String, String> names = new HashMap<>();
        read(t.getSpeakerNames()).fields().forEachRemaining(e -> {
            String v = e.getValue().asText("").trim();
            if (!v.isEmpty()) names.put(e.getKey(), v);
        });
        return names;
    }

    private record Turn(Integer speaker, long start, String text) {}

    /** 同一说话人的连续句子合成一轮，超过 TURN_MAX_CHARS 另起一轮 */
    private static List<Turn> turns(List<JsonNode> utts) {
        List<Turn> turns = new ArrayList<>();
        Integer speaker = null;
        long start = 0;
        StringBuilder sb = new StringBuilder();
        for (JsonNode u : utts) {
            Integer sp = u.hasNonNull("speaker") ? u.path("speaker").asInt() : null;
            String text = u.path("text").asText("").trim();
            boolean sameTurn = !sb.isEmpty() && java.util.Objects.equals(sp, speaker)
                    && sb.length() + text.length() <= TURN_MAX_CHARS;
            if (!sameTurn) {
                if (!sb.isEmpty()) turns.add(new Turn(speaker, start, sb.toString()));
                sb.setLength(0);
                speaker = sp;
                start = u.path("start").asLong();
            }
            sb.append(text);
        }
        if (!sb.isEmpty()) turns.add(new Turn(speaker, start, sb.toString()));
        return turns;
    }

    /** 逐句原文里的有效句子：去掉空句和"[第 N 段转写失败]"占位 */
    private static List<JsonNode> sentences(JsonNode arr) {
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode u : arr) {
            String text = u.path("text").asText("").trim();
            if (!text.isEmpty() && !text.startsWith("[第 ")) list.add(u);
        }
        return list;
    }

    private static boolean multiSpeaker(List<JsonNode> utts) {
        Set<Integer> speakers = new HashSet<>();
        utts.forEach(u -> { if (u.hasNonNull("speaker")) speakers.add(u.path("speaker").asInt()); });
        return speakers.size() > 1;
    }

    private static String speakerName(int n, Map<String, String> names) {
        String name = names.get(String.valueOf(n));
        return name != null ? name : "说话人 " + n;
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) return objectMapper.missingNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.missingNode();
        }
    }
}
