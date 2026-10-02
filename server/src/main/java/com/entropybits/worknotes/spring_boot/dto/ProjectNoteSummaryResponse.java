/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 项目页 AI 助手读取关联笔记时用的精简表示——只给标题+纯文本正文，不是完整 NoteResponse。 */
public record ProjectNoteSummaryResponse(Long id, String title, String bodyText) {}
