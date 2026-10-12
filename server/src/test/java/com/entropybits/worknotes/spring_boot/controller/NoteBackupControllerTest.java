/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteBackupControllerTest {

    @Test
    void originOfDirectRequestUsesSchemeHostAndPort() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/notes/export");
        req.setScheme("http");
        req.setServerName("localhost");
        req.setServerPort(8080);

        assertEquals("http://localhost:8080", NoteBackupController.originOf(req));
    }

    @Test
    void originOfOmitsDefaultPortForDirectRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/notes/export");
        req.setScheme("https");
        req.setServerName("example.com");
        req.setServerPort(443);

        assertEquals("https://example.com", NoteBackupController.originOf(req));
    }

    @Test
    void originOfPrefersForwardedProtoAndHost() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/notes/export");
        req.setScheme("http");
        req.setServerName("127.0.0.1");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "public.example.com");

        assertEquals("https://public.example.com", NoteBackupController.originOf(req));
    }

    @Test
    void originOfTakesFirstForwardedValueAndStripsDefaultPort() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/notes/export");
        req.setScheme("http");
        req.setServerName("127.0.0.1");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https, http");
        req.addHeader("X-Forwarded-Host", "public.example.com:443, internal:8080");

        assertEquals("https://public.example.com", NoteBackupController.originOf(req));
    }

    @Test
    void originOfKeepsNonDefaultForwardedPort() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/notes/export");
        req.setScheme("http");
        req.setServerName("127.0.0.1");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("X-Forwarded-Host", "public.example.com:8443");

        assertEquals("https://public.example.com:8443", NoteBackupController.originOf(req));
    }
}
