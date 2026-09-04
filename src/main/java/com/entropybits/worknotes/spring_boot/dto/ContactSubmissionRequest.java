/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 联系表单提交请求 DTO
 */
@Data
public class ContactSubmissionRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 200, message = "姓名长度不能超过200个字符")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 200, message = "邮箱长度不能超过200个字符")
    private String email;

    @Size(max = 200, message = "组织名称长度不能超过200个字符")
    private String organization;

    @NotBlank(message = "项目摘要不能为空")
    private String projectSummary;
}





