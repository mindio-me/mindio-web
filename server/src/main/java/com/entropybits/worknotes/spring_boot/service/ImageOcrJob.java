/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.entity.LocalMediaFile;
import com.entropybits.worknotes.spring_boot.repository.AttachmentRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaFileRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteImageRefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片OCR执行引擎：捞PENDING记录→定位实际文件字节→调Python的vision-extract→回写状态。
 * 触发方是ImageOcrScheduler的定时轮询，不是HTTP请求驱动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOcrJob {

    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "gif", "image/gif", "bmp", "image/bmp", "webp", "image/webp", "heic", "image/heic");

    private final LocalFileExtractionRepository extractionRepository;
    private final LocalMediaFileRepository localMediaFileRepository;
    private final AttachmentRepository attachmentRepository;
    private final UploadPathConfig uploadPathConfig;
    private final AgentServiceClient agentServiceClient;
    private final ContentIndexingService contentIndexingService;
    private final NoteImageRefRepository noteImageRefRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /**
     * 捞一批PENDING记录，逐条异步处理，不阻塞调用方（调用方是@Scheduled线程）。
     * 必须在这里（提交给executor之前）就同步把状态翻成PROCESSING并落库——如果留到
     * processOne真正跑起来才翻状态，线程池只有2个线程，backlog超过容量时下一次
     * 30秒的调度tick会重新查到同一批仍是PENDING的记录，导致重复提交、重复调用Python。
     */
    public void processPendingBatch() {
        List<LocalFileExtraction> pending = extractionRepository
                .findTop20ByStatusOrderByCreatedAtAsc(LocalFileExtraction.Status.PENDING);
        for (LocalFileExtraction extraction : pending) {
            extraction.setStatus(LocalFileExtraction.Status.PROCESSING);
            extractionRepository.save(extraction);
            executor.submit(() -> processOne(extraction.getId()));
        }
    }

    void processOne(Long extractionId) {
        LocalFileExtraction extraction = extractionRepository.findById(extractionId).orElse(null);
        // PENDING：直接调用（测试/未来的手动重试路径）；PROCESSING：已被processPendingBatch
        // 预先认领，这里不用再校验一次，直接继续处理。其余状态（已成功/失败/已删除）跳过。
        if (extraction == null || (extraction.getStatus() != LocalFileExtraction.Status.PENDING
                && extraction.getStatus() != LocalFileExtraction.Status.PROCESSING)) {
            return;
        }

        extraction.setStatus(LocalFileExtraction.Status.PROCESSING);
        extractionRepository.save(extraction);

        try {
            ImageFile imageFile = loadImageFile(extraction.getContentHash());
            if (imageFile == null) {
                markFailed(extraction, "找不到对应的图片文件");
                return;
            }
            String dataUri = "data:" + imageFile.mimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(imageFile.bytes());
            String text = agentServiceClient.visionExtract(dataUri);
            if (text == null || text.isBlank()) {
                log.warn("图片OCR返回空文字 extractionId={} contentHash={}", extractionId, extraction.getContentHash());
            }

            extraction.setExtractedText(text);
            extraction.setStatus(LocalFileExtraction.Status.SUCCESS);
            extraction.setProcessedAt(LocalDateTime.now());
            extractionRepository.save(extraction);

            contentIndexingService.reindexLocalMediaExtraction(extraction.getId());
            reindexNotesReferencingImage(extraction.getContentHash());
        } catch (Exception e) {
            log.warn("图片OCR处理失败 extractionId={}", extractionId, e);
            markFailed(extraction, e.getMessage());
        }
    }

    /**
     * 笔记里贴/传的图片OCR完成后，找到引用了这张图片的笔记，重新分块让OCR文字进入笔记检索结果。
     * 按content_hash查NoteImageRef——同一张图可能通过不同的上传/粘贴事件（不同Attachment行）
     * 进入多篇笔记，只有按hash查才能一次性找全，不能像早期版本那样只挑一个Attachment的URL去猜。
     */
    private void reindexNotesReferencingImage(String contentHash) {
        for (var ref : noteImageRefRepository.findByContentHash(contentHash)) {
            contentIndexingService.reindexNote(ref.getNote().getId());
        }
    }

    private void markFailed(LocalFileExtraction extraction, String message) {
        extraction.setStatus(LocalFileExtraction.Status.FAILED);
        extraction.setRetryCount(extraction.getRetryCount() + 1);
        extraction.setProcessedAt(LocalDateTime.now());
        extractionRepository.save(extraction);
        log.warn("图片OCR处理失败 extractionId={} contentHash={} reason={}", extraction.getId(), extraction.getContentHash(), message);
    }

    private record ImageFile(byte[] bytes, String mimeType) {}

    private ImageFile loadImageFile(String contentHash) throws Exception {
        var mediaFile = localMediaFileRepository.findFirstByContentHash(contentHash);
        if (mediaFile.isPresent()) {
            LocalMediaFile f = mediaFile.get();
            byte[] bytes = Files.readAllBytes(Path.of(f.getAbsolutePath()));
            return new ImageFile(bytes, mimeTypeFor(f.getFileExtension()));
        }
        var attachment = attachmentRepository.findFirstByContentHash(contentHash);
        if (attachment.isPresent()) {
            Attachment a = attachment.get();
            Path path = resolveAttachmentPath(a.getAttDir());
            byte[] bytes = Files.readAllBytes(path);
            return new ImageFile(bytes, mimeTypeFor(a.getAttType()));
        }
        return null;
    }

    private Path resolveAttachmentPath(String attDir) {
        // attDir形如"/uploads/public/note/xxx.jpg"，uploadPathConfig.getUploadPath()是"uploads"
        // 目录在磁盘上的绝对根路径，两者拼接前先去掉共同的"uploads/"前缀，避免路径重复一层。
        String relative = attDir.startsWith("/uploads/") ? attDir.substring("/uploads/".length()) : attDir;
        return Path.of(uploadPathConfig.getUploadPath()).resolve(relative);
    }

    private String mimeTypeFor(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase();
        return EXTENSION_MIME_TYPES.getOrDefault(ext, "image/jpeg");
    }
}
