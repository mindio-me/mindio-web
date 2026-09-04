/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.NoteReferenceItem;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteClipRef;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.repository.NoteClipRefRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteClipRefServiceTest {

    @Mock NoteClipRefRepository refRepository;
    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository clipRepository;

    private NoteClipRefService service() {
        return new NoteClipRefService(refRepository, noteRepository, clipRepository);
    }

    @Test
    void getFullContentForNote_returnsTitleAndContentOfEachLinkedClip() {
        Note note = Note.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(2L).title("参考文章").content("正文内容").build();
        NoteClipRef ref = NoteClipRef.builder().note(note).clip(clip).sortOrder(0).build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(refRepository.findByNoteOrderBySortOrderAsc(note)).thenReturn(List.of(ref));

        List<NoteReferenceItem> result = service().getFullContentForNote(1L);

        assertThat(result).containsExactly(new NoteReferenceItem("参考文章", "正文内容"));
    }

    @Test
    void getFullContentForNote_truncatesVeryLongContent() {
        Note note = Note.builder().id(1L).build();
        String longContent = "x".repeat(5000);
        SourceClip clip = SourceClip.builder().id(2L).title("长文章").content(longContent).build();
        NoteClipRef ref = NoteClipRef.builder().note(note).clip(clip).sortOrder(0).build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(refRepository.findByNoteOrderBySortOrderAsc(note)).thenReturn(List.of(ref));

        List<NoteReferenceItem> result = service().getFullContentForNote(1L);

        assertThat(result.get(0).content()).hasSize(4000 + "…（内容过长，已截断）".length());
    }
}
