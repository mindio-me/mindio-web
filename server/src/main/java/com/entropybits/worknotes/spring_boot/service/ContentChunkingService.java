/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 把笔记/收藏内容归一化成纯文本，再按结构单元（EditorJS block / 段落）分块，
 * 目标单块 300-800 字，超过 1500 字硬上限才按句子边界二次切分。
 */
@Slf4j
@Service
public class ContentChunkingService {

    private static final int MIN_CHUNK_SIZE = 300;
    private static final int MAX_CHUNK_SIZE = 800;
    private static final int HARD_CAP = 1500;

    private final ObjectMapper objectMapper;
    private final LocalFileExtractionService extractionService;

    public ContentChunkingService(ObjectMapper objectMapper, LocalFileExtractionService extractionService) {
        this.objectMapper = objectMapper;
        this.extractionService = extractionService;
    }

    public List<String> chunkNote(Note note) {
        String contentType = note.getContentType();
        if (contentType == null) {
            contentType = "richtext"; // Default to richtext for null content type
        }
        List<String> units = switch (contentType) {
            case "editorjs" -> editorjsToTextUnits(note.getContent());
            case "richtext" -> htmlToParagraphs(note.getContent());
            default -> markdownToParagraphs(note.getContent());
        };
        List<String> imageOcrUnits = extractImageUrls(note).stream()
                .map(extractionService::findExtractedTextForImageUrl)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        List<String> combined = new ArrayList<>(units);
        combined.addAll(imageOcrUnits);
        return prependTitleChunk(note.getTitle(), mergeAndSplit(combined));
    }

    public List<String> chunkClip(SourceClip clip) {
        List<String> units = "html".equals(clip.getContentFormat())
                ? htmlToParagraphs(clip.getContent())
                : markdownToParagraphs(clip.getContent());
        return prependTitleChunk(clip.getTitle(), mergeAndSplit(units));
    }

    /**
     * 标题单独成一块，不参与 mergeAndSplit 的合并/裁切：如果把标题混进正文一起合并，
     * 短标题会被稀释进几百字的正文分块里，按标题关键词检索时余弦相似度上不去，
     * 命中不了（这也是标题搜索长期搜不到的根因）。单独一块能保证标题关键词有一个
     * 未被稀释的强匹配向量。
     */
    private List<String> prependTitleChunk(String title, List<String> bodyChunks) {
        if (title == null || title.isBlank()) return bodyChunks;
        List<String> result = new ArrayList<>(bodyChunks.size() + 1);
        result.add(title.trim());
        result.addAll(bodyChunks);
        return result;
    }

    /** OCR等场景产出的纯文本，复用markdown的按空行分段逻辑。 */
    public List<String> chunkPlainText(String text) {
        return mergeAndSplit(markdownToParagraphs(text));
    }

    /**
     * 提取笔记正文里引用的图片URL（&lt;img src&gt;或markdown语法）。供chunkNote折入OCR文字，
     * 也供ContentIndexingService维护"笔记→图片hash"的引用关系（NoteImageRef）复用。
     * editorjs的image block这次不处理——现有编辑页只暴露richtext/markdown两种内容类型，
     * editorjs不是可达路径，YAGNI。
     */
    public List<String> extractImageUrls(Note note) {
        String contentType = note.getContentType();
        if (contentType == null) contentType = "richtext";
        if ("editorjs".equals(contentType)) return List.of();
        return extractImageUrls(note.getContent(), "richtext".equals(contentType));
    }

    private List<String> extractImageUrls(String content, boolean isHtml) {
        if (content == null || content.isBlank()) return List.of();
        java.util.regex.Pattern pattern = isHtml
                ? java.util.regex.Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                : java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
    }

