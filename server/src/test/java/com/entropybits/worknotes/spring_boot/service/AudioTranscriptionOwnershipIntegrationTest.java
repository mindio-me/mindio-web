/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.dto.NoteRequest;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * requireOwner 走真实 Spring 代理 + 真实 JPA：Note.owner 是 LAZY、全局 open-in-view=false，
 * 控制器里调用时没有外层事务。单元测试用的是 mock 仓库、@DataJpaTest 自带事务，都看不到
 * LazyInitializationException，所以这里刻意不加 @Transactional。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class AudioTranscriptionOwnershipIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbPath = tempDir.resolve("audio-transcription-owner");
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private AudioTranscriptionService audioTranscriptionService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void requireOwner_worksOutsideTransactionWithLazyOwner() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        User owner = userRepository.save(User.builder()
                .username("audio-owner").password("hashed").role("USER").build());
        userRepository.save(User.builder()
                .username("audio-stranger").password("hashed").role("USER").build());

        NoteRequest request = new NoteRequest();
        request.setTitle("录音");
        request.setContent("正文");
        request.setContentType("markdown");
        Long noteId = noteService.createNote(request, owner.getUsername()).getId();

        assertThatCode(() -> audioTranscriptionService.requireOwner(noteId, "audio-owner"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> audioTranscriptionService.requireOwner(noteId, "audio-stranger"))
                .isInstanceOf(ForbiddenException.class);
    }
}
