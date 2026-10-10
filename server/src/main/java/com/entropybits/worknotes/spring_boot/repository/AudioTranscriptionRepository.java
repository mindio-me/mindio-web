/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AudioTranscriptionRepository extends JpaRepository<AudioTranscription, Long> {

    Optional<AudioTranscription> findByNoteIdAndBlockId(Long noteId, String blockId);

    List<AudioTranscription> findByNoteId(Long noteId);

    List<AudioTranscription> findTop10ByStatusOrderByCreatedAtAsc(AudioTranscription.Status status);

    List<AudioTranscription> findByStatus(AudioTranscription.Status status);

    @Transactional
    void deleteByNoteId(Long noteId);
}
