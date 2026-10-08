/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * system_settings 里 AI 相关的键名，集中定义；未列在 ORDERED 里的键一律拒绝写入。
 */
public final class AiSettingKeys {

    public static final List<String> PROVIDERS = List.of("anthropic", "openai", "deepseek", "doubao");

    public static final String DEFAULT_PROVIDER = "ai.default_provider";
    public static final String DOUBAO_CHAT_PATH = "ai.doubao.chat_path";
    public static final String EMBEDDING_MODEL = "ai.embedding.model";
    public static final String ASR_APP_KEY = "asr.volcengine.app_key";
    public static final String ASR_ACCESS_KEY = "asr.volcengine.access_key";

    public static final String MISSING_API_KEY_MESSAGE = "请到 设置 → AI 服务 填写 API Key";

    public static String apiKey(String provider) { return "ai." + provider + ".api_key"; }
    public static String model(String provider) { return "ai." + provider + ".model"; }
    public static String baseUrl(String provider) { return "ai." + provider + ".base_url"; }

    /** 界面展示顺序，同时也是全部合法键 */
    public static final List<String> ORDERED;
    public static final Set<String> ALL;
    public static final Set<String> SECRETS;

    static {
        List<String> ordered = new ArrayList<>();
        Set<String> secrets = new LinkedHashSet<>();
        ordered.add(DEFAULT_PROVIDER);
        for (String p : PROVIDERS) {
            ordered.add(apiKey(p));
            ordered.add(model(p));
            ordered.add(baseUrl(p));
            secrets.add(apiKey(p));
            if ("doubao".equals(p)) {
                ordered.add(DOUBAO_CHAT_PATH);
            }
        }
        ordered.add(EMBEDDING_MODEL);
        ordered.add(ASR_APP_KEY);
        ordered.add(ASR_ACCESS_KEY);
        secrets.add(ASR_APP_KEY);
        secrets.add(ASR_ACCESS_KEY);
        ORDERED = List.copyOf(ordered);
        ALL = Set.copyOf(ordered);
        SECRETS = Set.copyOf(secrets);
    }

    private AiSettingKeys() {}
}
