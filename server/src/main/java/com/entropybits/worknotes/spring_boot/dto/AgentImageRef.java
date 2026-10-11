/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 笔记/收藏里的一张图片：index 是从上到下的序号（从 1 开始），caption 是图片说明或 alt 文字。 */
public record AgentImageRef(int index, String url, String caption) {}
