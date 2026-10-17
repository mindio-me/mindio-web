/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * v-md-document：挂在整篇 Markdown 笔记的外层容器（div.md-document）上，
 * 处理页内 # 锚点跳转和图片加载失败降级。具体逻辑见 utils/markdown-document-dom.js
 */

import { handleAnchorClick, handleImageError } from '~/utils/markdown-document-dom'

export default {
  bind(el, binding, vnode) {
    const t = (key, params) => (vnode.context && vnode.context.$t ? vnode.context.$t(key, params) : key)
    el.__mdDocumentClick = event => handleAnchorClick(el, event)
    el.__mdDocumentError = event => handleImageError(event, t)
    el.addEventListener('click', el.__mdDocumentClick)
    el.addEventListener('error', el.__mdDocumentError, true) // img 的 error 不冒泡，只能在捕获阶段拿到
  },
  unbind(el) {
    el.removeEventListener('click', el.__mdDocumentClick)
    el.removeEventListener('error', el.__mdDocumentError, true)
    delete el.__mdDocumentClick
    delete el.__mdDocumentError
  }
}
