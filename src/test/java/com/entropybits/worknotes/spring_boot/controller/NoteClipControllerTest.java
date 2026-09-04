/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.LinkClipFromUrlRequest;
import com.entropybits.worknotes.spring_boot.dto.NoteClipRefResponse;
import com.entropybits.worknotes.spring_boot.dto.SourceClipDraft;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.service.ClipImportService;
import com.entropybits.worknotes.spring_boot.service.NoteClipRefService;
import com.entropybits.worknotes.spring_boot.service.SourceClipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteClipControllerTest {

    @Mock NoteClipRefService refService;
    @Mock ClipImportService clipImportService;
    @Mock SourceClipService sourceClipService;

    NoteClipController controller() {
        return new NoteClipController(refService, clipImportService, sourceClipService);
    }

    @Test
    void linkFromUrl_fetchesCreatesAndLinksClip() {
        SourceClipDraft draft = SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://example.com/a")
                .extractionMode(SourceClip.ExtractionMode.FULL)
                .extractionStatus(SourceClip.ExtractionStatus.SUCCESS)
                .suggestedTitle("网页标题")
                .content("网页正文")
                .contentFormat("html")
                .build();
        when(clipImportService.fetchFromUrl(any())).thenReturn(draft);
        SourceClipResponse created = SourceClipResponse.builder().id(42L).build();
        when(sourceClipService.createClip(any(), eq("alice"))).thenReturn(created);
        NoteClipRefResponse linked = NoteClipRefResponse.builder().refId(1L).noteId(9L).build();
        when(refService.linkClipToNote(9L, 42L, null)).thenReturn(linked);

        ResponseEntity<NoteClipRefResponse> response = controller().linkFromUrl(
                9L, new LinkClipFromUrlRequest("https://example.com/a", "候选标题"),
                new User("alice", "pw", java.util.List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(linked);
        ArgumentCaptor<com.entropybits.worknotes.spring_boot.dto.SourceClipRequest> captor =
                ArgumentCaptor.forClass(com.entropybits.worknotes.spring_boot.dto.SourceClipRequest.class);
        verify(sourceClipService).createClip(captor.capture(), eq("alice"));
        assertThat(captor.getValue().getTitle()).isEqualTo("网页标题");
    }

    @Test
    void linkFromUrl_fallsBackToTitleHintWhenNoSuggestedTitle() {
        SourceClipDraft draft = SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://example.com/a")
                .extractionMode(SourceClip.ExtractionMode.FULL)
                .content("正文")
                .build();
        when(clipImportService.fetchFromUrl(any())).thenReturn(draft);
        when(sourceClipService.createClip(any(), eq("alice")))
                .thenReturn(SourceClipResponse.builder().id(1L).build());
        when(refService.linkClipToNote(eq(9L), eq(1L), isNull()))
                .thenReturn(NoteClipRefResponse.builder().build());

        controller().linkFromUrl(9L, new LinkClipFromUrlRequest("https://example.com/a", "候选标题"),
                new User("alice", "pw", java.util.List.of()));

        ArgumentCaptor<com.entropybits.worknotes.spring_boot.dto.SourceClipRequest> captor =
                ArgumentCaptor.forClass(com.entropybits.worknotes.spring_boot.dto.SourceClipRequest.class);
        verify(sourceClipService).createClip(captor.capture(), eq("alice"));
        assertThat(captor.getValue().getTitle()).isEqualTo("候选标题");
    }
}
