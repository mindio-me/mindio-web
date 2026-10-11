/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import java.util.Map;

/** 图片编辑器的输出：改好的正文 + 推给前端同步编辑器用的事件载荷（op/blockId 等） */
record AgentImageEditResult(String content, Map<String, Object> event) {}
