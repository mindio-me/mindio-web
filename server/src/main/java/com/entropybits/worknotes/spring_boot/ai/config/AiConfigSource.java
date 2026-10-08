/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.config;

/**
 * 当前生效的 AI 配置来源。每次调用都可能返回不同的值（界面改了配置立即生效），
 * 使用方不要把返回值存成字段，用到时再取。
 */
@FunctionalInterface
public interface AiConfigSource {
    AiProperties current();
}
