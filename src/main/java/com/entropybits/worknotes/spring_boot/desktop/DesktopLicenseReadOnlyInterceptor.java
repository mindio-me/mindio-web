/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DesktopLicenseReadOnlyInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> ALLOWED_WRITE_PATHS = List.of(
            "/v1/auth/**",
            "/v1/desktop/license/status",
            "/v1/site-export"
    );

    private final DesktopLicenseService desktopLicenseService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }
        final String requestPath = path;

        if (ALLOWED_WRITE_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestPath))) {
            return true;
        }

        if (desktopLicenseService.isReadOnly()) {
            throw new ForbiddenException("MindIO desktop license is read-only. Reading and exporting local data are still allowed, but creating or editing content is disabled.");
        }

        return true;
    }
}
