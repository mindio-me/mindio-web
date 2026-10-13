/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class NoteBackupRoundTripTest {
    @Autowired NoteRepository notes;
    @Autowired UserRepository users;
    @Autowired AudioTranscriptionRepository transcriptions;
    @Autowired EntityManager em;
    @TempDir Path uploads;
    @TempDir Path extracted;

    @Test
    void singleNoteRoundTripPersistsIdentityAndRecognizesItOnBothOwners() throws Exception {
        User alice = users.saveAndFlush(User.builder().username("roundtrip-alice").email("roundtrip-a@example.com")
                .password("x").build());
        User bob = users.saveAndFlush(User.builder().username("roundtrip-bob").email("roundtrip-b@example.com")
                .password("x").build());
        Files.createDirectories(uploads.resolve("file"));
        Files.writeString(uploads.resolve("file/image.png"), "roundtrip-image");
        String content = "{\"blocks\":[{\"id\":\"img\",\"type\":\"image\",\"data\":{\"file\":{\"url\":\"/api/uploads/file/image.png\"}}}]}";
        Note original = notes.saveAndFlush(Note.builder().owner(alice).title("Single note").contentType("editorjs")
                .content(content).sectionTypes(new ArrayList<>(List.of("image")))
                .sectionContents(new ArrayList<>(List.of("{\"file\":{\"url\":\"/api/uploads/file/image.png\"}}"))).build());
        Long id = original.getId();
        UploadPathConfig paths = mock(UploadPathConfig.class);
        when(paths.getUploadPath()).thenReturn(uploads.toString());
        ObjectMapper mapper = new ObjectMapper();
        NoteExportService exporter = new NoteExportService(notes, users, transcriptions, paths, mapper, "");
        UploadService upload = mock(UploadService.class);
        when(upload.isAcceptableUpload(anyString(), anyLong())).thenReturn(true);
        String hash = NoteAssetRewriter.sha256Hex(uploads.resolve("file/image.png"));
        when(upload.findOwnedImageUrlByContentHash(bob.getId(), hash))
                .thenReturn(Optional.of("/api/uploads/file/image.png"));
        when(upload.findOwnedImageUrlByContentHash(alice.getId(), hash))
                .thenReturn(Optional.of("/api/uploads/file/image.png"));
        NoteImportService importer = new NoteImportService(notes, users, mock(NoteRevisionService.class), upload,
                mock(ContentIndexingService.class), transcriptions, new NoteImportStagingStore(Clock.systemUTC()),
                paths, mapper, "", 500);
        List<Path> exports = new ArrayList<>();
        try {
            NoteExportService.ExportResult first = exporter.export(alice.getUsername(), List.of(id), false, "", "web");
            exports.add(first.file());
            em.clear();
            String uid = notes.findById(id).orElseThrow().getUid();
            assertThat(UUID.fromString(uid).toString()).isEqualTo(uid);
            MindioPackageReader.ReadPackage pkg = new MindioPackageReader(mapper, 100_000_000, 1000)
                    .read(first.file(), extracted.resolve("first"));
            assertThat(pkg.notes()).singleElement().satisfies(n -> assertThat(n.uid()).isEqualTo(uid));
            assertThat(pkg.assets()).containsOnlyKeys(hash + ".png");
            assertThat(first.noteCount()).isEqualTo(1);
            assertThat(first.missingAssetCount()).isZero();
            byte[] bytes = Files.readAllBytes(first.file());

            Note local = notes.findById(id).orElseThrow();
            local.setTitle("Local edit");
            notes.saveAndFlush(local);
            NoteImportService.Preview same = importer.preview(alice.getUsername(), file(bytes));
            assertThat(same.newNotes()).isEmpty();
            assertThat(same.conflicts()).singleElement().satisfies(c -> assertThat(c.uid()).isEqualTo(uid));
            assertThat(importer.commit(alice.getUsername(), same.importToken(), Set.of()))
                    .isEqualTo(new NoteImportService.CommitResult(0, 0, 1));
            assertThat(notes.findById(id).orElseThrow().getTitle()).isEqualTo("Local edit");
            same = importer.preview(alice.getUsername(), file(bytes));
            assertThat(importer.commit(alice.getUsername(), same.importToken(), Set.of(uid)))
                    .isEqualTo(new NoteImportService.CommitResult(0, 1, 0));
            em.clear();
            assertThat(notes.findById(id).orElseThrow().getUid()).isEqualTo(uid);
            assertThat(notes.findById(id).orElseThrow().getTitle()).isEqualTo("Single note");

            NoteImportService.Preview other = importer.preview(bob.getUsername(), file(bytes));
            assertThat(other.conflicts()).isEmpty();
            assertThat(other.newNotes()).singleElement().satisfies(n -> assertThat(n.uid()).isEqualTo(uid));
            assertThat(importer.commit(bob.getUsername(), other.importToken(), Set.of()))
                    .isEqualTo(new NoteImportService.CommitResult(1, 0, 0));
            em.clear();
            Note imported = notes.findByOwnerAndUidIn(bob, List.of(uid)).get(0);
            assertThat(imported.getId()).isNotEqualTo(id);
            assertThat(imported.getContent()).isEqualTo(content);
            assertThat(imported.getSectionContents()).isEqualTo(pkg.notes().get(0).sections().stream()
                    .map(s -> s.content().replace("asset://" + hash + ".png", "/api/uploads/file/image.png")).toList());
            NoteExportService.ExportResult again = exporter.export(bob.getUsername(), List.of(imported.getId()), false, "", "desktop");
            exports.add(again.file());
            assertThat(new MindioPackageReader(mapper, 100_000_000, 1000)
                    .read(again.file(), extracted.resolve("again")).notes()).singleElement()
                    .satisfies(n -> assertThat(n.uid()).isEqualTo(uid));
            NoteImportService.Preview repeated = importer.preview(bob.getUsername(), file(bytes));
            assertThat(repeated.conflicts()).hasSize(1);
            assertThat(importer.commit(bob.getUsername(), repeated.importToken(), Set.of()))
                    .isEqualTo(new NoteImportService.CommitResult(0, 0, 1));
        } finally {
            for (Path path : exports) Files.deleteIfExists(path);
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "single.mindio", "application/octet-stream", bytes);
    }
}
