/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import katex from 'katex'

function esc(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 块级公式渲染成 MathML（浏览器原生排版，免加载 KaTeX 的 CSS 和字体，PDF 导出也适用）。
 * TeX 写错时显示源码和错误信息，不抛异常。
 */
export function renderTex(tex) {
  if (!tex || !tex.trim()) return ''
  try {
    return katex.renderToString(tex, { displayMode: true, output: 'mathml', throwOnError: true })
  } catch (e) {
    return `<pre class="cdx-math-block__error">${esc(tex)}</pre>` +
      `<div class="cdx-math-block__error-msg">${esc(e.message || e)}</div>`
  }
}
