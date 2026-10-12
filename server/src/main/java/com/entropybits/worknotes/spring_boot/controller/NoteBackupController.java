/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.service.NoteExportService;
import com.entropybits.worknotes.spring_boot.service.NoteImportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * editor.js 笔记的 .mindio 备份导出/导入。详见 docs/superpowers/specs/2026-10-12-editorjs-note-export-import-design.md。
 */
@RestController
@RequestMapping("/v1/notes")
@RequiredArgsConstructor
public class NoteBackupController {

    public record ExportRequest(List<Long> noteIds, Boolean all, String exportedFrom) {}

    public record CommitRequest(String importToken, List<String> overwrite) {}

    private final NoteExportService exportService;
    private final NoteImportService importService;

    @PostMapping("/export")
    public ResponseEntity<InputStreamResource> export(@RequestBody ExportRequest request,
                                                      @AuthenticationPrincipal UserDetails userDetails,
                                                      HttpServletRequest http) throws IOException {
        NoteExportService.ExportResult result = exportService.export(userDetails.getUsername(), request.noteIds(),
                Boolean.TRUE.equals(request.all()), originOf(http), request.exportedFrom());
        long size = Files.size(result.file());
        // 流读完关闭时删除临时文件
        InputStream in = Files.newInputStream(result.file(), StandardOpenOption.DELETE_ON_CLOSE);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mindio-notes.mindio\"")
                .header("X-Mindio-Note-Count", String.valueOf(result.noteCount()))
                .header("X-Mindio-Missing-Assets", String.valueOf(result.missingAssetCount()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .body(new InputStreamResource(in));
    }

    @PostMapping("/import/preview")
    public ResponseEntity<NoteImportService.Preview> preview(@RequestParam("file") MultipartFile file,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(importService.preview(userDetails.getUsername(), file));
    }

    @PostMapping("/import/commit")
    public ResponseEntity<NoteImportService.CommitResult> commit(@RequestBody CommitRequest request,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(importService.commit(userDetails.getUsername(), request.importToken(),
                request.overwrite() == null ? List.of() : request.overwrite()));
    }

    /** 当前请求的 origin（经反向代理时是对外域名），用来识别正文里写成本站绝对地址的上传文件 */
    private static String originOf(HttpServletRequest http) {
        int port = http.getServerPort();
        boolean defaultPort = ("http".equals(http.getScheme()) && port == 80) || ("https".equals(http.getScheme()) && port == 443);
        return http.getScheme() + "://" + http.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
