/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时触发ImageOcrJob处理PENDING记录，并周期性对账：把没超过重试上限的FAILED记录
 * 重置回PENDING，把卡在PROCESSING太久（进程重启等原因导致状态卡住）的记录也重置回PENDING。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOcrScheduler {

    static final int MAX_RETRY = 3;
    static final int STUCK_PROCESSING_MINUTES = 10;

    private final LocalFileExtractionRepository extractionRepository;
    private final ImageOcrJob imageOcrJob;

    @Scheduled(fixedDelay = 30_000)
    public void dispatchPending() {
        imageOcrJob.processPendingBatch();
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void reconcile() {
        List<LocalFileExtraction> retryable = extractionRepository
                .findByStatusAndRetryCountLessThan(LocalFileExtraction.Status.FAILED, MAX_RETRY);
        for (LocalFileExtraction extraction : retryable) {
            extraction.setStatus(LocalFileExtraction.Status.PENDING);
        }
        extractionRepository.saveAll(retryable);

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_PROCESSING_MINUTES);
        List<LocalFileExtraction> stuck = extractionRepository
                .findByStatusAndUpdatedAtBefore(LocalFileExtraction.Status.PROCESSING, cutoff);
        for (LocalFileExtraction extraction : stuck) {
            extraction.setStatus(LocalFileExtraction.Status.PENDING);
        }
        extractionRepository.saveAll(stuck);

        if (!retryable.isEmpty() || !stuck.isEmpty()) {
            log.info("图片OCR对账：重置{}条FAILED重试、{}条卡住的PROCESSING", retryable.size(), stuck.size());
        }
    }
}
