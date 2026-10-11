/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 一轮对话里被 AI 改过的笔记；modifiedSince = 改完之后用户又改过（撤销会丢掉这些修改） */
public record EditedNoteRef(Long noteId, String title, boolean modifiedSince) {}
