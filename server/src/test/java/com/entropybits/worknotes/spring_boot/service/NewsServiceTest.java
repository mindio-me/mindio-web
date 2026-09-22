/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.NewsSourceConfig;
import com.entropybits.worknotes.spring_boot.news.NewsFetcher;
import com.entropybits.worknotes.spring_boot.repository.NewsItemRepository;
import com.entropybits.worknotes.spring_boot.repository.NewsSourceConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsSourceConfigRepository sourceConfigRepo;

    @Mock
    private NewsItemRepository newsItemRepo;

    @Mock
    private NewsFetcher fetcher;

    @Test
    void emptyFetchIsFailureAndDoesNotDeleteExistingItems() throws Exception {
        NewsSourceConfig config = new NewsSourceConfig();
        config.setSourceKey("google_news");
        when(sourceConfigRepo.findBySourceKey("google_news")).thenReturn(Optional.of(config));
        when(sourceConfigRepo.save(config)).thenReturn(config);
        when(fetcher.fetch()).thenReturn(List.of());

        NewsService service = new NewsService(sourceConfigRepo, newsItemRepo, List.of(fetcher));
        service.fetchSource("google_news", fetcher);

        assertThat(config.getLastFetchStatus()).isEqualTo("FAILED");
        assertThat(config.getLastFetchError()).contains("no news items");
        assertThat(config.getLastFetchedAt()).isNotNull();
        verify(newsItemRepo, never()).deleteBySourceKeyAndFetchDate("google_news", LocalDate.now());
        verify(newsItemRepo, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
