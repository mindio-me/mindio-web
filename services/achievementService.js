/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * Achievement API 服务
 */
export default class AchievementService extends ApiService {
  /**
   * 获取所有公开展示的成就
   */
  async getPublicAchievements() {
    return await this.get('/v1/achievements')
  }

  /**
   * 获取当前用户的全部成就（含未公开的），供管理页使用
   */
  async getMyAchievements() {
    return await this.get('/v1/achievements/my')
  }

  /**
   * 根据ID获取成就
   */
  async getAchievementById(id) {
    return await this.get(`/v1/achievements/${id}`)
  }

  /**
   * 创建成就
   */
  async createAchievement(data) {
    return await this.post('/v1/achievements', data)
  }

  /**
   * 更新成就
   */
  async updateAchievement(id, data) {
    return await this.put(`/v1/achievements/${id}`, data)
  }

  /**
   * 删除成就
   */
  async deleteAchievement(id) {
    return await this.delete(`/v1/achievements/${id}`)
  }
}
