/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 每 10 秒派发待转写的录音。不像图片 OCR 那样按"卡住超过 N 分钟"对账——长录音整理本身就可能
 * 跑十几分钟，按时长判断会误伤正在跑的任务；本应用单实例，启动时还在 PROCESSING 的一定是上次
 * 退出 / 崩溃时没做完的，直接放回 PENDING 续做。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioTranscriptionScheduler {

    private final AudioTranscriptionRepository repository;
    private final AudioTranscriptionJob job;

    @Scheduled(fixedDelay = 10_000)
    public void dispatchPending() {
        job.processPendingBatch();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        List<AudioTranscription> stuck = repository.findByStatus(AudioTranscription.Status.PROCESSING);
        stuck.forEach(t -> t.setStatus(AudioTranscription.Status.PENDING));
        repository.saveAll(stuck);
        if (!stuck.isEmpty()) {
            log.info("录音转写：启动时恢复 {} 条未完成的任务", stuck.size());
        }
    }
}
