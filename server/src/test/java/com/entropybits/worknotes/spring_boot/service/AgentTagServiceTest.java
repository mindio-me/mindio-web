/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.TagResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTagServiceTest {

    @Mock TagRepository tagRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock ClipTagLinkRepository clipTagLinkRepository;
    @Mock TagService tagService;
    @Mock SourceClipService sourceClipService;
    @InjectMocks AgentTagService service;

    private final User alice = User.builder().id(1L).username("alice").build();
    private final User bob = User.builder().id(2L).username("bob").build();

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    }

    private Tag tag(long id, String name) {
        return Tag.builder().id(id).name(name).owner(alice).usedByNotes(true).build();
    }

    @Test
    void updateNoteTags_addsExistingCreatesMissingAndRemovesByName() {
        Tag keep = tag(10L, "保留");
        Tag drop = tag(11L, "旧标签");
        Tag existing = tag(12L, "研究");
        Note note = Note.builder().id(5L).owner(alice).tags(new HashSet<>(Set.of(keep, drop))).build();
        Tag created = tag(13L, "新标签");
        when(noteRepository.findById(5L)).thenReturn(Optional.of(note));
        when(tagRepository.findByNameAndOwner("研究", alice)).thenReturn(Optional.of(existing));
        when(tagRepository.findByNameAndOwner("新标签", alice)).thenReturn(Optional.empty());
        when(tagService.createTag(argThat(r -> r != null && "新标签".equals(r.getName()) && "note".equals(r.getScope())), eq("alice")))
                .thenReturn(TagResponse.builder().id(13L).name("新标签").build());
        when(tagRepository.findById(13L)).thenReturn(Optional.of(created));

        Map<String, Object> result = service.updateNoteTags("alice", 5L, List.of(" 研究 ", "新标签"), List.of("旧标签"));

        assertThat(result.get("tags")).isEqualTo(List.of("保留", "研究", "新标签").stream().sorted().toList());
        verify(noteRepository).save(note);
    }

    @Test
    void updateNoteTags_rejectsSomeoneElsesNote() {
        when(noteRepository.findById(5L)).thenReturn(Optional.of(Note.builder().id(5L).owner(bob).build()));

        assertThatThrownBy(() -> service.updateNoteTags("alice", 5L, List.of("x"), null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void createTag_returnsExistingTagWithoutCreatingAnother() {
        when(tagRepository.findByNameAndOwner("研究", alice)).thenReturn(Optional.of(tag(12L, "研究")));

        Map<String, Object> result = service.createTag("alice", "研究", null);

        assertThat(result).containsEntry("id", 12L).containsEntry("created", false);
        verify(tagService, never()).createTag(any(), any());
    }

    @Test
    void deleteTag_unknownNameIsNotFound() {
        when(tagRepository.findByNameAndOwner("不存在", alice)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTag("alice", "不存在"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("不存在");
        verify(tagService, never()).deleteTag(any(), any());
    }

    @Test
    void deleteTag_delegatesToTagServiceById() {
        when(tagRepository.findByNameAndOwner("研究", alice)).thenReturn(Optional.of(tag(12L, "研究")));

        Map<String, Object> result = service.deleteTag("alice", "研究");

        verify(tagService).deleteTag(12L, "alice");
        assertThat(result).containsEntry("name", "研究");
    }

    @Test
    void blankTagNameIsRejected() {
        assertThatThrownBy(() -> service.createTag("alice", "  ", null)).isInstanceOf(BadRequestException.class);
    }
}
