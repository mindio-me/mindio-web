/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

class ClipSearchService extends ApiService {
  getMessages() {
    return this.get('/v1/clip-search/messages')
  }

  sendMessage(content) {
    return this.post('/v1/clip-search/messages', { content })
  }

  saveResult(messageId, resultIndex) {
    return this.post(`/v1/clip-search/messages/${messageId}/results/${resultIndex}/save`)
  }
}

export default ClipSearchService
