/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

/**
 * 喂给 summarizeCluster 的单条收藏素材：标题 + 作者（可空）+ 正文摘录（没有正文时为空字符串，
 * 模型只能靠标题推断）。
 */
public record ClipContent(String title, String author, String excerpt) {}
