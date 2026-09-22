/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * Project API 服务
 */
export default class ProjectService extends ApiService {
  /**
   * 获取所有公开项目
   */
  async getPublicProjects() {
    return await this.get('/v1/projects/public')
  }

  /**
   * 获取精选项目
   */
  async getFeaturedProjects() {
    return await this.get('/v1/projects/featured')
  }

  /**
   * 按分类获取项目
   */
  async getProjectsByCategory(category) {
    return await this.get(`/v1/projects/category/${category}`)
  }

  /**
   * 根据ID获取项目
   */
  async getProjectById(id) {
    return await this.get(`/v1/projects/${id}`)
  }

  /**
   * 获取当前用户的项目
   */
  async getMyProjects() {
    return await this.get('/v1/projects/my')
  }

  /**
   * 创建项目
   */
  async createProject(data) {
    return await this.post('/v1/projects', data)
  }

  /**
   * 更新项目
   */
  async updateProject(id, data) {
    return await this.put(`/v1/projects/${id}`, data)
  }

  /**
   * 删除项目
   */
  async deleteProject(id) {
    return await this.delete(`/v1/projects/${id}`)
  }
}
