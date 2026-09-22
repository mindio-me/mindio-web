/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteImageRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteImageRefRepository extends JpaRepository<NoteImageRef, Long> {

    List<NoteImageRef> findByContentHash(String contentHash);

    void deleteByNote(Note note);
}
