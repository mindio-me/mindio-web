/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.entity.LocalMediaFile;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteImageRef;
import com.entropybits.worknotes.spring_boot.repository.AttachmentRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaFileRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteImageRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageOcrJobTest {

    @Mock LocalFileExtractionRepository extractionRepository;
    @Mock LocalMediaFileRepository localMediaFileRepository;
    @Mock AttachmentRepository attachmentRepository;
    @Mock AgentServiceClient agentServiceClient;
    @Mock ContentIndexingService contentIndexingService;
    @Mock NoteImageRefRepository noteImageRefRepository;

    @TempDir Path tempDir;

    private ImageOcrJob job;

    @BeforeEach
    void setUp() {
        UploadPathConfig uploadPathConfig = mock(UploadPathConfig.class);
        when(uploadPathConfig.getUploadPath()).thenReturn(tempDir.toString());
        job = new ImageOcrJob(extractionRepository, localMediaFileRepository, attachmentRepository,
                uploadPathConfig, agentServiceClient, contentIndexingService, noteImageRefRepository);
        when(extractionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void processPendingBatch_marksRecordsProcessingSynchronouslyBeforeHandingOffToExecutor() {
        // 回归用例：如果状态只在异步任务真正跑起来后才翻成PROCESSING，backlog超过线程池
        // 容量时，下一次30秒的调度tick会重新查到同一批仍是PENDING的记录并重复提交。
        // 必须在这里（调度线程里，提交给executor之前）就同步落库PROCESSING才算修好。
        LocalFileExtraction e1 = LocalFileExtraction.builder()
                .id(10L).contentHash("h10").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        LocalFileExtraction e2 = LocalFileExtraction.builder()
                .id(11L).contentHash("h11").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findTop20ByStatusOrderByCreatedAtAsc(LocalFileExtraction.Status.PENDING))
                .thenReturn(List.of(e1, e2));
        when(extractionRepository.findById(any())).thenReturn(Optional.empty());

        job.processPendingBatch();

        assertThat(e1.getStatus()).isEqualTo(LocalFileExtraction.Status.PROCESSING);
        assertThat(e2.getStatus()).isEqualTo(LocalFileExtraction.Status.PROCESSING);
        verify(extractionRepository).save(e1);
        verify(extractionRepository).save(e2);
    }

    @Test
    void processOne_succeedsAndReindexesWhenLocalMediaFileExists() throws Exception {
        Files.write(tempDir.resolve("photo.jpg"), "fake image bytes".getBytes());
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(1L).contentHash("hash1").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash1")).thenReturn(Optional.of(
                LocalMediaFile.builder().absolutePath(tempDir.resolve("photo.jpg").toString()).fileExtension("jpg").build()));
        when(agentServiceClient.visionExtract(any())).thenReturn("识别到的文字");

        job.processOne(1L);

        assertThat(extraction.getStatus()).isEqualTo(LocalFileExtraction.Status.SUCCESS);
        assertThat(extraction.getExtractedText()).isEqualTo("识别到的文字");
        verify(contentIndexingService).reindexLocalMediaExtraction(1L);
    }

    @Test
    void processOne_fallsBackToAttachmentWhenNoLocalMediaFileMatches() throws Exception {
        Files.createDirectories(tempDir.resolve("public/note"));
        Files.write(tempDir.resolve("public/note/pasted.jpg"), "fake image bytes".getBytes());
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(2L).contentHash("hash2").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(2L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash2")).thenReturn(Optional.empty());
        Attachment attachment = new Attachment();
        attachment.setAttDir("/uploads/public/note/pasted.jpg");
        attachment.setAttType("jpg");
        when(attachmentRepository.findFirstByContentHash("hash2")).thenReturn(Optional.of(attachment));
        when(agentServiceClient.visionExtract(any())).thenReturn("笔记截图里的文字");

        job.processOne(2L);

        assertThat(extraction.getStatus()).isEqualTo(LocalFileExtraction.Status.SUCCESS);
        assertThat(extraction.getExtractedText()).isEqualTo("笔记截图里的文字");
    }

    @Test
    void processOne_reindexesEveryNoteReferencingTheOcrdImageEvenWhenSharedAcrossMultipleNotes() throws Exception {
        // 回归用例：同一张图片可能通过不同的上传/粘贴事件（不同Attachment行）进入多篇笔记，
        // 只有按content_hash查NoteImageRef才能一次性找全，不能像早期版本那样只挑一个
        // Attachment的URL去笔记正文里做字符串匹配——那种做法会漏掉引用了其它Attachment行的笔记。
        Files.write(tempDir.resolve("photo.jpg"), "fake image bytes".getBytes());
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(6L).contentHash("hash6").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(6L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash6")).thenReturn(Optional.of(
                LocalMediaFile.builder().absolutePath(tempDir.resolve("photo.jpg").toString()).fileExtension("jpg").build()));
        when(agentServiceClient.visionExtract(any())).thenReturn("笔记截图里的文字");
        Note noteA = Note.builder().id(42L).build();
        Note noteB = Note.builder().id(43L).build();
        when(noteImageRefRepository.findByContentHash("hash6")).thenReturn(List.of(
                NoteImageRef.builder().note(noteA).contentHash("hash6").build(),
                NoteImageRef.builder().note(noteB).contentHash("hash6").build()));

        job.processOne(6L);

        verify(contentIndexingService).reindexNote(42L);
        verify(contentIndexingService).reindexNote(43L);
    }

    @Test
    void processOne_doesNotReindexAnyNoteWhenNoNoteImageRefMatchesHash() throws Exception {
        Files.write(tempDir.resolve("photo.jpg"), "fake image bytes".getBytes());
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(7L).contentHash("hash7").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(7L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash7")).thenReturn(Optional.of(
                LocalMediaFile.builder().absolutePath(tempDir.resolve("photo.jpg").toString()).fileExtension("jpg").build()));
        when(agentServiceClient.visionExtract(any())).thenReturn("识别到的文字");
        when(noteImageRefRepository.findByContentHash("hash7")).thenReturn(List.of());

        job.processOne(7L);

        assertThat(extraction.getStatus()).isEqualTo(LocalFileExtraction.Status.SUCCESS);
        verify(contentIndexingService, never()).reindexNote(any());
    }

    @Test
    void processOne_marksFailedAndIncrementsRetryCountWhenVisionCallThrows() throws Exception {
        Files.write(tempDir.resolve("photo.jpg"), "fake image bytes".getBytes());
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(3L).contentHash("hash3").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(3L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash3")).thenReturn(Optional.of(
                LocalMediaFile.builder().absolutePath(tempDir.resolve("photo.jpg").toString()).fileExtension("jpg").build()));
        when(agentServiceClient.visionExtract(any())).thenThrow(new RuntimeException("boom"));

        job.processOne(3L);

        assertThat(extraction.getStatus()).isEqualTo(LocalFileExtraction.Status.FAILED);
        assertThat(extraction.getRetryCount()).isEqualTo(1);
        verify(contentIndexingService, never()).reindexLocalMediaExtraction(any());
    }

    @Test
    void processOne_marksFailedWhenNoFileFoundForHash() {
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(4L).contentHash("hash4").status(LocalFileExtraction.Status.PENDING).retryCount(0).build();
        when(extractionRepository.findById(4L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash4")).thenReturn(Optional.empty());
        when(attachmentRepository.findFirstByContentHash("hash4")).thenReturn(Optional.empty());

        job.processOne(4L);

        assertThat(extraction.getStatus()).isEqualTo(LocalFileExtraction.Status.FAILED);
        assertThat(extraction.getRetryCount()).isEqualTo(1);
    }

    @Test
    void processOne_skipsWhenRecordIsNoLongerPending() {
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(5L).contentHash("hash5").status(LocalFileExtraction.Status.SUCCESS).retryCount(0).build();
        when(extractionRepository.findById(5L)).thenReturn(Optional.of(extraction));

        job.processOne(5L);

        verify(extractionRepository, never()).save(any());
    }
}
