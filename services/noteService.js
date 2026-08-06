/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/**
 * 笔记服务 - 处理笔记的 API 调用
 */
class NoteService extends ApiService {
  /**
   * 获取当前用户的笔记列表（分页）
   */
  getMyNotes(page = 0, size = 10, sortBy = 'modifiedAt', sortDir = 'DESC') {
    return this.get(`/v1/notes?page=${page}&size=${size}&sortBy=${sortBy}&direction=${sortDir}`)
  }

  /**
   * 获取笔记详情
   */
  getNoteById(id) {
    return this.get(`/v1/notes/${id}`)
  }

  /**
   * 创建新笔记
   */
  createNote(data) {
    return this.post('/v1/notes', data)
  }

  /**
   * 更新笔记
   */
  updateNote(id, data) {
    return this.put(`/v1/notes/${id}`, data)
  }

  /**
   * 删除笔记
   */
  deleteNote(id) {
    return this.delete(`/v1/notes/${id}`)
  }

  /**
   * 全文搜索
   */
  fullTextSearch(keyword, page = 0, size = 10) {
    return this.get(`/v1/notes/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`)
  }

  /**
   * 多条件组合搜索
   */
  advancedSearch(searchRequest, page = 0, size = 10) {
    return this.post(`/v1/notes/search/advanced?page=${page}&size=${size}`, searchRequest)
  }

  /**
   * 获取公开笔记列表
   */
  getPublicNotes(page = 0, size = 10) {
    return this.get(`/v1/notes/public?page=${page}&size=${size}`)
  }

  /**
   * 获取公开笔记详情（无需认证）
   */
  async getPublicNoteById(id) {
    const result = await this.get(`/v1/notes/public/${id}`)
    console.log('[NoteService] getPublicNoteById 返回:', {
      id: result?.id,
      title: result?.title,
      contentType: result?.contentType,
      contentLength: result?.content?.length,
      contentPreview: result?.content?.substring(0, 200)
    })
    return result
  }
}

export default NoteService

