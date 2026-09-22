/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ServiceRequest;
import com.entropybits.worknotes.spring_boot.dto.ServiceResponse;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.ServiceRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service 业务逻辑层
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllActiveServices() {
        return serviceRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(ServiceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getFeaturedServices() {
        return serviceRepository.findByIsFeaturedTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(ServiceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(Long id) {
        com.entropybits.worknotes.spring_boot.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务不存在，ID: " + id));
        return ServiceResponse.fromEntity(service);
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        com.entropybits.worknotes.spring_boot.entity.Service service = com.entropybits.worknotes.spring_boot.entity.Service.builder()
                .name(request.getName())
                .description(request.getDescription())
                .detailedDescription(request.getDetailedDescription())
                .icon(request.getIcon())
                .category(request.getCategory())
                .features(request.getFeatures())
                .pricing(request.getPricing())
                .isActive(request.getIsActive())
                .isFeatured(request.getIsFeatured())
                .displayOrder(request.getDisplayOrder())
                .owner(user)
                .build();

        com.entropybits.worknotes.spring_boot.entity.Service savedService = serviceRepository.save(service);
        return ServiceResponse.fromEntity(savedService);
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceRequest request, String username) {
        com.entropybits.worknotes.spring_boot.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务不存在，ID: " + id));

        if (!service.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("没有权限修改此服务");
        }

        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setDetailedDescription(request.getDetailedDescription());
        service.setIcon(request.getIcon());
        service.setCategory(request.getCategory());
        service.setFeatures(request.getFeatures());
        service.setPricing(request.getPricing());
        service.setIsActive(request.getIsActive());
        service.setIsFeatured(request.getIsFeatured());
        service.setDisplayOrder(request.getDisplayOrder());

        com.entropybits.worknotes.spring_boot.entity.Service updatedService = serviceRepository.save(service);
        return ServiceResponse.fromEntity(updatedService);
    }

    @Transactional
    public void deleteService(Long id, String username) {
        com.entropybits.worknotes.spring_boot.entity.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("服务不存在，ID: " + id));

        if (!service.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("没有权限删除此服务");
        }

        serviceRepository.delete(service);
    }
}
