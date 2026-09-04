/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import com.entropybits.worknotes.spring_boot.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesktopLicenseReadOnlyInterceptorTest {

    @Mock
    DesktopLicenseService desktopLicenseService;

    private DesktopLicenseReadOnlyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DesktopLicenseReadOnlyInterceptor(desktopLicenseService);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        request.setRequestURI("/api" + uri);
        return request;
    }

    @Test
    void allowsGetRequestsRegardlessOfLicenseState() {
        boolean result = interceptor.preHandle(request("GET", "/v1/notes"), new MockHttpServletResponse(), new Object());
        assertThat(result).isTrue();
    }

    @Test
    void allowsSiteExportWithoutCheckingLicenseState() {
        boolean result = interceptor.preHandle(request("POST", "/v1/site-export"), new MockHttpServletResponse(), new Object());
        assertThat(result).isTrue();
    }

    @Test
    void blocksOtherWritesWhenReadOnly() {
        when(desktopLicenseService.isReadOnly()).thenReturn(true);
        assertThatThrownBy(() ->
                interceptor.preHandle(request("POST", "/v1/notes"), new MockHttpServletResponse(), new Object())
        ).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void allowsWritesWhenNotReadOnly() {
        when(desktopLicenseService.isReadOnly()).thenReturn(false);
        boolean result = interceptor.preHandle(request("POST", "/v1/notes"), new MockHttpServletResponse(), new Object());
        assertThat(result).isTrue();
    }
}
