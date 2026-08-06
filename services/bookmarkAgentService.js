/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * AI 聚类归档 / 时间线回顾生成服务
 */
class BookmarkAgentService extends ApiService {
  generate(type) {
    return this.post('/v1/bookmark-agent/generate', null, { params: { type } })
  }

  async getCurrentJob(type) {
    try {
      return await this.get('/v1/bookmark-agent/jobs/current', { params: { type } })
    } catch (error) {
      if (error?.response?.status === 404) return null
      throw error
    }
  }
}

export default BookmarkAgentService
