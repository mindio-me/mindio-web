/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.SourceClipRequest;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.entity.ClipTagLink;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourceClipService {

    private static final int EXCERPT_MAX_LEN = 300;

    private final SourceClipRepository clipRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ClipTagLinkRepository clipTagLinkRepository;
    private final ContentIndexingService contentIndexingService;

    @Transactional
    public SourceClipResponse createClip(SourceClipRequest request, String username) {
        User user = getUser(username);

        SourceClip clip = SourceClip.builder()
                .sourceType(request.getSourceType())
                .sourceUrl(request.getSourceUrl())
                .sourceTitle(request.getSourceTitle())
                .sourceAuthor(request.getSourceAuthor())
                .extractionMode(request.getExtractionMode() != null
                        ? request.getExtractionMode() : SourceClip.ExtractionMode.FULL)
                .extractionStatus(request.getExtractionStatus())
                .title(request.getTitle())
                .content(request.getContent())
                .contentFormat(request.getContentFormat())
                .excerpt(buildExcerpt(request.getContent()))
                .owner(user)
                .build();
        clip = clipRepository.save(clip);
        replaceManualTags(clip, request.getTagIds(), user, false);
        reindexClipAfterCommit(clip.getId());

        return SourceClipResponse.fromEntity(clip);
    }

    @Transactional(readOnly = true)
    public Page<SourceClipResponse> listClips(String username, String keyword, SourceClip.SourceType sourceType,
                                              List<Long> tagIds, boolean untagged, Pageable pageable) {
        User user = getUser(username);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        Page<SourceClip> page;
        if (untagged) {
            page = clipRepository.searchByOwnerAndTypeWithNoTags(user, kw, sourceType, pageable);
        } else if (tagIds != null && !tagIds.isEmpty()) {
            page = clipRepository.searchByOwnerAndTypeAndTags(user, kw, sourceType, tagIds, pageable);
        } else if (kw != null || sourceType != null) {
            page = clipRepository.searchByOwnerAndType(user, kw, sourceType, pageable);
        } else {
            page = clipRepository.findByOwner(user, pageable);
        }
        return page.map(SourceClipResponse::fromEntitySummary);
    }

    @Transactional
    public SourceClipResponse getClip(Long id, String username) {
        SourceClip clip = findClip(id);
        clip.setLastAccessedAt(LocalDateTime.now());
        clipRepository.save(clip);
        return SourceClipResponse.fromEntity(clip);
    }

    @Transactional(readOnly = true)
    public List<SourceClipResponse> listRecentlyAccessed(String username, int limit) {
        User user = getUser(username);
        return clipRepository.findRecentlyAccessedByOwner(user, PageRequest.of(0, limit))
                .stream()
                .map(SourceClipResponse::fromEntitySummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public SourceClipResponse updateClip(Long id, SourceClipRequest request, String username) {
        User user = getUser(username);
        SourceClip clip = findClip(id);
        if (!clip.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限操作此收藏");
        }

        clip.setSourceType(request.getSourceType());
        clip.setSourceUrl(request.getSourceUrl());
        clip.setSourceTitle(request.getSourceTitle());
        clip.setSourceAuthor(request.getSourceAuthor());
        clip.setExtractionMode(request.getExtractionMode() != null
                ? request.getExtractionMode() : SourceClip.ExtractionMode.FULL);
        clip.setExtractionStatus(request.getExtractionStatus());
        clip.setTitle(request.getTitle());
        clip.setContent(request.getContent());
        clip.setContentFormat(request.getContentFormat());
        clip.setExcerpt(buildExcerpt(request.getContent()));
        clip = clipRepository.save(clip);
        replaceManualTags(clip, request.getTagIds(), user, true);
        reindexClipAfterCommit(clip.getId());

        return SourceClipResponse.fromEntity(clip);
    }

    @Transactional
    public SourceClipResponse updateTitle(Long id, String title) {
        SourceClip clip = findClip(id);
        clip.setTitle(title);
        return SourceClipResponse.fromEntitySummary(clipRepository.save(clip));
    }

    @Transactional
    public void deleteClip(Long id, String username) {
        SourceClip clip = findClip(id);
        contentIndexingService.deleteChunksFor(ContentChunk.SourceType.CLIP, id);
        // clip_tag_links 的外键没有 ON DELETE CASCADE，需要应用层先手动删除关联行
        List<ClipTagLink> links = clipTagLinkRepository.findByClip(clip);
        clipTagLinkRepository.deleteAll(links);
        clipRepository.delete(clip);
        links.stream().map(ClipTagLink::getTag).distinct().forEach(this::reconcileUsedByClips);
    }

    @Transactional
    public SourceClipResponse addTag(Long clipId, Long tagId, String username) {
        User user = getUser(username);
        SourceClip clip = findClip(clipId);
        if (!clip.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限操作此收藏");
        }
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("标签", "id", tagId));
        if (!tag.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限使用此标签");
        }

        ClipTagLink link = clipTagLinkRepository.findByClipAndTag(clip, tag).orElse(null);
        if (link == null) {
            link = ClipTagLink.builder().clip(clip).tag(tag).build();
            clip.getClipTagLinks().add(link);
        }
        link.setManuallyAdded(true);
        clipTagLinkRepository.save(link);
        markUsedByClips(tag);

        clip.setTagsManuallyAdjusted(true);
        clipRepository.save(clip);

        return SourceClipResponse.fromEntity(clip);
    }

    @Transactional
    public SourceClipResponse removeTag(Long clipId, Long tagId, String username) {
        User user = getUser(username);
        SourceClip clip = findClip(clipId);
        if (!clip.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限操作此收藏");
        }
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("标签", "id", tagId));
        if (!tag.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限使用此标签");
        }

        clipTagLinkRepository.findByClipAndTag(clip, tag).ifPresent(link -> {
            if (Boolean.TRUE.equals(link.getAiSuggested())) {
                link.setManuallyAdded(false);
                clipTagLinkRepository.save(link);
            } else {
                clipTagLinkRepository.delete(link);
                clip.getClipTagLinks().remove(link);
                reconcileUsedByClips(tag);
            }
            clip.setTagsManuallyAdjusted(true);
            clipRepository.save(clip);
        });

        return SourceClipResponse.fromEntity(clip);
    }

    // ---- helpers ----

    private SourceClip findClip(Long id) {
        return clipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("素材不存在"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }

    /**
     * 把异步重索引推迟到当前事务真正提交之后再触发。
     * reindexClip 是 @Async + @Transactional：它跑在另一个线程、另一个连接、另一个事务里，
     * 看不到调用方还没提交的行。若在事务内直接调用，创建场景会 findById 落空（静默不索引），
     * 更新场景会读到旧内容（哈希不变，整篇跳过）。没有事务上下文时（如单元测试直接 new 出服务）
     * 回退到立即调用，行为与改动前一致。
     */
    private void reindexClipAfterCommit(Long clipId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    contentIndexingService.reindexClip(clipId);
                }
            });
        } else {
            contentIndexingService.reindexClip(clipId);
        }
    }

    /**
     * 整体替换语义：新列表里的 tag 都置 manuallyAdded=true；
     * 旧列表里、新列表里没有的 tag，若 aiSuggested=false 则删除该行，否则只把 manuallyAdded 置 false。
     * markManuallyAdjusted=true 时才会把这条 clip 标记为「用户已手动调整过标签」——
     * 创建时带初始标签不算用户手动调整（此时还没有 AI 分类），只有 PUT 更新才是真正的手动调整。
     */
    private void replaceManualTags(SourceClip clip, List<Long> tagIds, User user, boolean markManuallyAdjusted) {
        Set<Long> newIds = tagIds == null ? Set.of() : new HashSet<>(tagIds);
        List<ClipTagLink> existing = clipTagLinkRepository.findByClip(clip);
        Map<Long, ClipTagLink> byTagId = new HashMap<>();
        for (ClipTagLink link : existing) byTagId.put(link.getTag().getId(), link);

        for (Long tagId : newIds) {
            ClipTagLink link = byTagId.get(tagId);
            if (link != null) {
                link.setManuallyAdded(true);
                clipTagLinkRepository.save(link);
            } else {
                Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new ResourceNotFoundException("标签", "id", tagId));
                if (!tag.getOwner().getId().equals(user.getId())) {
                    throw new UnauthorizedException("无权限使用此标签");
                }
                markUsedByClips(tag);
                ClipTagLink saved = clipTagLinkRepository.save(ClipTagLink.builder()
                        .clip(clip).tag(tag).manuallyAdded(true).build());
                clip.getClipTagLinks().add(saved);
            }
        }

        for (ClipTagLink link : existing) {
            if (newIds.contains(link.getTag().getId())) continue;
            if (Boolean.TRUE.equals(link.getAiSuggested())) {
                link.setManuallyAdded(false);
                clipTagLinkRepository.save(link);
            } else {
                clipTagLinkRepository.delete(link);
                clip.getClipTagLinks().remove(link);
                reconcileUsedByClips(link.getTag());
            }
        }

        if (markManuallyAdjusted) {
            clip.setTagsManuallyAdjusted(true);
            clipRepository.save(clip);
        }
    }

    private void markUsedByClips(Tag tag) {
        if (!Boolean.TRUE.equals(tag.getUsedByClips())) {
            tag.setUsedByClips(true);
            tagRepository.save(tag);
        }
    }

    /** 标签最后一条生效的 ClipTagLink 被删除后，把 usedByClips 重置为 false，避免僵尸标签常驻标签列表 */
    private void reconcileUsedByClips(Tag tag) {
        if (Boolean.TRUE.equals(tag.getUsedByClips()) && !clipTagLinkRepository.existsByTag(tag)) {
            tag.setUsedByClips(false);
            tagRepository.save(tag);
        }
    }

    /** 从 HTML/文本中提取纯文本 excerpt */
    static String buildExcerpt(String content) {
        if (content == null || content.isBlank()) return "";
        // 去除 HTML 标签
        String plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (plain.length() <= EXCERPT_MAX_LEN) return plain;
        return plain.substring(0, EXCERPT_MAX_LEN - 1) + "…";
    }
}
