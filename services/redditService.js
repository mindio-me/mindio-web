/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default class RedditService {
  constructor(axios) {
    this.axios = axios
  }

  getStatus() {
    return this.axios.$get('/v1/integrations/reddit/status')
  }

  /** Returns { url } for browser redirect */
  getAuthorizeUrl() {
    return this.axios.$get('/v1/integrations/reddit/oauth/authorize-url')
  }

  disconnect() {
    return this.axios.$post('/v1/integrations/reddit/disconnect')
  }

  /** Returns list of { name, title, subscribers } */
  getSubreddits() {
    return this.axios.$get('/v1/integrations/reddit/subreddits')
  }

  publish(noteId, payload) {
    return this.axios.$post(`/v1/integrations/reddit/publish/${noteId}`, payload)
  }

  getLogs(noteId) {
    return this.axios.$get(`/v1/integrations/reddit/logs/${noteId}`)
  }
}
