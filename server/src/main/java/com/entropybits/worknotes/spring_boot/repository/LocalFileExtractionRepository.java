/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocalFileExtractionRepository extends JpaRepository<LocalFileExtraction, Long> {
    Optional<LocalFileExtraction> findByContentHash(String contentHash);

    java.util.List<LocalFileExtraction> findTop20ByStatusOrderByCreatedAtAsc(LocalFileExtraction.Status status);

    java.util.List<LocalFileExtraction> findByStatusAndRetryCountLessThan(LocalFileExtraction.Status status, int retryCount);

    java.util.List<LocalFileExtraction> findByStatusAndUpdatedAtBefore(LocalFileExtraction.Status status, java.time.LocalDateTime cutoff);
}
