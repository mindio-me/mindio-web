/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicBaseUrlTest {

    private static MockHttpServletRequest req() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setScheme("http");
        r.setServerName("internal-host");
        r.setServerPort(8080);
        r.addHeader("Host", "internal-host:8080");
        return r;
    }

    @Test
    void usesFrontendBaseUrlWhenPublic() {
        assertThat(PublicBaseUrl.resolve("https://demo.mindio.me", req())).isEqualTo("https://demo.mindio.me");
    }

    @Test
    void ignoresLocalhostFrontendBaseUrl() {
        MockHttpServletRequest r = req();
        r.addHeader("X-Forwarded-Proto", "https");
        r.addHeader("X-Forwarded-Host", "demo.mindio.me");
        assertThat(PublicBaseUrl.resolve("http://localhost:10822", r)).isEqualTo("https://demo.mindio.me");
        assertThat(PublicBaseUrl.resolve("http://127.0.0.1:10822", r)).isEqualTo("https://demo.mindio.me");
    }

    @Test
    void usesForwardedHeaders() {
        MockHttpServletRequest r = req();
        r.addHeader("X-Forwarded-Proto", "https");
        r.addHeader("X-Forwarded-Host", "a.example.com");
        assertThat(PublicBaseUrl.resolve("", r)).isEqualTo("https://a.example.com");
    }

    @Test
    void fallsBackToHostHeader() {
        assertThat(PublicBaseUrl.resolve(null, req())).isEqualTo("http://internal-host:8080");
    }

    @Test
    void stripsTrailingSlash() {
        assertThat(PublicBaseUrl.resolve("https://demo.mindio.me/", req())).isEqualTo("https://demo.mindio.me");
    }
}
