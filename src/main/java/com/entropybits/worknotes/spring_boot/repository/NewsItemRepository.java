/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.NewsItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    List<NewsItem> findBySourceKeyAndFetchDateOrderByRankOrderAsc(String sourceKey, LocalDate fetchDate);

    @Query(
            value = "select distinct n.fetchDate from NewsItem n order by n.fetchDate desc",
            countQuery = "select count(distinct n.fetchDate) from NewsItem n"
    )
    Page<LocalDate> findDistinctFetchDates(Pageable pageable);

    @Transactional
    void deleteBySourceKeyAndFetchDate(String sourceKey, LocalDate fetchDate);
}
