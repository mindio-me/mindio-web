/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

class ClipService extends ApiService {
  // ---- 素材 CRUD ----

  getClips({ keyword, sourceType, tagIds, untagged, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page, size })
    if (keyword) params.append('keyword', keyword)
    if (sourceType) params.append('sourceType', sourceType)
    if (tagIds) tagIds.forEach(id => params.append('tagIds', id))
    if (untagged) params.append('untagged', 'true')
    return this.get(`/v1/clips?${params}`)
  }

  getRecentClips(limit = 5) {
    return this.get(`/v1/clips/recent?limit=${limit}`)
  }

  async getClipById(id) {
    const clip = await this.get(`/v1/clips/${id}`)
    if (clip && clip.content) clip.content = this._fixImageUrls(clip.content)
    return clip
  }

  createClip(data) {
    return this.post('/v1/clips', data)
  }

  updateClip(id, data) {
    return this.put(`/v1/clips/${id}`, data)
  }

  updateClipTitle(id, title) {
    return this.patch(`/v1/clips/${id}/title`, { title })
  }

  getTags(scope) {
    const query = scope ? `?scope=${scope}` : ''
    return this.get(`/v1/tags${query}`)
  }

  addClipTag(clipId, tagId) {
    return this.post(`/v1/clips/${clipId}/tags/${tagId}`)
  }

  removeClipTag(clipId, tagId) {
    return this.delete(`/v1/clips/${clipId}/tags/${tagId}`)
  }

  deleteClip(id) {
    return this.delete(`/v1/clips/${id}`)
  }

  // ---- 导入 ----

  async importFromUrl(url, extractionMode = 'FULL') {
    const draft = await this.post('/v1/clips/import/url', { url, extractionMode })
    if (draft && draft.content) draft.content = this._fixImageUrls(draft.content)
    return draft
  }

  // ---- 笔记关联 ----

  getNoteClips(noteId) {
    return this.get(`/v1/notes/${noteId}/clips`)
  }

  getClipLinkedNotes(clipId) {
    return this.get(`/v1/clips/${clipId}/notes`)
  }

  getNoteClipCount(noteId) {
    return this.get(`/v1/notes/${noteId}/clips/count`).then(data => data.count)
  }

  linkClipToNote(noteId, clipId, { userNote, sortOrder } = {}) {
    return this.post(`/v1/notes/${noteId}/clips/${clipId}`, { userNote, sortOrder })
  }

  unlinkClipFromNote(noteId, clipId) {
    return this.delete(`/v1/notes/${noteId}/clips/${clipId}`)
  }

  updateNoteClipRef(noteId, clipId, { userNote, sortOrder } = {}) {
    return this.patch(`/v1/notes/${noteId}/clips/${clipId}`, { userNote, sortOrder })
  }
  _fixImageUrls(html) {
    const base = (this.$axios.defaults.baseURL || '').replace(/\/$/, '')
    return html.replace(/src="\/uploads\//g, `src="${base}/uploads/`)
  }
}

export default ClipService
