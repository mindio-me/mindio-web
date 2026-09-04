/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SiteExportImageRewriterTest {

    private static final String PREFIX = "http://127.0.0.1:8080/api";

    @TempDir
    Path uploadRoot;

    private SiteExportImageRewriter rewriter(Path root) {
        return new SiteExportImageRewriter(PREFIX, root);
    }

    @Test
    void rewritesUploadUrlInHtmlContent() {
        String html = "<p>look</p><img src=\"" + PREFIX + "/uploads/worknotesimage/public/notes/a.png\">";
        SiteExportImageRewriter.RewriteResult result = rewriter(uploadRoot).rewriteText(html);

        assertThat(result.text()).isEqualTo(
                "<p>look</p><img src=\"assets/uploads/worknotesimage/public/notes/a.png\">");
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).relativeAssetPath())
                .isEqualTo("assets/uploads/worknotesimage/public/notes/a.png");
        assertThat(result.matches().get(0).sourceFile())
                .isEqualTo(uploadRoot.resolve("worknotesimage/public/notes/a.png"));
    }

    @Test
    void rewritesMultipleOccurrencesInSameText() {
        String text = PREFIX + "/uploads/a.png and " + PREFIX + "/uploads/b.png";
        SiteExportImageRewriter.RewriteResult result = rewriter(uploadRoot).rewriteText(text);

        assertThat(result.text()).isEqualTo("assets/uploads/a.png and assets/uploads/b.png");
        assertThat(result.matches()).hasSize(2);
    }

    @Test
    void leavesTextWithoutMatchesUnchanged() {
        SiteExportImageRewriter.RewriteResult result = rewriter(uploadRoot).rewriteText("plain text, no images");
        assertThat(result.text()).isEqualTo("plain text, no images");
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void handlesNullAndBlankInput() {
        SiteExportImageRewriter r = rewriter(uploadRoot);
        assertThat(r.rewriteText(null).text()).isNull();
        assertThat(r.rewriteText(null).matches()).isEmpty();
        assertThat(r.rewriteText("").matches()).isEmpty();
    }

    @Test
    void matchSingleUrlReturnsEmptyForExternalUrl() {
        Optional<SiteExportImageRewriter.Match> match =
                rewriter(uploadRoot).matchSingleUrl("https://example.com/photo.jpg");
        assertThat(match).isEmpty();
    }

    @Test
    void matchSingleUrlResolvesLocalUploadUrl() {
        Optional<SiteExportImageRewriter.Match> match =
                rewriter(uploadRoot).matchSingleUrl(PREFIX + "/uploads/worknotesimage/public/notes/a.png");
        assertThat(match).isPresent();
        assertThat(match.get().relativeAssetPath()).isEqualTo("assets/uploads/worknotesimage/public/notes/a.png");
    }
}
