/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

/**
 * 解析对外可访问的站点根地址（给爬虫用的绝对链接）。
 */
public final class PublicBaseUrl {

    private PublicBaseUrl() {
    }

    public static String resolve(String frontendBaseUrl, HttpServletRequest req) {
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank() && !isLocal(frontendBaseUrl.trim())) {
            return stripSlash(frontendBaseUrl.trim());
        }
        String fwdHost = firstValue(req.getHeader("X-Forwarded-Host"));
        if (fwdHost != null) {
            String proto = firstValue(req.getHeader("X-Forwarded-Proto"));
            return stripSlash((proto != null ? proto : req.getScheme()) + "://" + fwdHost);
        }
        String host = req.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = req.getServerName() + ":" + req.getServerPort();
        }
        return stripSlash(req.getScheme() + "://" + host.trim());
    }

    private static boolean isLocal(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static String firstValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.split(",")[0].trim();
    }

    private static String stripSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }
}
