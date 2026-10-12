/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * .mindio 包导入：preview 解析并暂存，不写库；commit 按用户逐篇的选择新建/覆盖/保留。
 * 同一篇笔记按"当前用户 + uid"识别——多用户服务器上别人名下的同 uid 笔记与本次导入无关。
 */
@Service
public class NoteImportService {

    public record NewItem(String uid, String title) {}

    public record ConflictItem(String uid, String localTitle, String localModifiedAt,
                               String packageTitle, String packageModifiedAt) {}

    public record Preview(String importToken, List<NewItem> newNotes, List<ConflictItem> conflicts,
                          List<String> missingAssets) {}

    private static final int MAX_ENTRIES = 20_000;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NoteRevisionService revisionService;
    private final UploadService uploadService;
    private final ContentIndexingService contentIndexingService;
    private final NoteImportStagingStore stagingStore;
    private final UploadPathConfig uploadPathConfig;
    private final ObjectMapper objectMapper;
    private final String uploadUrlPrefix;
    private final long maxUnpackedBytes;

    public NoteImportService(NoteRepository noteRepository, UserRepository userRepository,
                             NoteRevisionService revisionService, UploadService uploadService,
                             ContentIndexingService contentIndexingService, NoteImportStagingStore stagingStore,
                             UploadPathConfig uploadPathConfig, ObjectMapper objectMapper,
                             @Value("${worknotes.upload.url-prefix:}") String uploadUrlPrefix,
                             @Value("${worknotes.upload.max-file-size-mb:500}") int maxFileSizeMb) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.revisionService = revisionService;
        this.uploadService = uploadService;
        this.contentIndexingService = contentIndexingService;
        this.stagingStore = stagingStore;
        this.uploadPathConfig = uploadPathConfig;
        this.objectMapper = objectMapper;
        this.uploadUrlPrefix = uploadUrlPrefix;
        // 包本身受上传上限约束；图片等已压缩内容解压后不会膨胀太多，4 倍足够正常包，又能挡住 zip 炸弹
        this.maxUnpackedBytes = 4L * maxFileSizeMb * 1024 * 1024;
    }

    public Preview preview(String username, MultipartFile file) {
        User user = loadUser(username);
        Path dir;
        try {
            dir = Files.createTempDirectory("mindio-import-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Path zip = dir.resolve("package.mindio");
            file.transferTo(zip);
            MindioPackageReader.ReadPackage pkg =
                    new MindioPackageReader(objectMapper, maxUnpackedBytes, MAX_ENTRIES).read(zip, dir.resolve("extract"));
            Files.deleteIfExists(zip);

            List<String> uids = pkg.notes().stream().map(MindioPackage.PackagedNote::uid).toList();
            Map<String, Note> existing = uids.isEmpty() ? Map.of()
                    : noteRepository.findByOwnerAndUidIn(user, uids).stream()
                    .collect(Collectors.toMap(Note::getUid, Function.identity()));

            List<NewItem> newNotes = new ArrayList<>();
            List<ConflictItem> conflicts = new ArrayList<>();
            Set<String> missing = new LinkedHashSet<>();
            NoteAssetRewriter rewriter = rewriter();
            for (MindioPackage.PackagedNote n : pkg.notes()) {
                Note local = existing.get(n.uid());
                if (local == null) {
                    newNotes.add(new NewItem(n.uid(), n.title()));
                } else {
                    conflicts.add(new ConflictItem(n.uid(), local.getTitle(), MindioPackage.toIso(local.getModifiedAt()),
                            n.title(), n.modifiedAt()));
                }
                for (String text : textsOf(n)) {
                    for (String name : rewriter.findAssetNames(text)) {
                        if (!pkg.assets().containsKey(name)) missing.add(name);
                    }
                }
            }
            NoteImportStagingStore.Staged staged = stagingStore.stage(user.getId(), dir, pkg);
            return new Preview(staged.token(), newNotes, conflicts, new ArrayList<>(missing));
        } catch (IOException e) {
            deleteQuietly(dir);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }

    private NoteAssetRewriter rewriter() {
        return new NoteAssetRewriter(uploadUrlPrefix, Path.of(uploadPathConfig.getUploadPath()), List.of());
    }

    static List<String> textsOf(MindioPackage.PackagedNote n) {
        List<String> texts = new ArrayList<>();
        texts.add(n.content());
        if (n.sections() != null) n.sections().forEach(s -> texts.add(s.content()));
        return texts;
    }

    private static void deleteQuietly(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ignored) {
            // 临时目录残留不影响功能
        }
    }
}
