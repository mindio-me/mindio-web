/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.NewsFeedResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsDayResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsHistoryResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsItemResponse;
import com.entropybits.worknotes.spring_boot.dto.NewsSourceConfigResponse;
import com.entropybits.worknotes.spring_boot.entity.NewsItem;
import com.entropybits.worknotes.spring_boot.entity.NewsSourceConfig;
import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.news.NewsItemData;
import com.entropybits.worknotes.spring_boot.repository.NewsItemRepository;
import com.entropybits.worknotes.spring_boot.repository.NewsSourceConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final NewsSourceConfigRepository sourceConfigRepo;
    private final NewsItemRepository newsItemRepo;
    private final List<NewsFetcher> fetchers;

    @Transactional(readOnly = true)
    public List<NewsFeedResponse> getNewsByDate(LocalDate date) {
        return sourceConfigRepo.findAllByOrderBySortOrderAsc().stream()
                .map(config -> {
                    List<NewsItemResponse> items = newsItemRepo
                            .findBySourceKeyAndFetchDateOrderByRankOrderAsc(config.getSourceKey(), date)
                            .stream()
                            .map(NewsItemResponse::from)
                            .toList();
                    return new NewsFeedResponse(NewsSourceConfigResponse.from(config), items);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsHistoryResponse getNewsHistory(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 10);
        Page<LocalDate> datePage = newsItemRepo.findDistinctFetchDates(PageRequest.of(safePage, safeSize));
        List<NewsDayResponse> days = datePage.getContent().stream()
                .map(date -> new NewsDayResponse(date, getNewsByDate(date)))
                .toList();
        return new NewsHistoryResponse(days, safePage, safeSize, datePage.hasNext());
    }

    @Transactional(readOnly = true)
    public List<NewsSourceConfigResponse> getAllSources() {
        return sourceConfigRepo.findAllByOrderBySortOrderAsc().stream()
                .map(NewsSourceConfigResponse::from)
                .toList();
    }

    @Transactional
    public NewsSourceConfigResponse toggleSource(String sourceKey) {
        NewsSourceConfig config = sourceConfigRepo.findBySourceKey(sourceKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + sourceKey));
        config.setEnabled(!config.getEnabled());
        return NewsSourceConfigResponse.from(sourceConfigRepo.save(config));
    }

    public void fetchAll() {
        Map<String, NewsFetcher> fetcherMap = fetchers.stream()
                .collect(Collectors.toMap(NewsFetcher::getSourceKey, Function.identity()));

        sourceConfigRepo.findByEnabledTrueOrderBySortOrderAsc()
                .forEach(config -> fetchSource(config.getSourceKey(), fetcherMap.get(config.getSourceKey())));
    }

    public void fetchSource(String sourceKey) {
        Map<String, NewsFetcher> fetcherMap = fetchers.stream()
                .collect(Collectors.toMap(NewsFetcher::getSourceKey, Function.identity()));
        fetchSource(sourceKey, fetcherMap.get(sourceKey));
    }

    @Transactional
    public void fetchSource(String sourceKey, NewsFetcher fetcher) {
        if (fetcher == null) {
            log.warn("No fetcher registered for source: {}", sourceKey);
            return;
        }

        NewsSourceConfig config = sourceConfigRepo.findBySourceKey(sourceKey).orElse(null);
        if (config == null) {
            log.warn("Source config not found: {}", sourceKey);
            return;
        }

        config.setLastFetchStatus("FETCHING");
        sourceConfigRepo.save(config);

        LocalDate today = LocalDate.now();
        try {
            List<NewsItemData> items = fetcher.fetch();
            if (items == null || items.isEmpty()) {
                throw new IllegalStateException("Fetcher returned no news items");
            }
            newsItemRepo.deleteBySourceKeyAndFetchDate(sourceKey, today);

            LocalDateTime now = LocalDateTime.now();
            List<NewsItem> entities = items.stream()
                    .map(d -> NewsItem.builder()
                            .sourceKey(sourceKey)
                            .rankOrder(d.rank())
                            .title(d.title())
                            .url(d.url())
                            .fetchDate(today)
                            .fetchedAt(now)
                            .build())
                    .collect(Collectors.toList());
            newsItemRepo.saveAll(entities);

            config.setLastFetchedAt(now);
            config.setLastFetchStatus("SUCCESS");
            config.setLastFetchError(null);
            log.info("Fetched {} items for source: {}", items.size(), sourceKey);
        } catch (Exception e) {
            config.setLastFetchedAt(LocalDateTime.now());
            config.setLastFetchStatus("FAILED");
            String errMsg = e.getMessage();
            config.setLastFetchError(errMsg != null && errMsg.length() > 500 ? errMsg.substring(0, 500) : errMsg);
            log.error("Fetch failed for source {}: {}", sourceKey, e.getMessage(), e);
        }
        sourceConfigRepo.save(config);
    }
}
