/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRefRewriterTest {

    final ObjectMapper om = new ObjectMapper();
    final NoteRefRewriter rw = new NoteRefRewriter(om, List.of("https://mindio.example.com"));
    static final String U7 = "77777777-7777-7777-7777-777777777777";
    static final String U8 = "88888888-8888-8888-8888-888888888888";

    String doc = "{\"blocks\":["
            + "{\"id\":\"r1\",\"type\":\"references\",\"data\":{\"items\":["
            + "{\"kind\":\"note\",\"title\":\"B\",\"noteId\":7},"
            + "{\"kind\":\"link\",\"title\":\"L\",\"url\":\"https://x.com\"}]}},"
            + "{\"id\":\"p1\",\"type\":\"paragraph\",\"data\":{\"text\":"
            + "\"see <a href=\\\"/workspace/notes/8\\\">C</a> and http://localhost:3000/workspace/notes/8/edit"
            + " and https://other.com/workspace/notes/9\"}}]}";

    @Test
    void collectsReferenceItemIdsAndLocalLinks() {
        assertThat(rw.collectNoteIds(doc, null)).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void toPackageRewritesMappedRefsOnly() throws Exception {
        String out = rw.toPackage(doc, null, Map.of(7L, U7, 8L, U8));

        JsonNode item = om.readTree(out).path("blocks").get(0).path("data").path("items").get(0);
        assertThat(item.has("noteId")).isFalse();
        assertThat(item.path("noteUid").asText()).isEqualTo(U7);
        assertThat(out).contains("/workspace/notes/uid/" + U8 + "\\\"")
                .contains("/workspace/notes/uid/" + U8 + "/edit")
                .doesNotContain("localhost:3000")
                .contains("https://other.com/workspace/notes/9");
    }

    @Test
    void unmappedIdsStayUntouched() {
        assertThat(rw.toPackage(doc, null, Map.of())).isEqualTo(doc);
    }

    @Test
    void roundTripsThroughUids() {
        String pkg = rw.toPackage(doc, null, Map.of(7L, U7, 8L, U8));
        assertThat(rw.collectNoteUids(pkg, null)).containsExactlyInAnyOrder(U7, U8);

        String back = rw.fromPackage(pkg, null, Map.of(U7, 70L, U8, 80L));

        assertThat(back).contains("\"noteId\":70").doesNotContain("noteUid")
                .contains("/workspace/notes/80\\\"").contains("/workspace/notes/80/edit");
    }

    @Test
    void unresolvedUidsStayAsUidRefs() {
        String pkg = rw.toPackage(doc, null, Map.of(7L, U7, 8L, U8));
        String back = rw.fromPackage(pkg, null, Map.of(U7, 70L));
        assertThat(back).contains("\"noteId\":70").contains("/workspace/notes/uid/" + U8);
    }

    @Test
    void referencesSectionIsHandledStructurally() {
        String section = "{\"items\":[{\"kind\":\"note\",\"title\":\"B\",\"noteId\":7}]}";
        assertThat(rw.collectNoteIds(section, "references")).containsExactly(7L);
        assertThat(rw.toPackage(section, "references", Map.of(7L, U7))).contains("\"noteUid\":\"" + U7 + "\"");
        // 非 references 类型的 section 不做结构化处理
        assertThat(rw.collectNoteIds(section, "paragraph")).isEmpty();
    }

    @Test
    void invalidJsonFallsBackToTextLinksOnly() {
        String broken = "{not json /workspace/notes/8";
        assertThat(rw.collectNoteIds(broken, null)).containsExactly(8L);
        assertThat(rw.toPackage(broken, null, Map.of(8L, U8))).isEqualTo("{not json /workspace/notes/uid/" + U8);
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(rw.collectNoteIds(null, null)).isEmpty();
        assertThat(rw.toPackage("", null, Map.of(1L, U7))).isEmpty();
        assertThat(rw.fromPackage(null, null, Map.of())).isNull();
    }
}
