/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 把磁盘上的文件当作上传文件交给 UploadService，不把大文件（视频等）整个读进内存 */
public class PathMultipartFile implements MultipartFile {

    private final Path file;
    private final String originalFilename;

    public PathMultipartFile(Path file, String originalFilename) {
        this.file = file;
        this.originalFilename = originalFilename;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return originalFilename; }
    @Override public String getContentType() { return null; }
    @Override public boolean isEmpty() { return getSize() == 0; }

    @Override
    public long getSize() {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(file); }
    @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(file); }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.copy(file, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
