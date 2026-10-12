/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.*;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.ImportItemRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkMergeService {

    private final ImportItemRepository itemRepository;
    private final SourceClipRepository clipRepository;
    private final TagRepository tagRepository;
    private final ClipTagLinkRepository clipTagLinkRepository;
    private final ContentIndexingService contentIndexingService;

    @Transactional
    public void markDecision(ImportJob job, ImportItem.Category category, ImportItem.UserDecision decision) {
        List<ImportItem> items = itemRepository.findByJobAndCategory(job, category);
        items.forEach(i -> i.setUserDecision(decision));
        itemRepository.saveAll(items);
    }

    @Transactional
    public void markItemDecision(ImportItem item, ImportItem.UserDecision decision) {
        item.setUserDecision(decision);
        itemRepository.save(item);
    }

    @Transactional
    public void markItemsDecision(List<ImportItem> items, ImportItem.UserDecision decision) {
        items.forEach(i -> i.setUserDecision(decision));
        itemRepository.saveAll(items);
    }

    @Transactional
    public List<SourceClip> merge(ImportJob job, List<ImportItem> confirmedItems) {
        List<SourceClip> created = new ArrayList<>();
        for (ImportItem item : confirmedItems) {
            String leafFolder = leafFolderName(item.getFolderPath());
            Tag tag = leafFolder != null ? resolveTag(leafFolder, job.getOwner()) : null;

            boolean fromDeadLink = item.getCategory() == ImportItem.Category.DEAD_LINK;

            SourceClip clip = SourceClip.builder()
                    .sourceType(SourceClip.SourceType.WEBPAGE)
                    .sourceUrl(item.getRawUrl())
                    .title(item.getRawTitle() != null && !item.getRawTitle().isBlank()
                            ? item.getRawTitle() : item.getRawUrl())
                    .extractionMode(SourceClip.ExtractionMode.LINK_ONLY)
                    .extractionStatus(SourceClip.ExtractionStatus.SUCCESS)
                    .originalBookmarkedAt(item.getBookmarkAddedAt())
                    .wasDetectedDeadLink(fromDeadLink)
                    .manuallyConfirmedAlive(fromDeadLink)
                    .owner(job.getOwner())
                    .build();
            clip = clipRepository.save(clip);

            if (tag != null) {
                markUsedByClips(tag);
                ClipTagLink link = clipTagLinkRepository.save(ClipTagLink.builder()
                        .clip(clip).tag(tag).manuallyAdded(true).build());
                clip.getClipTagLinks().add(link);
            }

            created.add(clip);

            item.setResultClipId(clip.getId());
            itemRepository.save(item);
        }
        reindexClipsAfterCommit(created.stream().map(SourceClip::getId).toList());
        return created;
    }

    /**
     * 导入的书签之前从不进检索索引（只有 SourceClipService 的新建/编辑会触发 reindexClip），
     * AI 助手的 search_workspace 因此搜不到任何导入的收藏。推迟到提交之后的理由同
     * SourceClipService.reindexClipAfterCommit：reindexClip 是 @Async，在另一个事务里看不到
     * 还没提交的新行。
     */
    private void reindexClipsAfterCommit(List<Long> clipIds) {
        if (clipIds.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clipIds.forEach(contentIndexingService::reindexClip);
                }
            });
        } else {
            clipIds.forEach(contentIndexingService::reindexClip);
        }
    }

    private String leafFolderName(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) return null;
        String[] parts = folderPath.split("/");
        return parts[parts.length - 1];
    }

    private Tag resolveTag(String name, User owner) {
        return tagRepository.findByNameAndOwner(name, owner)
                .orElseGet(() -> tagRepository.save(Tag.builder().name(name).owner(owner).build()));
    }

    private void markUsedByClips(Tag tag) {
        if (!Boolean.TRUE.equals(tag.getUsedByClips())) {
            tag.setUsedByClips(true);
            tagRepository.save(tag);
        }
    }
}