    // ---- markdown ----
    List<String> markdownToParagraphs(String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        return Arrays.stream(markdown.split("\n\\s*\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ---- richtext (HTML) ----
    List<String> htmlToParagraphs(String html) {
        if (html == null || html.isBlank()) return List.of();
        // Treat closing tags of block-level elements as boundaries
        String withBreaks = html.replaceAll("(?i)</p>|<br\\s*/?>|</h[1-6]>|</li>|</blockquote>|</tr>|</td>|</th>", "\n");
        String plain = stripHtml(withBreaks);
        return Arrays.stream(plain.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "").trim();
    }

    // ---- EditorJS ----
    @SuppressWarnings("unchecked")
    List<String> editorjsToTextUnits(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) return List.of();
        try {
            Map<String, Object> root = objectMapper.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) root.get("blocks");
            if (blocks == null) return List.of();
            List<String> units = new ArrayList<>();
            for (Map<String, Object> block : blocks) {
                try {
                    String text = extractBlockText(block);
                    if (text != null && !text.isBlank()) units.add(text);
                } catch (Exception e) {
                    // Skip malformed block and continue processing other blocks
                    log.warn("EditorJS block conversion failed, skipping block: {}", e.getMessage());
                }
            }
            return units;
        } catch (Exception e) {
            // 解析失败（脏数据/未知格式）时该篇笔记本次跳过索引，不影响调用方
            log.warn("EditorJS root parsing failed, skipping entire document: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractBlockText(Map<String, Object> block) {
        String type = (String) block.get("type");
        Map<String, Object> data = (Map<String, Object>) block.get("data");
        if (data == null || type == null) return null;
        return switch (type) {
            case "paragraph", "header", "quote" -> stripHtml((String) data.get("text"));
            case "code" -> (String) data.get("code");
            case "markdown" -> (String) data.get("markdown");
            case "list" -> joinListItems((List<Object>) data.get("items"));
            case "table" -> joinTableRows((List<Object>) data.get("content"));
            case "image", "embed", "video", "audio" -> stripHtml((String) data.get("caption"));
            case "references" -> joinReferenceItems((List<Object>) data.get("items"));
            case "mediaGallery" -> joinGalleryCaptions((List<Object>) data.get("items"));
            case "timeline" -> joinTimelineItems((List<Object>) data.get("items"));
            case "checklist" -> joinChecklistItems((List<Object>) data.get("items"));
            case "warning" -> joinWarning(data);
            case "linkTool" -> joinLinkToolMeta(data);
            case "attaches" -> stripHtml((String) data.get("title"));
            default -> null; // delimiter/audioRecord 等没有可索引文本的 block 类型
        };
    }

    @SuppressWarnings("unchecked")
    private String joinListItems(List<Object> items) {
        if (items == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (item instanceof String s) {
                // 旧版 @editorjs/list 数据格式：items 是纯字符串数组
                sb.append(stripHtml(s)).append("\n");
            } else if (item instanceof Map<?, ?> m) {
                // 新版数据格式：items 是 {content, meta, items(嵌套子列表)} 对象数组
                Object content = m.get("content");
                if (content instanceof String s) sb.append(stripHtml(s)).append("\n");
                Object nested = m.get("items");
                if (nested instanceof List<?> nestedList && !nestedList.isEmpty()) {
                    String nestedText = joinListItems((List<Object>) nestedList);
                    if (nestedText != null) sb.append(nestedText).append("\n");
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private String joinTableRows(List<Object> rows) {
        if (rows == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object row : rows) {
            if (row instanceof List<?> cells) {
                for (Object cell : cells) {
                    sb.append(stripHtml(String.valueOf(cell))).append(" ");
                }
                sb.append("\n");
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String joinReferenceItems(List<Object> items) {
        if (items == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            if (m.get("title") instanceof String s && !s.isBlank()) sb.append(s).append("\n");
            if (m.get("note") instanceof String s && !s.isBlank()) sb.append(s).append("\n");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String joinGalleryCaptions(List<Object> items) {
        if (items == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            if (m.get("caption") instanceof String s && !s.isBlank()) sb.append(s).append("\n");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String joinTimelineItems(List<Object> items) {
        if (items == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            StringBuilder line = new StringBuilder();
            if (m.get("date") instanceof String s && !s.isBlank()) line.append(s).append(" ");
            if (m.get("title") instanceof String s && !s.isBlank()) line.append(s);
            if (!line.isEmpty()) sb.append(line).append("\n");
            if (m.get("description") instanceof String s && !s.isBlank()) sb.append(s).append("\n");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String joinChecklistItems(List<Object> items) {
        if (items == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            if (!(m.get("text") instanceof String text) || text.isBlank()) continue;
            boolean checked = Boolean.TRUE.equals(m.get("checked"));
            sb.append(checked ? "[x] " : "[ ] ").append(stripHtml(text)).append("\n");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private String joinWarning(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        if (data.get("title") instanceof String s && !s.isBlank()) sb.append(stripHtml(s)).append("\n");
        if (data.get("message") instanceof String s && !s.isBlank()) sb.append(stripHtml(s)).append("\n");
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private String joinLinkToolMeta(Map<String, Object> data) {
        if (!(data.get("meta") instanceof Map<?, ?> meta)) return null;
        StringBuilder sb = new StringBuilder();
        if (meta.get("title") instanceof String s && !s.isBlank()) sb.append(stripHtml(s)).append("\n");
        if (meta.get("description") instanceof String s && !s.isBlank()) sb.append(stripHtml(s)).append("\n");
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    // ---- 合并小单元 / 二次切分超长单元 ----
    List<String> mergeAndSplit(List<String> units) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String unit : units) {
            if (unit.length() > HARD_CAP) {
                if (!buffer.isEmpty()) {
                    merged.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                merged.addAll(splitBySentence(unit));
                continue;
            }
            if (buffer.length() + unit.length() > MAX_CHUNK_SIZE && buffer.length() >= MIN_CHUNK_SIZE) {
                merged.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            buffer.append(unit).append("\n");
        }
        if (!buffer.isEmpty()) merged.add(buffer.toString().trim());
        return merged;
    }

    private List<String> splitBySentence(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？.!?])")) {
            // 完全没有句末标点的内容（粘贴的 base64/日志/CSV/压缩过的 JSON）整段就是一个"句子"，
            // 不强行硬切的话会原样进入输出，远超 HARD_CAP，进而超出 embedding 接口的输入上限报错
            if (sentence.length() > HARD_CAP) {
                if (!buffer.isEmpty()) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                for (int i = 0; i < sentence.length(); i += HARD_CAP) {
                    result.add(sentence.substring(i, Math.min(i + HARD_CAP, sentence.length())));
                }
                continue;
            }
            if (buffer.length() + sentence.length() > MAX_CHUNK_SIZE && !buffer.isEmpty()) {
                result.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            buffer.append(sentence);
        }
        if (!buffer.isEmpty()) result.add(buffer.toString().trim());
        return result;
    }
}
