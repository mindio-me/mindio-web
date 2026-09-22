/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaBlockServiceTest {

    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final UploadPathConfig uploadPathConfig = mock(UploadPathConfig.class);
    private MediaBlockService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new MediaBlockService(noteRepository, new ObjectMapper(), uploadPathConfig);
        when(uploadPathConfig.getUploadPath()).thenReturn(tempDir.toString());
    }

    private Note noteWithBlocks(String blocksJson) {
        return Note.builder().id(1L).contentType("editorjs")
                .content("{\"blocks\":" + blocksJson + "}").build();
    }

    @Test
    void listMediaBlocks_returnsImageAndAudioBlocksWithExistingFields() {
        Note note = noteWithBlocks("["
                + "{\"id\":\"b1\",\"type\":\"image\",\"data\":{\"url\":\"/api/uploads/a.png\",\"caption\":\"一张图\"}},"
                + "{\"id\":\"b2\",\"type\":\"audio\",\"data\":{\"url\":\"/api/uploads/a.mp3\"}},"
                + "{\"id\":\"b3\",\"type\":\"paragraph\",\"data\":{\"text\":\"正文\"}}"
                + "]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        List<Map<String, Object>> result = service.listMediaBlocks(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("blockId", "b1").containsEntry("blockType", "image")
                .containsEntry("caption", "一张图");
        assertThat(result.get(1)).containsEntry("blockId", "b2").containsEntry("blockType", "audio")
                .doesNotContainKey("caption");
    }

    @Test
    void listMediaBlocks_skipsBlocksWithoutId() {
        Note note = noteWithBlocks("[{\"type\":\"image\",\"data\":{\"url\":\"a.png\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThat(service.listMediaBlocks(1L)).isEmpty();
    }

    @Test
    void getBlockFile_readsBytesFromLocalDiskAndBase64Encodes() throws Exception {
        Path sub = tempDir.resolve("public/audio");
        Files.createDirectories(sub);
        Path audioFile = sub.resolve("clip.mp3");
        byte[] content = "fake-mp3-bytes".getBytes();
        Files.write(audioFile, content);

        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"audio\","
                + "\"data\":{\"url\":\"/api/uploads/public/audio/clip.mp3\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        Map<String, Object> result = service.getBlockFile(1L, "b1");

        assertThat(result.get("mimeType")).isEqualTo("audio/mpeg");
        assertThat(result.get("base64Data")).isEqualTo(java.util.Base64.getEncoder().encodeToString(content));
    }

    @Test
    void getBlockFile_resolvesNestedFileUrlForImageBlocks() throws Exception {
        Path sub = tempDir.resolve("public/img");
        Files.createDirectories(sub);
        Path imageFile = sub.resolve("a.png");
        byte[] content = "fake-png-bytes".getBytes();
        Files.write(imageFile, content);

        // 真实 @editorjs/image 的形状：url 在 data.file.url，根本没有顶层 data.url
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\","
                + "\"data\":{\"file\":{\"url\":\"/api/uploads/public/img/a.png\"},\"caption\":\"\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        Map<String, Object> result = service.getBlockFile(1L, "b1");

        assertThat(result.get("mimeType")).isEqualTo("image/png");
        assertThat(result.get("base64Data")).isEqualTo(java.util.Base64.getEncoder().encodeToString(content));
    }

    @Test
    void listMediaBlocks_resolvesNestedFileUrlForImageBlocks() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\","
                + "\"data\":{\"file\":{\"url\":\"/api/uploads/public/img/a.png\"},\"caption\":\"\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        List<Map<String, Object>> result = service.listMediaBlocks(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("url", "/api/uploads/public/img/a.png");
    }

    @Test
    void getBlockFile_rejectsPathTraversal() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\","
                + "\"data\":{\"file\":{\"url\":\"/api/uploads/../../../../etc/passwd\"}}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.getBlockFile(1L, "b1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("非法的文件路径");
    }

    @Test
    void getBlockFile_rejectsOversizedFile() throws Exception {
        Path sub = tempDir.resolve("public/audio");
        Files.createDirectories(sub);
        Files.write(sub.resolve("huge.mp3"), new byte[26 * 1024 * 1024]);

        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"audio\","
                + "\"data\":{\"url\":\"/api/uploads/public/audio/huge.mp3\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.getBlockFile(1L, "b1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("文件过大");
    }

    @Test
    void getBlockFile_throwsWhenBlockHasNoUrl() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\",\"data\":{}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.getBlockFile(1L, "b1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void getBlockFile_throwsWhenBlockIdNotFound() {
        Note note = noteWithBlocks("[]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.getBlockFile(1L, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void patchBlock_writesCaptionOntoImageBlockAndReturnsFullData() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\","
                + "\"data\":{\"url\":\"/api/uploads/a.png\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.patchBlock(1L, "b1", Map.of("caption", "一张手写笔记照片"));

        assertThat(result).containsEntry("caption", "一张手写笔记照片").containsEntry("url", "/api/uploads/a.png");
        assertThat(note.getContent()).contains("一张手写笔记照片");
    }

    @Test
    void patchBlock_writesTranscriptAndSummaryOntoAudioBlock() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"audioRecord\","
                + "\"data\":{\"url\":\"/api/uploads/a.webm\",\"duration\":12}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.patchBlock(1L, "b1",
                Map.of("transcript", "今天开会讨论了三件事", "summary", "会议纪要：三件事"));

        assertThat(result).containsEntry("transcript", "今天开会讨论了三件事")
                .containsEntry("summary", "会议纪要：三件事").containsEntry("duration", 12);
    }

    @Test
    void patchBlock_rejectsFieldNotInWhitelistForBlockType() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"image\",\"data\":{\"url\":\"a.png\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.patchBlock(1L, "b1", Map.of("transcript", "不该能写这个")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void patchBlock_rejectsUnsupportedBlockType() {
        Note note = noteWithBlocks("[{\"id\":\"b1\",\"type\":\"paragraph\",\"data\":{\"text\":\"x\"}}]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.patchBlock(1L, "b1", Map.of("caption", "x")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void patchBlock_throwsWhenBlockIdNotFound() {
        Note note = noteWithBlocks("[]");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.patchBlock(1L, "missing", Map.of("caption", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
