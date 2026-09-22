/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.LocalMediaDirectory;
import com.entropybits.worknotes.spring_boot.entity.LocalMediaFile;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaDirectoryRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaFileRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalMediaServiceTest {

    @Mock LocalMediaDirectoryRepository dirRepository;
    @Mock LocalMediaFileRepository fileRepository;
    @Mock UserRepository userRepository;
    @Mock LocalFileExtractionService extractionService;
    @Mock ContentIndexingService contentIndexingService;
    @Mock PlatformTransactionManager transactionManager;

    @TempDir Path tempDir;

    private LocalMediaService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new LocalMediaService(dirRepository, fileRepository, userRepository, extractionService,
                contentIndexingService, transactionManager);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(extractionService.registerImageForOcr(any())).thenReturn(
                LocalFileExtraction.builder()
                        .contentHash("stub-hash")
                        .status(LocalFileExtraction.Status.PENDING)
                        .build());
    }

    @Test
    void scan_classifiesImageFilesCorrectly() throws IOException {
        Files.createFile(tempDir.resolve("photo.jpg"));
        Files.createFile(tempDir.resolve("screenshot.png"));
        Files.createFile(tempDir.resolve("animation.gif"));
        Files.createFile(tempDir.resolve("ignored.txt"));

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(1L)
                .owner(user)
                .dirPath(tempDir.toString())
                .scanStatus("IDLE")
                .fileCount(0)
                .build();

        when(dirRepository.findById(1L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rescan(1L, "testuser");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalMediaFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileRepository, atLeastOnce()).saveAll(captor.capture());

        List<LocalMediaFile> saved = captor.getAllValues().stream()
                .flatMap(List::stream).toList();

        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(f -> "IMAGE".equals(f.getMediaType()));
        assertThat(saved.stream().map(LocalMediaFile::getFileExtension))
                .containsExactlyInAnyOrder("jpg", "png", "gif");
    }

    @Test
    void scan_resetsStatusToErrorWhenNonIOExceptionOccursMidScan() throws IOException {
        // scan()只catch IOException——如果批量落库这一步抛出的是别的RuntimeException
        // （DB瞬时异常、唯一约束冲突等），必须仍然能把scanStatus从SCANNING复位，
        // 否则这个目录以后每次扫描都会被"该目录正在扫描中"卡死，没有任何自愈手段。
        Files.createFile(tempDir.resolve("photo.jpg"));

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(3L).owner(user).dirPath(tempDir.toString()).scanStatus("IDLE").fileCount(0).build();
        when(dirRepository.findById(3L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("db hiccup")).when(fileRepository).deleteByDirectory(dir);

        service.rescan(3L, "testuser");

        assertThat(dir.getScanStatus()).isEqualTo("ERROR");
        assertThat(dir.getLastScanError()).contains("db hiccup");
    }

    @Test
    void scan_classifiesVideoAndAudioCorrectly() throws IOException {
        Files.createFile(tempDir.resolve("clip.mp4"));
        Files.createFile(tempDir.resolve("song.mp3"));
        Files.createFile(tempDir.resolve("audio.flac"));

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(2L)
                .owner(user)
                .dirPath(tempDir.toString())
                .scanStatus("IDLE")
                .fileCount(0)
                .build();

        when(dirRepository.findById(2L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rescan(2L, "testuser");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalMediaFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileRepository, atLeastOnce()).saveAll(captor.capture());

        List<LocalMediaFile> saved = captor.getAllValues().stream()
                .flatMap(List::stream).toList();

        assertThat(saved).hasSize(3);
        assertThat(saved.stream()
                .filter(f -> "VIDEO".equals(f.getMediaType()))
                .map(LocalMediaFile::getFileExtension))
                .containsExactly("mp4");
        assertThat(saved.stream()
                .filter(f -> "AUDIO".equals(f.getMediaType()))
                .map(LocalMediaFile::getFileExtension))
                .containsExactlyInAnyOrder("mp3", "flac");
    }

    @Test
    void scan_computesContentHashAndRegistersImageExtractionForImageFiles() throws IOException {
        Files.write(tempDir.resolve("photo.jpg"), "fake bytes".getBytes());

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(4L).owner(user).dirPath(tempDir.toString()).scanStatus("IDLE").fileCount(0).build();
        when(dirRepository.findById(4L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(extractionService.registerImageForOcr(any())).thenReturn(
                LocalFileExtraction.builder()
                        .contentHash("abc123hash")
                        .status(LocalFileExtraction.Status.PENDING)
                        .build());

        service.rescan(4L, "testuser");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalMediaFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileRepository, atLeastOnce()).saveAll(captor.capture());
        List<LocalMediaFile> saved = captor.getAllValues().stream().flatMap(List::stream).toList();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContentHash()).isEqualTo("abc123hash");
        verify(extractionService).registerImageForOcr(any());
        verify(contentIndexingService, never()).reindexLocalMediaExtraction(any());
    }

    @Test
    void scan_reindexesAfterSwapTransactionCommits() throws IOException {
        Files.write(tempDir.resolve("photo.jpg"), "fake bytes".getBytes());

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(6L).owner(user).dirPath(tempDir.toString()).scanStatus("IDLE").fileCount(0).build();
        when(dirRepository.findById(6L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(extractionService.registerImageForOcr(any())).thenReturn(
                LocalFileExtraction.builder()
                        .id(99L)
                        .contentHash("already-done-hash")
                        .status(LocalFileExtraction.Status.SUCCESS)
                        .build());

        service.rescan(6L, "testuser");

        // TransactionTemplate.execute()返回时删旧插新的短事务已经提交，之后直接同步调用即可。
        verify(contentIndexingService).reindexLocalMediaExtraction(99L);
    }

    @Test
    void scan_doesNotRegisterExtractionForNonImageFiles() throws IOException {
        Files.createFile(tempDir.resolve("clip.mp4"));

        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(5L).owner(user).dirPath(tempDir.toString()).scanStatus("IDLE").fileCount(0).build();
        when(dirRepository.findById(5L)).thenReturn(Optional.of(dir));
        when(dirRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rescan(5L, "testuser");

        verify(extractionService, never()).registerImageForOcr(any());
    }

    @Test
    void getAllFiles_searchesOnlyTheCurrentUsersFiles() {
        LocalMediaDirectory dir = LocalMediaDirectory.builder()
                .id(3L)
                .owner(user)
                .dirPath(tempDir.toString())
                .build();
        LocalMediaFile file = LocalMediaFile.builder()
                .id(10L)
                .directory(dir)
                .owner(user)
                .fileName("photo.jpg")
                .absolutePath(tempDir.resolve("photo.jpg").toString())
                .mediaType("IMAGE")
                .build();
        PageRequest pageable = PageRequest.of(0, 40);
        when(fileRepository.searchByOwner(user, "photo", "IMAGE", pageable))
                .thenReturn(new PageImpl<>(List.of(file), pageable, 1));

        var result = service.getAllFiles("testuser", "photo", "IMAGE", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(fileRepository).searchByOwner(user, "photo", "IMAGE", pageable);
    }
}
