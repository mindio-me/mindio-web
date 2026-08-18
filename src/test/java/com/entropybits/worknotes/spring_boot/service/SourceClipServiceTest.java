/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.SourceClipRequest;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.entity.ClipTagLink;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceClipServiceTest {

    @Mock SourceClipRepository clipRepository;
    @Mock UserRepository userRepository;
    @Mock TagRepository tagRepository;
    @Mock ClipTagLinkRepository clipTagLinkRepository;

    private SourceClipService service;

    @Test
    void updateTitle_changesOnlyTitle_preservesOtherFields() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        SourceClip clip = SourceClip.builder()
                .id(1L)
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .title("Old Title")
                .content("<p>original content</p>")
                .extractionMode(SourceClip.ExtractionMode.FULL)
                .extractionStatus(SourceClip.ExtractionStatus.SUCCESS)
                .build();
        when(clipRepository.findById(1L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SourceClipResponse response = service.updateTitle(1L, "New Title");

        assertThat(response.getTitle()).isEqualTo("New Title");
        assertThat(clip.getContent()).isEqualTo("<p>original content</p>");
        assertThat(clip.getExtractionStatus()).isEqualTo(SourceClip.ExtractionStatus.SUCCESS);
    }

    @Test
    void updateTitle_throwsWhenClipNotFound() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        when(clipRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTitle(99L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getClip_bumpsLastAccessedAtToNow() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        SourceClip clip = SourceClip.builder().id(1L).sourceType(SourceClip.SourceType.WEBPAGE)
                .title("Title").lastAccessedAt(java.time.LocalDateTime.now().minusDays(3)).build();
        when(clipRepository.findById(1L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        java.time.LocalDateTime before = java.time.LocalDateTime.now();
        service.getClip(1L, "alice");

        assertThat(clip.getLastAccessedAt()).isAfterOrEqualTo(before);
        verify(clipRepository).save(clip);
    }

    @Test
    void listRecentlyAccessed_mapsToSummaryResponsesInRepositoryOrder() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip recent = SourceClip.builder().id(2L).title("最近打开的").build();
        SourceClip older = SourceClip.builder().id(1L).title("较早打开的").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findRecentlyAccessedByOwner(eq(user), any()))
                .thenReturn(List.of(recent, older));

        List<SourceClipResponse> result = service.listRecentlyAccessed("alice", 5);

        assertThat(result).extracting(SourceClipResponse::getId).containsExactly(2L, 1L);
    }

    private SourceClipRequest baseRequest(List<Long> tagIds) {
        SourceClipRequest request = new SourceClipRequest();
        request.setSourceType(SourceClip.SourceType.WEBPAGE);
        request.setTitle("Title");
        request.setContent("<p>content</p>");
        request.setTagIds(tagIds);
        return request;
    }

    @Test
    void createClip_withNewTagId_createsManualLinkAndFlipsTagUsedByClips() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).username("alice").build();
        Tag tag = Tag.builder().id(10L).name("AI").owner(user).usedByClips(false).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(any())).thenReturn(List.of());
        when(tagRepository.findById(10L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createClip(baseRequest(List.of(10L)), "alice");

        verify(clipTagLinkRepository).save(argThat(link ->
                link.getTag() == tag && Boolean.TRUE.equals(link.getManuallyAdded())));
        assertThat(tag.getUsedByClips()).isTrue();
        verify(tagRepository).save(tag);
    }

    @Test
    void updateClip_promotesExistingAiSuggestedLinkWhenTagKeptInNewList() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).username("alice").build();
        Tag tag = Tag.builder().id(20L).name("AI").owner(user).usedByClips(true).build();
        SourceClip clip = SourceClip.builder().id(1L).sourceType(SourceClip.SourceType.WEBPAGE)
                .title("Old").owner(user).build();
        ClipTagLink existingLink = ClipTagLink.builder().id(100L).clip(clip).tag(tag)
                .manuallyAdded(false).aiSuggested(true).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(1L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(clip)).thenReturn(List.of(existingLink));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateClip(1L, baseRequest(List.of(20L)), "alice");

        assertThat(existingLink.getManuallyAdded()).isTrue();
        assertThat(existingLink.getAiSuggested()).isTrue();
        verify(clipTagLinkRepository).save(existingLink);
        verify(clipTagLinkRepository, never()).save(argThat(link -> link != existingLink));
        verify(tagRepository, never()).findById(any());
    }

    @Test
    void updateClip_demotesManualAiLinkInsteadOfDeletingWhenTagRemovedFromNewList() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).username("alice").build();
        Tag tag = Tag.builder().id(30L).name("AI").owner(user).usedByClips(true).build();
        SourceClip clip = SourceClip.builder().id(1L).sourceType(SourceClip.SourceType.WEBPAGE)
                .title("Old").owner(user).build();
        ClipTagLink existingLink = ClipTagLink.builder().id(101L).clip(clip).tag(tag)
                .manuallyAdded(true).aiSuggested(true).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(1L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(clip)).thenReturn(List.of(existingLink));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateClip(1L, baseRequest(List.of()), "alice");

        assertThat(existingLink.getManuallyAdded()).isFalse();
        assertThat(existingLink.getAiSuggested()).isTrue();
        verify(clipTagLinkRepository).save(existingLink);
        verify(clipTagLinkRepository, never()).delete(any());
    }

    @Test
    void updateClip_deletesManualOnlyLinkWhenTagRemovedFromNewList() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).username("alice").build();
        Tag tag = Tag.builder().id(40L).name("AI").owner(user).usedByClips(true).build();
        SourceClip clip = SourceClip.builder().id(1L).sourceType(SourceClip.SourceType.WEBPAGE)
                .title("Old").owner(user).build();
        ClipTagLink existingLink = ClipTagLink.builder().id(102L).clip(clip).tag(tag)
                .manuallyAdded(true).aiSuggested(false).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(1L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(clip)).thenReturn(List.of(existingLink));
        when(clipTagLinkRepository.existsByTag(tag)).thenReturn(false);
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateClip(1L, baseRequest(List.of()), "alice");

        verify(clipTagLinkRepository).delete(existingLink);
        verify(clipTagLinkRepository, never()).save(existingLink);
        assertThat(tag.getUsedByClips()).isFalse();
    }

    @Test
    void addTag_createsManuallyAddedLinkAndMarksTagUsedByClips() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).usedByClips(false).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.findByClipAndTag(clip, tag)).thenReturn(Optional.empty());
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addTag(10L, 5L, "alice");

        verify(clipTagLinkRepository).save(argThat(l -> Boolean.TRUE.equals(l.getManuallyAdded())));
        verify(tagRepository).save(argThat(t -> Boolean.TRUE.equals(t.getUsedByClips())));
        assertThat(clip.getTagsManuallyAdjusted()).isTrue();
    }

    @Test
    void addTag_reactivatesExistingAiSuggestedLinkAsManuallyAdded() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).usedByClips(true).build();
        ClipTagLink existingLink = ClipTagLink.builder().id(1L).clip(clip).tag(tag)
                .manuallyAdded(false).aiSuggested(true).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.findByClipAndTag(clip, tag)).thenReturn(Optional.of(existingLink));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addTag(10L, 5L, "alice");

        assertThat(existingLink.getManuallyAdded()).isTrue();
        assertThat(existingLink.getAiSuggested()).isTrue();
    }

    @Test
    void addTag_throwsWhenClipNotOwnedByUser() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(2L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(otherUser).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));

        assertThatThrownBy(() -> service.addTag(10L, 5L, "alice"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void removeTag_throwsWhenClipNotOwnedByUser() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(2L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(otherUser).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));

        assertThatThrownBy(() -> service.removeTag(10L, 5L, "alice"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void removeTag_deletesLinkWhenNotAiSuggested() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).usedByClips(true).build();
        ClipTagLink link = ClipTagLink.builder().id(1L).clip(clip).tag(tag)
                .manuallyAdded(true).aiSuggested(false).build();
        clip.getClipTagLinks().add(link);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.findByClipAndTag(clip, tag)).thenReturn(Optional.of(link));
        when(clipTagLinkRepository.existsByTag(tag)).thenReturn(false);
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeTag(10L, 5L, "alice");

        verify(clipTagLinkRepository).delete(link);
        assertThat(clip.getClipTagLinks()).doesNotContain(link);
        assertThat(tag.getUsedByClips()).isFalse();
        verify(tagRepository).save(tag);
    }

    @Test
    void removeTag_keepsUsedByClipsTrueWhenTagStillLinkedElsewhere() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).usedByClips(true).build();
        ClipTagLink link = ClipTagLink.builder().id(1L).clip(clip).tag(tag)
                .manuallyAdded(true).aiSuggested(false).build();
        clip.getClipTagLinks().add(link);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.findByClipAndTag(clip, tag)).thenReturn(Optional.of(link));
        // 标签在另一个 clip 上还有生效链接，删除本条不应清空 usedByClips
        when(clipTagLinkRepository.existsByTag(tag)).thenReturn(true);
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeTag(10L, 5L, "alice");

        assertThat(tag.getUsedByClips()).isTrue();
        verify(tagRepository, never()).save(tag);
    }

    @Test
    void removeTag_keepsRowButClearsManuallyAddedWhenAiSuggested() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).build();
        ClipTagLink link = ClipTagLink.builder().id(1L).clip(clip).tag(tag)
                .manuallyAdded(true).aiSuggested(true).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(clipTagLinkRepository.findByClipAndTag(clip, tag)).thenReturn(Optional.of(link));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeTag(10L, 5L, "alice");

        verify(clipTagLinkRepository, org.mockito.Mockito.never()).delete(any());
        assertThat(link.getManuallyAdded()).isFalse();
        assertThat(link.getAiSuggested()).isTrue();
    }

    @Test
    void updateClip_throwsWhenClipNotOwnedByUser() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(2L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(otherUser).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));

        assertThatThrownBy(() -> service.updateClip(10L, baseRequest(List.of()), "alice"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateClip_throwsWhenTagIdBelongsToDifferentUser() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(2L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag otherUsersTag = Tag.builder().id(50L).owner(otherUser).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(clip)).thenReturn(List.of());
        when(tagRepository.findById(50L)).thenReturn(Optional.of(otherUsersTag));

        assertThatThrownBy(() -> service.updateClip(10L, baseRequest(List.of(50L)), "alice"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void createClip_throwsWhenTagIdBelongsToDifferentUser() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        User otherUser = User.builder().id(2L).build();
        Tag otherUsersTag = Tag.builder().id(50L).owner(otherUser).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(clipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.findByClip(any())).thenReturn(List.of());
        when(tagRepository.findById(50L)).thenReturn(Optional.of(otherUsersTag));

        assertThatThrownBy(() -> service.createClip(baseRequest(List.of(50L)), "alice"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deleteClip_deletesClipTagLinksBeforeDeletingClip() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        SourceClip clip = SourceClip.builder().id(10L).owner(user).build();
        Tag tag = Tag.builder().id(5L).owner(user).usedByClips(true).build();
        ClipTagLink link = ClipTagLink.builder().id(1L).clip(clip).tag(tag).build();
        when(clipRepository.findById(10L)).thenReturn(Optional.of(clip));
        when(clipTagLinkRepository.findByClip(clip)).thenReturn(List.of(link));
        when(clipTagLinkRepository.existsByTag(tag)).thenReturn(false);
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deleteClip(10L, "alice");

        verify(clipTagLinkRepository).deleteAll(List.of(link));
        verify(clipRepository).delete(clip);
        assertThat(tag.getUsedByClips()).isFalse();
    }

    @Test
    void listClips_filtersByTagIdWhenProvided() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Pageable pageable = PageRequest.of(0, 20);
        when(clipRepository.searchByOwnerAndTypeAndTags(user, null, null, List.of(5L), pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.listClips("alice", null, null, List.of(5L), false, pageable);

        verify(clipRepository).searchByOwnerAndTypeAndTags(user, null, null, List.of(5L), pageable);
        verify(clipRepository, never()).findByOwner(any(), any());
        verify(clipRepository, never()).searchByOwnerAndType(any(), any(), any(), any());
    }

    @Test
    void listClips_filtersByAnyMatchingTagWhenMultipleTagIdsProvided() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Pageable pageable = PageRequest.of(0, 20);
        when(clipRepository.searchByOwnerAndTypeAndTags(user, null, null, List.of(5L, 7L), pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.listClips("alice", null, null, List.of(5L, 7L), false, pageable);

        verify(clipRepository).searchByOwnerAndTypeAndTags(user, null, null, List.of(5L, 7L), pageable);
    }

    @Test
    void listClips_ignoresEmptyTagIdList() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Pageable pageable = PageRequest.of(0, 20);
        when(clipRepository.findByOwner(user, pageable)).thenReturn(new PageImpl<>(List.of()));

        service.listClips("alice", null, null, List.of(), false, pageable);

        verify(clipRepository, never()).searchByOwnerAndTypeAndTags(any(), any(), any(), any(), any());
        verify(clipRepository).findByOwner(user, pageable);
    }

    @Test
    void listClips_usesNoTagsQueryWhenUntaggedRequested() {
        service = new SourceClipService(clipRepository, userRepository, tagRepository, clipTagLinkRepository);
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        Pageable pageable = PageRequest.of(0, 20);
        when(clipRepository.searchByOwnerAndTypeWithNoTags(user, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        // untagged=true 应该优先于 tagIds，即使两者同时传入也走"无标签"查询
        service.listClips("alice", null, null, List.of(5L), true, pageable);

        verify(clipRepository).searchByOwnerAndTypeWithNoTags(user, null, null, pageable);
        verify(clipRepository, never()).searchByOwnerAndTypeAndTags(any(), any(), any(), any(), any());
    }
}
