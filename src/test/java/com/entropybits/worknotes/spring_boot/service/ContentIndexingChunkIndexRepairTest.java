/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ContentChunkRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 复现并守住「单次 embedding 失败导致文档永久无法重建索引」这个 bug。
 * <p>
 * 必须跑在真实数据库上：ContentChunk 用 IDENTITY 主键，save() 一个瞬时实体会<b>立刻</b>发 INSERT，
 * 而 save() 一个托管实体只是把 UPDATE 排队等下次 flush——这个先后顺序 Mockito 根本观察不到，
 * 上一轮纯 mock 的单测因此全绿而真实 bug 依旧存在。这里用 TransactionTemplate 同步驱动
 * upsertChunks，让唯一约束冲突（如果还在）以真实异常的形式当场炸出来，而不是被 @Async 吞掉。
 * <p>
 * 关键：这个类<b>不能</b>加 @Transactional，否则两次 upsert 会挤在同一个事务/持久化上下文里，
 * 失去「上一轮已提交的残缺状态」这个前提。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class ContentIndexingChunkIndexRepairTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbPath = tempDir.resolve("chunk-index-repair");
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    /** 替换掉唯一的 EmbeddingService 实现（DoubaoEmbeddingService），避免测试真的去调火山方舟接口 */
    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private ContentIndexingService indexingService;

    @Autowired
    private ContentChunkingService chunkingService;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private ContentChunkRepository chunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void reindexRecoversAfterASingleChunkEmbeddingFailureLeftAGapInChunkIndex() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User owner = userRepository.save(User.builder()
                .username("chunk-index-repair-user")
                .password("hashed-password")
                .role("USER")
                .build());

        Note note = noteRepository.save(Note.builder()
                .title("三段式笔记")
                .contentType("markdown")
                .content(threeParagraphMarkdown())
                .owner(owner)
                .build());
        Long noteId = note.getId();

        List<String> chunks = chunkingService.chunkNote(note);
        assertThat(chunks).as("用例前提：这篇笔记必须切成 3 块").hasSize(3);

        // ---- 第一轮：位置 1 的分块 embedding 失败，落库的 chunk_index 只有 0 和 2，中间留下空洞 ----
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingService.embed(chunks.get(1))).thenThrow(new RuntimeException("模拟 embedding 接口失败"));

        tx.executeWithoutResult(status -> upsertNote(noteId));

        assertThat(chunkIndices(noteId))
                .as("第一轮之后应当留下空洞：只有 chunk_index 0 和 2")
                .containsExactly(0, 2);

        // ---- 第二轮：embedding 全部成功，必须能自愈，而不是撞唯一约束整体回滚 ----
        reset(embeddingService);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.3f, 0.4f});

        assertThatCode(() -> tx.executeWithoutResult(status -> upsertNote(noteId)))
                .as("第二轮重索引不能因为 (source_type, source_id, chunk_index) 唯一约束冲突而失败")
                .doesNotThrowAnyException();

        assertThat(chunkIndices(noteId))
                .as("自愈之后 chunk_index 必须是连续无重复的 0,1,2")
                .containsExactly(0, 1, 2);

        List<ContentChunk> rows = chunkRepository
                .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, noteId);
        assertThat(rows).extracting(ContentChunk::getChunkText)
                .as("每个位置存的必须是它自己那一块的正文")
                .containsExactly(chunks.get(0), chunks.get(1), chunks.get(2));

        // ---- 第三轮：内容一字未改，应当彻底幂等（不再重新 embedding，索引也不再变动）----
        reset(embeddingService);
        assertThatCode(() -> tx.executeWithoutResult(status -> upsertNote(noteId)))
                .as("内容未变的重复索引必须是无操作，且不触碰 embedding 接口")
                .doesNotThrowAnyException();
        assertThat(chunkIndices(noteId)).containsExactly(0, 1, 2);
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void reindexDoesNotCollideWhenTheSameChunkFailsEmbeddingOnTwoConsecutivePasses() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User owner = userRepository.save(User.builder()
                .username("chunk-index-repair-user-2")
                .password("hashed-password")
                .role("USER")
                .build());

        Note note = noteRepository.save(Note.builder()
                .title("三段式笔记二")
                .contentType("markdown")
                .content(threeParagraphMarkdown())
                .owner(owner)
                .build());
        Long noteId = note.getId();

        List<String> chunks = chunkingService.chunkNote(note);
        assertThat(chunks).as("用例前提：这篇笔记必须切成 3 块").hasSize(3);

        // ---- 第一轮：位置 1 失败，留下空洞 [0, 2] ----
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingService.embed(chunks.get(1))).thenThrow(new RuntimeException("模拟 embedding 接口失败（第一次）"));
        tx.executeWithoutResult(status -> upsertNote(noteId));
        assertThat(chunkIndices(noteId)).containsExactly(0, 2);

        // ---- 第二轮：同一块再次失败——不能因为 (source_type, source_id, chunk_index) 撞车而整体回滚 ----
        reset(embeddingService);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.3f, 0.4f});
        when(embeddingService.embed(chunks.get(1))).thenThrow(new RuntimeException("模拟 embedding 接口失败（第二次，连续失败）"));

        assertThatCode(() -> tx.executeWithoutResult(status -> upsertNote(noteId)))
                .as("同一块连续两轮 embedding 失败也不能撞唯一约束整体回滚")
                .doesNotThrowAnyException();
        assertThat(chunkIndices(noteId))
                .as("即使这一块还没修好，index 也必须是连续无重复的 0,1,2")
                .containsExactly(0, 1, 2);

        // ---- 第三轮：这一块终于 embedding 成功，必须收敛到完全正确的状态 ----
        reset(embeddingService);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.5f, 0.6f});

        tx.executeWithoutResult(status -> upsertNote(noteId));

        assertThat(chunkIndices(noteId)).containsExactly(0, 1, 2);
        List<ContentChunk> rows = chunkRepository
                .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, noteId);
        assertThat(rows).extracting(ContentChunk::getChunkText)
                .as("最终每个位置必须收敛为它自己那一块的正文")
                .containsExactly(chunks.get(0), chunks.get(1), chunks.get(2));
    }

    /** 复刻 reindexNote 的内部动作，但同步执行：这里要验的是 upsertChunks 的落库逻辑，不是异步/事务接线 */
    private void upsertNote(Long noteId) {
        Note managed = noteRepository.findById(noteId).orElseThrow();
        indexingService.upsertChunks(ContentChunk.SourceType.NOTE, noteId,
                managed.getOwner(), chunkingService.chunkNote(managed));
    }

    private List<Integer> chunkIndices(Long noteId) {
        return chunkRepository
                .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, noteId)
                .stream().map(ContentChunk::getChunkIndex).toList();
    }

    /**
     * 三个各 500+ 字的段落，按 ContentChunkingService 当前的合并规则（MAX_CHUNK_SIZE=800、
     * MIN_CHUNK_SIZE=300）会正好切成三块，每块就是一个段落。
     */
    private static String threeParagraphMarkdown() {
        return "第一段落：" + "甲".repeat(500) + "\n\n"
                + "第二段落：" + "乙".repeat(500) + "\n\n"
                + "第三段落：" + "丙".repeat(500);
    }
}
