/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 上传大小上限只有一个旋钮：worknotes.upload.max-file-size-mb / max-image-size-mb（UploadServiceImpl 按它们校验）。
 * Spring 的 multipart 上限由这里跟着算出来，保证永远不比业务上限小——以前两边各写一个数，
 * 改了一边忘了另一边，大文件就会在 Spring 这层被拦掉。
 * 注意：前面如果有 nginx，还要把 client_max_body_size 调到不小于这个值（见 deploy/DEV.md）。
 */
@Configuration
public class UploadLimitsConfig {

    /** 表单里除了文件本身还有 model/pid 等字段，请求总大小留一点余量 */
    private static final long REQUEST_OVERHEAD_MB = 10;

    private final int maxFileSizeMb;
    private final int maxImageSizeMb;

    public UploadLimitsConfig(@Value("${worknotes.upload.max-file-size-mb:500}") int maxFileSizeMb,
                              @Value("${worknotes.upload.max-image-size-mb:20}") int maxImageSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
        this.maxImageSizeMb = maxImageSizeMb;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long largest = Math.max(maxFileSizeMb, maxImageSizeMb);
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(largest));
        factory.setMaxRequestSize(DataSize.ofMegabytes(largest + REQUEST_OVERHEAD_MB));
        factory.setFileSizeThreshold(DataSize.ofMegabytes(10));
        return factory.createMultipartConfig();
    }
}
