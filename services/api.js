/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * API 服务基础类
 */
export default class ApiService {
  constructor($axios) {
    this.$axios = $axios
  }

  /**
   * GET 请求
   */
  async get(url, config = {}) {
    try {
      const response = await this.$axios.get(url, config)
      return response.data
    } catch (error) {
      throw error
    }
  }

  /**
   * POST 请求
   */
  async post(url, data = {}, config = {}) {
    try {
      const response = await this.$axios.post(url, data, config)
      return response.data
    } catch (error) {
      throw error
    }
  }

  /**
   * PUT 请求
   */
  async put(url, data = {}, config = {}) {
    try {
      const response = await this.$axios.put(url, data, config)
      return response.data
    } catch (error) {
      throw error
    }
  }

  /**
   * PATCH 请求
   */
  async patch(url, data = {}, config = {}) {
    try {
      const response = await this.$axios.patch(url, data, config)
      return response.data
    } catch (error) {
      throw error
    }
  }

  /**
   * DELETE 请求
   */
  async delete(url, config = {}) {
    try {
      const response = await this.$axios.delete(url, config)
      return response.data
    } catch (error) {
      throw error
    }
  }
}
