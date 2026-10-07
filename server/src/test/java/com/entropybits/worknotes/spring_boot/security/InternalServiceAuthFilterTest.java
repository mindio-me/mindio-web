/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalServiceAuthFilterTest {

    private static final String CORRECT_TOKEN = "correct-token";
    // 和 application.yml 的 server.servlet.context-path 一致：真实请求的 requestURI 带这个前缀
    private static final String CONTEXT_PATH = "/api";

    @Mock FilterChain filterChain;

    InternalServiceAuthFilter filter;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new InternalServiceAuthFilter();
        ReflectionTestUtils.setField(filter, "internalToken", CORRECT_TOKEN);
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest request(String pathWithinContext, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + pathWithinContext);
        request.setContextPath(CONTEXT_PATH);
        request.setServletPath(pathWithinContext);
        if (token != null) request.addHeader("X-Internal-Token", token);
        return request;
    }

    @Test
    void nonInternalPath_isNotAffectedAndPassesThrough() throws Exception {
        MockHttpServletRequest request = request("/v1/notes", null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void internalPath_missingToken_rejectedWith401() throws Exception {
        MockHttpServletRequest request = request("/internal/retrieve", null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void internalPath_wrongToken_rejectedWith401() throws Exception {
        MockHttpServletRequest request = request("/internal/agent-state/alice", "wrong-token");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void internalPath_correctToken_passesThrough() throws Exception {
        MockHttpServletRequest request = request("/internal/retrieve", CORRECT_TOKEN);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void pathThatOnlyStartsWithInternalWord_isNotTreatedAsInternal() throws Exception {
        MockHttpServletRequest request = request("/internalized/notes", null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
