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
   * Markdown 笔记原地转为 editor.js 块文档；返回 { turnId }，用于撤销/恢复原文
   */
  convertToEditorjs(id) {
    return this.post(`/v1/notes/${id}/convert-to-editorjs`, {}, { suppressErrorToast: true })
  }

  /**
   * 转为 editor.js 文档之前的 Markdown 原文（只读查看）；返回 { markdown, convertedAt }
   */
  getConvertOriginal(id) {
    return this.get(`/v1/notes/${id}/convert-original`, { suppressErrorToast: true })
  }

  /**
   * 导出 editor.js 笔记为 .mindio 备份包；返回 { blob, noteCount, missingAssets }
   */
  async exportNotes({ noteIds = null, all = false, exportedFrom = 'web' } = {}) {
    const response = await this.$axios.post('/v1/notes/export', { noteIds, all, exportedFrom }, {
      responseType: 'blob',
      suppressErrorToast: true
    })
    return {
      blob: response.data,
      noteCount: Number(response.headers['x-mindio-note-count'] || 0),
      missingAssets: Number(response.headers['x-mindio-missing-assets'] || 0)
    }
  }

  /**
   * 上传 .mindio 包做导入预检（不写库）；返回 { importToken, newNotes, conflicts, missingAssets }
   */
  previewImport(file) {
    const formData = new FormData()
    formData.append('file', file)
    return this.post('/v1/notes/import/preview', formData, { suppressErrorToast: true })
  }

  /**
   * 执行导入；overwrite 是选择"用备份覆盖"的 uid 列表。返回 { created, overwritten, kept }
   */
  commitImport(importToken, overwrite) {
    return this.post('/v1/notes/import/commit', { importToken, overwrite }, { suppressErrorToast: true })
  }

  suggestInsertPosition(noteId, content, cursorBlockId) {
    return this.post(`/v1/notes/${noteId}/insert-position`, { content, cursorBlockId })
  }

  insertContent(noteId, markdown, after) {
    return this.post(`/v1/notes/${noteId}/insert-content`, { markdown, after })
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

