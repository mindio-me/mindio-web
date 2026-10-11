/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentImageLocatorTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void markdown_recordsOffsetsAndMarkupForEachImage() {
        String md = "开头\n\n![架构](/api/uploads/a.png)\n*架构*\n\n见 <img src=\"https://x.com/b.png\" alt=\"二\"> 图";

        List<AgentImageLocator.LocatedImage> images = AgentImageLocator.locateInNote("markdown", md, om);

        assertThat(images).hasSize(2);
        AgentImageLocator.LocatedImage first = images.get(0);
        assertThat(md.substring(first.start(), first.end())).isEqualTo("![架构](/api/uploads/a.png)");
        assertThat(first.markup()).isEqualTo("![架构](/api/uploads/a.png)");
        assertThat(first.caption()).isEqualTo("架构");
        assertThat(images.get(1).caption()).isEqualTo("二");
        assertThat(images.get(1).standalone()).isFalse();
    }

    @Test
    void editorjs_marksStandaloneImageBlocksAndInlineImagesWithTheirBlockId() {
        String content = """
                {"blocks":[
                  {"id":"img1","type":"image","data":{"file":{"url":"/api/uploads/1.png"},"caption":"<b>图一</b>"}},
                  {"id":"p1","type":"paragraph","data":{"text":"<img src=\\"/api/uploads/2.png\\">"}}
                ]}""";

        List<AgentImageLocator.LocatedImage> images = AgentImageLocator.locateInNote("editorjs", content, om);

        assertThat(images).extracting(AgentImageLocator.LocatedImage::blockId).containsExactly("img1", "p1");
        assertThat(images).extracting(AgentImageLocator.LocatedImage::standalone).containsExactly(true, false);
        assertThat(images.get(0).caption()).isEqualTo("图一");
    }

    @Test
    void unusableUrlsAreSkippedSoIndexesStayStable() {
        String md = "![](feishu-image:abc)\n![](about:blank)\n![ok](/api/uploads/1.png)";

        assertThat(AgentImageLocator.locateInNote("markdown", md, om))
                .extracting(AgentImageLocator.LocatedImage::url).containsExactly("/api/uploads/1.png");
    }

    @Test
    void stripHtml_removesTagsAndNbsp() {
        assertThat(AgentImageLocator.stripHtml("<b>a</b>&nbsp;b ")).isEqualTo("a b");
    }
}
