/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AgentDocumentContent;
import com.entropybits.worknotes.spring_boot.dto.AgentDocumentSummary;
import com.entropybits.worknotes.spring_boot.dto.AgentImageRef;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * agent 的 read_note/read_clip/view_image 工具专用：按 ID 或标题读取用户任意一篇笔记/收藏的
 * 全文和其中的图片。之前 agent 只能读"当前笔记"、"当前笔记关联的素材"、"当前项目的笔记"，
 * 用户在对话里点名别的笔记时只能靠语义检索拿到零散片段（常常只命中标题块），读不到正文；
 * 看图也只能看当前笔记里 EditorJS 格式的图片块。
 * 归属校验在这里做：内部接口只认 username，ID 必须属于这个用户，否则一律当作不存在。
 */
@Service
public class AgentDocumentService {

    private static final int TITLE_MATCH_LIMIT = 10;
    // 视觉模型单图上限普遍在 10~20MB，base64 后还要再胀 1/3，这里取保守值
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_ATTR = Pattern.compile(
            "\\b(data-src|src|alt)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern MD_IMG = Pattern.compile("!\\[([^\\]]*)\\]\\(\\s*([^)\\s]+)[^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final SourceClipRepository clipRepository;
    private final ContentChunkingService chunkingService;
    private final MediaBlockService mediaBlockService;
    private final ObjectMapper objectMapper;

    public AgentDocumentService(UserRepository userRepository, NoteRepository noteRepository,
                                SourceClipRepository clipRepository, ContentChunkingService chunkingService,
                                MediaBlockService mediaBlockService, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.clipRepository = clipRepository;
        this.chunkingService = chunkingService;
        this.mediaBlockService = mediaBlockService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentDocumentSummary> findNotesByTitle(String username, String titleKeyword) {
        User user = getUser(username);
        PageRequest page = PageRequest.of(0, TITLE_MATCH_LIMIT, Sort.by(Sort.Direction.DESC, "modifiedAt"));
        return noteRepository.findByOwnerAndTitleContaining(user, titleKeyword.trim(), page).stream()
                .map(n -> new AgentDocumentSummary("note", n.getId(), n.getTitle()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentDocumentContent readNote(String username, Long noteId) {
        Note note = loadOwnedNote(username, noteId);
        return new AgentDocumentContent("note", note.getId(), note.getTitle(),
                bodyText(note.getTitle(), chunkingService.chunkNote(note)), noteImages(note).size());
    }

    @Transactional(readOnly = true)
    public AgentDocumentContent readClip(String username, Long clipId) {
        SourceClip clip = loadOwnedClip(username, clipId);
        return new AgentDocumentContent("clip", clip.getId(), clip.getTitle(),
                bodyText(clip.getTitle(), chunkingService.chunkClip(clip)), clipImages(clip).size());
    }

    @Transactional(readOnly = true)
    public List<AgentImageRef> listNoteImages(String username, Long noteId) {
        return noteImages(loadOwnedNote(username, noteId));
    }

    @Transactional(readOnly = true)
    public List<AgentImageRef> listClipImages(String username, Long clipId) {
        return clipImages(loadOwnedClip(username, clipId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadNoteImage(String username, Long noteId, int index) {
        return loadImage(pick(listNoteImages(username, noteId), index));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadClipImage(String username, Long clipId, int index) {
        return loadImage(pick(listClipImages(username, clipId), index));
    }

    /** chunkNote/chunkClip 会把标题单独插成第一块（为检索服务），读全文时标题已单独返回，去掉以免重复 */
    private static String bodyText(String title, List<String> chunks) {
        List<String> body = !chunks.isEmpty() && title != null && chunks.get(0).equals(title.trim())
                ? chunks.subList(1, chunks.size())
                : chunks;
        return String.join("\n", body);
    }

    private List<AgentImageRef> noteImages(Note note) {
        String contentType = note.getContentType() == null ? "richtext" : note.getContentType();
        List<String[]> found = new ArrayList<>();
        if ("editorjs".equals(contentType)) {
            collectEditorjsImages(note.getContent(), found);
        } else {
            collectInlineImages(note.getContent(), found);
        }
        return toRefs(found);
    }

    private List<AgentImageRef> clipImages(SourceClip clip) {
        List<String[]> found = new ArrayList<>();
        collectInlineImages(clip.getContent(), found);
        return toRefs(found);
    }

    /**
     * 图片块（@editorjs/image，URL 在 data.file.url，或平铺在 data.url）之外，markdown 块、
     * 段落里也可能内嵌图片（导入的内容常见），所以其余块的所有文本字段也扫一遍。
     */
    private void collectEditorjsImages(String content, List<String[]> found) {
        if (content == null || content.isBlank()) return;
        JsonNode blocks;
        try {
            blocks = objectMapper.readTree(content).path("blocks");
        } catch (IOException e) {
            return;
        }
        for (JsonNode block : blocks) {
            JsonNode data = block.path("data");
            if ("image".equals(block.path("type").asText())) {
                String url = data.path("file").path("url").asText("");
                if (url.isBlank()) url = data.path("url").asText("");
                found.add(new String[]{url, stripHtml(data.path("caption").asText(""))});
            } else {
                collectTextValues(data, text -> collectInlineImages(text, found));
            }
        }
    }

    private static void collectTextValues(JsonNode node, java.util.function.Consumer<String> sink) {
        if (node.isTextual()) {
            sink.accept(node.asText());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectTextValues(child, sink));
        }
    }

    /** HTML 的 &lt;img&gt; 和 markdown 的 ![]() 可能混在同一段内容里，按出现位置排序 */
    private static void collectInlineImages(String text, List<String[]> found) {
        if (text == null || text.isBlank()) return;
        List<Map.Entry<Integer, String[]>> hits = new ArrayList<>();

        Matcher tag = IMG_TAG.matcher(text);
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
            if (url != null) hits.add(Map.entry(tag.start(), new String[]{url, alt}));
        }

        Matcher md = MD_IMG.matcher(text);
        while (md.find()) {
            hits.add(Map.entry(md.start(), new String[]{md.group(2), md.group(1)}));
        }

        hits.sort(Comparator.comparing(Map.Entry::getKey));
        hits.forEach(h -> found.add(h.getValue()));
    }

    private static List<AgentImageRef> toRefs(List<String[]> found) {
        List<AgentImageRef> refs = new ArrayList<>();
        for (String[] f : found) {
            String url = f[0] == null ? "" : f[0].trim();
            if (!isUsableImageUrl(url)) continue;
            refs.add(new AgentImageRef(refs.size() + 1, url, f[1] == null ? "" : f[1].trim()));
        }
        return refs;
    }

    /** 过滤掉 about:blank、飞书导入时没解析成功的 feishu-image: 占位符等拿不到图的地址 */
    private static boolean isUsableImageUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/") || url.startsWith("data:image/");
    }

    private static AgentImageRef pick(List<AgentImageRef> images, int index) {
        if (index < 1 || index > images.size()) {
            throw new BadRequestException("没有第 " + index + " 张图片（共 " + images.size() + " 张）");
        }
        return images.get(index - 1);
    }

    /**
     * 本地上传的图片读字节转 base64（模型服务访问不到本机地址）；外链图片只回 URL，交给模型
     * 服务自己去取——不在这里代为下载，避免内部接口变成可以请求任意地址的代理。
     */
    private Map<String, Object> loadImage(AgentImageRef image) {
        String url = image.url();
        Map<String, Object> result = new LinkedHashMap<>();
        if (url.startsWith("data:image/")) {
            int comma = url.indexOf(',');
            if (comma < 0 || !url.substring(0, comma).endsWith(";base64")) {
                throw new BadRequestException("不支持的内嵌图片格式");
            }
            result.put("mimeType", url.substring("data:".length(), url.indexOf(';')));
            result.put("base64Data", url.substring(comma + 1));
            return result;
        }

        Path local = url.contains("/uploads/") ? tryResolveLocal(url) : null;
        if (local != null && Files.exists(local)) {
            try {
                if (Files.size(local) > MAX_IMAGE_BYTES) {
                    throw new BadRequestException("图片过大，暂不支持分析");
                }
                result.put("mimeType", MediaBlockService.guessMimeType(local.getFileName().toString()));
                result.put("base64Data", Base64.getEncoder().encodeToString(Files.readAllBytes(local)));
                return result;
            } catch (IOException e) {
                throw new RuntimeException("读取图片失败: " + e.getMessage(), e);
            }
        }
        // 外链里也可能带 /uploads/（比如 WordPress 的 wp-content/uploads），本地找不到就当外链处理
        if (url.startsWith("http://") || url.startsWith("https://")) {
            result.put("url", url);
            return result;
        }
        throw new ResourceNotFoundException("图片文件不存在: " + url);
    }

    private Path tryResolveLocal(String url) {
        try {
            return mediaBlockService.resolveLocalFilePath(url);
        } catch (BadRequestException e) {
            return null;
        }
    }

    private static String stripHtml(String html) {
        return HTML_TAG.matcher(html).replaceAll("").trim();
    }

    private Note loadOwnedNote(String username, Long noteId) {
        User user = getUser(username);
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在"));
    }

    private SourceClip loadOwnedClip(String username, Long clipId) {
        User user = getUser(username);
        return clipRepository.findById(clipId)
                .filter(c -> c.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("收藏不存在"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
