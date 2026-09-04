/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiteExportRequest {

    @NotBlank(message = "导出目录不能为空")
    private String targetPath;

    @NotBlank(message = "templateId 不能为空")
    private String templateId;

    // 可选：导出时桌面版当前使用的语言（zh-CN/en）。不传时 SiteExportService
    // 按 zh-CN 处理。前端在 nuxt-frontend/pages/workspace/settings/index.vue
    // 调用导出接口时传入当前 this.$i18n.locale。
    private String locale;
}
