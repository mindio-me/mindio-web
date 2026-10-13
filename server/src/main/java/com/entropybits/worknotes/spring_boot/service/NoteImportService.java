/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
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
                          List<String> missingAssets, int unresolvedNoteRefs) {}

    private static final int MAX_ENTRIES = 20_000;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NoteRevisionService revisionService;
    private final UploadService uploadService;
    private final ContentIndexingService contentIndexingService;
    private final AudioTranscriptionRepository audioTranscriptionRepository;
    private final NoteImportStagingStore stagingStore;
    private final UploadPathConfig uploadPathConfig;
    private final ObjectMapper objectMapper;
    private final String uploadUrlPrefix;
    private final long maxUnpackedBytes;

    public NoteImportService(NoteRepository noteRepository, UserRepository userRepository,
                             NoteRevisionService revisionService, UploadService uploadService,
                             ContentIndexingService contentIndexingService,
                             AudioTranscriptionRepository audioTranscriptionRepository, NoteImportStagingStore stagingStore,
                             UploadPathConfig uploadPathConfig, ObjectMapper objectMapper,
                             @Value("${worknotes.upload.url-prefix:}") String uploadUrlPrefix,
                             @Value("${worknotes.upload.max-file-size-mb:500}") int maxFileSizeMb) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.revisionService = revisionService;
        this.uploadService = uploadService;
        this.contentIndexingService = contentIndexingService;
        this.audioTranscriptionRepository = audioTranscriptionRepository;
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
                        Path asset = pkg.assets().get(name);
                        if (asset == null || !isAcceptable(name, asset)) missing.add(name);
                    }
                }
            }
            int unresolvedNoteRefs = countUnresolvedRefs(user, pkg, Set.copyOf(uids));
            NoteImportStagingStore.Staged staged = stagingStore.stage(user.getId(), dir, pkg);
            return new Preview(staged.token(), newNotes, conflicts, new ArrayList<>(missing), unresolvedNoteRefs);
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
        Set<String> unusable = new HashSet<>();
        String turnId = "import-" + importToken;

        record Written(Note note, LocalDateTime modifiedAt) {}
        List<Written> written = new ArrayList<>();
        int created = 0, overwritten = 0, kept = 0;
        for (MindioPackage.PackagedNote n : pkg.notes()) {
            Note local = existing.get(n.uid());
            if (local != null && !overwrite.contains(n.uid())) {
                kept++;
                continue;
            }
            Map<String, String> urls = resolveAssets(n, rewriter, pkg, user, assetUrls, unusable, writtenFiles);
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
                audioTranscriptionRepository.saveAll(transcriptionsFor(note.getId(), n));
                written.add(new Written(note, modifiedAt));
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
                audioTranscriptionRepository.deleteByNoteId(local.getId());
                audioTranscriptionRepository.saveAll(transcriptionsFor(local.getId(), n));
                written.add(new Written(local, modifiedAt));
                reindexIds.add(local.getId());
                overwritten++;
            }
        }
        resolveNoteRefs(user, written.stream().map(Written::note).toList());
        for (Written w : written) {
            if (w.modifiedAt() != null) noteRepository.overwriteModifiedAt(w.note().getId(), w.modifiedAt());
        }
        return new CommitResult(created, overwritten, kept);
    }

    /** 包里引用到、但包里和本机都没有的笔记数（spec 8.1），只作提示 */
    private int countUnresolvedRefs(User user, MindioPackageReader.ReadPackage pkg, Set<String> packageUids) {
        NoteRefRewriter refs = new NoteRefRewriter(objectMapper, List.of());
        Set<String> referenced = new LinkedHashSet<>();
        for (MindioPackage.PackagedNote n : pkg.notes()) {
            referenced.addAll(refs.collectNoteUids(n.content(), null));
            if (n.sections() != null) {
                for (MindioPackage.Section s : n.sections()) referenced.addAll(refs.collectNoteUids(s.content(), s.type()));
            }
        }
        referenced.removeAll(packageUids);
        if (referenced.isEmpty()) return 0;
        Set<String> local = noteRepository.findByOwnerAndUidIn(user, referenced).stream()
                .map(Note::getUid).collect(Collectors.toSet());
        return (int) referenced.stream().filter(u -> !local.contains(u)).count();
    }

    /**
     * 所有笔记写完后统一解析包里的 uid 引用（spec 8.1）：同一个包里互相引用的笔记这时都已有本机 id。
     * 改写会再保存一次，@PreUpdate 会刷新 modifiedAt，所以调用方在这之后统一用包里的值覆盖。
     */
    private void resolveNoteRefs(User user, List<Note> notes) {
        NoteRefRewriter refs = new NoteRefRewriter(objectMapper, List.of());
        Set<String> wanted = new LinkedHashSet<>();
        for (Note n : notes) {
            wanted.addAll(refs.collectNoteUids(n.getContent(), null));
            List<String> types = n.getSectionTypes();
            List<String> contents = n.getSectionContents();
            for (int i = 0; i < Math.min(types.size(), contents.size()); i++) {
                wanted.addAll(refs.collectNoteUids(contents.get(i), types.get(i)));
            }
        }
        if (wanted.isEmpty()) return;
        Map<String, Long> uidToId = noteRepository.findByOwnerAndUidIn(user, wanted).stream()
                .collect(Collectors.toMap(Note::getUid, Note::getId, (x, y) -> x));
        if (uidToId.isEmpty()) return;
        for (Note n : notes) {
            boolean changed = false;
            String content = refs.fromPackage(n.getContent(), null, uidToId);
            if (!java.util.Objects.equals(content, n.getContent())) {
                n.setContent(content);
                changed = true;
            }
            List<String> types = n.getSectionTypes();
            List<String> contents = new ArrayList<>(n.getSectionContents());
            boolean sectionsChanged = false;
            for (int i = 0; i < Math.min(types.size(), contents.size()); i++) {
                String s = refs.fromPackage(contents.get(i), types.get(i), uidToId);
                if (!java.util.Objects.equals(s, contents.get(i))) {
                    contents.set(i, s);
                    sectionsChanged = true;
                }
            }
            if (sectionsChanged) {
                n.setSectionContents(contents);
                changed = true;
            }
            if (changed) noteRepository.saveAndFlush(n);
        }
    }

    private List<AudioTranscription> transcriptionsFor(Long noteId, MindioPackage.PackagedNote n) {
        LocalDateTime now = LocalDateTime.now();
        List<AudioTranscription> rows = new ArrayList<>();
        for (MindioPackage.PackagedTranscription t : n.transcriptions()) {
            LocalDateTime createdAt = MindioPackage.fromIso(t.createdAt());
            LocalDateTime completedAt = MindioPackage.fromIso(t.completedAt());
            rows.add(AudioTranscription.builder()
                    .noteId(noteId).blockId(t.blockId())
                    .status(AudioTranscription.Status.DONE)
                    .triggerType(AudioTranscription.TriggerType.MANUAL)
                    .stage(AudioTranscription.Stage.valueOf(t.stage()))
                    .attempts(0)
                    .utterances(t.utterances()).polished(t.polished()).summary(t.summary())
                    .createdAt(createdAt != null ? createdAt : now)
                    .completedAt(completedAt != null ? completedAt : now)
                    .build());
        }
        return rows;
    }

    /** 每个资源在一次导入里只落盘一次；图片优先复用本机已有的同哈希文件 */
    private Map<String, String> resolveAssets(MindioPackage.PackagedNote n, NoteAssetRewriter rewriter,
                                              MindioPackageReader.ReadPackage pkg, User user,
                                              Map<String, String> assetUrls, Set<String> unusable,
                                              List<Path> writtenFiles) {
        for (String text : textsOf(n)) {
            for (String name : rewriter.findAssetNames(text)) {
                if (assetUrls.containsKey(name) || unusable.contains(name) || !pkg.assets().containsKey(name)) continue;
                Path asset = pkg.assets().get(name);
                // 单个资源不合格（类型/大小不被接受，或内容与文件名里的哈希对不上）只让它自己缺失，占位符留在正文里，其余照常导入
                if (!isAcceptable(name, asset) || !hashMatches(name, asset)) {
                    unusable.add(name);
                    continue;
                }
                String hash = name.substring(0, 64);
                String ext = name.length() > 65 ? name.substring(65).toLowerCase(Locale.ROOT) : "";
                boolean image = IMAGE_EXTS.contains(ext);
                String url = image ? uploadService.findOwnedImageUrlByContentHash(user.getId(), hash).orElse(null) : null;
                if (url == null) {
                    PathMultipartFile file = new PathMultipartFile(asset, name);
                    FileResultVo vo;
                    try {
                        vo = image
                                ? uploadService.imageUpload(file, "note", 0, user.getId())
                                : uploadService.fileUpload(file, "note", 0, user.getId());
                    } catch (BadRequestException e) {
                        // 兜底：预判漏掉的拒绝原因同样只影响这一个资源（UploadServiceImpl 无事务注解，不会把外层事务标成仅回滚）
                        unusable.add(name);
                        continue;
                    }
                    url = vo.getUrl();
                    rewriter.findUploads(url).stream().map(NoteAssetRewriter.UploadRef::file)
                            .filter(java.util.Objects::nonNull).forEach(writtenFiles::add);
                }
                assetUrls.put(name, url);
            }
        }
        return assetUrls;
    }

    private boolean isAcceptable(String name, Path asset) {
        try {
            return uploadService.isAcceptableUpload(name, Files.size(asset));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hashMatches(String name, Path asset) {
        try {
            return NoteAssetRewriter.sha256Hex(asset).equals(name.substring(0, 64));
        } catch (RuntimeException e) {
            return false;
        }
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
