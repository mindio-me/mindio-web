/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MindioPackageTest {

    @TempDir Path tmp;
    final ObjectMapper om = new ObjectMapper();
    static final String UID = "3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e";
    static final String HASH = "a".repeat(64);

    MindioPackage.PackagedNote sample() {
        return new MindioPackage.PackagedNote(UID, "标题", "摘要", "2026-01-02T03:04:05Z", "2026-02-03T04:05:06Z",
                "editorjs", "{\"blocks\":[]}", List.of(new MindioPackage.Section("paragraph", "{\"text\":\"x\"}")));
    }

    MindioPackage.Manifest manifest(int version) {
        return new MindioPackage.Manifest(MindioPackage.FORMAT, version, "2026-10-12T00:00:00Z", "web", 1, List.of());
    }

    Path writeValid() throws IOException {
        Path asset = tmp.resolve("src.png");
        Files.writeString(asset, "img");
        Path zip = tmp.resolve("ok.mindio");
        try (OutputStream out = Files.newOutputStream(zip); MindioPackageWriter w = new MindioPackageWriter(out, om)) {
            w.writeNote(sample());
            assertThat(w.writeAsset(HASH + ".png", asset)).isTrue();
            assertThat(w.writeAsset(HASH + ".png", asset)).isFalse();
            w.writeManifest(manifest(1));
        }
        return zip;
    }

    MindioPackageReader reader() {
        return new MindioPackageReader(om, 10_000_000, 1000);
    }

    Path rawZip(String name, String... entriesAndContents) throws IOException {
        Path zip = tmp.resolve(name);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                z.putNextEntry(new ZipEntry(entriesAndContents[i]));
                z.write(entriesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return zip;
    }

    String manifestJson(int version) throws IOException {
        return om.writeValueAsString(manifest(version));
    }

    @Test
    void roundTripsNotesAndAssets() throws Exception {
        MindioPackageReader.ReadPackage pkg = reader().read(writeValid(), tmp.resolve("x"));

        assertThat(pkg.manifest().formatVersion()).isEqualTo(1);
        assertThat(pkg.notes()).containsExactly(sample());
        assertThat(pkg.assets()).containsOnlyKeys(HASH + ".png");
        assertThat(Files.readString(pkg.assets().get(HASH + ".png"))).isEqualTo("img");
    }

    @Test
    void rejectsNewerFormatVersion() throws Exception {
        Path zip = rawZip("v2.mindio", "manifest.json", manifestJson(2));
        assertThatThrownBy(() -> reader().read(zip, tmp.resolve("x")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class)
                .hasMessageContaining("升级");
    }

    @Test
    void rejectsMissingManifestAndNonZip() throws Exception {
        Path noManifest = rawZip("nm.mindio", "notes/" + UID + ".json", om.writeValueAsString(sample()));
        assertThatThrownBy(() -> reader().read(noManifest, tmp.resolve("a")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);

        Path notZip = tmp.resolve("bad.mindio");
        Files.writeString(notZip, "not a zip");
        assertThatThrownBy(() -> reader().read(notZip, tmp.resolve("b")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
    }

    @Test
    void rejectsZipSlipAndUnknownEntries() throws Exception {
        Path slip = rawZip("slip.mindio", "manifest.json", manifestJson(1), "assets/../../evil.txt", "x");
        assertThatThrownBy(() -> reader().read(slip, tmp.resolve("c")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
        assertThat(tmp.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    void rejectsOversizedAndTooManyEntries() throws Exception {
        Path big = rawZip("big.mindio", "manifest.json", manifestJson(1), "assets/" + HASH, "x".repeat(2000));
        assertThatThrownBy(() -> new MindioPackageReader(om, 1000, 100).read(big, tmp.resolve("d")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
        assertThatThrownBy(() -> new MindioPackageReader(om, 10_000_000, 1).read(big, tmp.resolve("e")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
    }

    @Test
    void rejectsNonEditorjsNoteAndUidMismatch() throws Exception {
        MindioPackage.PackagedNote md = new MindioPackage.PackagedNote(UID, "t", null, null, null, "markdown", "x", List.of());
        Path zip1 = rawZip("md.mindio", "manifest.json", manifestJson(1), "notes/" + UID + ".json", om.writeValueAsString(md));
        assertThatThrownBy(() -> reader().read(zip1, tmp.resolve("f")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);

        String otherUid = "00000000-0000-0000-0000-000000000000";
        Path zip2 = rawZip("uid.mindio", "manifest.json", manifestJson(1), "notes/" + otherUid + ".json", om.writeValueAsString(sample()));
        assertThatThrownBy(() -> reader().read(zip2, tmp.resolve("g")))
                .isInstanceOf(MindioPackageReader.InvalidPackageException.class);
    }

    @Test
    void isoHelpersTreatLocalDateTimeAsUtc() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        assertThat(MindioPackage.toIso(t)).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(MindioPackage.fromIso("2026-01-02T03:04:05Z")).isEqualTo(t);
        assertThat(MindioPackage.fromIso("2026-01-02T11:04:05+08:00")).isEqualTo(t);
        assertThat(MindioPackage.toIso(null)).isNull();
        assertThat(MindioPackage.fromIso(null)).isNull();
    }
}
