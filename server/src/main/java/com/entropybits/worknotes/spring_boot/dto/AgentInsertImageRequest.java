/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** POST /internal/documents/notes/{noteId}/images 的请求体 */
public record AgentInsertImageRequest(AgentImageSource source, String caption, String anchorText) {}
