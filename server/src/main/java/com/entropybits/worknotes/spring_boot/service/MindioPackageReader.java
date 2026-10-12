/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * 读取并校验 .mindio 包。条目名走白名单（manifest.json / notes/&lt;uuid&gt;.json / assets/&lt;sha256&gt;[.ext]），
 * 带 ../ 的条目名过不了白名单，zip slip 无从发生；解压总字节数和条目数有上限，防 zip 炸弹。
 * 任何结构问题都整包拒绝，不做"能导几篇导几篇"。
 */
public class MindioPackageReader {

    public static class InvalidPackageException extends BadRequestException {
        public InvalidPackageException(String message) {
            super(message);
        }
    }

    public record ReadPackage(MindioPackage.Manifest manifest, List<MindioPackage.PackagedNote> notes,
                              Map<String, Path> assets) {}

    static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern NOTE_ENTRY = Pattern.compile("notes/(" + UUID_RE + ")\\.json");
    private static final Pattern ASSET_ENTRY = Pattern.compile("assets/([0-9a-f]{64}(?:\\.[a-z0-9]{1,10})?)");
    private static final int MAX_JSON_BYTES = 64 * 1024 * 1024;

    private final ObjectMapper om;
    private final long maxTotalBytes;
    private final int maxEntries;

    public MindioPackageReader(ObjectMapper om, long maxTotalBytes, int maxEntries) {
        this.om = om;
        this.maxTotalBytes = maxTotalBytes;
        this.maxEntries = maxEntries;
    }

    public ReadPackage read(Path zipFile, Path extractDir) {
        MindioPackage.Manifest manifest = null;
        List<MindioPackage.PackagedNote> notes = new ArrayList<>();
        Map<String, Path> assets = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long[] total = {0};
        int entries = 0;
        try {
            Files.createDirectories(extractDir.resolve("assets"));
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    if (++entries > maxEntries) throw new InvalidPackageException("备份文件包含的条目过多");
                    String name = entry.getName();
                    if (!seen.add(name)) throw new InvalidPackageException("备份文件里有重复的条目：" + name);
                    Matcher note = NOTE_ENTRY.matcher(name);
                    Matcher asset = ASSET_ENTRY.matcher(name);
                    if ("manifest.json".equals(name)) {
                        manifest = om.readValue(readLimited(zip, total), MindioPackage.Manifest.class);
                    } else if (note.matches()) {
                        MindioPackage.PackagedNote n = om.readValue(readLimited(zip, total), MindioPackage.PackagedNote.class);
                        validateNote(n, note.group(1));
                        notes.add(n);
                    } else if (asset.matches()) {
                        Path target = extractDir.resolve("assets").resolve(asset.group(1));
                        try (OutputStream out = Files.newOutputStream(target)) {
                            copyLimited(zip, out, total);
                        }
                        assets.put(asset.group(1), target);
                    } else {
                        throw new InvalidPackageException("备份文件里有无法识别的条目：" + name);
                    }
                }
            }
        } catch (InvalidPackageException e) {
            throw e;
        } catch (ZipException e) {
            throw new InvalidPackageException("不是有效的 mindio 备份文件（无法解压）");
        } catch (IOException e) {
            throw new InvalidPackageException("备份文件内容损坏：" + e.getMessage());
        }
        if (manifest == null) throw new InvalidPackageException("不是有效的 mindio 备份文件（缺少 manifest.json）");
        if (!MindioPackage.FORMAT.equals(manifest.format())) {
            throw new InvalidPackageException("不是 mindio 笔记备份文件");
        }
        if (manifest.formatVersion() > MindioPackage.FORMAT_VERSION) {
            throw new InvalidPackageException("此备份由更新版本的 mindio 导出，请先升级");
        }
        if (manifest.formatVersion() < 1) throw new InvalidPackageException("备份文件版本无效");
        return new ReadPackage(manifest, notes, assets);
    }

    private void validateNote(MindioPackage.PackagedNote n, String uidFromName) {
        if (n.uid() == null || !n.uid().equals(uidFromName)) {
            throw new InvalidPackageException("备份文件里的笔记标识不一致：" + uidFromName);
        }
        if (!"editorjs".equals(n.contentType())) {
            throw new InvalidPackageException("备份文件里有不支持的笔记类型：" + n.contentType());
        }
        if (n.content() == null) throw new InvalidPackageException("备份文件里有缺少正文的笔记：" + uidFromName);
    }

    private byte[] readLimited(InputStream in, long[] total) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        long before = total[0];
        copyLimited(in, buf, total);
        if (total[0] - before > MAX_JSON_BYTES) throw new InvalidPackageException("备份文件里的笔记过大");
        return buf.toByteArray();
    }

    private void copyLimited(InputStream in, OutputStream out, long[] total) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            total[0] += n;
            if (total[0] > maxTotalBytes) throw new InvalidPackageException("备份文件解压后过大");
            out.write(buf, 0, n);
        }
    }
}
