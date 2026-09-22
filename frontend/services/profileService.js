/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * 个人信息服务 - 处理个人资料的 API 调用
 */
class ProfileService extends ApiService {
  /**
   * 获取当前用户的个人资料
   */
  getCurrentUserProfile() {
    return this.get('/v1/profiles/me')
  }

  /**
   * 根据用户名获取个人资料（公开）
   */
  getProfileByUsername(username) {
    return this.get(`/v1/profiles/${username}`)
  }

  /**
   * 创建或更新当前用户的个人资料
   */
  updateProfile(profileData) {
    return this.put('/v1/profiles/me', profileData)
  }

  /**
   * 获取站点 Owner 的公开资料（无需认证）
   */
  getOwnerProfile() {
    return this.get('/v1/profiles/owner')
  }

  /**
   * 修改密码
   */
  changePassword(passwordData) {
    return this.put('/v1/auth/change-password', passwordData)
  }
}

export default ProfileService

