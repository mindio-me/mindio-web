/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSourceNormalizerTest {

    private static String norm(String md) {
        return MarkdownSourceNormalizer.normalize(md);
    }

    @Test
    void normalizesLineEndings() {
        assertThat(norm("a\r\nb\rc")).isEqualTo("a\nb\nc");
    }

    @Test
    void frontmatterWithKeysBecomesYamlFence() {
        assertThat(norm("---\ntitle: 周报\ntags: [a]\n---\n正文"))
                .isEqualTo("```yaml\ntitle: 周报\ntags: [a]\n```\n正文");
    }

    @Test
    void leadingRuleWithoutKeysIsKept() {
        assertThat(norm("---\n\n正文\n\n---\n\n尾")).isEqualTo("---\n\n正文\n\n---\n\n尾");
    }

    @Test
    void multiLineMathBecomesMathFence() {
        assertThat(norm("前\n$$\na+b\n$$\n后")).isEqualTo("前\n```math\na+b\n```\n后");
    }

    @Test
    void oneLineMathBecomesMathFence() {
        assertThat(norm("$$ a+b $$")).isEqualTo("```math\na+b\n```");
    }

    @Test
    void unclosedMathIsLeftAlone() {
        assertThat(norm("$$\na")).isEqualTo("$$\na");
    }

    @Test
    void fencedCodeIsUntouched() {
        String md = "```\n$$\n[[x]]\n$$\n```";
        assertThat(norm(md)).isEqualTo(md);
    }

    @Test
    void imageEmbedsBecomeImagesAndWikilinksBecomeText() {
        assertThat(norm("![[pic 1.png|300]] [[A#小节|别名]] [[B]] ![[其他笔记]]"))
                .isEqualTo("![pic 1.png](<pic 1.png>) 别名 B 其他笔记");
    }
}
