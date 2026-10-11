/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AgentImageRef;
import com.entropybits.worknotes.spring_boot.dto.AgentImageSource;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentImageEditServiceTest {

    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock AgentDocumentService agentDocumentService;
    @Mock MediaBlockService mediaBlockService;
    @Mock SafeImageDownloader downloader;
    @Mock UploadService uploadService;
    @Mock ContentIndexingService contentIndexingService;

    AgentImageEditService service;
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();

    @BeforeEach
    void setUp() {
        service = new AgentImageEditService(userRepository, noteRepository, agentDocumentService, mediaBlockService,
                downloader, uploadService, contentIndexingService, new ObjectMapper());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    }

    private Note markdownNote(User owner, String content) {
        return Note.builder().id(10L).title("t").owner(owner).contentType("markdown").content(content).build();
    }

    @Test
    void insert_fromOtherNotesLocalImage_reusesUrlSavesAndReindexes(@TempDir Path dir) throws Exception {
        Note note = markdownNote(alice, "【配图位】");
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(agentDocumentService.listNoteImages("alice", 5L))
                .thenReturn(List.of(new AgentImageRef(1, "http://localhost:8080/api/uploads/x/1.png", "")));
        Path file = Files.write(dir.resolve("1.png"), new byte[]{1});
        when(mediaBlockService.resolveLocalFilePath("http://localhost:8080/api/uploads/x/1.png")).thenReturn(file);

        Map<String, Object> event = service.insertImage("alice", 10L,
                new AgentImageSource("note", 5L, 1, null), "图1", "【配图位】");

        assertThat(note.getContent()).isEqualTo("![图1](http://localhost:8080/api/uploads/x/1.png)\n*图1*");
        assertThat(event).containsEntry("op", "insert").containsEntry("noteId", 10L).containsEntry("contentType", "markdown");
        verify(noteRepository).save(note);
        verify(contentIndexingService).reindexNote(10L);
        verify(downloader, never()).download(any());
    }

    @Test
    void insert_fromClipExternalImage_downloadsAndStoresLocally() throws Exception {
        Note note = markdownNote(alice, "");
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(agentDocumentService.listClipImages("alice", 7L))
                .thenReturn(List.of(new AgentImageRef(1, "https://mmbiz.qpic.cn/a.png", "")));
        when(downloader.download("https://mmbiz.qpic.cn/a.png"))
                .thenReturn(new SafeImageDownloader.DownloadedImage(new byte[]{1, 2}, "image/png", "png"));
        FileResultVo stored = new FileResultVo();
        stored.setUrl("http://localhost:8080/api/uploads/worknotesimage/public/agent_image/x.png");
        when(uploadService.imageUpload(any(MultipartFile.class), eq("agent_image"), eq(1), eq(1L))).thenReturn(stored);

        service.insertImage("alice", 10L, new AgentImageSource("clip", 7L, 1, null), "", null);

        assertThat(note.getContent()).isEqualTo("![](http://localhost:8080/api/uploads/worknotesimage/public/agent_image/x.png)");
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(uploadService).imageUpload(file.capture(), eq("agent_image"), eq(1), eq(1L));
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("agent-image.png");
    }

    /** Windows 上 Path.resolve 遇到 ?、: 会抛 InvalidPathException，不能变成 500，应当按外链下载 */
    @Test
    void insert_fromClipImageWithUploadsAndQueryInUrl_fallsBackToDownloadOnInvalidPath() throws Exception {
        Note note = markdownNote(alice, "");
        String wp = "https://blog.example.com/wp-content/uploads/2024/05/x.jpg?w=1024";
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(agentDocumentService.listClipImages("alice", 7L)).thenReturn(List.of(new AgentImageRef(1, wp, "")));
        when(mediaBlockService.resolveLocalFilePath(wp)).thenThrow(new java.nio.file.InvalidPathException(wp, "Illegal char <?>"));
        when(downloader.download(wp)).thenReturn(new SafeImageDownloader.DownloadedImage(new byte[]{1}, "image/jpeg", "jpg"));
        FileResultVo stored = new FileResultVo();
        stored.setUrl("/api/uploads/agent_image/x.jpg");
        when(uploadService.imageUpload(any(MultipartFile.class), eq("agent_image"), eq(1), eq(1L))).thenReturn(stored);

        service.insertImage("alice", 10L, new AgentImageSource("clip", 7L, 1, null), "", null);

        assertThat(note.getContent()).isEqualTo("![](/api/uploads/agent_image/x.jpg)");
    }

    @Test
    void insert_fromUrl_usesDownloader() throws Exception {
        Note note = markdownNote(alice, "");
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(downloader.download("https://example.com/a.jpg"))
                .thenReturn(new SafeImageDownloader.DownloadedImage(new byte[]{1}, "image/jpeg", "jpg"));
        FileResultVo stored = new FileResultVo();
        stored.setUrl("/api/uploads/a.jpg");
        when(uploadService.imageUpload(any(MultipartFile.class), eq("agent_image"), eq(1), eq(1L))).thenReturn(stored);

        service.insertImage("alice", 10L, new AgentImageSource("url", null, null, "https://example.com/a.jpg"), "", null);

        assertThat(note.getContent()).isEqualTo("![](/api/uploads/a.jpg)");
    }

    @Test
    void insert_fromAttachment_requiresExistingLocalUpload(@TempDir Path dir) throws Exception {
        Note note = markdownNote(alice, "");
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(mediaBlockService.resolveLocalFilePath("/api/uploads/chat/a.png")).thenReturn(dir.resolve("missing.png"));

        assertThatThrownBy(() -> service.insertImage("alice", 10L,
                new AgentImageSource("attachment", null, null, "/api/uploads/chat/a.png"), "", null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("附件");
        assertThatThrownBy(() -> service.insertImage("alice", 10L,
                new AgentImageSource("attachment", null, null, "https://evil.example/a.png"), "", null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("附件");
        verify(noteRepository, never()).save(any());
    }

    @Test
    void insert_intoOtherUsersNote_throwsNotFound() {
        when(noteRepository.findById(10L)).thenReturn(Optional.of(markdownNote(bob, "")));

        assertThatThrownBy(() -> service.insertImage("alice", 10L,
                new AgentImageSource("url", null, null, "https://example.com/a.png"), "", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(downloader, never()).download(any());
    }

    @Test
    void insert_noteSourceWithoutIndex_throwsBadRequest() {
        when(noteRepository.findById(10L)).thenReturn(Optional.of(markdownNote(alice, "")));

        assertThatThrownBy(() -> service.insertImage("alice", 10L, new AgentImageSource("note", 5L, null, null), "", null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("index");
    }

    @Test
    void delete_markdownImageByIndex() {
        Note note = markdownNote(alice, "a\n![x](/api/uploads/1.png)\n*x*\nb");
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));

        Map<String, Object> event = service.deleteImage("alice", 10L, 1);

        assertThat(note.getContent()).isEqualTo("a\nb");
        assertThat(event).containsEntry("op", "delete").containsEntry("noteId", 10L);
        verify(contentIndexingService).reindexNote(10L);
    }

    @Test
    void setCaption_editorjs_returnsBlockIdForFrontendSync() {
        Note note = Note.builder().id(10L).title("t").owner(alice).contentType("editorjs")
                .content("{\"blocks\":[{\"id\":\"img\",\"type\":\"image\",\"data\":{\"file\":{\"url\":\"/api/uploads/1.png\"},\"caption\":\"\"}}]}")
                .build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));

        Map<String, Object> event = service.setImageCaption("alice", 10L, 1, "新");

        assertThat(event).containsEntry("op", "caption").containsEntry("blockId", "img")
                .containsEntry("contentType", "editorjs");
        assertThat(note.getContent()).contains("\"caption\":\"新\"");
    }

    @Test
    void delete_indexOutOfRange_throws() {
        when(noteRepository.findById(10L)).thenReturn(Optional.of(markdownNote(alice, "![](/api/uploads/1.png)")));

        assertThatThrownBy(() -> service.deleteImage("alice", 10L, 2))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("共 1 张");
    }
}
