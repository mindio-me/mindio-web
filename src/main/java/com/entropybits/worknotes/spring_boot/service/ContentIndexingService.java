/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ContentChunkRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔记/收藏保存后异步增量重索引：按分块内容哈希 diff，哈希不变则复用旧 embedding，
 * 只有真正变化的分块才重新调用 embedding 接口——避免编辑页 2 秒防抖自动保存
 * 触发整篇重新 embedding。
 */
@Slf4j
@Service
public class ContentIndexingService {

    private final ContentChunkRepository chunkRepository;
    private final NoteRepository noteRepository;
    private final SourceClipRepository clipRepository;
    private final ContentChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public ContentIndexingService(ContentChunkRepository chunkRepository,
                                   NoteRepository noteRepository,
                                   SourceClipRepository clipRepository,
                                   ContentChunkingService chunkingService,
                                   EmbeddingService embeddingService,
                                   ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.noteRepository = noteRepository;
        this.clipRepository = clipRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void reindexNote(Long noteId) {
        noteRepository.findById(noteId).ifPresentOrElse(
                note -> upsertChunks(ContentChunk.SourceType.NOTE, noteId, note.getOwner(), chunkingService.chunkNote(note)),
                () -> log.debug("笔记 {} 在异步索引执行前已被删除，跳过", noteId)
        );
    }

    @Async
    @Transactional
    public void reindexClip(Long clipId) {
        clipRepository.findById(clipId).ifPresentOrElse(
                clip -> upsertChunks(ContentChunk.SourceType.CLIP, clipId, clip.getOwner(), chunkingService.chunkClip(clip)),
                () -> log.debug("收藏 {} 在异步索引执行前已被删除，跳过", clipId)
        );
    }

    @Transactional
    public void deleteChunksFor(ContentChunk.SourceType type, Long sourceId) {
        chunkRepository.deleteBySourceTypeAndSourceId(type, sourceId);
    }

    void upsertChunks(ContentChunk.SourceType type, Long sourceId, User owner, List<String> newChunkTexts) {
        List<ContentChunk> existing = chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(type, sourceId);

        // 新行不能在循环里就地 save：ContentChunk 用的是 IDENTITY 主键，save() 一个瞬时实体会让
        // Hibernate 立刻发出 INSERT（必须立即拿到自增 id，无法推迟到事务提交时统一 flush），
        // 而 save() 一个已托管实体只是把 UPDATE 排进队列、等下一次 flush 才真正落库。
        // 于是「前面位置要 UPDATE、后面位置要 INSERT」时，INSERT 会抢在 UPDATE 之前打到数据库，
        // 撞上还没被腾出来的 (source_type, source_id, chunk_index) 唯一约束。
        // 解决办法：先把新行攒起来，等所有 UPDATE / DELETE 显式 flush 完、索引槽位彻底腾干净之后再插入。
        List<ContentChunk> toInsert = new ArrayList<>();

        for (int i = 0; i < newChunkTexts.size(); i++) {
            String text = newChunkTexts.get(i);
            String hash = sha256(text);
            ContentChunk row = i < existing.size() ? existing.get(i) : null;
            if (row != null && hash.equals(row.getContentHash())) {
                // 内容没变可以跳过 embedding，但 chunk_index 仍然可能是过期的（上一轮某块 embedding
                // 失败留下的空洞会让后面的行整体前移），这里顺手自愈，否则空洞会一直保留到下一次
                // 分块数变化时再次触发唯一约束冲突。
                if (!Integer.valueOf(i).equals(row.getChunkIndex())) {
                    row.setChunkIndex(i);
                    chunkRepository.save(row);
                }
                continue;
            }

            float[] embedding;
            try {
                embedding = embeddingService.embed(text);
            } catch (Exception e) {
                log.warn("分块 embedding 失败，跳过这一块 sourceType={} sourceId={} chunkIndex={}", type, sourceId, i, e);
                // 即使这一块 embedding 失败，也要把已有行的 chunk_index 纠正到位：否则同一块连续
                // 两轮都失败时，这行会带着过期 index 一直留到后面某个新行插入时再撞唯一约束。
                if (row != null && !Integer.valueOf(i).equals(row.getChunkIndex())) {
                    row.setChunkIndex(i);
                    chunkRepository.save(row);
                }
                continue;
            }
            String embeddingJson = writeEmbeddingJson(embedding);

            if (row == null) {
                toInsert.add(ContentChunk.builder()
                        .owner(owner).sourceType(type).sourceId(sourceId).chunkIndex(i)
                        .chunkText(text).contentHash(hash).embeddingJson(embeddingJson)
                        .build());
            } else {
                // 必须把 chunkIndex 纠正回它在列表中的真实位置：embedding 失败的 continue 分支会在
                // 已落库的 chunk_index 序列里留下空洞，之后按位置对齐时旧行会带着过期的 index。
                // 已有行按 chunk_index 升序取出，过期 index 恒有 old_index >= new_position，
                // 所以升序改写位置的这些 UPDATE 彼此之间不会撞车。
                row.setChunkIndex(i);
                row.setChunkText(text);
                row.setContentHash(hash);
                row.setEmbeddingJson(embeddingJson);
                chunkRepository.save(row);
            }
        }

        if (existing.size() > newChunkTexts.size()) {
            chunkRepository.deleteAll(existing.subList(newChunkTexts.size(), existing.size()));
        }

        // 先把上面所有 UPDATE / DELETE 真正打到数据库，腾空被占用的 chunk_index 槽位，再插入新行
        chunkRepository.flush();
        chunkRepository.saveAll(toInsert);
    }

    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String writeEmbeddingJson(float[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
