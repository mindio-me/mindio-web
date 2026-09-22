/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AuthResponse;
import com.entropybits.worknotes.spring_boot.dto.DesktopSessionRequest;
import com.entropybits.worknotes.spring_boot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桌面端专属认证控制器
 * 仅在 desktop profile 下装配，web 部署不会暴露这个 bean/接口
 */
@RestController
@RequestMapping("/v1/auth")
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopAuthController {

    private final AuthService authService;

    /**
     * 静默会话：许可证已激活即可换取本地账号 token，无需用户名密码
     */
    @PostMapping("/desktop-session")
    public ResponseEntity<AuthResponse> desktopSession(@Valid @RequestBody(required = false) DesktopSessionRequest request) {
        String email = request != null ? request.getEmail() : null;
        AuthResponse response = authService.desktopSession(email);
        return ResponseEntity.ok(response);
    }
}
