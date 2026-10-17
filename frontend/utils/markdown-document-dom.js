/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Markdown 文档容器上的交互：页内 # 锚点跳转、图片加载失败降级。由 directives/md-document.js 挂到容器上。
 * 不依赖 Vue，方便用 jsdom 单测。
 */

const ID_PREFIX = 'user-content-' // 与 markdown-document.js 里 DOMPurify 的 SANITIZE_NAMED_PROPS 前缀一致

function safeDecode(value) {
  try {
    return decodeURIComponent(value)
  } catch (e) {
    return value
  }
}

/**
 * 按 #hash 在容器内找目标元素：先找带 user-content- 前缀的 id，再找原样 id
 */
export function findAnchorTarget(container, hash) {
  const id = safeDecode(String(hash || '').replace(/^#/, ''))
  if (!id) return null
  const nodes = Array.from(container.querySelectorAll('[id]'))
  return nodes.find(n => n.id === ID_PREFIX + id) || nodes.find(n => n.id === id) || null
}

/**
 * 点击容器内的 # 链接：在容器内滚动到目标，不改路由（Nuxt 路由处理 hash 会跳页或无效）。
 * @returns {boolean} 是否处理了这次点击
 */
export function handleAnchorClick(container, event) {
  const link = event.target && event.target.closest ? event.target.closest('a') : null
  if (!link || !container.contains(link)) return false
  const href = link.getAttribute('href') || ''
  if (!href.startsWith('#')) return false
  event.preventDefault()
  const target = findAnchorTarget(container, href)
  if (target) target.scrollIntoView({ block: 'start' })
  return true
}

/**
 * 图片加载失败：隐藏图片，在后面插入提示文案。本地图片（有 data-md-local）提示"未随导入上传"，其余提示"加载失败"。
 * 文案用 textContent 插入，不解析 HTML。
 * @param {Event} event - 容器上捕获阶段收到的 error 事件
 * @param {Function} t - i18n 翻译函数 (key, params) => string
 * @returns {boolean} 是否处理了这次事件
 */
export function handleImageError(event, t) {
  const img = event.target
  if (!img || img.tagName !== 'IMG' || img.getAttribute('data-md-fallback')) return false
  img.setAttribute('data-md-fallback', '1')
  img.style.display = 'none'
  const local = img.getAttribute('data-md-local')
  const fallback = img.ownerDocument.createElement('span')
  fallback.className = 'md-image-fallback'
  fallback.textContent = local
    ? t('markdownDocument.imageNotImported', { name: safeDecode(local) })
    : t('markdownDocument.imageLoadFailed', { name: img.getAttribute('alt') || safeDecode(img.getAttribute('src') || '') })
  img.insertAdjacentElement('afterend', fallback)
  return true
}
