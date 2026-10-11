/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AgentImageRef;
import com.entropybits.worknotes.spring_boot.dto.AgentImageSource;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * agent 的 insert_image/delete_image/set_image_caption 工具：按笔记格式改正文里的图片，
 * 保存后触发重新索引，返回推给前端同步编辑器的事件载荷。
 * 故意不加 @Transactional：网上图片的下载可能要十几秒，不能在事务里占着数据库连接；
 * 每次只 save 一篇笔记，用 Repository 自带的事务就够了。
 */
@Service
public class AgentImageEditService {

    private static final String UPLOAD_MODEL = "agent_image";

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final AgentDocumentService agentDocumentService;
    private final MediaBlockService mediaBlockService;
    private final SafeImageDownloader downloader;
    private final UploadService uploadService;
    private final ContentIndexingService contentIndexingService;
    private final ObjectMapper objectMapper;
    private final NoteRevisionService revisionService;
    private final AgentEditorjsImageEditor editorjsEditor;

    public AgentImageEditService(UserRepository userRepository, NoteRepository noteRepository,
                                 AgentDocumentService agentDocumentService, MediaBlockService mediaBlockService,
                                 SafeImageDownloader downloader, UploadService uploadService,
                                 ContentIndexingService contentIndexingService, ObjectMapper objectMapper,
                                 NoteRevisionService revisionService) {
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.agentDocumentService = agentDocumentService;
        this.mediaBlockService = mediaBlockService;
        this.downloader = downloader;
        this.uploadService = uploadService;
        this.contentIndexingService = contentIndexingService;
        this.objectMapper = objectMapper;
        this.revisionService = revisionService;
        this.editorjsEditor = new AgentEditorjsImageEditor(objectMapper);
    }

    public Map<String, Object> insertImage(String username, Long noteId, AgentImageSource source,
                                           String caption, String anchorText) {
        return insertImage(username, noteId, source, caption, anchorText, null);
    }

    public Map<String, Object> insertImage(String username, Long noteId, AgentImageSource source,
                                           String caption, String anchorText, String conversationId) {
        User user = getUser(username);
        Note note = loadOwnedNote(user, noteId);
        String url = resolveSourceUrl(username, user, source);
        AgentImageEditResult result = switch (contentType(note)) {
            case "editorjs" -> editorjsEditor.insert(note.getContent(), url, caption, anchorText);
            case "markdown" -> AgentMarkdownImageEditor.insert(note.getContent(), url, caption, anchorText);
            default -> AgentHtmlImageEditor.insert(note.getContent(), url, caption, anchorText);
        };
        return save(user, note, result, conversationId);
    }

    public Map<String, Object> deleteImage(String username, Long noteId, int index) {
        return deleteImage(username, noteId, index, null);
    }

    public Map<String, Object> deleteImage(String username, Long noteId, int index, String conversationId) {
        User user = getUser(username);
        Note note = loadOwnedNote(user, noteId);
        AgentImageLocator.LocatedImage image = locate(note, index);
        AgentImageEditResult result = switch (contentType(note)) {
            case "editorjs" -> editorjsEditor.delete(note.getContent(), image);
            case "markdown" -> AgentMarkdownImageEditor.delete(note.getContent(), image);
            default -> AgentHtmlImageEditor.delete(note.getContent(), image);
        };
        return save(user, note, result, conversationId);
    }

    public Map<String, Object> setImageCaption(String username, Long noteId, int index, String caption) {
        return setImageCaption(username, noteId, index, caption, null);
    }

    public Map<String, Object> setImageCaption(String username, Long noteId, int index, String caption,
                                               String conversationId) {
        User user = getUser(username);
        Note note = loadOwnedNote(user, noteId);
        AgentImageLocator.LocatedImage image = locate(note, index);
        AgentImageEditResult result = switch (contentType(note)) {
            case "editorjs" -> editorjsEditor.setCaption(note.getContent(), image, caption);
            case "markdown" -> AgentMarkdownImageEditor.setCaption(note.getContent(), image, caption);
            default -> AgentHtmlImageEditor.setCaption(note.getContent(), image, caption);
        };
        return save(user, note, result, conversationId);
    }

