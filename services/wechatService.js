/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default class WechatService {
  constructor(axios) {
    this.axios = axios
  }

  getStatus() {
    return this.axios.$get('/v1/integrations/wechat/status')
  }

  pushDraft(noteId, payload) {
    return this.axios.$post(`/v1/integrations/wechat/draft/${noteId}`, payload)
  }

  publish(noteId, payload) {
    return this.axios.$post(`/v1/integrations/wechat/publish/${noteId}`, payload)
  }

  getLogs(noteId) {
    return this.axios.$get(`/v1/integrations/wechat/logs/${noteId}`)
  }

  uploadCoverImage(file) {
    const form = new FormData()
    form.append('file', file)
    return this.axios.$post('/v1/integrations/wechat/upload-image', form)
  }
}
