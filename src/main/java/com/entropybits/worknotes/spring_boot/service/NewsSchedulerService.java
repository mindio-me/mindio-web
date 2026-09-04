/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NewsSchedulerService.class);

    private final NewsService newsService;

    @Scheduled(cron = "0 0 8 * * ?", zone = "Asia/Shanghai")
    public void scheduledFetch() {
        log.info("Scheduled news fetch starting...");
        newsService.fetchAll();
        log.info("Scheduled news fetch completed.");
    }
}
