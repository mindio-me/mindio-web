/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.InternalSearchWebRequest;
import com.entropybits.worknotes.spring_boot.dto.NoteReferenceItem;
import com.entropybits.worknotes.spring_boot.search.SearchResultItem;
import com.entropybits.worknotes.spring_boot.search.WebSearchProvider;
import com.entropybits.worknotes.spring_boot.search.WebSearchProviderResolver;
import com.entropybits.worknotes.spring_boot.service.NoteClipRefService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalNoteResearchControllerTest {

    @Mock WebSearchProviderResolver searchProviderResolver;
    @Mock WebSearchProvider webSearchProvider;
    @Mock NoteClipRefService noteClipRefService;

    InternalNoteResearchController controller() {
        return new InternalNoteResearchController(searchProviderResolver, noteClipRefService);
    }

    @Test
    void searchWeb_delegatesToResolvedProvider() throws Exception {
        when(searchProviderResolver.resolve()).thenReturn(webSearchProvider);
        List<SearchResultItem> expected = List.of(new SearchResultItem("标题", "https://x.com", "摘要"));
        when(webSearchProvider.search("用户增长", 5)).thenReturn(expected);

        List<SearchResultItem> result = controller().searchWeb(new InternalSearchWebRequest("用户增长", 5));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void noteReferences_delegatesToNoteClipRefService() {
        List<NoteReferenceItem> expected = List.of(new NoteReferenceItem("标题", "内容"));
        when(noteClipRefService.getFullContentForNote(1L)).thenReturn(expected);

        List<NoteReferenceItem> result = controller().noteReferences(1L);

        assertThat(result).isEqualTo(expected);
    }
}
