/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ProjectNoteSummaryResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectNoteContextServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock ContentChunkingService chunkingService;

    private ProjectNoteContextService service() {
        return new ProjectNoteContextService(noteRepository, chunkingService);
    }

    @Test
    void getNoteSummaries_returnsTitleAndChunkedBodyForEachLinkedNote() {
        Note note1 = Note.builder().id(1L).title("开发日志").contentType("editorjs").content("{}").build();
        Note note2 = Note.builder().id(2L).title("复盘笔记").contentType("markdown").content("# 正文").build();
        when(noteRepository.findByProjectId(9L)).thenReturn(List.of(note1, note2));
        when(chunkingService.chunkNote(note1)).thenReturn(List.of("开发日志", "第一段", "第二段"));
        when(chunkingService.chunkNote(note2)).thenReturn(List.of("复盘笔记", "复盘正文"));

        List<ProjectNoteSummaryResponse> result = service().getNoteSummaries(9L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).title()).isEqualTo("开发日志");
        assertThat(result.get(0).bodyText()).isEqualTo("开发日志\n第一段\n第二段");
        assertThat(result.get(1).bodyText()).isEqualTo("复盘笔记\n复盘正文");
    }

    @Test
    void getNoteSummaries_noLinkedNotes_returnsEmptyList() {
        when(noteRepository.findByProjectId(9L)).thenReturn(List.of());

        List<ProjectNoteSummaryResponse> result = service().getNoteSummaries(9L);

        assertThat(result).isEmpty();
    }

    @Test
    void getNoteSummaries_truncatesBodyTextLongerThan1500Chars() {
        Note note = Note.builder().id(1L).title("长笔记").contentType("markdown").content("正文").build();
        when(noteRepository.findByProjectId(9L)).thenReturn(List.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("x".repeat(2000)));

        List<ProjectNoteSummaryResponse> result = service().getNoteSummaries(9L);

        assertThat(result.get(0).bodyText()).hasSize(1500 + "…（内容过长，已截断）".length());
        assertThat(result.get(0).bodyText()).endsWith("…（内容过长，已截断）");
    }
}
