/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerConflictTest {

    @Test
    void conflictMapsTo409WithMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/notes/1/audio-transcriptions/b/utterances");
        ResponseEntity<ErrorResponse> res = new GlobalExceptionHandler()
                .handleConflictException(new ConflictException("录音正在处理"), request);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getMessage()).isEqualTo("录音正在处理");
    }
}
