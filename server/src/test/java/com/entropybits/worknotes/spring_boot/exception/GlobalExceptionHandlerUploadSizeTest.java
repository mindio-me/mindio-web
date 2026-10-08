/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerUploadSizeTest {

    @Test
    void oversizedMultipartReturns413WithTheLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/upload/file");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleMaxUploadSize(new MaxUploadSizeExceededException(500L * 1024 * 1024), request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getMessage()).contains("500 MB");
    }
}
