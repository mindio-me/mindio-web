/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 顺序写出 .mindio 包；同名资源只写一次（资源按内容哈希命名，同一张图被多篇引用也只存一份） */
public class MindioPackageWriter implements Closeable {

    private final ZipOutputStream zip;
    private final ObjectMapper om;
    private final Set<String> writtenAssets = new HashSet<>();

    public MindioPackageWriter(OutputStream out, ObjectMapper om) {
        this.zip = new ZipOutputStream(out);
        this.om = om;
    }

    public void writeNote(MindioPackage.PackagedNote note) throws IOException {
        zip.putNextEntry(new ZipEntry("notes/" + note.uid() + ".json"));
        zip.write(om.writeValueAsBytes(note));
        zip.closeEntry();
    }

    public boolean writeAsset(String name, Path file) throws IOException {
        if (!writtenAssets.add(name)) return false;
        zip.putNextEntry(new ZipEntry("assets/" + name));
        Files.copy(file, zip);
        zip.closeEntry();
        return true;
    }

    public void writeManifest(MindioPackage.Manifest manifest) throws IOException {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(om.writeValueAsBytes(manifest));
        zip.closeEntry();
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
