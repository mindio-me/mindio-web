/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.entity.Note;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharePageRendererTest {

    private static final String BASE = "https://demo.mindio.me";

    private static Note.NoteBuilder base() {
        return Note.builder().id(2L).title("Note title").summary("A summary").isPublic(true);
    }

    @Test
    void cardNoteOutputsLargeImageMeta() {
        String html = SharePageRenderer.render(
                base().shareCardImageUrl("/api/uploads/share-card/a.jpg").build(), BASE);
        assertThat(html).contains("content=\"summary_large_image\"");
        assertThat(html).contains("og:image\" content=\"https://demo.mindio.me/api/uploads/share-card/a.jpg\"");
        assertThat(html).contains("og:image:width\" content=\"1200\"", "og:image:height\" content=\"630\"");
        assertThat(html).contains("twitter:image\" content=\"https://demo.mindio.me/api/uploads/share-card/a.jpg\"");
    }

    @Test
    void noCardNoteOutputsSummaryWithoutImage() {
        String html = SharePageRenderer.render(base().build(), BASE);
        assertThat(html).contains("content=\"summary\"");
        assertThat(html).doesNotContain("og:image").doesNotContain("twitter:image");
    }

    @Test
    void usesCardTitleOverNoteTitle() {
        String html = SharePageRenderer.render(base().shareCardTitle("Card T").build(), BASE);
        assertThat(html).contains("og:title\" content=\"Card T\"").doesNotContain("Note title");
    }

    @Test
    void fallsBackToNoteTitle() {
        String html = SharePageRenderer.render(base().shareCardTitle(" ").build(), BASE);
        assertThat(html).contains("og:title\" content=\"Note title\"");
    }

    @Test
    void usesAiSummaryThenPlainText() {
        String html = SharePageRenderer.render(base().build(), BASE);
        assertThat(html).contains("og:description\" content=\"A summary\"");
        Note n = base().summary(null).content("<p>" + "x".repeat(300) + "</p>").contentType("richtext").build();
        String html2 = SharePageRenderer.render(n, BASE);
        assertThat(html2).contains("og:description\" content=\"" + "x".repeat(150) + "\"");
        assertThat(html2).doesNotContain("x".repeat(151));
    }

    @Test
    void keepsNonAsciiCharactersUnescaped() {
        String html = SharePageRenderer.render(base().title("Videos —— 2000 PRs 中文").build(), BASE);
        assertThat(html).contains("og:title\" content=\"Videos —— 2000 PRs 中文\"").doesNotContain("&mdash;");
    }

    @Test
    void fallsBackToTitleWhenNoTextCanBeExtracted() {
        Note n = base().summary(null).content(null).build();
        String html = SharePageRenderer.render(n, BASE);
        assertThat(html).contains("og:description\" content=\"Note title\"")
                .contains("twitter:description\" content=\"Note title\"");
    }

    @Test
    void escapesHtmlInTitleAndDescription() {
        Note n = base().title("A <b> & \"Q\"").summary("<script>").build();
        String html = SharePageRenderer.render(n, BASE);
        assertThat(html).contains("A &lt;b&gt; &amp; &quot;Q&quot;").contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<b>").doesNotContain("<script>");
    }

    @Test
    void rewritesLocalhostImageUrlToPublicBase() {
        String html = SharePageRenderer.render(
                base().shareCardImageUrl("http://localhost:8080/api/uploads/x.jpg?v=1").build(), BASE);
        assertThat(html).contains("og:image\" content=\"https://demo.mindio.me/api/uploads/x.jpg?v=1\"");
        assertThat(html).doesNotContain("localhost");
    }

    @Test
    void bodyContainsLinkToNote() {
        assertThat(SharePageRenderer.render(base().build(), BASE)).contains("href=\"https://demo.mindio.me/notes/2\"");
    }
}
