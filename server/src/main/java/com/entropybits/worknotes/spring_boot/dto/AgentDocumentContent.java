/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/**
 * agent 读取单篇笔记/收藏的全文（纯文本，不截断；长文的上下文卸载由 agent 侧负责）。
 * imageCount 让 agent 知道正文里还有图可以用 view_image 看——纯文本里图片只剩说明/OCR 文字。
 */
public record AgentDocumentContent(String type, Long id, String title, String bodyText, int imageCount) {}
