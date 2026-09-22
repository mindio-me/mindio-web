/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SendChatMessageRequest {

    // 不再用 @NotBlank：允许"只发一张图不带文字"这种用法。
    private String content;

    private Long currentNoteId;

    // 客户端也有10MB/单附件的限制（见AttachmentPayload.base64Data上的@Size），这里的数量上限
    // 只是为了不让恶意客户端绕开前端、一次塞几十个大附件把内存和大模型调用成本都打爆。
    @Valid
    @Size(max = 10, message = "单条消息最多附带10个附件")
    private List<AttachmentPayload> attachments;
}
