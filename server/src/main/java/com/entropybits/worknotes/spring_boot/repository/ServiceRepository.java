/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.Service;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Service Repository
 */
@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    /**
     * 查找用户的所有服务
     */
    List<Service> findByOwnerOrderByDisplayOrderAsc(User owner);

    /**
     * 查找所有启用的服务
     */
    List<Service> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * 查找所有核心服务
     */
    List<Service> findByIsFeaturedTrueAndIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * 按分类查找启用的服务
     */
    List<Service> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);

    /**
     * 查找用户的启用服务数量
     */
    long countByOwnerAndIsActiveTrue(User owner);
}
