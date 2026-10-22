/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareAudioTranscriptionControllerTest {

    private static final String CONTENT = "{\"blocks\":["
            + "{\"id\":\"a1\",\"type\":\"audio\",\"data\":{}},"
            + "{\"id\":\"r1\",\"type\":\"audioRecord\",\"data\":{}},"
            + "{\"id\":\"p1\",\"type\":\"paragraph\",\"data\":{\"text\":\"mentions gone1\"}}]}";

    private NoteRepository notes;
    private AudioTranscriptionRepository transcriptions;
    private MockMvc mvc;

    private static AudioTranscription record(String blockId) {
        return AudioTranscription.builder().noteId(1L).blockId(blockId)
                .status(AudioTranscription.Status.FAILED).stage(AudioTranscription.Stage.FULL)
                .errorKind(AudioTranscription.ErrorKind.ASR_FAILED).errorMessage("secret internal detail")
                .utterances("[{\"text\":\"hi\"}]").updatedAt(LocalDateTime.of(2026, 10, 1, 8, 0)).build();
    }

    @BeforeEach
    void setUp() {
        notes = mock(NoteRepository.class);
        transcriptions = mock(AudioTranscriptionRepository.class);
        when(notes.findById(1L)).thenReturn(Optional.of(
                Note.builder().id(1L).isPublic(true).contentType("editorjs").content(CONTENT).build()));
        when(notes.findById(2L)).thenReturn(Optional.of(
                Note.builder().id(2L).isPublic(false).contentType("editorjs").content(CONTENT).build()));
        when(notes.findById(3L)).thenReturn(Optional.empty());
        when(transcriptions.findByNoteId(1L)).thenReturn(List.of(record("a1"), record("r1"), record("gone1")));
        mvc = MockMvcBuilders.standaloneSetup(
                new ShareAudioTranscriptionController(notes, transcriptions, new ObjectMapper())).build();
    }

    @Test
    void publicNoteReturnsOnlyRecordsOfBlocksStillInContent() throws Exception {
        mvc.perform(get("/v1/share/notes/1/audio-transcriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].blockId").value("a1"))
                .andExpect(jsonPath("$[1].blockId").value("r1"))
                .andExpect(jsonPath("$[0].utterances[0].text").value("hi"));
    }

    @Test
    void errorDetailIsNotExposed() throws Exception {
        mvc.perform(get("/v1/share/notes/1/audio-transcriptions"))
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist())
                .andExpect(jsonPath("$[0].errorKind").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    void nullOrInvalidContentReturnsEmptyList() throws Exception {
        when(notes.findById(4L)).thenReturn(Optional.of(
                Note.builder().id(4L).isPublic(true).content(null).build()));
        when(notes.findById(5L)).thenReturn(Optional.of(
                Note.builder().id(5L).isPublic(true).content("not json {").build()));
        when(transcriptions.findByNoteId(4L)).thenReturn(List.of(record("a1")));
        when(transcriptions.findByNoteId(5L)).thenReturn(List.of(record("a1")));
        mvc.perform(get("/v1/share/notes/4/audio-transcriptions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/share/notes/5/audio-transcriptions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void privateNoteReturnsBare404() throws Exception {
        mvc.perform(get("/v1/share/notes/2/audio-transcriptions"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verify(transcriptions, never()).findByNoteId(2L);
    }

    @Test
    void missingNoteReturnsBare404() throws Exception {
        mvc.perform(get("/v1/share/notes/3/audio-transcriptions"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test
    void blockIdOnlyMentionedInTextIsNotReturned() throws Exception {
        when(transcriptions.findByNoteId(1L)).thenReturn(List.of(record("gone1")));
        mvc.perform(get("/v1/share/notes/1/audio-transcriptions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
