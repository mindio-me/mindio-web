/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.NoteRequest;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuDocumentSnapshotRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuImageMappingRepository;
import com.entropybits.worknotes.spring_boot.integration.feishu.repository.FeishuWikiImportMappingRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteServiceTest {

    @Test
    void rewritesSingleUploadUrlPrefix() {
        String content = "![img](http://localhost:8081/api/uploads/worknotesimage/public/a.png)";

        String result = NoteService.rewriteUploadUrls(content, "https://cdn.example.com/api");

        assertThat(result).isEqualTo(
                "![img](https://cdn.example.com/api/uploads/worknotesimage/public/a.png)");
    }

    @Test
    void doesNotSwallowContentBetweenMultipleImageUrls() {
        // 回归用例：飞书导入文档里多张图片之间夹杂着含双引号的文本（如 JSON 示例）时，
        // 旧的贪婪正则 [^"]+ 会把两张图片之间的所有正文当成 URL 的一部分吞掉。
        String content = "![img1](http://localhost:8081/api/uploads/a.png)\n"
                + "这是图片之间的重要正文内容，不应该丢失。\n"
                + "示例 JSON：{msg: \"登录成功\"}\n"
                + "![img2](http://localhost:8081/api/uploads/b.png)\n"
                + "图片之后的内容也不应该丢失。";

        String result = NoteService.rewriteUploadUrls(content, "https://cdn.example.com/api");

        assertThat(result)
                .contains("![img1](https://cdn.example.com/api/uploads/a.png)")
                .contains("![img2](https://cdn.example.com/api/uploads/b.png)")
                .contains("这是图片之间的重要正文内容，不应该丢失。")
                .contains("示例 JSON：{msg: \"登录成功\"}")
                .contains("图片之后的内容也不应该丢失。");
    }

    @Test
    void leavesContentUnchangedWhenNoMatchingUrls() {
        String content = "普通文本，没有需要替换的 URL。";

        String result = NoteService.rewriteUploadUrls(content, "https://cdn.example.com/api");

        assertThat(result).isEqualTo(content);
    }

    @Test
    void createNote_marksFetchedTagsAsUsedByNotes() {
        NoteRepository noteRepository = mock(NoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FeishuWikiImportMappingRepository feishuWikiImportMappingRepository = mock(FeishuWikiImportMappingRepository.class);
        FeishuDocumentSnapshotRepository feishuDocumentSnapshotRepository = mock(FeishuDocumentSnapshotRepository.class);
        FeishuImageMappingRepository feishuImageMappingRepository = mock(FeishuImageMappingRepository.class);
        ContentIndexingService contentIndexingService = mock(ContentIndexingService.class);
        NoteService service = new NoteService(noteRepository, userRepository, tagRepository, projectRepository,
                feishuWikiImportMappingRepository, feishuDocumentSnapshotRepository, feishuImageMappingRepository,
                contentIndexingService);

        User user = User.builder().id(1L).build();
        Tag tag = Tag.builder().id(5L).owner(user).name("学习").usedByNotes(false).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feishuWikiImportMappingRepository.findByNote(any())).thenReturn(Optional.empty());

        NoteRequest request = new NoteRequest();
        request.setTitle("标题");
        request.setContentType("richtext");
        request.setTagIds(Set.of(5L));

        service.createNote(request, "alice");

        verify(tagRepository).save(argThat(t -> Boolean.TRUE.equals(t.getUsedByNotes())));
    }

    @Test
    void createNote_triggersAsyncReindexWithSavedNoteId() {
        NoteRepository noteRepository = mock(NoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FeishuWikiImportMappingRepository feishuWikiImportMappingRepository = mock(FeishuWikiImportMappingRepository.class);
        FeishuDocumentSnapshotRepository feishuDocumentSnapshotRepository = mock(FeishuDocumentSnapshotRepository.class);
        FeishuImageMappingRepository feishuImageMappingRepository = mock(FeishuImageMappingRepository.class);
        ContentIndexingService contentIndexingService = mock(ContentIndexingService.class);
        NoteService service = new NoteService(noteRepository, userRepository, tagRepository, projectRepository,
                feishuWikiImportMappingRepository, feishuDocumentSnapshotRepository, feishuImageMappingRepository,
                contentIndexingService);

        User user = User.builder().id(1L).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(noteRepository.save(any())).thenAnswer(inv -> {
            var note = (com.entropybits.worknotes.spring_boot.entity.Note) inv.getArgument(0);
            note.setId(77L);
            return note;
        });

        NoteRequest request = new NoteRequest();
        request.setTitle("标题");
        request.setContent("内容");
        request.setContentType("markdown");

        service.createNote(request, "alice");

        verify(contentIndexingService).reindexNote(77L);
    }

    @Test
    void updateNote_triggersAsyncReindexWithUpdatedNoteId() {
        NoteRepository noteRepository = mock(NoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FeishuWikiImportMappingRepository feishuWikiImportMappingRepository = mock(FeishuWikiImportMappingRepository.class);
        FeishuDocumentSnapshotRepository feishuDocumentSnapshotRepository = mock(FeishuDocumentSnapshotRepository.class);
        FeishuImageMappingRepository feishuImageMappingRepository = mock(FeishuImageMappingRepository.class);
        ContentIndexingService contentIndexingService = mock(ContentIndexingService.class);
        NoteService service = new NoteService(noteRepository, userRepository, tagRepository, projectRepository,
                feishuWikiImportMappingRepository, feishuDocumentSnapshotRepository, feishuImageMappingRepository,
                contentIndexingService);

        User user = User.builder().id(1L).build();
        Note existingNote = Note.builder().id(42L).owner(user).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(noteRepository.findById(42L)).thenReturn(Optional.of(existingNote));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NoteRequest request = new NoteRequest();
        request.setTitle("新标题");
        request.setContent("新内容");
        request.setContentType("markdown");

        service.updateNote(42L, request, "alice");

        verify(contentIndexingService).reindexNote(42L);
    }

    @Test
    void deleteNote_cleansUpChunksBeforeDeletingNote() {
        NoteRepository noteRepository = mock(NoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TagRepository tagRepository = mock(TagRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FeishuWikiImportMappingRepository feishuWikiImportMappingRepository = mock(FeishuWikiImportMappingRepository.class);
        FeishuDocumentSnapshotRepository feishuDocumentSnapshotRepository = mock(FeishuDocumentSnapshotRepository.class);
        FeishuImageMappingRepository feishuImageMappingRepository = mock(FeishuImageMappingRepository.class);
        ContentIndexingService contentIndexingService = mock(ContentIndexingService.class);
        NoteService service = new NoteService(noteRepository, userRepository, tagRepository, projectRepository,
                feishuWikiImportMappingRepository, feishuDocumentSnapshotRepository, feishuImageMappingRepository,
                contentIndexingService);

        User user = User.builder().id(1L).build();
        Note existingNote = Note.builder().id(9L).owner(user).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(noteRepository.findById(9L)).thenReturn(Optional.of(existingNote));

        service.deleteNote(9L, "alice");

        InOrder inOrder = inOrder(contentIndexingService, noteRepository);
        inOrder.verify(contentIndexingService).deleteChunksFor(ContentChunk.SourceType.NOTE, 9L);
        inOrder.verify(noteRepository).delete(existingNote);
    }
}
