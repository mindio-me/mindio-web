/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAssetRewriterTest {

    @TempDir Path root;
    NoteAssetRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new NoteAssetRewriter("", root, List.of("https://mindio.example.com"));
    }

    @Test
    void findsRelativeLocalhostAndOwnOriginUploads() {
        String text = "{\"url\":\"/api/uploads/file/a.png\"} "
                + "<img src=\\\"http://localhost:8080/api/uploads/file/b.png\\\"> "
                + "![x](http://127.0.0.1:3000/api/uploads/file/c.png) "
                + "https://mindio.example.com/api/uploads/file/d.mp4";

        List<NoteAssetRewriter.UploadRef> refs = rewriter.findUploads(text);

        assertThat(refs).extracting(NoteAssetRewriter.UploadRef::url).containsExactly(
                "/api/uploads/file/a.png",
                "http://localhost:8080/api/uploads/file/b.png",
                "http://127.0.0.1:3000/api/uploads/file/c.png",
                "https://mindio.example.com/api/uploads/file/d.mp4");
        assertThat(refs.get(0).file()).isEqualTo(root.resolve("file/a.png").toAbsolutePath().normalize());
    }

    @Test
    void leavesOtherServersUploadPathsAlone() {
        String text = "https://other.example.com/api/uploads/file/a.png and //cdn.x.com/api/uploads/b.png";
        assertThat(rewriter.findUploads(text)).isEmpty();
    }

    @Test
    void pathTraversalGivesNullFile() {
        List<NoteAssetRewriter.UploadRef> refs = rewriter.findUploads("/api/uploads/../../secret.txt");
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).file()).isNull();
    }

    @Test
    void queryStringIsIgnoredForFileResolution() {
        List<NoteAssetRewriter.UploadRef> refs = rewriter.findUploads("/api/uploads/file/a.png?v=2");
        assertThat(refs.get(0).file()).isEqualTo(root.resolve("file/a.png").toAbsolutePath().normalize());
    }

    @Test
    void configuredAbsolutePrefixIsRecognised() {
        NoteAssetRewriter withPrefix = new NoteAssetRewriter("https://cdn.mindio.example.com/api", root, List.of());
        assertThat(withPrefix.findUploads("https://cdn.mindio.example.com/api/uploads/file/a.png")).hasSize(1);
    }

    @Test
    void replacesOnlyMappedUrls() {
        String text = "/api/uploads/file/a.png | /api/uploads/file/b.png | https://ext.com/x.png";
        String out = rewriter.replaceUploads(text, Map.of("/api/uploads/file/a.png", "asset://h1.png"));
        assertThat(out).isEqualTo("asset://h1.png | /api/uploads/file/b.png | https://ext.com/x.png");
    }

    @Test
    void findsAndRestoresAssetNames() {
        String h = "a".repeat(64);
        String text = "{\"url\":\"asset://" + h + ".png\"} asset://" + "b".repeat(64);
        assertThat(rewriter.findAssetNames(text)).containsExactlyInAnyOrder(h + ".png", "b".repeat(64));
        String out = rewriter.restoreAssets(text, Map.of(h + ".png", "/api/uploads/file/new.png"));
        assertThat(out).isEqualTo("{\"url\":\"/api/uploads/file/new.png\"} asset://" + "b".repeat(64));
    }

    @Test
    void sha256AndAssetNameAreStable() throws Exception {
        Path f = root.resolve("x.PNG");
        Files.writeString(f, "hello", StandardCharsets.UTF_8);
        String hash = NoteAssetRewriter.sha256Hex(f);
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(hash).isEqualTo(LocalFileExtractionService.sha256("hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(NoteAssetRewriter.assetName(hash, f)).isEqualTo(hash + ".png");
        assertThat(NoteAssetRewriter.assetName(hash, root.resolve("noext"))).isEqualTo(hash);
    }
}
