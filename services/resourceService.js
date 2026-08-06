/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * Resource API 服务
 */
export default class ResourceService extends ApiService {
  /**
   * 获取所有公开资源
   */
  async getAllResources() {
    return await this.get('/v1/resources')
  }

  /**
   * 按分类获取资源
   */
  async getResourcesByCategory(category) {
    return await this.get(`/v1/resources/category/${category}`)
  }

  /**
   * 根据ID获取资源
   */
  async getResourceById(id) {
    return await this.get(`/v1/resources/${id}`)
  }

  /**
   * 创建资源
   */
  async createResource(data) {
    return await this.post('/v1/resources', data)
  }

  /**
   * 更新资源
   */
  async updateResource(id, data) {
    return await this.put(`/v1/resources/${id}`, data)
  }

  /**
   * 删除资源
   */
  async deleteResource(id) {
    return await this.delete(`/v1/resources/${id}`)
  }
}
