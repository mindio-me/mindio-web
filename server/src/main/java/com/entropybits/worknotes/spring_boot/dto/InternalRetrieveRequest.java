/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 独立Agent服务调用 /internal/retrieve 时的请求体。 */
public record InternalRetrieveRequest(String username, String query, int topK) {}
