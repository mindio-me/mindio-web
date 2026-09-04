/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 只对 /internal/** 路径生效的鉴权：这些接口是给独立的Python/LangGraph Agent服务回调用的
 * （检索、agent状态读写），不走JWT体系，改用一个约定的共享密钥。SecurityConfig 里
 * /internal/** 被设为 permitAll（Spring Security的授权检查不要求这些路径有Authentication），
 * 真正的准入把关就是这个过滤器：请求头不带正确的密钥直接401，不继续往下传。
 * 其他路径完全不受影响，原样放行给后续过滤器链（JWT鉴权走 JwtAuthenticationFilter，与本过滤器无关）。
 */
@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(InternalServiceAuthFilter.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    @Value("${agent.internal-token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String presentedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(presentedToken) || !presentedToken.equals(internalToken)) {
            logger.warn("rejected internal request to {} with missing/invalid {}", request.getRequestURI(), INTERNAL_TOKEN_HEADER);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
