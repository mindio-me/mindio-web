/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ServiceRequest;
import com.entropybits.worknotes.spring_boot.dto.ServiceResponse;
import com.entropybits.worknotes.spring_boot.service.ServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service API Controller
 */
@RestController
@RequestMapping("/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    /**
     * 获取所有启用的服务
     * GET /api/v1/services
     */
    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAllActiveServices() {
        List<ServiceResponse> services = serviceService.getAllActiveServices();
        return ResponseEntity.ok(services);
    }

    /**
     * 获取所有核心服务
     * GET /api/v1/services/featured
     */
    @GetMapping("/featured")
    public ResponseEntity<List<ServiceResponse>> getFeaturedServices() {
        List<ServiceResponse> services = serviceService.getFeaturedServices();
        return ResponseEntity.ok(services);
    }

    /**
     * 根据ID获取服务
     * GET /api/v1/services/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        ServiceResponse service = serviceService.getServiceById(id);
        return ResponseEntity.ok(service);
    }

    /**
     * 创建服务
     * POST /api/v1/services
     */
    @PostMapping
    public ResponseEntity<ServiceResponse> createService(
            @Valid @RequestBody ServiceRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ServiceResponse service = serviceService.createService(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(service);
    }

    /**
     * 更新服务
     * PUT /api/v1/services/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ServiceResponse service = serviceService.updateService(id, request, username);
        return ResponseEntity.ok(service);
    }

    /**
     * 删除服务
     * DELETE /api/v1/services/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(
            @PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        serviceService.deleteService(id, username);
        return ResponseEntity.noContent().build();
    }
}
