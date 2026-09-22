/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.TagRequest;
import com.entropybits.worknotes.spring_boot.dto.TagResponse;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock ClipTagLinkRepository clipTagLinkRepository;

    private TagService service;

    @Test
    void createTag_setsUsedByNotesTrueAndUsedByClipsFalse() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.existsByNameAndOwner("学习", user)).thenReturn(false);
        when(tagRepository.save(any())).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            t.setId(5L);
            return t;
        });

        TagRequest request = new TagRequest();
        request.setName("学习");

        TagResponse response = service.createTag(request, "alice");

        assertThat(response.getId()).isEqualTo(5L);
        verify(tagRepository).save(argThat(t -> Boolean.TRUE.equals(t.getUsedByNotes())
                && Boolean.FALSE.equals(t.getUsedByClips())));
    }

    @Test
    void getUserTags_filtersByNoteScope() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        Tag noteTag = Tag.builder().id(1L).name("笔记标签").owner(user).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.findByOwnerAndUsedByNotesTrue(user)).thenReturn(List.of(noteTag));

        List<TagResponse> result = service.getUserTags("alice", "note");

        assertThat(result).extracting(TagResponse::getId).containsExactly(1L);
    }

    @Test
    void getUserTags_includesClipCountForClipScope() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        Tag tagWithLinks = Tag.builder().id(3L).name("AI").owner(user).build();
        Tag tagWithoutLinks = Tag.builder().id(4L).name("旅行").owner(user).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.findByOwnerAndUsedByClipsTrue(user)).thenReturn(List.of(tagWithLinks, tagWithoutLinks));
        ClipTagLinkRepository.TagClipCount count = new ClipTagLinkRepository.TagClipCount() {
            public Long getTagId() { return 3L; }
            public Long getClipCount() { return 5L; }
        };
        when(clipTagLinkRepository.countActiveLinksByOwnerGroupedByTag(user)).thenReturn(List.of(count));

        List<TagResponse> result = service.getUserTags("alice", "clip");

        assertThat(result).extracting(TagResponse::getId, TagResponse::getClipCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3L, 5L),
                        org.assertj.core.groups.Tuple.tuple(4L, 0L));
    }

    @Test
    void getUserTags_returnsAllWhenScopeIsNull() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        Tag tag = Tag.builder().id(2L).name("任意标签").owner(user).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.findByOwner(user)).thenReturn(List.of(tag));

        List<TagResponse> result = service.getUserTags("alice", null);

        assertThat(result).extracting(TagResponse::getId).containsExactly(2L);
    }

    @Test
    void deleteTag_deletesClipTagLinksBeforeDeletingTag() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        Tag tag = Tag.builder().id(7L).owner(user).build();
        com.entropybits.worknotes.spring_boot.entity.ClipTagLink link =
                com.entropybits.worknotes.spring_boot.entity.ClipTagLink.builder().id(1L).tag(tag).build();
        when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipTagLinkRepository.findByTag(tag)).thenReturn(List.of(link));

        service.deleteTag(7L, "alice");

        verify(clipTagLinkRepository).deleteAll(List.of(link));
        verify(tagRepository).delete(tag);
    }

    @Test
    void createTag_withClipScope_setsUsedByClipsTrueAndUsedByNotesFalse() {
        service = new TagService(tagRepository, userRepository, noteRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.existsByNameAndOwner("阅读", user)).thenReturn(false);
        when(tagRepository.save(any())).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            t.setId(9L);
            return t;
        });

        TagRequest request = new TagRequest();
        request.setName("阅读");
        request.setScope("clip");

        service.createTag(request, "alice");

        verify(tagRepository).save(argThat(t -> Boolean.FALSE.equals(t.getUsedByNotes())
                && Boolean.TRUE.equals(t.getUsedByClips())));
    }
}
