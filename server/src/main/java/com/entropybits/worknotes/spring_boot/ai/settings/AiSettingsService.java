/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.settings;

import com.entropybits.worknotes.spring_boot.ai.config.AiConfigSource;
import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.dto.AiSettingItem;
import com.entropybits.worknotes.spring_boot.ai.dto.AiSettingsResponse;
import com.entropybits.worknotes.spring_boot.ai.dto.InternalAiConfigResponse;
import com.entropybits.worknotes.spring_boot.entity.SystemSetting;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.integration.feishu.FeishuProperties;
import com.entropybits.worknotes.spring_boot.integration.feishu.crypto.AesGcmStringEncryptor;
import com.entropybits.worknotes.spring_boot.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.entropybits.worknotes.spring_boot.ai.settings.AiSettingKeys.*;

/**
 * 界面配置的 AI 服务（存 system_settings）与环境变量（AiProperties）的合并点。
 * 规则：逐键，库里有行用库里的，否则用 AiProperties；解不开的密文按"没有行"处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService implements AiConfigSource {

    static final int MAX_VALUE_LENGTH = 2000;

    private final SystemSettingRepository repository;
    private final AiProperties envProperties;
    private final FeishuProperties feishuProperties;

    private record Stored(Map<String, String> values, Set<String> unreadable) {}

    @Override
    public AiProperties current() {
        AiProperties cfg = copyOf(envProperties);
        stored().values().forEach((key, value) -> apply(cfg, key, value));
        return cfg;
    }

    @Transactional
    public void update(Map<String, String> changes) {
        for (String key : changes.keySet()) {
            if (!ALL.contains(key)) {
                throw new BadRequestException("未知的配置项: " + key);
            }
        }
        String provider = changes.get(DEFAULT_PROVIDER);
        if (provider != null && !provider.isBlank() && !PROVIDERS.contains(provider.trim())) {
            throw new BadRequestException("不支持的 AI 服务: " + provider);
        }
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (e.getValue() != null && e.getValue().trim().length() > MAX_VALUE_LENGTH) {
                throw new BadRequestException("配置值过长: " + e.getKey());
            }
        }
        for (Map.Entry<String, String> e : changes.entrySet()) {
            String key = e.getKey();
            if (e.getValue() == null) {
                continue;
            }
            String value = e.getValue().trim();
            if (value.isEmpty()) {
                repository.deleteBySettingKey(key);
                continue;
            }
            boolean secret = SECRETS.contains(key);
            String storedValue = secret ? requireEncryptor().encrypt(value) : value;
            SystemSetting row = repository.findBySettingKey(key)
                    .orElseGet(() -> SystemSetting.builder().settingKey(key).build());
            row.setSettingValue(storedValue);
            row.setEncrypted(secret);
            repository.save(row);
        }
    }

    public AiSettingsResponse view() {
        Stored stored = stored();
        List<AiSettingItem> items = ORDERED.stream().map(key -> {
            boolean secret = SECRETS.contains(key);
            String db = stored.values().get(key);
            String env = envValue(envProperties, key);
            String source = db != null ? "database" : (env != null && !env.isBlank() ? "environment" : "none");
            String effective = db != null ? db : env;
            boolean configured = effective != null && !effective.isBlank();
            String last4 = secret && configured && effective.length() >= 4
                    ? effective.substring(effective.length() - 4) : null;
            return new AiSettingItem(key, secret, secret ? null : db, configured, last4, source,
                    secret ? null : effective, stored.unreadable().contains(key));
        }).toList();
        return new AiSettingsResponse(items, optionalEncryptor().isPresent());
    }

    public InternalAiConfigResponse internalConfig() {
        Map<String, String> s = stored().values();
        Map<String, InternalAiConfigResponse.Provider> providers = new LinkedHashMap<>();
        for (String p : PROVIDERS) {
            InternalAiConfigResponse.Provider provider = new InternalAiConfigResponse.Provider(
                    s.get(apiKey(p)), s.get(model(p)), s.get(baseUrl(p)),
                    "doubao".equals(p) ? s.get(DOUBAO_CHAT_PATH) : null);
            if (!provider.isEmpty()) {
                providers.put(p, provider);
            }
        }
        InternalAiConfigResponse.Asr asr = new InternalAiConfigResponse.Asr(s.get(ASR_APP_KEY), s.get(ASR_ACCESS_KEY));
        return new InternalAiConfigResponse(s.get(DEFAULT_PROVIDER), providers, asr.isEmpty() ? null : asr);
    }

    private Stored stored() {
        Map<String, String> values = new HashMap<>();
        Set<String> unreadable = new HashSet<>();
        Optional<AesGcmStringEncryptor> encryptor = Optional.empty();
        boolean encryptorResolved = false;
        for (SystemSetting row : repository.findAll()) {
            String key = row.getSettingKey();
            if (!ALL.contains(key) || row.getSettingValue() == null) {
                continue;
            }
            if (!row.isEncrypted()) {
                values.put(key, row.getSettingValue());
                continue;
            }
            if (!encryptorResolved) {
                encryptor = optionalEncryptor();
                encryptorResolved = true;
            }
            try {
                values.put(key, encryptor.orElseThrow().decrypt(row.getSettingValue()));
            } catch (RuntimeException ex) {
                log.warn("system_settings 中 {} 无法解密（加密密钥可能已变更），按未配置处理", key);
                unreadable.add(key);
            }
        }
        return new Stored(values, unreadable);
    }

    private AesGcmStringEncryptor requireEncryptor() {
        return new AesGcmStringEncryptor(feishuProperties.getTokenCryptoKey());
    }

    private Optional<AesGcmStringEncryptor> optionalEncryptor() {
        try {
            return Optional.of(requireEncryptor());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static AiProperties.ProviderConfig providerOf(AiProperties props, String provider) {
        return switch (provider) {
            case "anthropic" -> props.getAnthropic();
            case "openai" -> props.getOpenai();
            case "deepseek" -> props.getDeepseek();
            case "doubao" -> props.getDoubao();
            default -> throw new IllegalArgumentException("unknown provider " + provider);
        };
    }

    static AiProperties copyOf(AiProperties src) {
        AiProperties dst = new AiProperties();
        dst.setProvider(src.getProvider());
        for (String p : PROVIDERS) {
            AiProperties.ProviderConfig from = providerOf(src, p);
            AiProperties.ProviderConfig to = providerOf(dst, p);
            to.setApiKey(from.getApiKey());
            to.setModel(from.getModel());
            to.setBaseUrl(from.getBaseUrl());
            to.setChatPath(from.getChatPath());
        }
        dst.getEmbedding().setModel(src.getEmbedding().getModel());
        dst.getEmbedding().setPath(src.getEmbedding().getPath());
        return dst;
    }

    /** asr.* 只给 agents 用，Java 侧没有对应字段，这里忽略 */
    private static void apply(AiProperties cfg, String key, String value) {
        if (key.equals(DEFAULT_PROVIDER)) {
            cfg.setProvider(value);
        } else if (key.equals(EMBEDDING_MODEL)) {
            cfg.getEmbedding().setModel(value);
        } else if (key.startsWith("ai.")) {
            String[] parts = key.split("\\.");
            AiProperties.ProviderConfig c = providerOf(cfg, parts[1]);
            switch (parts[2]) {
                case "api_key" -> c.setApiKey(value);
                case "model" -> c.setModel(value);
                case "base_url" -> c.setBaseUrl(value);
                case "chat_path" -> c.setChatPath(value);
                default -> { }
            }
        }
    }

    /** 环境变量/application.yml 里对应的值；asr.* Java 侧不知道（只在 agents 的环境变量里），返回 null */
    private static String envValue(AiProperties env, String key) {
        if (key.equals(DEFAULT_PROVIDER)) return env.getProvider();
        if (key.equals(EMBEDDING_MODEL)) return env.getEmbedding().getModel();
        if (!key.startsWith("ai.")) return null;
        String[] parts = key.split("\\.");
        AiProperties.ProviderConfig c = providerOf(env, parts[1]);
        return switch (parts[2]) {
            case "api_key" -> c.getApiKey();
            case "model" -> c.getModel();
            case "base_url" -> c.getBaseUrl();
            case "chat_path" -> c.getChatPath();
            default -> null;
        };
    }
}
