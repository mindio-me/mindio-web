/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** 把内存里的字节交给 UploadService.imageUpload（它只收 MultipartFile），走同一套落盘/附件记录逻辑 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] bytes;

    public ByteArrayMultipartFile(String filename, String contentType, byte[] bytes) {
        this.filename = filename;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
}
