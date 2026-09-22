/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AuthResponse;
import com.entropybits.worknotes.spring_boot.dto.ChangePasswordRequest;
import com.entropybits.worknotes.spring_boot.dto.LoginRequest;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AuthenticationManager authenticationManager;
    @Mock Authentication authentication;

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, jwtUtil, authenticationManager);
    }

    @Test
    void login_carriesMustChangePasswordFlagFromUser() {
        AuthService service = service();
        User user = User.builder()
                .id(1L)
                .username("admin")
                .password("hashed")
                .role("ADMIN")
                .mustChangePassword(true)
                .build();

        UserDetails principal = org.springframework.security.core.userdetails.User.builder()
                .username("admin")
                .password("hashed")
                .authorities("ROLE_ADMIN")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtUtil.generateToken(principal)).thenReturn("token");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        AuthResponse response = service.login(new LoginRequest("admin", "admin123"));

        assertThat(response.isMustChangePassword()).isTrue();
    }

    @Test
    void changePassword_clearsMustChangePasswordFlag() {
        AuthService service = service();
        User user = User.builder()
                .id(1L)
                .username("admin")
                .password("oldHash")
                .role("ADMIN")
                .mustChangePassword(true)
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "oldHash")).thenReturn(true);
        when(passwordEncoder.matches("newpass123", "oldHash")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");

        service.changePassword("admin", new ChangePasswordRequest("admin123", "newpass123", "newpass123"));

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPassword()).isEqualTo("newHash");
    }
}
