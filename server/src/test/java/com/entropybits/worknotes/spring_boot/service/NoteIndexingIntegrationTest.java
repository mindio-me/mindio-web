/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.dto.NoteRequest;
import com.entropybits.worknotes.spring_boot.dto.NoteResponse;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ContentChunkRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 端到端验证「保存笔记 → content_chunks 真的落库」这条链路：走真实的 Spring bean 和代理链，
 * 覆盖单元测试永远看不到的 @Transactional + @Async 交互。
 * <p>
 * 关键：这个类<b>不能</b>加 @Transactional。Spring Test 的 @Transactional 会把测试方法包进一个
 * 结束即回滚的事务，afterCommit 回调永远不会触发，测试就失去了它要证明的东西——必须发生真实提交。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class NoteIndexingIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbPath = tempDir.resolve("note-indexing-e2e");
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    /** 替换掉唯一的 EmbeddingService 实现（DoubaoEmbeddingService），避免测试真的去调火山方舟接口 */
    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContentChunkRepository chunkRepository;

    @Test
    void createNoteThroughRealSpringBeansEventuallyProducesContentChunk() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        User owner = userRepository.save(User.builder()
                .username("indexing-e2e-user")
                .password("hashed-password")
                .role("USER")
                .build());

        NoteRequest request = new NoteRequest();
        request.setTitle("集成测试笔记");
        request.setContent("这是一段用于验证索引链路端到端打通的笔记正文。");
        request.setContentType("markdown");

        NoteResponse created = noteService.createNote(request, owner.getUsername());
        Long noteId = created.getId();

        // 重索引在事务提交之后、另一个线程上执行，必须轮询等待而不是立刻断言
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ContentChunk> chunks = chunkRepository
                    .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, noteId);
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).getChunkText()).contains("端到端打通");
        });
    }
}
