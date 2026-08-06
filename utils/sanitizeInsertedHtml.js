/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 清理外部 HTML 文件内容后再插入富文本正文：
// 去掉 <style>/<script> 等标签和内联 style/on* 属性，避免原网页的
// 全局样式（如 position: fixed 吸顶导航）和事件脚本污染编辑器所在的页面。
const DISALLOWED_TAGS = ['script', 'style', 'link', 'meta', 'title', 'base', 'iframe', 'object', 'embed']

export function sanitizeInsertedHtml(html) {
  if (!html) return ''

  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  DISALLOWED_TAGS.forEach((tag) => {
    doc.querySelectorAll(tag).forEach((el) => el.remove())
  })

  doc.querySelectorAll('*').forEach((el) => {
    el.removeAttribute('style')
    Array.from(el.attributes).forEach((attr) => {
      if (/^on/i.test(attr.name)) el.removeAttribute(attr.name)
    })
  })

  return doc.body ? doc.body.innerHTML : ''
}
