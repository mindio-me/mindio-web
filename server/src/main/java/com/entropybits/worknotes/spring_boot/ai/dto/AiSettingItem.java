/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.dto;

/**
 * 设置页一项 AI 配置。secret 项的 value/effective 永远为 null，只给 last4。
 * source：database = 界面配置；environment = 环境变量/application.yml；none = 都没有。
 */
public record AiSettingItem(String key, boolean secret, String value, boolean configured,
                            String last4, String source, String effective, boolean unreadable) {}
