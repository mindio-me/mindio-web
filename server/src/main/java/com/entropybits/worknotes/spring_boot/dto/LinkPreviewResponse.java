/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 响应格式对接 @editorjs/link 官方客户端的约定（非自定义）：
 * https://github.com/editor-js/link 的 fetchLinkData()/onFetch() 只认
 * { success, link, meta: { title, description, image: { url } } } 这个形状。
 */
@Data
@Builder
public class LinkPreviewResponse {

    private boolean success;
    private String link;
    private Meta meta;

    @Data
    @Builder
    public static class Meta {
        private String title;
        private String description;
        private Image image;
    }

    @Data
    @Builder
    public static class Image {
        private String url;
    }
}
