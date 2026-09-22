/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.config;

import com.entropybits.worknotes.spring_boot.security.CustomUserDetailsService;
import com.entropybits.worknotes.spring_boot.security.InternalServiceAuthFilter;
import com.entropybits.worknotes.spring_boot.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalServiceAuthFilter internalServiceAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    @Value("${worknotes.frontend.base-url:http://localhost:10822}")
    private String frontendBaseUrl;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（使用 JWT 时不需要）
                .csrf(AbstractHttpConfigurer::disable)

                // H2 Console 需要 sameOrigin iframe，仅在 desktop profile（h2 console 启用时）放宽
                .headers(headers -> {
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(frame -> frame.sameOrigin());
                    }
                })

                // 配置 CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 统一未登录返回 401（避免前端看到 403 误以为无权限）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 允许 OPTIONS 预检请求（CORS）
                        .requestMatchers("OPTIONS").permitAll()
                        // 允许认证接口无需授权
                        .requestMatchers("/v1/auth/**").permitAll()
                        // Desktop runtime license status is needed before normal product actions.
                        .requestMatchers("/v1/desktop/license/status").permitAll()
                        // 允许飞书 OAuth 回调（浏览器跳转回调时不带 JWT）
                        .requestMatchers("/v1/integrations/feishu/oauth/callback").permitAll()
                        // 允许微信消息回调（微信服务器直接调用，签名校验在 Controller 内完成）
                        .requestMatchers("/v1/integrations/wechat/callback").permitAll()
                        // 允许扫码上传会话（基于 token 校验）
                        .requestMatchers("/v1/upload/sessions/**").permitAll()
                        // 允许访问上传的静态文件
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/uploads/**").permitAll()
                        // 允许访问公开笔记（使用更明确的路径匹配）
                        .requestMatchers("/v1/notes/public").permitAll()
                        .requestMatchers("/v1/notes/public/**").permitAll()
                        // 允许访问公开项目
                        .requestMatchers("/v1/projects/public", "/v1/projects/featured", "/v1/projects/category/**").permitAll()
                        // 允许访问服务列表（仅公开列表接口，写操作走同路径的 POST/PUT/DELETE，需要登录）
                        .requestMatchers(HttpMethod.GET, "/v1/services", "/v1/services/featured", "/v1/services/*").permitAll()
                        // 允许访问成就列表（仅公开列表接口，/my 和 /{id} 都需要登录，故不用通配符）
                        .requestMatchers(HttpMethod.GET, "/v1/achievements").permitAll()
                        // 允许访问资源列表
                        .requestMatchers("/v1/resources", "/v1/resources/category/**", "/v1/resources/*").permitAll()
                        // 允许访问公开个人资料
                        .requestMatchers("/v1/profiles/*").permitAll()
                        // 允许联系表单提交（公开接口）
                        .requestMatchers("/v1/contact/**").permitAll()
                        // 允许 H2 Console（desktop 开发调试用）
                        .requestMatchers("/h2-console/**").permitAll()
                        // 允许 Actuator 健康检查（可选）
                        .requestMatchers("/actuator/health").permitAll()
                        // /internal/** 是给独立Agent服务回调用的内部接口，不走JWT——鉴权
                        // 由 InternalServiceAuthFilter 负责校验共享密钥，这里只是放行给它处理
                        .requestMatchers("/internal/**").permitAll()
                        // 允许访问 Swagger 文档
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )

                // 配置无状态会话
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 配置认证提供者
                .authenticationProvider(authenticationProvider())

                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 添加内部服务鉴权过滤器（只对 /internal/** 生效，见该过滤器实现）
                .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*:*",
                frontendBaseUrl
        ));

        // 允许的 HTTP 方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许发送凭证
        configuration.setAllowCredentials(true);

        // 预检请求的有效期（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * 认证提供者
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
