/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.service.TweetPosterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareTweetVideoControllerTest {

    private static final String GALLERY = "{\"blocks\":[{\"type\":\"mediaGallery\",\"data\":{\"items\":["
            + "{\"type\":\"video\",\"embedUrl\":\"https://platform.twitter.com/embed/Tweet.html?id=1234567890123&dnt=true\"}]}}]}";

    private NoteRepository repo;
    private TweetPosterService tweets;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repo = mock(NoteRepository.class);
        tweets = mock(TweetPosterService.class);
        when(repo.findById(1L)).thenReturn(Optional.of(
                Note.builder().id(1L).isPublic(true).contentType("editorjs").content(GALLERY).build()));
        when(repo.findById(2L)).thenReturn(Optional.of(
                Note.builder().id(2L).isPublic(false).contentType("editorjs").content(GALLERY).build()));
        when(repo.findById(3L)).thenReturn(Optional.empty());
        when(tweets.fetchVideo(any(), any())).thenReturn(new TweetPosterService.TweetVideo("https://video.twimg.com/a.mp4", "16/9"));
        mvc = MockMvcBuilders.standaloneSetup(new ShareTweetVideoController(repo, tweets)).build();
    }

    @Test
    void publicNoteReferencingTweetReturnsVideo() throws Exception {
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").value("https://video.twimg.com/a.mp4"))
                .andExpect(jsonPath("$.aspectRatio").value("16/9"));
    }

    @Test
    void privateOrMissingNoteReturns404WithoutCallingX() throws Exception {
        mvc.perform(get("/v1/share/notes/2/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        mvc.perform(get("/v1/share/notes/3/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isNotFound());
        verify(tweets, never()).fetchVideo(any(), any());
    }

    @Test
    void tweetNotReferencedByNoteReturns404WithoutCallingX() throws Exception {
        // 前缀相同但不是同一条推文，不能被子串误匹配
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "123456789012").param("token", "t"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "999").param("token", "t"))
                .andExpect(status().isNotFound());
        verify(tweets, never()).fetchVideo(any(), any());
    }

    @Test
    void statusLinkReferenceIsAccepted_plainAndJsonEscaped() throws Exception {
        when(repo.findById(4L)).thenReturn(Optional.of(Note.builder().id(4L).isPublic(true)
                .content("{\"u\":\"https://x.com/someone/status/555000111\"}").build()));
        when(repo.findById(5L)).thenReturn(Optional.of(Note.builder().id(5L).isPublic(true)
                .content("{\"u\":\"https:\\/\\/x.com\\/someone\\/status\\/555000111\"}").build()));
        mvc.perform(get("/v1/share/notes/4/tweet-video").param("id", "555000111").param("token", "t"))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/share/notes/5/tweet-video").param("id", "555000111").param("token", "t"))
                .andExpect(status().isOk());
    }

    @Test
    void overLongIdReturns404WithoutCallingX() throws Exception {
        String id = "1".repeat(26);
        when(repo.findById(6L)).thenReturn(Optional.of(Note.builder().id(6L).isPublic(true)
                .content("Tweet.html?id=" + id).build()));
        mvc.perform(get("/v1/share/notes/6/tweet-video").param("id", id).param("token", "t"))
                .andExpect(status().isNotFound());
        verify(tweets, never()).fetchVideo(any(), any());
    }

    @Test
    void invalidTokenReturnsBare404WithoutCallingX() throws Exception {
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "1234567890123").param("token", "BAD TOKEN!"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verify(tweets, never()).fetchVideo(any(), any());
    }

    @Test
    void secondRequestIsServedFromCacheAndSendsCacheControl() throws Exception {
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")));
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").value("https://video.twimg.com/a.mp4"));
        verify(tweets, org.mockito.Mockito.times(1)).fetchVideo(any(), any());
    }

    @Test
    void missIsNotCachedByBrowsers() throws Exception {
        when(tweets.fetchVideo(any(), any())).thenReturn(null);
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "1234567890123").param("token", "t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrl").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void nonDigitIdIsRejectedWithoutCallingX() throws Exception {
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "12.*").param("token", "t"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/share/notes/1/tweet-video").param("id", "abc").param("token", "t"))
                .andExpect(status().isNotFound());
        verify(tweets, never()).fetchVideo(any(), any());
    }
}
