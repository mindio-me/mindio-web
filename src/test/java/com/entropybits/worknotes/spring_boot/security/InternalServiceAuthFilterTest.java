/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalServiceAuthFilterTest {

    private static final String CORRECT_TOKEN = "correct-token";

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    InternalServiceAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalServiceAuthFilter();
        ReflectionTestUtils.setField(filter, "internalToken", CORRECT_TOKEN);
    }

    @Test
    void nonInternalPath_isNotAffectedAndPassesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/v1/notes");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void internalPath_missingToken_rejectedWith401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/retrieve");
        when(request.getHeader("X-Internal-Token")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void internalPath_wrongToken_rejectedWith401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/agent-state/alice");
        when(request.getHeader("X-Internal-Token")).thenReturn("wrong-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void internalPath_correctToken_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/retrieve");
        when(request.getHeader("X-Internal-Token")).thenReturn(CORRECT_TOKEN);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt());
    }
}
