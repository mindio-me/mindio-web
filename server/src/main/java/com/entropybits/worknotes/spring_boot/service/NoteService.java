/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.NoteRequest;
import com.entropybits.worknotes.spring_boot.dto.NoteResponse;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.integration.feishu.entity.FeishuDocumentSnapshot;
import com.entropybits.worknotes.spring_boot.integration.feishu.entity.FeishuImageMapping;
import com.entropybits.worknotes.spring_boot.integration.feishu.entity.FeishuWikiImportMapping;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuDocumentSnapshotRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuImageMappingRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuWikiImportMappingRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteImageRefRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/**
 * 笔记服务
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    @Value("${worknotes.upload.url-prefix:}")
    private String uploadUrlPrefix;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ProjectRepository projectRepository;
    private final FeishuWikiImportMappingRepository feishuWikiImportMappingRepository;
    private final FeishuDocumentSnapshotRepository feishuDocumentSnapshotRepository;
    private final FeishuImageMappingRepository feishuImageMappingRepository;
    private final ContentIndexingService contentIndexingService;
    private final NoteImageRefRepository noteImageRefRepository;

    /**
     * 创建笔记
     */
    @Transactional
    public NoteResponse createNote(NoteRequest request, String username) {
        User user = getUserByUsername(username);

        // 获取或创建标签
        Set<Tag> tags = getOrCreateTags(request.getTagIds(), user);

        // 验证并获取项目（如果提供了 projectId）
        Project project = validateAndGetProject(request.getProjectId(), user);

        Note note = Note.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .contentType(request.getContentType())
                .isPublic(request.getIsPublic())
                .owner(user)
                .tags(tags)
                .project(project)
                .summary(request.getSummary())
                .sectionContents(request.getSectionContents())
                .sectionTypes(request.getSectionTypes())
                .createdAt(request.getCreatedAt())
                .modifiedAt(request.getModifiedAt())
                .build();

        Note savedNote = noteRepository.save(note);
        reindexNoteAfterCommit(savedNote.getId());
        return enrichWithFeishu(NoteResponse.fromEntity(savedNote), savedNote);
    }

    /**
     * 更新笔记
     */
    @Transactional
    public NoteResponse updateNote(Long noteId, NoteRequest request, String username) {
        Note note = getNoteById(noteId);
        User user = getUserByUsername(username);

        // 检查权限
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限修改此笔记");
        }

        // 更新标签
        Set<Tag> tags = getOrCreateTags(request.getTagIds(), user);
        note.getTags().clear();
        note.getTags().addAll(tags);

        // 验证并更新项目关联
        Project project = validateAndGetProject(request.getProjectId(), user);
        note.setProject(project);

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setContentType(request.getContentType());
        note.setIsPublic(request.getIsPublic());
        note.setSummary(request.getSummary());
        note.setSectionContents(request.getSectionContents());
        note.setSectionTypes(request.getSectionTypes());

        Note updatedNote = noteRepository.save(note);
        reindexNoteAfterCommit(updatedNote.getId());
        return enrichWithFeishu(NoteResponse.fromEntity(updatedNote), updatedNote);
    }

    /**
     * 删除笔记
     */
    @Transactional
    public void deleteNote(Long noteId, String username) {
        Note note = getNoteById(noteId);
        User user = getUserByUsername(username);

        // 检查权限
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限删除此笔记");
        }

        // 在删除笔记前，先处理所有关联记录的 note_id 字段
        // 由于已删除数据库外键约束，需要在代码层面维护关联关系
        
        // 1. 处理 feishu_document_snapshots 表中的关联记录
        java.util.List<FeishuDocumentSnapshot> snapshots = feishuDocumentSnapshotRepository.findByNoteOrderByCreatedAtDesc(note);
        if (!snapshots.isEmpty()) {
            snapshots.forEach(snapshot -> snapshot.setNote(null));
            feishuDocumentSnapshotRepository.saveAll(snapshots);
        }

        // 2. 处理 feishu_image_mappings 表中的关联记录
        java.util.List<FeishuImageMapping> imageMappings = feishuImageMappingRepository.findByNoteOrderByCreatedAtAsc(note);
        if (!imageMappings.isEmpty()) {
            imageMappings.forEach(imageMapping -> imageMapping.setNote(null));
            feishuImageMappingRepository.saveAll(imageMappings);
        }

        // 3. 处理 feishu_wiki_import_mappings 表中的关联记录
        java.util.Optional<FeishuWikiImportMapping> mappingOpt = feishuWikiImportMappingRepository.findByNote(note);
        if (mappingOpt.isPresent()) {
            FeishuWikiImportMapping mapping = mappingOpt.get();
            mapping.setNote(null);
            feishuWikiImportMappingRepository.save(mapping);
        }

        // 4. 删除笔记
        contentIndexingService.deleteChunksFor(ContentChunk.SourceType.NOTE, note.getId());
        noteImageRefRepository.deleteByNote(note);
        noteRepository.delete(note);
    }

    /**
     * 获取笔记详情
     */
    @Transactional
    public NoteResponse getNoteById(Long noteId, String username) {
        Note note = getNoteById(noteId);

        // 如果笔记不是公开的，检查权限
        if (!note.getIsPublic()) {
            User user = getUserByUsername(username);
            if (!note.getOwner().getId().equals(user.getId())) {
                throw new UnauthorizedException("无权限查看此笔记");
            }
        }

        // 增加阅读次数
        note.setViewCount(note.getViewCount() + 1);
        note = noteRepository.save(note);

        // 在 Session 关闭前初始化懒加载集合
        initializeLazyCollections(note);

        return enrichWithFeishu(NoteResponse.fromEntity(note), note);
    }

    /**
     * 获取公开笔记详情（无需认证）
     */
    @Transactional(readOnly = true)
    public NoteResponse getPublicNoteById(Long noteId) {
        Note note = getNoteById(noteId);

        if (!note.getIsPublic()) {
            throw new UnauthorizedException("此笔记不是公开的");
        }

        // 在 Session 关闭前初始化懒加载集合
        initializeLazyCollections(note);

        return enrichWithFeishu(NoteResponse.fromEntity(note), note);
    }

    /**
     * 获取用户的笔记列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> getUserNotes(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.findByOwner(user, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 搜索用户的笔记（标题模糊搜索）- 保留向后兼容
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> searchUserNotes(String username, String keyword, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.findByOwnerAndTitleContaining(user, keyword, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 全文搜索：在标题、内容和摘要中搜索关键词
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> fullTextSearch(String username, String keyword, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.fullTextSearch(user, keyword, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 多条件组合搜索
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> advancedSearch(String username, String keyword, Boolean isPublic, 
                                             Set<Long> tagIds, java.time.LocalDateTime startDate, 
                                             java.time.LocalDateTime endDate, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.advancedSearch(user, keyword, isPublic, tagIds, startDate, endDate, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 根据标签筛选笔记
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> getNotesByTags(String username, Set<Long> tagIds, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.findByOwnerAndTagIds(user, tagIds, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 根据项目ID列表筛选笔记（支持多选筛选）
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> getNotesByProjects(String username, java.util.List<Long> projectIds, Pageable pageable) {
        User user = getUserByUsername(username);
        Page<Note> notes = noteRepository.findByOwnerAndProjectIdIn(user, projectIds, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 获取所有公开笔记
     */
    @Transactional(readOnly = true)
    public Page<NoteResponse> getPublicNotes(Pageable pageable) {
        Page<Note> notes = noteRepository.findByIsPublic(true, pageable);
        // 初始化所有笔记的懒加载集合
        notes.getContent().forEach(this::initializeLazyCollections);
        return notes.map(NoteResponse::fromEntity);
    }

    /**
     * 切换笔记公开状态（isPublic 取反）
     */
    @Transactional
    public NoteResponse togglePublic(Long noteId, String username) {
        Note note = getNoteById(noteId);
        User user = getUserByUsername(username);

        // 仅笔记所有者可以切换公开状态
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限修改此笔记的公开状态");
        }

        note.setIsPublic(!Boolean.TRUE.equals(note.getIsPublic()));
        Note saved = noteRepository.save(note);
        // 初始化懒加载集合后返回
        initializeLazyCollections(saved);
        return NoteResponse.fromEntity(saved);
    }

    // ========== 辅助方法 ==========

    private Note getNoteById(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记", "id", noteId));
    }

    /**
     * 把异步重索引推迟到当前事务真正提交之后再触发。
     * reindexNote 是 @Async + @Transactional：它跑在另一个线程、另一个连接、另一个事务里，
     * 看不到调用方还没提交的行。若在事务内直接调用，创建场景会 findById 落空（静默不索引），
     * 更新场景会读到旧内容（哈希不变，整篇跳过）。没有事务上下文时（如单元测试直接 new 出服务）
     * 回退到立即调用，行为与改动前一致。
     */
    private void reindexNoteAfterCommit(Long noteId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    contentIndexingService.reindexNote(noteId);
                }
            });
        } else {
            contentIndexingService.reindexNote(noteId);
        }
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户", "username", username));
    }

    private Set<Tag> getOrCreateTags(Set<Long> tagIds, User user) {
        Set<Tag> tags = new HashSet<>();
        if (tagIds != null && !tagIds.isEmpty()) {
            for (Long tagId : tagIds) {
                Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new ResourceNotFoundException("标签", "id", tagId));

                // 检查标签是否属于当前用户
                if (!tag.getOwner().getId().equals(user.getId())) {
                    throw new BadRequestException("标签不属于当前用户");
                }

                if (!Boolean.TRUE.equals(tag.getUsedByNotes())) {
                    tag.setUsedByNotes(true);
                    tagRepository.save(tag);
                }

                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * 验证并获取项目（如果 projectId 不为空）
     * 验证项目是否存在且属于当前用户
     */
    private Project validateAndGetProject(Long projectId, User user) {
        if (projectId == null) {
            return null;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BadRequestException("项目不存在，ID: " + projectId));

        // 验证项目是否属于当前用户
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new BadRequestException("项目不属于当前用户");
        }

        return project;
    }

    /**
     * 初始化 Note 实体的懒加载集合，避免 LazyInitializationException
     */
    private void initializeLazyCollections(Note note) {
        // 初始化 ElementCollection 集合
        Hibernate.initialize(note.getSectionContents());
        Hibernate.initialize(note.getSectionTypes());
        // 初始化 ManyToMany 集合
        Hibernate.initialize(note.getTags());
        // 初始化 ManyToOne 关联（如果需要访问 owner 的详细信息）
        if (note.getOwner() != null) {
            Hibernate.initialize(note.getOwner());
        }
        // 初始化项目关联
        if (note.getProject() != null) {
            Hibernate.initialize(note.getProject());
        }
    }

    /**
     * 为笔记响应补充飞书导入元数据（如果存在）。
     */
    private NoteResponse enrichWithFeishu(NoteResponse resp, Note note) {
        if (resp == null || note == null) return resp;
        FeishuWikiImportMapping mapping = feishuWikiImportMappingRepository.findByNote(note).orElse(null);
        if (mapping != null) {
            resp.setFeishuSpaceId(mapping.getSpaceId());
            resp.setFeishuNodeToken(mapping.getNodeToken());
            resp.setFeishuSourceUrl(mapping.getSourceUrl());
        }
        rewriteContentUrls(resp);
        return resp;
    }

    /**
     * 将 content 中历史遗留的 localhost 上传 URL 替换为当前配置的 url-prefix。
     * 匹配模式：http(s)://任意host:port/api/uploads/ → {uploadUrlPrefix}/uploads/
     */
    private void rewriteContentUrls(NoteResponse resp) {
        String content = resp.getContent();
        if (content == null || content.isBlank()) return;
        String rewritten = rewriteUploadUrls(content, resolveRewriteTarget(uploadUrlPrefix));
        if (!rewritten.equals(content)) {
            resp.setContent(rewritten);
        }
    }

    /**
     * 上传 URL 归一化的目标前缀：
     * 配置了 {@code worknotes.upload.url-prefix} 时用它（绝对地址，供跨源/多环境）；
     * 未配置时退回 servlet context-path {@code /api}，让正文里存/发的是根相对地址
     * （{@code /api/uploads/...}），与前端 {@code API_BASE_URL=/api} 的同源部署天然匹配。
     */
    static String resolveRewriteTarget(String uploadUrlPrefix) {
        return (uploadUrlPrefix != null && !uploadUrlPrefix.isBlank()) ? uploadUrlPrefix : "/api";
    }

    /**
     * 把 content 里“我们自己的上传 URL”归一化为当前环境的 {@code target} 前缀，分两趟：
     * <ol>
     *   <li>历史遗留的绝对地址 {@code http(s)://任意host:port/api/uploads/} → {@code {target}/uploads/}</li>
     *   <li>根相对地址 {@code /api/uploads/} → {@code {target}/uploads/}（{@code target} 为 "/api" 时是 no-op）</li>
     * </ol>
     * 判据是紧跟 {@code /api} 之后的 {@code /uploads/} 段：外部图片（无此段）两趟都不会被碰。
     * <p>
     * 第一趟的字符类必须排除空白符/引号/括号/尖括号等 markdown 分隔符，否则贪婪匹配会跨越
     * 多个 URL 甚至整段正文（例如正文中出现了不相关的英文双引号），把中间内容当作
     * URL 的一部分一并吞掉，导致连续图片场景下大段内容丢失。
     * <p>
     * 第二趟的反向断言 {@code (?<![\w:/])} 保证只匹配真正的根相对 {@code /api}，
     * 不会误伤第一趟刚替换出来的绝对地址里的 {@code /api} 片段。
     */
    static String rewriteUploadUrls(String content, String target) {
        if (content == null || content.isBlank()) return content;
        String replacement = java.util.regex.Matcher.quoteReplacement(target);
        String out = content.replaceAll(
                "https?://[^\\s\"'()<>]+/api(?=/uploads/)",
                replacement
        );
        if (!"/api".equals(target)) {
            out = out.replaceAll("(?<![\\w:/])/api(?=/uploads/)", replacement);
        }
        return out;
    }
}
