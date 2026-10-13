/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteImportCommitTest {

    @Mock NoteRepository noteRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRevisionService revisionService;
    @Mock UploadService uploadService;
    @Mock ContentIndexingService contentIndexingService;
    @Mock UploadPathConfig uploadPathConfig;
    @Mock com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository audioTranscriptionRepository;
    @TempDir Path uploads;
    @TempDir Path src;

    final ObjectMapper om = new ObjectMapper();
    final NoteImportStagingStore store = new NoteImportStagingStore(Clock.systemUTC());
    NoteImportService service;
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();
    static final String UID1 = NoteImportPreviewTest.UID1;
    static final String UID2 = NoteImportPreviewTest.UID2;
    String hash;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(uploadPathConfig.getUploadPath()).thenReturn(uploads.toString());
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        lenient().when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        lenient().when(noteRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            if (n.getId() == null) n.setId(100L);
            return n;
        });
        lenient().when(uploadService.isAcceptableUpload(anyString(), anyLong()))
                .thenAnswer(inv -> !((String) inv.getArgument(0)).endsWith(".bin"));
        service = new NoteImportService(noteRepository, userRepository, revisionService, uploadService,
                contentIndexingService, audioTranscriptionRepository, store, uploadPathConfig, om, "", 500);
        TransactionSynchronizationManager.initSynchronization();
        Files.writeString(src.resolve("a.png"), "img-a");
        hash = NoteAssetRewriter.sha256Hex(src.resolve("a.png"));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    String preview(String user, MindioPackage.PackagedNote... notes) throws Exception {
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of());
        // 预检测试里的打包工具把资源固定命名成 "c"*64；这里自己写包，资源名用真实哈希
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (MindioPackageWriter w = new MindioPackageWriter(bytes, om)) {
            for (MindioPackage.PackagedNote n : notes) w.writeNote(n);
            w.writeAsset(hash + ".png", src.resolve("a.png"));
            w.writeManifest(new MindioPackage.Manifest(MindioPackage.FORMAT, 1, null, "web", notes.length, List.of()));
        }
        return service.preview(user, new org.springframework.mock.web.MockMultipartFile(
                "file", "b.mindio", "application/octet-stream", bytes.toByteArray())).importToken();
    }

    MindioPackage.PackagedNote pn(String uid, String title) {
        return new MindioPackage.PackagedNote(uid, title, "新摘要", "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z",
                "editorjs", "{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"asset://" + hash + ".png\"}}}]}",
                List.of(new MindioPackage.Section("image", "{\"file\":{\"url\":\"asset://" + hash + ".png\"}}")));
    }

    FileResultVo uploaded(String url) {
        FileResultVo vo = new FileResultVo();
        vo.setUrl(url);
        return vo;
    }

    @Test
    void createsNewNotesWithPackageIdentityAndUploadsAssetsOnce() throws Exception {
        String token = preview("alice", pn(UID1, "一"), pn(UID2, "二"));
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of());
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.empty());
        when(uploadService.imageUpload(any(), eq("note"), eq(0), eq(1L))).thenReturn(uploaded("/api/uploads/file/new.png"));

        NoteImportService.CommitResult r = service.commit("alice", token, Set.of());

        assertThat(r).isEqualTo(new NoteImportService.CommitResult(2, 0, 0));
        verify(uploadService, times(1)).imageUpload(any(), eq("note"), eq(0), eq(1L));
        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository, times(2)).saveAndFlush(saved.capture());
        Note n = saved.getAllValues().get(0);
        assertThat(n.getUid()).isEqualTo(UID1);
        assertThat(n.getOwner()).isSameAs(alice);
        assertThat(n.getIsPublic()).isFalse();
        assertThat(n.getContentType()).isEqualTo("editorjs");
        assertThat(n.getContent()).contains("/api/uploads/file/new.png").doesNotContain("asset://");
        assertThat(n.getSectionContents()).containsExactly("{\"file\":{\"url\":\"/api/uploads/file/new.png\"}}");
        assertThat(n.getSectionTypes()).containsExactly("image");
        assertThat(n.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(n.getModifiedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
        assertThat(n.getTags()).isEmpty();
        assertThat(n.getProject()).isNull();
        verify(contentIndexingService, never()).reindexNote(any());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(contentIndexingService, times(2)).reindexNote(100L);
    }

    @Test
    void overwriteOfNoLongerEditorjsNoteSwitchesContentTypeAfterSnapshot() throws Exception {
        String token = preview("alice", pn(UID1, "包里标题"));
        Note local = Note.builder().id(9L).owner(alice).uid(UID1).title("本机").contentType("markdown")
                .content("# md").sectionTypes(new ArrayList<>()).sectionContents(new ArrayList<>()).build();
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of(local));
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.of("/api/uploads/file/e.png"));
        List<String> typeAtSnapshot = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> typeAtSnapshot.add(((Note) inv.getArgument(0)).getContentType()))
                .when(revisionService).snapshotForImport(any(), anyString(), anyString());

        service.commit("alice", token, Set.of(UID1));

        assertThat(typeAtSnapshot).containsExactly("markdown");
        assertThat(local.getContentType()).isEqualTo("editorjs");
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        verify(contentIndexingService).reindexNote(9L);
    }

    @Test
    void overwriteChangesOnlyContentFieldsAfterSnapshot() throws Exception {
        String token = preview("alice", pn(UID1, "包里标题"));
        Tag tag = new Tag();
        Project project = new Project();
        Note local = Note.builder().id(9L).owner(alice).uid(UID1).title("本机标题").summary("旧摘要")
                .contentType("editorjs").content("{\"old\":1}").language("zh").isPublic(true)
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0)).modifiedAt(LocalDateTime.of(2026, 2, 1, 0, 0))
                .tags(new HashSet<>(Set.of(tag))).project(project)
                .sectionTypes(new ArrayList<>()).sectionContents(new ArrayList<>()).build();
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of(local));
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.of("/api/uploads/file/existing.png"));
        List<String> titleAtSnapshot = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> titleAtSnapshot.add(((Note) inv.getArgument(0)).getTitle()))
                .when(revisionService).snapshotForImport(any(), anyString(), anyString());

        NoteImportService.CommitResult r = service.commit("alice", token, Set.of(UID1));

        assertThat(r).isEqualTo(new NoteImportService.CommitResult(0, 1, 0));
        assertThat(titleAtSnapshot).containsExactly("本机标题");
        verify(revisionService).snapshotForImport(eq(local), eq("import-" + token), anyString());
        assertThat(local.getTitle()).isEqualTo("包里标题");
        assertThat(local.getSummary()).isEqualTo("新摘要");
        assertThat(local.getContent()).contains("/api/uploads/file/existing.png");
        assertThat(local.getSectionTypes()).containsExactly("image");
        assertThat(local.getId()).isEqualTo(9L);
        assertThat(local.getLanguage()).isEqualTo("zh");
        assertThat(local.getIsPublic()).isTrue();
        assertThat(local.getTags()).containsExactly(tag);
        assertThat(local.getProject()).isSameAs(project);
        assertThat(local.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
        verify(noteRepository).overwriteModifiedAt(9L, LocalDateTime.of(2026, 3, 1, 0, 0));
        verify(uploadService, never()).imageUpload(any(), anyString(), anyInt(), any());
    }

    @Test
    void keepLeavesConflictingNoteUntouched() throws Exception {
        String token = preview("alice", pn(UID1, "包里标题"));
        Note local = Note.builder().id(9L).owner(alice).uid(UID1).title("本机标题").contentType("editorjs").build();
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of(local));

        NoteImportService.CommitResult r = service.commit("alice", token, Set.of());

        assertThat(r).isEqualTo(new NoteImportService.CommitResult(0, 0, 1));
        assertThat(local.getTitle()).isEqualTo("本机标题");
        verify(noteRepository, never()).saveAndFlush(any());
        verify(revisionService, never()).snapshotForImport(any(), anyString(), anyString());
        verify(uploadService, never()).imageUpload(any(), anyString(), anyInt(), any());
    }

    @Test
    void otherUsersTokenIsRejectedWithoutConsumingIt() throws Exception {
        String token = preview("alice", pn(UID1, "一"));

        assertThatThrownBy(() -> service.commit("bob", token, Set.of())).isInstanceOf(BadRequestException.class);

        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.of("/api/uploads/file/e.png"));
        assertThat(service.commit("alice", token, Set.of()).created()).isEqualTo(1);
    }

    @Test
    void rollbackDeletesFilesWrittenByThisImport() throws Exception {
        String token = preview("alice", pn(UID1, "一"));
        Files.createDirectories(uploads.resolve("file"));
        Path written = Files.writeString(uploads.resolve("file/new.png"), "img-a");
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.empty());
        when(uploadService.imageUpload(any(), eq("note"), eq(0), eq(1L))).thenReturn(uploaded("/api/uploads/file/new.png"));

        service.commit("alice", token, Set.of());
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertThat(written).doesNotExist();
        verify(contentIndexingService, never()).reindexNote(any());
    }

    String previewWithAssets(java.util.Map<String, Path> assets, String content) throws Exception {
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (MindioPackageWriter w = new MindioPackageWriter(bytes, om)) {
            w.writeNote(new MindioPackage.PackagedNote(UID1, "一", "s", "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z",
                    "editorjs", content, List.of()));
            for (var e : assets.entrySet()) w.writeAsset(e.getKey(), e.getValue());
            w.writeManifest(new MindioPackage.Manifest(MindioPackage.FORMAT, 1, null, "web", 1, List.of()));
        }
        return service.preview("alice", new org.springframework.mock.web.MockMultipartFile(
                "file", "b.mindio", "application/octet-stream", bytes.toByteArray())).importToken();
    }

    @Test
    void unacceptableAssetStaysAsPlaceholderWhileTheRestOfTheImportProceeds() throws Exception {
        Path bin = Files.writeString(src.resolve("b.bin"), "blob");
        String binName = NoteAssetRewriter.sha256Hex(bin) + ".bin";
        String content = "{\"a\":\"asset://" + hash + ".png\",\"b\":\"asset://" + binName + "\"}";
        String token = previewWithAssets(new java.util.LinkedHashMap<>(java.util.Map.of(
                hash + ".png", src.resolve("a.png"), binName, bin)), content);
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.empty());
        when(uploadService.imageUpload(any(), eq("note"), eq(0), eq(1L))).thenReturn(uploaded("/api/uploads/file/new.png"));

        NoteImportService.CommitResult r = service.commit("alice", token, Set.of());

        assertThat(r.created()).isEqualTo(1);
        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).contains("/api/uploads/file/new.png").contains("asset://" + binName);
        verify(uploadService, times(1)).imageUpload(any(), eq("note"), eq(0), eq(1L));
        verify(uploadService, never()).fileUpload(any(), anyString(), anyInt(), any());
    }

    @Test
    void uploadRejectionOfSingleAssetIsSwallowedAndLeavesPlaceholder() throws Exception {
        String token = preview("alice", pn(UID1, "一"));
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.empty());
        when(uploadService.imageUpload(any(), eq("note"), eq(0), eq(1L))).thenThrow(new BadRequestException("nope"));

        assertThat(service.commit("alice", token, Set.of()).created()).isEqualTo(1);
        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).contains("asset://" + hash + ".png");
    }

    @Test
    void assetWhoseBytesDoNotMatchItsNameIsTreatedAsMissing() throws Exception {
        String wrongName = "e".repeat(64) + ".png";
        String token = previewWithAssets(java.util.Map.of(wrongName, src.resolve("a.png")),
                "{\"a\":\"asset://" + wrongName + "\"}");

        assertThat(service.commit("alice", token, Set.of()).created()).isEqualTo(1);

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).contains("asset://" + wrongName);
        verify(uploadService, never()).findOwnedImageUrlByContentHash(any(), anyString());
        verify(uploadService, never()).imageUpload(any(), anyString(), anyInt(), any());
    }

    @Test
    void exportThenImportAsAnotherUserRoundTrips() throws Exception {
        Files.createDirectories(uploads.resolve("file"));
        Files.writeString(uploads.resolve("file/a.png"), "img-a");
        Note original = Note.builder().id(5L).owner(alice).uid(UID1).title("往返").summary("s").contentType("editorjs")
                .content("{\"blocks\":[{\"type\":\"image\",\"data\":{\"file\":{\"url\":\"/api/uploads/file/a.png\"}}}]}")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).modifiedAt(LocalDateTime.of(2026, 3, 1, 0, 0))
                .sectionTypes(new ArrayList<>(List.of("image")))
                .sectionContents(new ArrayList<>(List.of("{\"file\":{\"url\":\"/api/uploads/file/a.png\"}}"))).build();
        when(noteRepository.findAllById(List.of(5L))).thenReturn(List.of(original));
        NoteExportService exporter = new NoteExportService(noteRepository, userRepository, audioTranscriptionRepository,
                uploadPathConfig, om, "");
        Path pkgFile = exporter.export("alice", List.of(5L), false, "", "web").file();

        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of());
        String token = service.preview("bob", new org.springframework.mock.web.MockMultipartFile(
                "file", "b.mindio", "application/octet-stream", Files.readAllBytes(pkgFile))).importToken();
        Files.deleteIfExists(pkgFile);
        when(uploadService.findOwnedImageUrlByContentHash(2L, hash)).thenReturn(Optional.of("/api/uploads/file/a.png"));

        service.commit("bob", token, Set.of());

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUid()).isEqualTo(UID1);
        assertThat(saved.getValue().getOwner()).isSameAs(bob);
        assertThat(saved.getValue().getContent()).isEqualTo(original.getContent());
        assertThat(saved.getValue().getSectionContents()).isEqualTo(original.getSectionContents());
    }

    @Test
    void resolvesRefsBetweenNotesImportedTogetherAndKeepsPackageModifiedAt() throws Exception {
        String a = "{\"blocks\":[{\"id\":\"r\",\"type\":\"references\",\"data\":{\"items\":["
                + "{\"kind\":\"note\",\"title\":\"B\",\"noteUid\":\"" + UID2 + "\"}]}},"
                + "{\"id\":\"p\",\"type\":\"paragraph\",\"data\":{\"text\":\"/workspace/notes/uid/" + UID2
                + " /workspace/notes/uid/99999999-9999-9999-9999-999999999999\"}}]}";
        MindioPackage.PackagedNote na = new MindioPackage.PackagedNote(UID1, "A", null, "2026-01-01T00:00:00Z",
                "2026-03-01T00:00:00Z", "editorjs", a, List.of());
        MindioPackage.PackagedNote nb = new MindioPackage.PackagedNote(UID2, "B", null, "2026-01-01T00:00:00Z",
                "2026-03-02T00:00:00Z", "editorjs", "{}", List.of());
        String token = preview("alice", na, nb);
        List<Note> saved = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            Note n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UID1.equals(n.getUid()) ? 101L : 102L);
            saved.add(n);
            return n;
        }).when(noteRepository).saveAndFlush(any());
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<String> asked = inv.getArgument(1);
            return saved.stream().filter(n -> asked.contains(n.getUid())).distinct().toList();
        });

        service.commit("alice", token, Set.of());

        Note savedA = saved.stream().filter(n -> n.getId() == 101L).reduce((x, y) -> y).orElseThrow();
        assertThat(savedA.getContent()).contains("\"noteId\":102").doesNotContain("\"noteUid\"")
                .contains("/workspace/notes/102 ")
                .contains("/workspace/notes/uid/99999999-9999-9999-9999-999999999999");
        verify(noteRepository).overwriteModifiedAt(101L, LocalDateTime.of(2026, 3, 1, 0, 0));
        verify(noteRepository).overwriteModifiedAt(102L, LocalDateTime.of(2026, 3, 2, 0, 0));
        // 第二轮保存（解析引用）发生在 overwriteModifiedAt 之前
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(noteRepository);
        order.verify(noteRepository, org.mockito.Mockito.times(3)).saveAndFlush(any());
        order.verify(noteRepository).overwriteModifiedAt(eq(101L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTranscriptionsForNewNotesAndReplacesThemOnOverwrite() throws Exception {
        MindioPackage.PackagedTranscription t = new MindioPackage.PackagedTranscription("rec1", "FULL", "[u]", "p",
                "s", "2026-01-02T03:04:05Z", null);
        MindioPackage.PackagedNote n = pn(UID1, "一");
        MindioPackage.PackagedNote withT = new MindioPackage.PackagedNote(n.uid(), n.title(), n.summary(),
                n.createdAt(), n.modifiedAt(), n.contentType(), n.content(), n.sections(), List.of(t));
        when(uploadService.findOwnedImageUrlByContentHash(1L, hash)).thenReturn(Optional.of("/api/uploads/file/e.png"));

        // 新建
        service.commit("alice", preview("alice", withT), Set.of());
        ArgumentCaptor<Iterable<AudioTranscription>> rows = ArgumentCaptor.forClass(Iterable.class);
        verify(audioTranscriptionRepository).saveAll(rows.capture());
        AudioTranscription row = rows.getValue().iterator().next();
        assertThat(row.getNoteId()).isEqualTo(100L);
        assertThat(row.getBlockId()).isEqualTo("rec1");
        assertThat(row.getStatus()).isEqualTo(AudioTranscription.Status.DONE);
        assertThat(row.getTriggerType()).isEqualTo(AudioTranscription.TriggerType.MANUAL);
        assertThat(row.getStage()).isEqualTo(AudioTranscription.Stage.FULL);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertThat(row.getCompletedAt()).isNotNull();
        verify(audioTranscriptionRepository, never()).deleteByNoteId(any());

        // 覆盖：先删本机的再写入
        Note local = Note.builder().id(9L).owner(alice).uid(UID1).title("本机").contentType("editorjs").content("{}")
                .sectionTypes(new ArrayList<>()).sectionContents(new ArrayList<>()).build();
        String token = preview("alice", withT);
        when(noteRepository.findByOwnerAndUidIn(any(), anyCollection())).thenReturn(List.of(local));
        service.commit("alice", token, Set.of(UID1));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(audioTranscriptionRepository);
        order.verify(audioTranscriptionRepository).deleteByNoteId(9L);
        order.verify(audioTranscriptionRepository).flush();
        order.verify(audioTranscriptionRepository).saveAll(any());
    }
}
