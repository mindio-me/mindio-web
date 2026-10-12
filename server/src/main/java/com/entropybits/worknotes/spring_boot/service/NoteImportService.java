/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public record CommitResult(int created, int overwritten, int kept) {}

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic");
    private static final int MAX_TITLE = 200;

    @Transactional
    public CommitResult commit(String username, String importToken, Collection<String> overwriteUids) {
        User user = loadUser(username);
        NoteImportStagingStore.Staged staged = stagingStore.take(importToken, user.getId());
        List<Path> writtenFiles = new ArrayList<>();
        List<Long> reindexIds = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // reindexNote 是 @Async，必须等事务提交后再触发，否则别的线程读不到新内容
                reindexIds.forEach(contentIndexingService::reindexNote);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    writtenFiles.forEach(f -> {
                        try {
                            Files.deleteIfExists(f);
                        } catch (IOException ignored) {
                            // 残留的孤儿文件不影响数据一致性
                        }
                    });
                }
                stagingStore.release(staged);
            }
        });

        Set<String> overwrite = overwriteUids == null ? Set.of() : new HashSet<>(overwriteUids);
        MindioPackageReader.ReadPackage pkg = staged.pkg();
        List<String> uids = pkg.notes().stream().map(MindioPackage.PackagedNote::uid).toList();
        Map<String, Note> existing = uids.isEmpty() ? Map.of()
                : noteRepository.findByOwnerAndUidIn(user, uids).stream()
                .collect(Collectors.toMap(Note::getUid, Function.identity()));
        NoteAssetRewriter rewriter = rewriter();
        Map<String, String> assetUrls = new HashMap<>();
        String turnId = "import-" + importToken;

        int created = 0, overwritten = 0, kept = 0;
        for (MindioPackage.PackagedNote n : pkg.notes()) {
            Note local = existing.get(n.uid());
            if (local != null && !overwrite.contains(n.uid())) {
                kept++;
                continue;
            }
            Map<String, String> urls = resolveAssets(n, rewriter, pkg, user, assetUrls, writtenFiles);
            String content = rewriter.restoreAssets(n.content(), urls);
            List<String> sectionTypes = new ArrayList<>();
            List<String> sectionContents = new ArrayList<>();
            if (n.sections() != null) {
                for (MindioPackage.Section s : n.sections()) {
                    sectionTypes.add(s.type());
                    sectionContents.add(rewriter.restoreAssets(s.content(), urls));
                }
            }
            LocalDateTime modifiedAt = MindioPackage.fromIso(n.modifiedAt());
            if (local == null) {
                Note note = Note.builder().uid(n.uid()).owner(user).title(normalizeTitle(n.title()))
                        .summary(n.summary()).contentType("editorjs").content(content).isPublic(false)
                        .createdAt(MindioPackage.fromIso(n.createdAt())).modifiedAt(modifiedAt)
                        .sectionTypes(sectionTypes).sectionContents(sectionContents).build();
                noteRepository.saveAndFlush(note);
                reindexIds.add(note.getId());
                created++;
            } else {
                revisionService.snapshotForImport(local, turnId, toJson(MindioPackage.fromNote(
                        local, local.getUid(), local.getContent(), MindioPackage.sectionsOf(local))));
                local.setTitle(normalizeTitle(n.title()));
                local.setSummary(n.summary());
                local.setContent(content);
                local.setContentType("editorjs");
                local.setSectionTypes(sectionTypes);
                local.setSectionContents(sectionContents);
                noteRepository.saveAndFlush(local);
                if (modifiedAt != null) noteRepository.overwriteModifiedAt(local.getId(), modifiedAt);
                reindexIds.add(local.getId());
                overwritten++;
            }
        }
        return new CommitResult(created, overwritten, kept);
    }

    /** 每个资源在一次导入里只落盘一次；图片优先复用本机已有的同哈希文件 */
    private Map<String, String> resolveAssets(MindioPackage.PackagedNote n, NoteAssetRewriter rewriter,
                                              MindioPackageReader.ReadPackage pkg, User user,
                                              Map<String, String> assetUrls, List<Path> writtenFiles) {
        for (String text : textsOf(n)) {
            for (String name : rewriter.findAssetNames(text)) {
                if (assetUrls.containsKey(name) || !pkg.assets().containsKey(name)) continue;
                String hash = name.substring(0, 64);
                String ext = name.length() > 65 ? name.substring(65).toLowerCase(Locale.ROOT) : "";
                boolean image = IMAGE_EXTS.contains(ext);
                String url = image ? uploadService.findOwnedImageUrlByContentHash(user.getId(), hash).orElse(null) : null;
                if (url == null) {
                    PathMultipartFile file = new PathMultipartFile(pkg.assets().get(name), name);
                    FileResultVo vo = image
                            ? uploadService.imageUpload(file, "note", 0, user.getId())
                            : uploadService.fileUpload(file, "note", 0, user.getId());
                    url = vo.getUrl();
                    rewriter.findUploads(url).stream().map(NoteAssetRewriter.UploadRef::file)
                            .filter(java.util.Objects::nonNull).forEach(writtenFiles::add);
                }
                assetUrls.put(name, url);
            }
        }
        return assetUrls;
    }

    private static String normalizeTitle(String title) {
        String t = title == null || title.isBlank() ? "未命名笔记" : title.trim();
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
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
