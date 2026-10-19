/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ChangePasswordRequest;
import com.entropybits.worknotes.spring_boot.security.CustomUserDetailsService;
import com.entropybits.worknotes.spring_boot.service.AuthService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final AuthController controller = new AuthController(authService, userDetailsService);

    @Test
    void meWithoutAuthenticatedUserReturns401() {
        assertThat(controller.getCurrentUser(null).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void changePasswordWithoutAuthenticatedUserReturns401() {
        assertThat(controller.changePassword(null, new ChangePasswordRequest()).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(authService);
    }
}
