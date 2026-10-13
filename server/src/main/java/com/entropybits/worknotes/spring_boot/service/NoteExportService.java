/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AudioTranscriptionRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把选中的（或全部）editor.js 笔记导出成 .mindio 包。笔记没有 uid 时在这里分配并写回——导出是写操作，
 * 但走 JPQL 批量更新，不会刷新 modifiedAt。本实例上传的文件打进 assets/，外链原样保留。
 */
@Service
public class NoteExportService {

    public record ExportResult(Path file, int noteCount, int missingAssetCount) {}

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AudioTranscriptionRepository audioTranscriptionRepository;
    private final UploadPathConfig uploadPathConfig;
    private final ObjectMapper objectMapper;
    private final String uploadUrlPrefix;

    public NoteExportService(NoteRepository noteRepository, UserRepository userRepository,
                             AudioTranscriptionRepository audioTranscriptionRepository,
                             UploadPathConfig uploadPathConfig, ObjectMapper objectMapper,
                             @Value("${worknotes.upload.url-prefix:}") String uploadUrlPrefix) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.audioTranscriptionRepository = audioTranscriptionRepository;
        this.uploadPathConfig = uploadPathConfig;
        this.objectMapper = objectMapper;
        this.uploadUrlPrefix = uploadUrlPrefix;
    }

    @Transactional
    public ExportResult export(String username, List<Long> noteIds, boolean all, String requestOrigin,
                               String exportedFrom) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        List<Note> notes = selectNotes(user, noteIds, all);
        if (notes.isEmpty()) throw new BadRequestException("没有可导出的 editor.js 笔记");

        NoteAssetRewriter rewriter = new NoteAssetRewriter(uploadUrlPrefix,
                Path.of(uploadPathConfig.getUploadPath()), List.of(requestOrigin == null ? "" : requestOrigin));
        Path file;
        try {
            file = Files.createTempFile("mindio-export-", ".mindio");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<MindioPackage.MissingAsset> missing = new ArrayList<>();
        Map<Path, String> assetNames = new HashMap<>();
        NoteRefRewriter refs = new NoteRefRewriter(objectMapper, List.of(requestOrigin == null ? "" : requestOrigin));
        Map<Long, String> uids = new HashMap<>();
        try (OutputStream out = Files.newOutputStream(file);
             MindioPackageWriter writer = new MindioPackageWriter(out, objectMapper)) {
            Map<Long, String> idToUid = referencedUids(user, notes, refs, uids);
            for (Note note : notes) {
                String uid = ensureUid(note, uids);
                String content = packText(refs.toPackage(note.getContent(), null, idToUid), uid, rewriter, writer,
                        assetNames, missing);
                List<MindioPackage.Section> sections = new ArrayList<>();
                for (MindioPackage.Section s : MindioPackage.sectionsOf(note)) {
                    sections.add(new MindioPackage.Section(s.type(), packText(refs.toPackage(s.content(), s.type(),
                            idToUid), uid, rewriter, writer, assetNames, missing)));
                }
                writer.writeNote(MindioPackage.fromNote(note, uid, content, sections, transcriptionsOf(note)));
            }
            writer.writeManifest(new MindioPackage.Manifest(MindioPackage.FORMAT, MindioPackage.FORMAT_VERSION,
                    Instant.now().toString(), exportedFrom == null ? "web" : exportedFrom, notes.size(), missing));
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // 临时文件删不掉不影响报错本身
            }
            if (e instanceof RuntimeException re) throw re;
            throw new UncheckedIOException((IOException) e);
        }
        return new ExportResult(file, notes.size(), missing.size());
    }

    private List<Note> selectNotes(User user, List<Long> noteIds, boolean all) {
        if (all) return noteRepository.findByOwnerAndContentTypeOrderByIdAsc(user, "editorjs");
        if (noteIds == null || noteIds.isEmpty()) return List.of();
        List<Note> found = noteRepository.findAllById(noteIds);
        for (Note n : found) {
            if (!n.getOwner().getId().equals(user.getId())) throw new ResourceNotFoundException("笔记不存在");
        }
        return found.stream().filter(n -> "editorjs".equals(n.getContentType())).toList();
    }

    /** 被导出笔记引用的、属于当前用户的笔记：确保有 uid，返回 id → uid。别人的或不存在的笔记不在结果里 */
    private Map<Long, String> referencedUids(User user, List<Note> notes, NoteRefRewriter refs, Map<Long, String> cache) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Note n : notes) {
            ids.addAll(refs.collectNoteIds(n.getContent(), null));
            for (MindioPackage.Section s : MindioPackage.sectionsOf(n)) ids.addAll(refs.collectNoteIds(s.content(), s.type()));
        }
        Map<Long, String> idToUid = new HashMap<>();
        if (ids.isEmpty()) return idToUid;
        for (Note target : noteRepository.findAllById(ids)) {
            if (target.getOwner().getId().equals(user.getId())) idToUid.put(target.getId(), ensureUid(target, cache));
        }
        return idToUid;
    }

    /** 只导出已完成、且对应块还在正文里的转写记录（spec 8.2） */
    private List<MindioPackage.PackagedTranscription> transcriptionsOf(Note note) {
        Set<String> blockIds = MindioPackage.blockIds(objectMapper, note.getContent());
        return audioTranscriptionRepository.findByNoteId(note.getId()).stream()
                .filter(t -> t.getStatus() == AudioTranscription.Status.DONE && blockIds.contains(t.getBlockId()))
                .map(t -> new MindioPackage.PackagedTranscription(t.getBlockId(), t.getStage().name(),
                        t.getUtterances(), t.getPolished(), t.getSummary(),
                        MindioPackage.toIso(t.getCreatedAt()), MindioPackage.toIso(t.getCompletedAt())))
                .toList();
    }

    private String ensureUid(Note note, Map<Long, String> cache) {
        if (note.getUid() != null) return note.getUid();
        String cached = cache.get(note.getId());
        if (cached != null) return cached;
        String candidate = UUID.randomUUID().toString();
        // 不调用 note.setUid：托管实体一旦变脏，提交时会被 flush 并触发 @PreUpdate 刷新 modifiedAt
        // 赢得竞争时返回本次分配的值；输掉时读取胜者的 uid。REPEATABLE READ 下胜者的 uid 可能读不到
        // （快照不可见），或笔记在导出中途被删除——此时无法写出合法条目，整次导出失败而不是静默跳过
        String uid = noteRepository.assignUidIfAbsent(note.getId(), candidate) == 1
                ? candidate : noteRepository.findUidById(note.getId());
        if (uid == null) throw new BadRequestException("笔记在导出过程中被修改，请重试");
        cache.put(note.getId(), uid);
        return uid;
    }

    private String packText(String text, String uid, NoteAssetRewriter rewriter, MindioPackageWriter writer,
                            Map<Path, String> assetNames, List<MindioPackage.MissingAsset> missing) throws IOException {
        Map<String, String> replacements = new HashMap<>();
        for (NoteAssetRewriter.UploadRef ref : rewriter.findUploads(text)) {
            if (replacements.containsKey(ref.url())) continue;
            if (ref.file() == null || !Files.isRegularFile(ref.file())) {
                missing.add(new MindioPackage.MissingAsset(uid, ref.url()));
                replacements.put(ref.url(), ref.url());
                continue;
            }
            String name = assetNames.get(ref.file());
            if (name == null) {
                name = NoteAssetRewriter.assetName(NoteAssetRewriter.sha256Hex(ref.file()), ref.file());
                assetNames.put(ref.file(), name);
            }
            writer.writeAsset(name, ref.file());
            replacements.put(ref.url(), NoteAssetRewriter.ASSET_SCHEME + name);
        }
        return rewriter.replaceUploads(text, replacements);
    }
}
