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
import java.util.List;
import java.util.Map;
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
    private final UploadPathConfig uploadPathConfig;
    private final ObjectMapper objectMapper;
    private final String uploadUrlPrefix;

    public NoteExportService(NoteRepository noteRepository, UserRepository userRepository,
                             UploadPathConfig uploadPathConfig, ObjectMapper objectMapper,
                             @Value("${worknotes.upload.url-prefix:}") String uploadUrlPrefix) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
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
        try (OutputStream out = Files.newOutputStream(file);
             MindioPackageWriter writer = new MindioPackageWriter(out, objectMapper)) {
            for (Note note : notes) {
                String uid = ensureUid(note);
                String content = packText(note.getContent(), uid, rewriter, writer, assetNames, missing);
                List<MindioPackage.Section> sections = new ArrayList<>();
                for (MindioPackage.Section s : MindioPackage.sectionsOf(note)) {
                    sections.add(new MindioPackage.Section(s.type(),
                            packText(s.content(), uid, rewriter, writer, assetNames, missing)));
                }
                writer.writeNote(MindioPackage.fromNote(note, uid, content, sections));
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

    private String ensureUid(Note note) {
        if (note.getUid() != null) return note.getUid();
        String candidate = UUID.randomUUID().toString();
        // 不调用 note.setUid：托管实体一旦变脏，提交时会被 flush 并触发 @PreUpdate 刷新 modifiedAt
        return noteRepository.assignUidIfAbsent(note.getId(), candidate) == 1
                ? candidate : noteRepository.findUidById(note.getId());
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
