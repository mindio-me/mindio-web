/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatResumeRequest {
    // resume总是针对一个已存在的会话（confirm_request一定发生在某轮已经落过用户消息的
    // 会话里），不会像sendMessage那样有懒创建语义，所以这里是必填。
    @NotNull
    private Long conversationId;
    @NotBlank
    private String proposalId;
    @NotBlank
    private String decision;
}
