/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEditorjsImageEditorTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AgentEditorjsImageEditor editor = new AgentEditorjsImageEditor(om);

    private static final String DOC = """
            {"time":1,"blocks":[
              {"id":"h1","type":"header","data":{"text":"4.3 方案","level":2}},
              {"id":"p1","type":"paragraph","data":{"text":"<b>【配图位：图 1】</b>"}},
              {"id":"p2","type":"paragraph","data":{"text":"网关轮询见下图说明"}},
              {"id":"img","type":"image","data":{"file":{"url":"/api/uploads/x.png"},"caption":"旧图注","withBorder":false}},
              {"id":"p3","type":"paragraph","data":{"text":"<img src=\\"/api/uploads/inline.png\\">"}}
            ],"version":"2.31.6"}""";

    private JsonNode blocks(String content) throws Exception {
        return om.readTree(content).path("blocks");
    }

    @Test
    void insert_replacesBlockWhoseTextEqualsAnchor() throws Exception {
        AgentImageEditResult r = editor.insert(DOC, "/api/uploads/new.png", "图1 架构", "【配图位：图 1】");

        JsonNode blocks = blocks(r.content());
        assertThat(blocks).hasSize(5);
        JsonNode inserted = blocks.get(1);
        assertThat(inserted.path("type").asText()).isEqualTo("image");
        assertThat(inserted.path("data").path("file").path("url").asText()).isEqualTo("/api/uploads/new.png");
        assertThat(inserted.path("data").path("caption").asText()).isEqualTo("图1 架构");
        assertThat(inserted.path("id").asText()).hasSize(10).isEqualTo(r.event().get("blockId"));
        assertThat(r.event()).containsEntry("op", "insert").containsEntry("replacedBlockId", "p1")
                .doesNotContainKey("afterBlockId");
        assertThat(((Map<?, ?>) r.event().get("data")).get("caption")).isEqualTo("图1 架构");
        // 根对象其他字段原样保留
        assertThat(om.readTree(r.content()).path("version").asText()).isEqualTo("2.31.6");
    }

    @Test
    void insert_afterBlockThatContainsAnchor() throws Exception {
        AgentImageEditResult r = editor.insert(DOC, "/api/uploads/new.png", "", "网关轮询");

        JsonNode blocks = blocks(r.content());
        assertThat(blocks).hasSize(6);
        assertThat(blocks.get(3).path("type").asText()).isEqualTo("image");
        assertThat(r.event()).containsEntry("afterBlockId", "p2").doesNotContainKey("replacedBlockId");
    }

    @Test
    void insert_withoutAnchorAppendsToEnd() throws Exception {
        AgentImageEditResult r = editor.insert(DOC, "/api/uploads/new.png", null, null);

        JsonNode blocks = blocks(r.content());
        assertThat(blocks.get(blocks.size() - 1).path("type").asText()).isEqualTo("image");
        assertThat(r.event()).containsEntry("afterBlockId", "p3");
    }

    @Test
    void insert_intoEmptyNote() throws Exception {
        AgentImageEditResult r = editor.insert(null, "/api/uploads/new.png", "a", null);

        assertThat(blocks(r.content())).hasSize(1);
        assertThat(r.event()).doesNotContainKey("afterBlockId");
    }

    @Test
    void insert_anchorNotFound_throws() {
        assertThatThrownBy(() -> editor.insert(DOC, "/u.png", "", "不存在的文字"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("不存在的文字");
    }

    @Test
    void insert_sameAnchorTwiceAfterReplace_throwsSecondTime() {
        AgentImageEditResult first = editor.insert(DOC, "/u1.png", "", "【配图位：图 1】");

        assertThatThrownBy(() -> editor.insert(first.content(), "/u2.png", "", "【配图位：图 1】"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("【配图位：图 1】");
    }

    @Test
    void delete_removesStandaloneImageBlock() throws Exception {
        var image = AgentImageLocator.locateInNote("editorjs", DOC, om).get(0);

        AgentImageEditResult r = editor.delete(DOC, image);

        assertThat(blocks(r.content())).extracting(b -> b.path("id").asText()).doesNotContain("img");
        assertThat(r.event()).containsEntry("op", "delete").containsEntry("blockId", "img");
    }

    @Test
    void setCaption_updatesCaptionAndKeepsOtherFields() throws Exception {
        var image = AgentImageLocator.locateInNote("editorjs", DOC, om).get(0);

        AgentImageEditResult r = editor.setCaption(DOC, image, "新图注");

        JsonNode data = blocks(r.content()).get(3).path("data");
        assertThat(data.path("caption").asText()).isEqualTo("新图注");
        assertThat(data.path("withBorder").asBoolean(true)).isFalse();
        assertThat(r.event()).containsEntry("op", "caption").containsEntry("blockId", "img");
        assertThat(((Map<?, ?>) r.event().get("data")).get("caption")).isEqualTo("新图注");
    }

    @Test
    void deleteOrCaption_inlineImage_rejected() {
        var inline = AgentImageLocator.locateInNote("editorjs", DOC, om).get(1);

        assertThatThrownBy(() -> editor.delete(DOC, inline))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("嵌在文本块里");
        assertThatThrownBy(() -> editor.setCaption(DOC, inline, "x"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("嵌在文本块里");
    }
}
