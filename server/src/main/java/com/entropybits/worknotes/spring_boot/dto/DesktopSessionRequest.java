/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 桌面端静默会话请求 DTO
 * 首次调用（本地账号尚未创建）时需带上激活邮箱，之后的调用可不传
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesktopSessionRequest {

    @Email(message = "邮箱格式不正确")
    private String email;
}
