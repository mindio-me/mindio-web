/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记/收藏正文里"有哪些图、第 N 张在哪"的唯一实现。agent 读图（view_image、read_note 的
 * imageCount）和写图（delete_image、set_image_caption）共用这一套序号规则——两边各写一份
 * 的话，过滤规则稍有出入，"第 3 张"在读和写两侧就会指向不同的图。
 */
final class AgentImageLocator {

    /**
     * @param markup     Markdown/HTML 里这张图的原始标记（Editor.js 图片块为 null）
     * @param start      markup 在正文里的起始偏移（Editor.js 为 -1）
     * @param end        markup 在正文里的结束偏移（不含，Editor.js 为 -1）
     * @param blockId    Editor.js 里所在块的 id（Markdown/HTML 为 null）
     * @param standalone 是否是独立的 Editor.js image 块（只有这种能单独删除/改图注）
     */
    record LocatedImage(String url, String caption, String markup, int start, int end,
                        String blockId, boolean standalone) {}

    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_ATTR = Pattern.compile(
            "\\b(data-src|src|alt)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern MD_IMG = Pattern.compile("!\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)[^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private AgentImageLocator() {}

    static List<LocatedImage> locateInNote(String contentType, String content, ObjectMapper om) {
        return "editorjs".equals(contentType) ? locateInEditorjs(content, om) : locateInline(content);
    }

    /** HTML 的 &lt;img&gt; 和 markdown 的 ![]() 可能混在同一段内容里，按出现位置排序 */
    static List<LocatedImage> locateInline(String content) {
        List<LocatedImage> found = new ArrayList<>();
        if (content == null || content.isBlank()) return found;

        Matcher tag = IMG_TAG.matcher(content);
        while (tag.find()) {
            String src = null, dataSrc = null, alt = "";
            Matcher attr = IMG_ATTR.matcher(tag.group());
            while (attr.find()) {
                switch (attr.group(1).toLowerCase()) {
                    case "data-src" -> dataSrc = attr.group(2);
                    case "src" -> src = attr.group(2);
                    default -> alt = attr.group(2);
                }
            }
            // 公众号文章的真实地址在 data-src，src 往往是懒加载占位图
            String url = dataSrc != null && !dataSrc.isBlank() ? dataSrc : src;
            if (url != null) {
                found.add(new LocatedImage(url.trim(), alt.trim(), tag.group(), tag.start(), tag.end(), null, false));
            }
        }

        Matcher md = MD_IMG.matcher(content);
        while (md.find()) {
            found.add(new LocatedImage(md.group(2).trim(), md.group(1).trim(), md.group(), md.start(), md.end(),
                    null, false));
        }

        found.sort(Comparator.comparingInt(LocatedImage::start));
        return found.stream().filter(i -> isUsableImageUrl(i.url())).toList();
    }

    /**
     * 图片块（@editorjs/image，URL 在 data.file.url，或平铺在 data.url）之外，markdown 块、
     * 段落里也可能内嵌图片（导入的内容常见），所以其余块的所有文本字段也扫一遍。
     */
    private static List<LocatedImage> locateInEditorjs(String content, ObjectMapper om) {
        List<LocatedImage> found = new ArrayList<>();
        if (content == null || content.isBlank()) return found;
        JsonNode blocks;
        try {
            blocks = om.readTree(content).path("blocks");
        } catch (IOException e) {
            return found;
        }
        for (JsonNode block : blocks) {
            String blockId = block.path("id").asText(null);
            JsonNode data = block.path("data");
            if ("image".equals(block.path("type").asText())) {
                String url = data.path("file").path("url").asText("");
                if (url.isBlank()) url = data.path("url").asText("");
                url = url.trim();
                if (isUsableImageUrl(url)) {
                    found.add(new LocatedImage(url, stripHtml(data.path("caption").asText("")), null, -1, -1,
                            blockId, true));
                }
            } else {
                List<String> texts = new ArrayList<>();
                collectTextValues(data, texts::add);
                for (String text : texts) {
                    for (LocatedImage img : locateInline(text)) {
                        found.add(new LocatedImage(img.url(), img.caption(), img.markup(), -1, -1, blockId, false));
                    }
                }
            }
        }
        return found;
    }

    static void collectTextValues(JsonNode node, Consumer<String> sink) {
        if (node.isTextual()) {
            sink.accept(node.asText());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectTextValues(child, sink));
        }
    }

    /** 过滤掉 about:blank、飞书导入时没解析成功的 feishu-image: 占位符等拿不到图的地址 */
    static boolean isUsableImageUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/") || url.startsWith("data:image/");
    }

    static String stripHtml(String html) {
        return HTML_TAG.matcher(html).replaceAll("").replace("&nbsp;", " ").trim();
    }
}
