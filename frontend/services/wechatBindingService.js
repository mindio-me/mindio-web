/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default class WechatBindingService {
  constructor(axios) {
    this.axios = axios
  }

  getStatus() {
    return this.axios.$get('/v1/integrations/wechat/binding/status')
  }

  generateCode() {
    return this.axios.$post('/v1/integrations/wechat/binding/generate-code')
  }

  list() {
    return this.axios.$get('/v1/integrations/wechat/binding')
  }

  unbind(id) {
    return this.axios.delete(`/v1/integrations/wechat/binding/${id}`)
  }
}
