/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import java.time.Instant;

/** /internal/agent-state/{conversationId} 的GET响应；stateBlob为null表示这个会话还没有存过状态。 */
public record AgentStateResponse(String conversationId, String stateBlob, Instant updatedAt) {}
