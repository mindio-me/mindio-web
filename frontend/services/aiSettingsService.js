/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default class AiSettingsService {
  constructor(axios) {
    this.axios = axios
  }

  get() {
    return this.axios.$get('/v1/settings/ai')
  }

  // changes: { 配置键: 值 }，空字符串 = 清除（回到环境变量）
  update(changes) {
    return this.axios.$put('/v1/settings/ai', changes)
  }
}
