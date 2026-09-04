/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.Size;

/**
 * 前端提交的一个聊天附件：type 是 "image" 或 "document"；base64Data 是给大模型用的原始数据；
 * url/fileName 是前端提前调用 /v1/upload/file 或 /v1/upload/image 拿到的持久化引用，
 * 用于历史记录展示，后端不重复上传。
 */
public record AttachmentPayload(
        String type,
        String mimeType,
        // 前端已经拦截超过10MB的原始文件，这里按base64膨胀后的长度（~4/3倍，留了余量）再校验一遍，
        // 防止绕开前端直接调接口把内存和大模型调用成本打爆。
        @Size(max = 14_000_000, message = "单个附件不能超过10MB") String base64Data,
        String url,
        String fileName
) {}
