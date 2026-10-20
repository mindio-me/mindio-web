/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.settings;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.dto.AiSettingItem;
import com.entropybits.worknotes.spring_boot.ai.dto.InternalAiConfigResponse;
import com.entropybits.worknotes.spring_boot.entity.SystemSetting;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.integration.feishu.FeishuProperties;
import com.entropybits.worknotes.spring_boot.integration.feishu.crypto.AesGcmStringEncryptor;
import com.entropybits.worknotes.spring_boot.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSettingsServiceTest {

    // 32 字节测试密钥（仅测试用的固定值）
    static final String KEY_A = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final String KEY_B = "Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=";

    @Mock SystemSettingRepository repository;
    AiProperties env;
    FeishuProperties feishu;
    List<SystemSetting> rows;
    AiSettingsService service;

    @BeforeEach
    void setUp() {
        env = new AiProperties();
        env.setProvider("anthropic");
        env.getAnthropic().setApiKey("env-anthropic-key");
        env.getAnthropic().setModel("env-model");
        env.getAnthropic().setBaseUrl("https://api.anthropic.com");
        env.getDoubao().setBaseUrl("https://ark.cn-beijing.volces.com");
        env.getEmbedding().setModel("env-embedding");
        feishu = new FeishuProperties();
        feishu.setTokenCryptoKey(KEY_A);
        rows = new ArrayList<>();
        when(repository.findAll()).thenAnswer(inv -> List.copyOf(rows));
        when(repository.findBySettingKey(any())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getSettingKey().equals(inv.getArgument(0))).findFirst());
        service = new AiSettingsService(repository, env, feishu);
    }

    private void storePlain(String key, String value) {
        rows.add(SystemSetting.builder().settingKey(key).settingValue(value).encrypted(false).build());
    }

    private void storeSecret(String key, String plain, String cryptoKey) {
        rows.add(SystemSetting.builder().settingKey(key)
                .settingValue(new AesGcmStringEncryptor(cryptoKey).encrypt(plain)).encrypted(true).build());
    }

    @Test
    void current_returnsEnvValuesWhenNoRows() {
        AiProperties cfg = service.current();
        assertThat(cfg.getProvider()).isEqualTo("anthropic");
        assertThat(cfg.getAnthropic().getApiKey()).isEqualTo("env-anthropic-key");
        assertThat(cfg.getEmbedding().getModel()).isEqualTo("env-embedding");
        assertThat(cfg).isNotSameAs(env);
    }

    @Test
    void current_dbValueOverridesEnvWithoutMutatingTheBean() {
        storePlain("ai.default_provider", "doubao");
        storeSecret("ai.doubao.api_key", "db-doubao-key", KEY_A);
        storePlain("ai.embedding.model", "db-embedding");

        AiProperties cfg = service.current();

        assertThat(cfg.getProvider()).isEqualTo("doubao");
        assertThat(cfg.getDoubao().getApiKey()).isEqualTo("db-doubao-key");
        assertThat(cfg.getDoubao().getBaseUrl()).isEqualTo("https://ark.cn-beijing.volces.com");
        assertThat(cfg.getEmbedding().getModel()).isEqualTo("db-embedding");
        assertThat(env.getProvider()).isEqualTo("anthropic");
        assertThat(env.getDoubao().getApiKey()).isNull();
    }

    @Test
    void current_undecryptableSecretFallsBackToEnv() {
        storeSecret("ai.anthropic.api_key", "db-key", KEY_B);
        assertThat(service.current().getAnthropic().getApiKey()).isEqualTo("env-anthropic-key");
    }

    @Test
    void update_absentKeysAreUntouched() {
        Map<String, String> changes = new HashMap<>();
        changes.put("ai.anthropic.model", "new-model");
        changes.put("ai.anthropic.api_key", null);
        service.update(changes);

        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSettingKey()).isEqualTo("ai.anthropic.model");
        assertThat(saved.getValue().getSettingValue()).isEqualTo("new-model");
        verify(repository, never()).deleteBySettingKey(any());
    }

    @Test
    void update_blankValueDeletesTheRow() {
        service.update(Map.of("ai.anthropic.api_key", "  "));
        verify(repository).deleteBySettingKey("ai.anthropic.api_key");
        verify(repository, never()).save(any());
    }

    @Test
    void update_secretIsStoredEncrypted() {
        service.update(Map.of("ai.openai.api_key", " sk-test-1234 "));
        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isEncrypted()).isTrue();
        assertThat(saved.getValue().getSettingValue()).doesNotContain("sk-test");
        assertThat(new AesGcmStringEncryptor(KEY_A).decrypt(saved.getValue().getSettingValue())).isEqualTo("sk-test-1234");
    }

    @Test
    void update_rejectsUnknownKeyAndInvalidProvider() {
        assertThatThrownBy(() -> service.update(Map.of("ai.tavily.api_key", "x"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(Map.of("ai.default_provider", "gemini"))).isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_secretWithoutCryptoKeyIsRejected() {
        feishu.setTokenCryptoKey("");
        assertThatThrownBy(() -> service.update(Map.of("ai.openai.api_key", "sk-x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FEISHU_TOKEN_CRYPTO_KEY");
    }

    @Test
    void update_plainValueWorksWithoutCryptoKey() {
        feishu.setTokenCryptoKey("");
        service.update(Map.of("ai.openai.model", "gpt-x"));
        verify(repository).save(any());
    }

    @Test
    void update_rejectsOverlongValue() {
        assertThatThrownBy(() -> service.update(Map.of("ai.openai.model", "x".repeat(2001))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void view_neverReturnsSecretPlaintextAndReportsSource() {
        storeSecret("ai.openai.api_key", "sk-abcd-WXYZ", KEY_A);
        storePlain("ai.openai.model", "gpt-db");

        Map<String, AiSettingItem> items = new HashMap<>();
        service.view().items().forEach(i -> items.put(i.key(), i));

        AiSettingItem key = items.get("ai.openai.api_key");
        assertThat(key.secret()).isTrue();
        assertThat(key.value()).isNull();
        assertThat(key.effective()).isNull();
        assertThat(key.last4()).isEqualTo("WXYZ");
        assertThat(key.source()).isEqualTo("database");
        assertThat(key.configured()).isTrue();

        assertThat(items.get("ai.openai.model").value()).isEqualTo("gpt-db");
        assertThat(items.get("ai.anthropic.api_key").source()).isEqualTo("environment");
        assertThat(items.get("ai.deepseek.api_key").source()).isEqualTo("none");
        assertThat(items.get("ai.anthropic.model").effective()).isEqualTo("env-model");
        assertThat(service.view().cryptoKeyConfigured()).isTrue();
    }

    @Test
    void view_marksUndecryptableSecretAsUnreadable() {
        storeSecret("ai.deepseek.api_key", "db-key", KEY_B);
        AiSettingItem item = service.view().items().stream()
                .filter(i -> i.key().equals("ai.deepseek.api_key")).findFirst().orElseThrow();
        assertThat(item.unreadable()).isTrue();
        assertThat(item.source()).isEqualTo("none");
    }

    @Test
    void internalConfig_containsOnlyDatabaseValues() {
        storeSecret("ai.doubao.api_key", "db-doubao", KEY_A);
        storePlain("ai.doubao.chat_path", "/api/v3/chat/completions");
        storeSecret("asr.volcengine.app_key", "asr-new-key", KEY_A);

        InternalAiConfigResponse cfg = service.internalConfig();

        assertThat(cfg.defaultProvider()).isNull();
        assertThat(cfg.providers()).containsOnlyKeys("doubao");
        assertThat(cfg.providers().get("doubao").apiKey()).isEqualTo("db-doubao");
        assertThat(cfg.providers().get("doubao").model()).isNull();
        assertThat(cfg.providers().get("doubao").chatPath()).isEqualTo("/api/v3/chat/completions");
        assertThat(cfg.asr().appKey()).isEqualTo("asr-new-key");
        assertThat(cfg.asr().accessKey()).isNull();
    }

    @Test
    void internalConfigIncludesImage() {
        storePlain("ai.image.provider", "openai");
        InternalAiConfigResponse cfg = service.internalConfig();
        assertThat(cfg.image().provider()).isEqualTo("openai");
        assertThat(cfg.image().model()).isNull();
    }

    @Test
    void internalConfigOmitsImageWhenUnset() {
        assertThat(service.internalConfig().image()).isNull();
    }

    @Test
    void autoTranscribeEnabled_defaultsTrueAndHonorsFalse() {
        when(repository.save(any(SystemSetting.class))).thenAnswer(inv -> {
            SystemSetting row = inv.getArgument(0);
            rows.removeIf(r -> r.getSettingKey().equals(row.getSettingKey()));
            rows.add(row);
            return row;
        });
        assertThat(service.autoTranscribeEnabled()).isTrue();
        service.update(Map.of("asr.auto_transcribe", "false"));
        assertThat(service.autoTranscribeEnabled()).isFalse();
        service.update(Map.of("asr.auto_transcribe", "true"));
        assertThat(service.autoTranscribeEnabled()).isTrue();
    }

    @Test
    void acceptsImageProviderKeys() {
        when(repository.save(any(SystemSetting.class))).thenAnswer(inv -> {
            rows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        service.update(Map.of("ai.image.provider", "doubao"));
        assertThat(service.imageConfig()).containsEntry("ai.image.provider", "doubao");
        // image.* 键存在时 current()/view() 不能因此报错
        assertThat(service.current().getProvider()).isEqualTo("anthropic");
        assertThat(service.view().items()).anyMatch(i -> i.key().equals("ai.image.provider"));
    }

    @Test
    void rejectsUnknownImageProvider() {
        assertThatThrownBy(() -> service.update(Map.of("ai.image.provider", "midjourney")))
                .isInstanceOf(BadRequestException.class);
    }
}
