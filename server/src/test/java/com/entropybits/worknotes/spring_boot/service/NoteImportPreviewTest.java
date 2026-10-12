/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteImportPreviewTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRevisionService revisionService;
    @Mock UploadService uploadService;
    @Mock ContentIndexingService contentIndexingService;
    @Mock UploadPathConfig uploadPathConfig;
    @TempDir Path uploads;

    final ObjectMapper om = new ObjectMapper();
    NoteImportStagingStore store = new NoteImportStagingStore(Clock.systemUTC());
    NoteImportService service;
    User alice = User.builder().id(1L).username("alice").build();
    static final String UID1 = "11111111-1111-1111-1111-111111111111";
    static final String UID2 = "22222222-2222-2222-2222-222222222222";
    static final String HASH = "c".repeat(64);

    @BeforeEach
    void setUp() {
        lenient().when(uploadPathConfig.getUploadPath()).thenReturn(uploads.toString());
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        lenient().when(uploadService.isAcceptableUpload(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> !((String) inv.getArgument(0)).endsWith(".bin"));
        service = new NoteImportService(noteRepository, userRepository, revisionService, uploadService,
                contentIndexingService, store, uploadPathConfig, om, "", 500);
    }

    static MockMultipartFile packageFile(ObjectMapper om, Path assetSource, List<MindioPackage.PackagedNote> notes) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MindioPackageWriter w = new MindioPackageWriter(bytes, om)) {
            for (MindioPackage.PackagedNote n : notes) w.writeNote(n);
            if (assetSource != null) w.writeAsset(HASH + ".png", assetSource);
            w.writeManifest(new MindioPackage.Manifest(MindioPackage.FORMAT, 1, "2026-10-12T00:00:00Z", "desktop",
                    notes.size(), List.of()));
        }
        return new MockMultipartFile("file", "backup.mindio", "application/octet-stream", bytes.toByteArray());
    }

    static MindioPackage.PackagedNote pn(String uid, String title, String content) {
        return new MindioPackage.PackagedNote(uid, title, "sum", "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z",
                "editorjs", content, List.of());
    }

    @Test
    void splitsNewAndConflictingNotesAndListsMissingAssets() throws Exception {
        Path asset = uploads.resolve("src.png");
        Files.writeString(asset, "img");
        Note local = Note.builder().id(9L).owner(alice).uid(UID2).title("本机标题")
                .modifiedAt(LocalDateTime.of(2026, 2, 1, 0, 0)).build();
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of(local));

        NoteImportService.Preview p = service.preview("alice", packageFile(om, asset, List.of(
                pn(UID1, "新的", "{\"u\":\"asset://" + HASH + ".png\",\"v\":\"asset://" + "d".repeat(64) + ".jpg\"}"),
                pn(UID2, "包里标题", "{}"))));

        assertThat(p.importToken()).isNotBlank();
        assertThat(p.newNotes()).containsExactly(new NoteImportService.NewItem(UID1, "新的"));
        assertThat(p.conflicts()).containsExactly(new NoteImportService.ConflictItem(
                UID2, "本机标题", "2026-02-01T00:00:00Z", "包里标题", "2026-03-01T00:00:00Z"));
        assertThat(p.missingAssets()).containsExactly("d".repeat(64) + ".jpg");
        verify(noteRepository).findByOwnerAndUidIn(alice, List.of(UID1, UID2));
        verifyNoInteractions(revisionService);
    }

    @Test
    void assetsTheUploadServiceWouldRejectAreListedAsMissingOnce() throws Exception {
        Path png = Files.writeString(uploads.resolve("src.png"), "img");
        Path bin = Files.writeString(uploads.resolve("src.bin"), "blob");
        String binName = "b".repeat(64) + ".bin";
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (MindioPackageWriter w = new MindioPackageWriter(bytes, om)) {
            w.writeNote(pn(UID1, "一", "{\"u\":\"asset://" + HASH + ".png\",\"v\":\"asset://" + binName + "\",\"w\":\"asset://" + binName + "\"}"));
            w.writeAsset(HASH + ".png", png);
            w.writeAsset(binName, bin);
            w.writeAsset("a".repeat(64) + ".bin", bin);
            w.writeManifest(new MindioPackage.Manifest(MindioPackage.FORMAT, 1, null, "desktop", 1, List.of()));
        }
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of());

        NoteImportService.Preview p = service.preview("alice",
                new MockMultipartFile("file", "b.mindio", "application/octet-stream", bytes.toByteArray()));

        assertThat(p.missingAssets()).containsExactly(binName);
    }

    @Test
    void invalidPackageIsRejectedAndNothingStaged() {
        MockMultipartFile junk = new MockMultipartFile("file", "x.mindio", "application/octet-stream", "junk".getBytes());
        assertThatThrownBy(() -> service.preview("alice", junk))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
    }
}
