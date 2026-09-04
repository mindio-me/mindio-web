/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.ContentChunk;

public record RetrievedChunk(ContentChunk.SourceType sourceType, Long sourceId, String chunkText, double score) {}