    private AgentImageLocator.LocatedImage locate(Note note, int index) {
        List<AgentImageLocator.LocatedImage> images =
                AgentImageLocator.locateInNote(contentType(note), note.getContent(), objectMapper);
        if (index < 1 || index > images.size()) {
            throw new BadRequestException("没有第 " + index + " 张图片（共 " + images.size() + " 张）");
        }
        return images.get(index - 1);
    }

    private Map<String, Object> save(User user, Note note, AgentImageEditResult result, String conversationId) {
        String turnId = revisionService.resolveAgentTurn(user, conversationId);
        revisionService.snapshotBeforeEdit(note, turnId, NoteRevision.Source.AGENT);
        note.setContent(result.content());
        noteRepository.save(note);
        contentIndexingService.reindexNote(note.getId());
        revisionService.recordAfter(turnId, note.getId(), result.content());
        Map<String, Object> event = new LinkedHashMap<>(result.event());
        event.put("noteId", note.getId());
        event.put("contentType", contentType(note));
        event.put("turnId", turnId);
        return event;
    }

    private String resolveSourceUrl(String username, User user, AgentImageSource source) {
        if (source == null || source.type() == null) throw new BadRequestException("缺少图片来源");
        return switch (source.type()) {
            case "note" -> localize(user, pick(agentDocumentService.listNoteImages(username, requireId(source)),
                    requireIndex(source)).url());
            case "clip" -> localize(user, pick(agentDocumentService.listClipImages(username, requireId(source)),
                    requireIndex(source)).url());
            case "attachment" -> requireLocalAttachment(source.url());
            case "url" -> downloadToUploads(user, source.url());
            default -> throw new BadRequestException("不支持的图片来源: " + source.type());
        };
    }

    /** 本地已有的图直接共用地址；外链（收藏里常见）下载到本地，避免插进去后失效或被防盗链挡住 */
    String localize(User user, String url) {
        if (url.startsWith("data:")) throw new BadRequestException("暂不支持插入内嵌（data:）图片");
        if (url.contains("/uploads/") && localFileExists(url)) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return downloadToUploads(user, url);
        return url;
    }

    private String requireLocalAttachment(String url) {
        if (url == null || !url.contains("/uploads/") || !localFileExists(url)) {
            throw new BadRequestException("聊天附件地址无效或文件已不存在");
        }
        return url;
    }

    private boolean localFileExists(String url) {
        try {
            Path path = mediaBlockService.resolveLocalFilePath(url);
            return path != null && Files.exists(path);
        } catch (BadRequestException | InvalidPathException e) {
            // 外链里带 ?、: 等字符时 Windows 上 Path.resolve 会抛 InvalidPathException，按外链处理
            return false;
        }
    }

    private String downloadToUploads(User user, String url) {
        SafeImageDownloader.DownloadedImage image = downloader.download(url);
        FileResultVo stored = uploadService.imageUpload(
                new ByteArrayMultipartFile("agent-image." + image.extension(), image.mimeType(), image.bytes()),
                UPLOAD_MODEL, 1, user.getId());
        if (stored == null || stored.getUrl() == null) throw new BadRequestException("图片保存失败");
        return stored.getUrl();
    }

    private static AgentImageRef pick(List<AgentImageRef> images, int index) {
        if (index < 1 || index > images.size()) {
            throw new BadRequestException("来源里没有第 " + index + " 张图片（共 " + images.size() + " 张）");
        }
        return images.get(index - 1);
    }

    private static Long requireId(AgentImageSource source) {
        if (source.id() == null) throw new BadRequestException("图片来源缺少 id");
        return source.id();
    }

    private static int requireIndex(AgentImageSource source) {
        if (source.index() == null) throw new BadRequestException("从笔记/收藏取图需要 index（第几张，从 1 开始）");
        return source.index();
    }

    private static String contentType(Note note) {
        return note.getContentType() == null ? "richtext" : note.getContentType();
    }

    private Note loadOwnedNote(User user, Long noteId) {
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
