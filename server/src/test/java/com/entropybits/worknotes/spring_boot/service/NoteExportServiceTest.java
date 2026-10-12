/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteExportServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock UploadPathConfig uploadPathConfig;
    @TempDir Path uploads;
    @TempDir Path out;

    final ObjectMapper om = new ObjectMapper();
    NoteExportService service;
    final List<Path> produced = new ArrayList<>();
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();
    final LocalDateTime t = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(uploadPathConfig.getUploadPath()).thenReturn(uploads.toString());
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        service = new NoteExportService(noteRepository, userRepository, uploadPathConfig, om, "");
        Files.createDirectories(uploads.resolve("file"));
        Files.writeString(uploads.resolve("file/a.png"), "img-a");
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Path p : produced) Files.deleteIfExists(p);
    }

    Note note(long id, User owner, String uid, String content) {
        return Note.builder().id(id).owner(owner).uid(uid).title("n" + id).summary("s").contentType("editorjs")
                .content(content).createdAt(t).modifiedAt(t)
                .sectionTypes(new ArrayList<>(List.of("image")))
                .sectionContents(new ArrayList<>(List.of("{\"file\":{\"url\":\"/api/uploads/file/a.png\"}}")))
                .build();
    }

    NoteExportService.ExportResult run(List<Long> ids, boolean all) {
        NoteExportService.ExportResult r = service.export("alice", ids, all, "https://mindio.example.com", "web");
        produced.add(r.file());
        return r;
    }

    MindioPackageReader.ReadPackage read(Path file) {
        return new MindioPackageReader(om, 100_000_000, 1000).read(file, out.resolve("x" + produced.size()));
    }

    @Test
    void packsUploadsRewritesUrlsAndAssignsMissingUid() throws Exception {
        String content = "{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"/api/uploads/file/a.png\"}}},"
                + "{\"type\":\"paragraph\",\"data\":{\"text\":\"<img src=\\\"https://ext.com/x.png\\\">\"}}]}";
        Note n = note(5L, alice, null, content);
        when(noteRepository.findAllById(List.of(5L))).thenReturn(List.of(n));
        when(noteRepository.assignUidIfAbsent(eq(5L), anyString())).thenReturn(1);

        NoteExportService.ExportResult r = run(List.of(5L), false);
        MindioPackageReader.ReadPackage pkg = read(r.file());

        assertThat(r.noteCount()).isEqualTo(1);
        assertThat(r.missingAssetCount()).isZero();
        String hash = NoteAssetRewriter.sha256Hex(uploads.resolve("file/a.png"));
        assertThat(pkg.assets()).containsOnlyKeys(hash + ".png");
        MindioPackage.PackagedNote pn = pkg.notes().get(0);
        assertThat(pn.uid()).matches(MindioPackageReader.UUID_RE);
        verify(noteRepository).assignUidIfAbsent(5L, pn.uid());
        assertThat(n.getUid()).isNull();
        assertThat(pn.content()).contains("asset://" + hash + ".png").contains("https://ext.com/x.png")
                .doesNotContain("/api/uploads/");
        assertThat(pn.sections().get(0).content()).contains("asset://" + hash + ".png");
        assertThat(pn.title()).isEqualTo("n5");
        assertThat(pn.summary()).isEqualTo("s");
        assertThat(pn.createdAt()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(pkg.manifest().noteCount()).isEqualTo(1);
        assertThat(pkg.manifest().exportedFrom()).isEqualTo("web");
    }

    @Test
    void keepsExistingUidAndUsesWinnerWhenRaceLost() {
        Note keep = note(5L, alice, "11111111-1111-1111-1111-111111111111", "{}");
        Note raced = note(6L, alice, null, "{}");
        when(noteRepository.findAllById(List.of(5L, 6L))).thenReturn(List.of(keep, raced));
        when(noteRepository.assignUidIfAbsent(eq(6L), anyString())).thenReturn(0);
        when(noteRepository.findUidById(6L)).thenReturn("22222222-2222-2222-2222-222222222222");

        MindioPackageReader.ReadPackage pkg = read(run(List.of(5L, 6L), false).file());

        verify(noteRepository, never()).assignUidIfAbsent(eq(5L), anyString());
        assertThat(pkg.notes()).extracting(MindioPackage.PackagedNote::uid).containsExactlyInAnyOrder(
                "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
    }

    @Test
    void missingAndTraversalUploadsAreReportedNotPacked() throws Exception {
        // 真实存在的文件，位于上传目录之外（out 与 uploads 是两个兄弟临时目录）。
        // 没有越界保护时，/api/uploads/../<out>/secret.txt 会解析到它并被打包。
        Path secret = out.resolve("secret.txt");
        Files.writeString(secret, "top secret");
        String rel = uploads.relativize(secret).toString().replace('\\', '/');
        assertThat(rel).startsWith("..");
        String traversal = "/api/uploads/" + rel;

        Note n = note(5L, alice, "11111111-1111-1111-1111-111111111111",
                "{\"a\":\"/api/uploads/file/gone.png\",\"b\":\"" + traversal + "\"}");
        n.setSectionTypes(new ArrayList<>());
        n.setSectionContents(new ArrayList<>());
        when(noteRepository.findAllById(List.of(5L))).thenReturn(List.of(n));

        NoteExportService.ExportResult r = run(List.of(5L), false);
        MindioPackageReader.ReadPackage pkg = read(r.file());

        assertThat(r.missingAssetCount()).isEqualTo(2);
        assertThat(pkg.assets()).isEmpty();
        assertThat(pkg.manifest().missingAssets()).hasSize(2);
        assertThat(pkg.manifest().missingAssets()).extracting(MindioPackage.MissingAsset::url)
                .containsExactlyInAnyOrder("/api/uploads/file/gone.png", traversal);
        assertThat(pkg.notes().get(0).content()).contains("/api/uploads/file/gone.png").contains(traversal);
    }

    @Test
    void uidLostAndNotReadableFailsExportInsteadOfWritingNullUid() {
        Note n = note(6L, alice, null, "{}");
        when(noteRepository.findAllById(List.of(6L))).thenReturn(List.of(n));
        when(noteRepository.assignUidIfAbsent(eq(6L), anyString())).thenReturn(0);
        when(noteRepository.findUidById(6L)).thenReturn(null);

        long before = countExportTemps();
        assertThatThrownBy(() -> service.export("alice", List.of(6L), false, "https://mindio.example.com", "web"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("笔记在导出过程中被修改，请重试");
        assertThat(countExportTemps()).isEqualTo(before);
    }

    static long countExportTemps() {
        try (java.util.stream.Stream<Path> s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith("mindio-export-")
                    && p.getFileName().toString().endsWith(".mindio")).count();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void allExportsOnlyOwnersEditorjsNotes() {
        when(noteRepository.findByOwnerAndContentTypeOrderByIdAsc(alice, "editorjs"))
                .thenReturn(List.of(note(5L, alice, "11111111-1111-1111-1111-111111111111", "{}")));

        assertThat(run(null, true).noteCount()).isEqualTo(1);
    }

    @Test
    void selectedNotesSkipNonEditorjsAndRejectOthersNotes() {
        Note md = note(7L, alice, null, "x");
        md.setContentType("markdown");
        when(noteRepository.findAllById(List.of(7L))).thenReturn(List.of(md));
        assertThatThrownBy(() -> run(List.of(7L), false)).isInstanceOf(BadRequestException.class);

        when(noteRepository.findAllById(List.of(8L))).thenReturn(List.of(note(8L, bob, null, "{}")));
        assertThatThrownBy(() -> run(List.of(8L), false)).isInstanceOf(ResourceNotFoundException.class);
        verify(noteRepository, never()).assignUidIfAbsent(anyLong(), anyString());
    }
}
