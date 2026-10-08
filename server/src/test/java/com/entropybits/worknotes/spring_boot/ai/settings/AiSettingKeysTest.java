/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSettingKeysTest {

    @Test
    void allKeysAreOrderedAndSecretsAreTheKeyFields() {
        assertThat(AiSettingKeys.ORDERED).hasSize(17).doesNotHaveDuplicates();
        assertThat(AiSettingKeys.ALL).containsExactlyInAnyOrderElementsOf(AiSettingKeys.ORDERED);
        assertThat(AiSettingKeys.SECRETS).containsExactlyInAnyOrder(
                "ai.anthropic.api_key", "ai.openai.api_key", "ai.deepseek.api_key", "ai.doubao.api_key",
                "asr.volcengine.app_key", "asr.volcengine.access_key");
        assertThat(AiSettingKeys.ORDERED).contains("ai.default_provider", "ai.doubao.chat_path", "ai.embedding.model");
        assertThat(AiSettingKeys.ORDERED).doesNotContain("ai.anthropic.chat_path");
    }
}
