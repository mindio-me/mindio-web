/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 Obsidian/Typora 导出的方言归一化成 commonmark 认识的写法，交给 MarkdownToEditorjsConverter：
 * 文首 YAML 头、块级 $$ 公式变成带语言标记的围栏代码块（yaml / math），图片嵌入 ![[x.png]]
 * 变成普通图片语法，其余 [[wikilink]] 退化成纯文本。围栏代码块、缩进代码块和行内代码内部原样保留。
 */
final class MarkdownSourceNormalizer {

    private static final Pattern FENCE_OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
    private static final Pattern INDENTED = Pattern.compile("^(?: {4}|\\t| {1,3}\\t)");
    private static final Pattern LIST_ITEM = Pattern.compile("^ {0,3}(?:[-*+]|\\d{1,9}[.)])(?:\\s|$)");
    private static final Pattern FRONTMATTER_KEY = Pattern.compile("^[A-Za-z_][\\w-]*\\s*:");
    private static final Pattern ONE_LINE_MATH = Pattern.compile("^\\s*\\$\\$(.+?)\\$\\$\\s*$");
    private static final Pattern IMAGE_EMBED = Pattern.compile(
            "!\\[\\[([^\\]|#]+?\\.(?:png|jpe?g|gif|webp|svg|bmp))(?:[|#][^\\]]*)?\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKILINK =
            Pattern.compile("!?\\[\\[([^\\]|#]+)(?:#[^\\]|]*)?(?:\\|([^\\]]+))?\\]\\]");

    private MarkdownSourceNormalizer() {}

    static String normalize(String markdown) {
        if (markdown.startsWith("﻿")) markdown = markdown.substring(1);
        List<String> lines = Arrays.asList(markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        List<String> out = new ArrayList<>();
        int i = frontmatter(lines, out);
        String fence = null;
        boolean prevBlank = true;
        boolean inList = false;
        boolean inIndentedCode = false;
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            if (fence != null) {
                out.add(line);
                if (closesFence(line, fence)) fence = null;
                continue;
            }
            boolean blank = line.isBlank();
            boolean indented = !blank && INDENTED.matcher(line).find();
            // 缩进代码块：前一行空行（不能打断段落）且不在列表里（列表里的缩进行是子项/续段）；空行不打断它
            if (indented && (inIndentedCode || (prevBlank && !inList))) {
                inIndentedCode = true;
                out.add(line);
                prevBlank = false;
                continue;
            }
            if (!blank && !indented) {
                inIndentedCode = false;
                inList = LIST_ITEM.matcher(line).find() || (inList && !prevBlank);
            }
            prevBlank = blank;
            Matcher open = FENCE_OPEN.matcher(line);
            if (open.find()) {
                fence = open.group(1);
                out.add(line);
                continue;
            }
            if (line.trim().equals("$$")) {
                int end = indexOfTrimmed(lines, "$$", i + 1);
                if (end > 0) {
                    out.add("```math");
                    out.addAll(lines.subList(i + 1, end));
                    out.add("```");
                    i = end;
                    continue;
                }
            }
            Matcher math = ONE_LINE_MATH.matcher(line);
            if (math.matches()) {
                out.add("```math");
                out.add(math.group(1).trim());
                out.add("```");
                continue;
            }
            out.add(links(line));
        }
        return String.join("\n", out);
    }

    /** 文首 --- 到下一个 ---（或 ...）之间至少有一行 key: 才算 YAML 头，否则可能只是开头的分隔线；返回正文起始行 */
    private static int frontmatter(List<String> lines, List<String> out) {
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) return 0;
        int end = -1;
        for (int j = 1; j < lines.size(); j++) {
            String t = lines.get(j).trim();
            if (t.equals("---") || t.equals("...")) {
                end = j;
                break;
            }
        }
        if (end < 0) return 0;
        List<String> body = lines.subList(1, end);
        if (body.stream().noneMatch(l -> FRONTMATTER_KEY.matcher(l).find())) return 0;
        out.add("```yaml");
        out.addAll(body);
        out.add("```");
        return end + 1;
    }

    private static boolean closesFence(String line, String fence) {
        String t = line.trim();
        return t.length() >= fence.length() && t.chars().allMatch(ch -> ch == fence.charAt(0));
    }

    private static int indexOfTrimmed(List<String> lines, String target, int from) {
        for (int j = from; j < lines.size(); j++) {
            if (lines.get(j).trim().equals(target)) return j;
        }
        return -1;
    }

    /** 只改写行内代码（`...`）之外的部分；开头的反引号串找不到等长的闭合串时按普通字符处理 */
    private static String links(String line) {
        StringBuilder out = new StringBuilder();
        int start = 0;
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) != '`') {
                i++;
                continue;
            }
            int runEnd = backtickRunEnd(line, i);
            int close = findClosingRun(line, runEnd, runEnd - i);
            if (close < 0) {
                i = runEnd;
                continue;
            }
            out.append(linksOutsideCode(line.substring(start, i)));
            int spanEnd = close + (runEnd - i);
            out.append(line, i, spanEnd);
            start = i = spanEnd;
        }
        out.append(linksOutsideCode(line.substring(start)));
        return out.toString();
    }

    private static int backtickRunEnd(String line, int from) {
        int j = from;
        while (j < line.length() && line.charAt(j) == '`') j++;
        return j;
    }

    private static int findClosingRun(String line, int from, int length) {
        int j = from;
        while (j < line.length()) {
            if (line.charAt(j) != '`') {
                j++;
                continue;
            }
            int end = backtickRunEnd(line, j);
            if (end - j == length) return j;
            j = end;
        }
        return -1;
    }

    private static String linksOutsideCode(String line) {
        String s = IMAGE_EMBED.matcher(line)
                .replaceAll(m -> Matcher.quoteReplacement("![" + m.group(1) + "](<" + m.group(1) + ">)"));
        return WIKILINK.matcher(s)
                .replaceAll(m -> Matcher.quoteReplacement((m.group(2) != null ? m.group(2) : m.group(1)).trim()));
    }
}
