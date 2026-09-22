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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;

/**
 * 图片OCR管道的共享登记/查询逻辑：三个入口（本地目录扫描、笔记图片上传、笔记内粘贴）
 * 共用同一套"算hash→查/建PENDING记录"，两条消费路径共用同一套"按URL/ID反查OCR结果"。
 * 详见 docs/superpowers/specs/2026-09-05-image-ocr-pipeline-design.md。
 */
@Service
public class LocalFileExtractionService {

    private final LocalFileExtractionRepository extractionRepository;
    private final AttachmentRepository attachmentRepository;
    private final LocalMediaFileRepository localMediaFileRepository;

    @Value("${worknotes.upload.url-prefix:}")
    private String uploadUrlPrefix;

    public LocalFileExtractionService(LocalFileExtractionRepository extractionRepository,
                                       AttachmentRepository attachmentRepository,
                                       LocalMediaFileRepository localMediaFileRepository) {
        this.extractionRepository = extractionRepository;
        this.attachmentRepository = attachmentRepository;
        this.localMediaFileRepository = localMediaFileRepository;
    }

    /**
     * 按内容hash查/建一条待处理的OCR记录，已存在（任何状态）则直接返回，不重复入队。
     * find-or-create不是原子操作：两个并发请求（同一张图片经不同上传/扫描事件同时进来）
     * 都可能先查到空、再各自save，后insert的那个会撞content_hash唯一约束——这里重新查
     * 一次拿到赢家的记录，而不是让异常冒泡出去，被调用方（UploadServiceImpl/LocalMediaService）
     * 的兜底catch吞掉、永久丢失这条记录与图片的关联。
     */
    public LocalFileExtraction registerImageForOcr(byte[] content) {
        String hash = sha256(content);
        return extractionRepository.findByContentHash(hash).orElseGet(() -> {
            try {
                return extractionRepository.save(LocalFileExtraction.builder()
                        .contentHash(hash)
                        .extractionType(LocalFileExtraction.ExtractionType.IMAGE_OCR)
                        .status(LocalFileExtraction.Status.PENDING)
                        .retryCount(0)
                        .build());
            } catch (DataIntegrityViolationException e) {
                return extractionRepository.findByContentHash(hash).orElseThrow(() -> e);
            }
        });
    }

    /** 笔记正文里&lt;img&gt;/markdown图片引用按URL反查OCR文字；非SUCCESS或找不到都返回null。 */
    public String findExtractedTextForImageUrl(String imageUrl) {
        String contentHash = resolveContentHashForImageUrl(imageUrl);
        if (contentHash == null) return null;
        return extractionRepository.findByContentHash(contentHash)
                .filter(e -> e.getStatus() == LocalFileExtraction.Status.SUCCESS)
                .map(LocalFileExtraction::getExtractedText)
                .orElse(null);
    }

    /**
     * 笔记正文里图片引用按URL反查其内容hash，不关心OCR是否已完成——用于维护"笔记→图片"
     * 的引用关系（NoteImageRef），这样图片OCR完成之后才能反查回来是哪些笔记引用了它。
     */
    public String resolveContentHashForImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String attDir = stripUploadUrlPrefix(imageUrl);
        Attachment attachment = attachmentRepository.findByAttDir(attDir);
        return attachment == null ? null : attachment.getContentHash();
    }

    /** citation标题：按sourceId(=LocalFileExtraction.id)反查任意一个当前使用该hash的本地媒体文件名。 */
    public String findDisplayNameForExtraction(Long extractionId) {
        return extractionRepository.findById(extractionId)
                .flatMap(e -> localMediaFileRepository.findFirstByContentHash(e.getContentHash()))
                .map(LocalMediaFile::getFileName)
                .orElse("（本地图片）");
    }

    /**
     * 剥离当前对外前缀，和{@code NoteService.resolveRewriteTarget}/{@code UploadServiceImpl.publicUrlPrefix}
     * 用的是同一套兜底规则：未配置{@code worknotes.upload.url-prefix}时退回"/api"——
     * 笔记正文里的图片URL就是按这套规则写入的，这里必须保持一致，否则同源反代部署下
     * （推荐做法是把url-prefix留空）永远反查不到对应的Attachment。
     */
    private String stripUploadUrlPrefix(String url) {
        String prefix = (uploadUrlPrefix != null && !uploadUrlPrefix.isBlank()) ? uploadUrlPrefix : "/api";
        return url.startsWith(prefix) ? url.substring(prefix.length()) : url;
    }

    public static String sha256(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
