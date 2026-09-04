/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/** 落库/返回给前端的附件引用，不含base64原始数据。 */
public record ChatAttachmentRef(String type, String url, String fileName) {}
