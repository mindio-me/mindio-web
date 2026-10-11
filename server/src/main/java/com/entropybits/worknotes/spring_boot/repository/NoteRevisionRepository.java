/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NoteRevisionRepository extends JpaRepository<NoteRevision, Long> {

    Optional<NoteRevision> findByTurnIdAndNoteId(String turnId, Long noteId);

    List<NoteRevision> findByTurnIdOrderByIdAsc(String turnId);

    List<NoteRevision> findByTurnIdAndOwnerIdOrderByIdAsc(String turnId, Long ownerId);

    Optional<NoteRevision> findFirstByNoteIdAndOwnerIdAndSourceOrderByIdDesc(Long noteId, Long ownerId,
                                                                            NoteRevision.Source source);

    @Transactional
    void deleteByNoteId(Long noteId);
}
