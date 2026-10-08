/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.dto;

import java.util.List;

public record AiSettingsResponse(List<AiSettingItem> items, boolean cryptoKeyConfigured) {}
