/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentHtmlImageEditorTest {

    private final ObjectMapper om = new ObjectMapper();

    private AgentImageLocator.LocatedImage image(String html, int index) {
        return AgentImageLocator.locateInNote("richtext", html, om).get(index - 1);
    }

    @Test
    void insert_replacesParagraphEqualToAnchor() {
        String html = "<h2>4.3</h2><p><strong>【配图位：图 1】</strong></p><p>后文</p>";

        String out = AgentHtmlImageEditor.insert(html, "/a.png", "图1 <架构>", "【配图位：图 1】").content();

        assertThat(out).isEqualTo("<h2>4.3</h2><p><img src=\"/a.png\" alt=\"图1 &lt;架构&gt;\"></p>"
                + "<p><em>图1 &lt;架构&gt;</em></p><p>后文</p>");
    }

    @Test
    void insert_afterParagraphContainingAnchor_prefersInnerBlockOverWrappingDiv() {
        String html = "<div><p>网关轮询</p><p>其他</p></div>";

        String out = AgentHtmlImageEditor.insert(html, "/a.png", "", "网关").content();

        assertThat(out).isEqualTo("<div><p>网关轮询</p><p><img src=\"/a.png\" alt=\"\"></p><p>其他</p></div>");
    }

    @Test
    void insert_anchorEqualToHeading_insertsAfterInsteadOfReplacing() {
        String html = "<h2>4.3 方案</h2><p>正文</p>";

        assertThat(AgentHtmlImageEditor.insert(html, "/a.png", "", "4.3 方案").content())
                .isEqualTo("<h2>4.3 方案</h2><p><img src=\"/a.png\" alt=\"\"></p><p>正文</p>");
    }

    @Test
    void insert_withoutAnchorAppends() {
        assertThat(AgentHtmlImageEditor.insert("<p>a</p>", "/a.png", "说明", null).content())
                .isEqualTo("<p>a</p><p><img src=\"/a.png\" alt=\"说明\"></p><p><em>说明</em></p>");
    }

    @Test
    void insert_anchorNotFound_throws() {
        assertThatThrownBy(() -> AgentHtmlImageEditor.insert("<p>a</p>", "/a.png", "", "没有"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("没有");
    }

    @Test
    void delete_removesWrappingParagraphAndCaptionParagraph() {
        String html = "<p>前</p><p><img src=\"/a.png\" alt=\"说明\"></p><p><em>说明</em></p><p>后</p>";

        assertThat(AgentHtmlImageEditor.delete(html, image(html, 1)).content()).isEqualTo("<p>前</p><p>后</p>");
    }

    @Test
    void delete_imageInsideTextParagraph_removesOnlyTag() {
        String html = "<p>见<img src=\"/a.png\">图</p>";

        assertThat(AgentHtmlImageEditor.delete(html, image(html, 1)).content()).isEqualTo("<p>见图</p>");
    }

    @Test
    void setCaption_updatesAltAndCaptionParagraph() {
        String html = "<p><img src=\"/a.png\" alt=\"旧\"></p><p><em>旧</em></p>";

        assertThat(AgentHtmlImageEditor.setCaption(html, image(html, 1), "新").content())
                .isEqualTo("<p><img src=\"/a.png\" alt=\"新\"></p><p><em>新</em></p>");
    }

    @Test
    void setCaption_addsCaptionParagraphWhenMissing_andBlankRemovesIt() {
        String html = "<p><img src=\"/a.png\"></p><p>后</p>";

        String added = AgentHtmlImageEditor.setCaption(html, image(html, 1), "新").content();
        assertThat(added).isEqualTo("<p><img alt=\"新\" src=\"/a.png\"></p><p><em>新</em></p><p>后</p>");

        String removed = AgentHtmlImageEditor.setCaption(added, image(added, 1), "").content();
        assertThat(removed).isEqualTo("<p><img alt=\"\" src=\"/a.png\"></p><p>后</p>");
    }
}
