/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.entity.LocalMediaFile;
import com.entropybits.worknotes.spring_boot.repository.AttachmentRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalFileExtractionServiceTest {

    @Mock LocalFileExtractionRepository extractionRepository;
    @Mock AttachmentRepository attachmentRepository;
    @Mock LocalMediaFileRepository localMediaFileRepository;

    private LocalFileExtractionService service;

    @BeforeEach
    void setUp() {
        service = new LocalFileExtractionService(extractionRepository, attachmentRepository, localMediaFileRepository);
    }

    @Test
    void registerImageForOcr_createsNewPendingRecordWhenHashUnseen() {
        byte[] content = "fake image bytes".getBytes();
        when(extractionRepository.findByContentHash(any())).thenReturn(Optional.empty());
        when(extractionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalFileExtraction result = service.registerImageForOcr(content);

        assertThat(result.getStatus()).isEqualTo(LocalFileExtraction.Status.PENDING);
        assertThat(result.getExtractionType()).isEqualTo(LocalFileExtraction.ExtractionType.IMAGE_OCR);
        assertThat(result.getContentHash()).hasSize(64);
    }

    @Test
    void registerImageForOcr_reusesExistingRecordWhenHashAlreadySeen() {
        byte[] content = "fake image bytes".getBytes();
        LocalFileExtraction existing = LocalFileExtraction.builder().id(1L).status(LocalFileExtraction.Status.SUCCESS).build();
        when(extractionRepository.findByContentHash(any())).thenReturn(Optional.of(existing));

        LocalFileExtraction result = service.registerImageForOcr(content);

        assertThat(result).isSameAs(existing);
        verify(extractionRepository, never()).save(any());
    }

    @Test
    void registerImageForOcr_recoversFromConcurrentInsertRaceByRetryingLookup() {
        // find-or-create不是原子操作：两个并发请求（同一张图不同上传/扫描事件）都可能
        // 先查到空，再各自save，后insert的那个会撞content_hash唯一约束。这里必须重新
        // 查一次拿到赢家的记录，而不是让异常直接冒泡出去、被调用方吞掉导致contentHash丢失。
        byte[] content = "fake image bytes".getBytes();
        LocalFileExtraction winner = LocalFileExtraction.builder()
                .id(1L).status(LocalFileExtraction.Status.PENDING).build();
        when(extractionRepository.findByContentHash(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(extractionRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate content_hash"));

        LocalFileExtraction result = service.registerImageForOcr(content);

        assertThat(result).isSameAs(winner);
    }

    @Test
    void findExtractedTextForImageUrl_returnsTextWhenAttachmentAndExtractionSucceed() {
        Attachment attachment = new Attachment();
        attachment.setContentHash("hash123");
        when(attachmentRepository.findByAttDir("/uploads/public/note/x.png")).thenReturn(attachment);
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .status(LocalFileExtraction.Status.SUCCESS).extractedText("识别到的文字").build();
        when(extractionRepository.findByContentHash("hash123")).thenReturn(Optional.of(extraction));

        String text = service.findExtractedTextForImageUrl("/uploads/public/note/x.png");

        assertThat(text).isEqualTo("识别到的文字");
    }

    @Test
    void findExtractedTextForImageUrl_stripsConfiguredUploadUrlPrefixBeforeLookup() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadUrlPrefix", "http://127.0.0.1:8080/api");
        Attachment attachment = new Attachment();
        attachment.setContentHash("hash123");
        when(attachmentRepository.findByAttDir("/uploads/public/note/x.png")).thenReturn(attachment);
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .status(LocalFileExtraction.Status.SUCCESS).extractedText("识别到的文字").build();
        when(extractionRepository.findByContentHash("hash123")).thenReturn(Optional.of(extraction));

        String text = service.findExtractedTextForImageUrl("http://127.0.0.1:8080/api/uploads/public/note/x.png");

        assertThat(text).isEqualTo("识别到的文字");
    }

    @Test
    void findExtractedTextForImageUrl_stripsDefaultApiPrefixWhenUploadUrlPrefixUnconfigured() {
        // uploadUrlPrefix留空（同源反代部署的推荐配置），此时NoteService.resolveRewriteTarget()
        // 把笔记正文里的图片URL统一改写成"/api/uploads/..."，这里必须用同一个"/api"兜底前缀
        // 才能反查回Attachment.attDir存的"/uploads/..."，否则OCR文字永远关联不上笔记。
        Attachment attachment = new Attachment();
        attachment.setContentHash("hash123");
        when(attachmentRepository.findByAttDir("/uploads/public/note/x.png")).thenReturn(attachment);
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .status(LocalFileExtraction.Status.SUCCESS).extractedText("识别到的文字").build();
        when(extractionRepository.findByContentHash("hash123")).thenReturn(Optional.of(extraction));

        String text = service.findExtractedTextForImageUrl("/api/uploads/public/note/x.png");

        assertThat(text).isEqualTo("识别到的文字");
    }

    @Test
    void findExtractedTextForImageUrl_returnsNullWhenExtractionNotYetSuccess() {
        Attachment attachment = new Attachment();
        attachment.setContentHash("hash123");
        when(attachmentRepository.findByAttDir(any())).thenReturn(attachment);
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .status(LocalFileExtraction.Status.PENDING).build();
        when(extractionRepository.findByContentHash("hash123")).thenReturn(Optional.of(extraction));

        assertThat(service.findExtractedTextForImageUrl("/uploads/public/note/x.png")).isNull();
    }

    @Test
    void findExtractedTextForImageUrl_returnsNullWhenNoAttachmentMatchesUrl() {
        when(attachmentRepository.findByAttDir(any())).thenReturn(null);

        assertThat(service.findExtractedTextForImageUrl("/uploads/public/note/missing.png")).isNull();
    }

    @Test
    void resolveContentHashForImageUrl_returnsHashRegardlessOfOcrStatus() {
        Attachment attachment = new Attachment();
        attachment.setContentHash("hash123");
        when(attachmentRepository.findByAttDir("/uploads/public/note/x.png")).thenReturn(attachment);

        String hash = service.resolveContentHashForImageUrl("/uploads/public/note/x.png");

        assertThat(hash).isEqualTo("hash123");
        verifyNoInteractions(extractionRepository);
    }

    @Test
    void resolveContentHashForImageUrl_returnsNullWhenNoAttachmentMatchesUrl() {
        when(attachmentRepository.findByAttDir(any())).thenReturn(null);

        assertThat(service.resolveContentHashForImageUrl("/uploads/public/note/missing.png")).isNull();
    }

    @Test
    void findDisplayNameForExtraction_returnsMatchingLocalMediaFileName() {
        LocalFileExtraction extraction = LocalFileExtraction.builder().id(5L).contentHash("hash123").build();
        when(extractionRepository.findById(5L)).thenReturn(Optional.of(extraction));
        LocalMediaFile file = LocalMediaFile.builder().fileName("screenshot.png").build();
        when(localMediaFileRepository.findFirstByContentHash("hash123")).thenReturn(Optional.of(file));

        assertThat(service.findDisplayNameForExtraction(5L)).isEqualTo("screenshot.png");
    }

    @Test
    void findDisplayNameForExtraction_returnsFallbackWhenNoLocalMediaFileMatches() {
        LocalFileExtraction extraction = LocalFileExtraction.builder().id(6L).contentHash("hash999").build();
        when(extractionRepository.findById(6L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash999")).thenReturn(Optional.empty());

        assertThat(service.findDisplayNameForExtraction(6L)).isEqualTo("（本地图片）");
    }
}
