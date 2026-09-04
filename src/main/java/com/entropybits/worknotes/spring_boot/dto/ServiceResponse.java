/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import com.entropybits.worknotes.spring_boot.entity.Service;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务响应 DTO
 */
@Data
public class ServiceResponse {

    private Long id;
    private String name;
    private String description;
    private String detailedDescription;
    private String icon;
    private String category;
    private String features;
    private String pricing;
    private Boolean isActive;
    private Boolean isFeatured;
    private Integer displayOrder;
    private String ownerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ServiceResponse fromEntity(Service service) {
        ServiceResponse response = new ServiceResponse();
        response.setId(service.getId());
        response.setName(service.getName());
        response.setDescription(service.getDescription());
        response.setDetailedDescription(service.getDetailedDescription());
        response.setIcon(service.getIcon());
        response.setCategory(service.getCategory());
        response.setFeatures(service.getFeatures());
        response.setPricing(service.getPricing());
        response.setIsActive(service.getIsActive());
        response.setIsFeatured(service.getIsFeatured());
        response.setDisplayOrder(service.getDisplayOrder());
        response.setOwnerUsername(service.getOwner().getUsername());
        response.setCreatedAt(service.getCreatedAt());
        response.setModifiedAt(service.getModifiedAt());
        return response;
    }
}
