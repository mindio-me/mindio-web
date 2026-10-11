/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** agent 的 read_note 按标题查找时返回的候选项——只有类型/ID/标题，让模型据此再按 ID 读全文。 */
public record AgentDocumentSummary(String type, Long id, String title) {}
