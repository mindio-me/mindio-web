/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

class BookmarkImportService extends ApiService {
  async parse(file) {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.$axios.post('/v1/bookmark-import/parse', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  }

  getJob(jobId) {
    return this.get(`/v1/bookmark-import/jobs/${jobId}`)
  }

  confirmGroup(jobId, category, decision) {
    return this.post(`/v1/bookmark-import/jobs/${jobId}/confirm-group`, { category, decision })
  }

  confirmItems(jobId, itemIds, decision) {
    return this.post(`/v1/bookmark-import/jobs/${jobId}/confirm-items`, { itemIds, decision })
  }

  merge(jobId) {
    return this.post(`/v1/bookmark-import/jobs/${jobId}/merge`)
  }
}

export default BookmarkImportService
