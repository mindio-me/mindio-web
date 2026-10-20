/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharePageControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        NoteRepository repo = mock(NoteRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(
                Note.builder().id(1L).title("Pub").summary("S").isPublic(true).build()));
        when(repo.findById(2L)).thenReturn(Optional.of(
                Note.builder().id(2L).title("Secret").isPublic(false).build()));
        when(repo.findById(3L)).thenReturn(Optional.empty());
        SharePageController controller = new SharePageController(repo);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "https://demo.mindio.me");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void publicNoteReturnsOgHtml() throws Exception {
        mvc.perform(get("/v1/share/notes/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("og:title\" content=\"Pub\"")));
    }

    @Test
    void privateAndMissingReturn404WithoutTitle() throws Exception {
        mvc.perform(get("/v1/share/notes/2")).andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mvc.perform(get("/v1/share/notes/3")).andExpect(status().isNotFound());
    }
}
