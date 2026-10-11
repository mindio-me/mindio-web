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

class AgentMarkdownImageEditorTest {

    private final ObjectMapper om = new ObjectMapper();

    private AgentImageLocator.LocatedImage image(String md, int index) {
        return AgentImageLocator.locateInNote("markdown", md, om).get(index - 1);
    }

    @Test
    void insert_replacesParagraphEqualToAnchor_withImageAndCaptionLine() {
        String md = "## 4.3\n\n**【配图位：图 1】**\n\n后文";

        String out = AgentMarkdownImageEditor.insert(md, "/api/uploads/a.png", "图1 架构", "【配图位：图 1】").content();

        assertThat(out).isEqualTo("## 4.3\n\n![图1 架构](/api/uploads/a.png)\n*图1 架构*\n\n后文");
    }

    @Test
    void insert_afterParagraphContainingAnchor() {
        String md = "第一段提到网关\n\n第二段";

        String out = AgentMarkdownImageEditor.insert(md, "/a.png", "", "网关").content();

        assertThat(out).isEqualTo("第一段提到网关\n\n![](/a.png)\n\n第二段");
    }

    @Test
    void insert_withoutAnchorAppends() {
        assertThat(AgentMarkdownImageEditor.insert("正文\n\n", "/a.png", "说明", null).content())
                .isEqualTo("正文\n\n![说明](/a.png)\n*说明*\n");
        assertThat(AgentMarkdownImageEditor.insert(null, "/a.png", "", null).content()).isEqualTo("![](/a.png)");
    }

    @Test
    void insert_anchorNotFound_throws() {
        assertThatThrownBy(() -> AgentMarkdownImageEditor.insert("正文", "/a.png", "", "没有"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("没有");
    }

    @Test
    void captionWithBracketsAndStars_isNormalized() {
        String out = AgentMarkdownImageEditor.insert("", "/a.png", "图[1] *重点*", null).content();

        assertThat(out).isEqualTo("![图［1］ ＊重点＊](/a.png)\n*图［1］ ＊重点＊*");
    }

    @Test
    void delete_imageOnOwnLine_removesLineAndItsCaptionLine() {
        String md = "前\n\n![架构](/a.png)\n*架构*\n\n后";

        String out = AgentMarkdownImageEditor.delete(md, image(md, 1)).content();

        assertThat(out).isEqualTo("前\n\n\n后");
    }

    @Test
    void delete_imageInsideTextLine_removesOnlyTheMarkup() {
        String md = "见图 ![a](/a.png) 所示";

        assertThat(AgentMarkdownImageEditor.delete(md, image(md, 1)).content()).isEqualTo("见图  所示");
    }

    @Test
    void delete_keepsFollowingItalicLineThatIsNotThisImagesCaption() {
        String md = "![a](/a.png)\n*别的强调*";

        assertThat(AgentMarkdownImageEditor.delete(md, image(md, 1)).content()).isEqualTo("*别的强调*");
    }

    @Test
    void setCaption_replacesAltAndExistingCaptionLine() {
        String md = "![旧](/a.png)\n*旧*\n\n后";

        assertThat(AgentMarkdownImageEditor.setCaption(md, image(md, 1), "新").content())
                .isEqualTo("![新](/a.png)\n*新*\n\n后");
    }

    @Test
    void setCaption_addsCaptionLineWhenMissing() {
        String md = "![](/a.png)\n\n后";

        assertThat(AgentMarkdownImageEditor.setCaption(md, image(md, 1), "新").content())
                .isEqualTo("![新](/a.png)\n*新*\n\n后");
    }

    @Test
    void setCaption_blankRemovesCaptionLine() {
        String md = "![旧](/a.png)\n*旧*\n后";

        assertThat(AgentMarkdownImageEditor.setCaption(md, image(md, 1), "").content())
                .isEqualTo("![](/a.png)\n后");
    }

    @Test
    void setCaption_htmlImgInMarkdown_setsAltAttribute() {
        String md = "<img src=\"/a.png\" width=\"300\">";

        assertThat(AgentMarkdownImageEditor.setCaption(md, image(md, 1), "新").content())
                .isEqualTo("<img alt=\"新\" src=\"/a.png\" width=\"300\">\n*新*");
    }
}
