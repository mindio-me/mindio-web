/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * 标签服务 - 处理标签的 API 调用
 */
class TagService extends ApiService {
  /**
   * 获取当前用户的所有标签
   */
  getUserTags() {
    return this.get('/v1/tags')
  }

  /**
   * 根据 ID 获取标签
   */
  getTagById(id) {
    return this.get(`/v1/tags/${id}`)
  }

  /**
   * 创建新标签
   */
  createTag(data) {
    return this.post('/v1/tags', data)
  }

  /**
   * 更新标签
   */
  updateTag(id, data) {
    return this.put(`/v1/tags/${id}`, data)
  }

  /**
   * 删除标签
   */
  deleteTag(id) {
    return this.delete(`/v1/tags/${id}`)
  }
}

export default TagService

