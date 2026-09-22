/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AuthResponse;
import com.entropybits.worknotes.spring_boot.dto.ChangePasswordRequest;
import com.entropybits.worknotes.spring_boot.dto.LoginRequest;
import com.entropybits.worknotes.spring_boot.dto.RegisterRequest;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否已存在
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 创建新用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role("USER")
                .build();

        User savedUser = userRepository.save(user);

        // 生成 JWT
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(savedUser.getUsername())
                .password(savedUser.getPassword())
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    /**
     * 用户登录
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 验证用户凭证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // 生成 JWT
        String token = jwtUtil.generateToken(userDetails);

        // 获取用户信息
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        // 获取当前用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证当前密码是否正确
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("当前密码不正确");
        }

        // 验证新密码和确认密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("新密码与确认密码不一致");
        }

        // 验证新密码不能与当前密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * 桌面端静默会话
     * 桌面版每台机器的本地数据库最多只有一个用户（许可证持有人），
     * 已有则直接签发新 token，没有则用激活邮箱创建后再签发，
     * 全程不经过用户名/密码登录页
     */
    @Transactional
    public AuthResponse desktopSession(String email) {
        User user = userRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> createDesktopUser(email));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    private User createDesktopUser(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("首次创建桌面本地账号需要激活邮箱");
        }

        String username = email.length() <= 50 ? email : email.substring(0, 50);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .email(email)
                .role("USER")
                .build();

        return userRepository.save(user);
    }
}
