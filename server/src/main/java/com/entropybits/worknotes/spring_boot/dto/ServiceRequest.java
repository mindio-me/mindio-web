/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 服务请求 DTO
 */
@Data
public class ServiceRequest {

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 200, message = "服务名称长度不能超过200个字符")
    private String name;

    @NotBlank(message = "服务描述不能为空")
    private String description;

    private String detailedDescription;

    private String icon;

    private String category;

    private String features;

    private String pricing;

    private Boolean isActive = true;

    private Boolean isFeatured = false;

    private Integer displayOrder = 0;
}
