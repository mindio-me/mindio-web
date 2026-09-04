/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** /internal/agent-state/{conversationId} 的PUT请求体；stateBlob对Java是不透明数据，原样存取。 */
public record PutAgentStateRequest(String stateBlob) {}
