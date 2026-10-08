/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.config;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadLimitsConfigTest {

    private static final long MB = 1024L * 1024L;

    @Test
    void multipartLimitFollowsTheLargerAppLimit() {
        // 业务层的上限才是真正的规则，Spring 这一层只要不比它小就行，否则大文件会在 Spring 这里先被拦成 500
        MultipartConfigElement element = new UploadLimitsConfig(500, 20).multipartConfigElement();

        assertThat(element.getMaxFileSize()).isEqualTo(500 * MB);
        assertThat(element.getMaxRequestSize()).isGreaterThan(500 * MB);
    }

    @Test
    void multipartLimitUsesImageLimitWhenItIsTheLargerOne() {
        MultipartConfigElement element = new UploadLimitsConfig(10, 30).multipartConfigElement();

        assertThat(element.getMaxFileSize()).isEqualTo(30 * MB);
    }
}
