/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Markdown 笔记的文档级渲染（对齐 VS Code Markdown 预览），只给 contentType=markdown 的整篇笔记用。
 * editor.js 的 Markdown 块、ChatPanel、PDF 导出等仍用 utils/markdown.js 的 renderMarkdown，两者互不影响。
 * 返回值已经过 DOMPurify 过滤，调用方直接 v-html，不要再做别的处理。
 */

import MarkdownIt from 'markdown-it'
import taskLists from 'markdown-it-task-lists'
import footnote from 'markdown-it-footnote'
import mark from 'markdown-it-mark'
import anchor from 'markdown-it-anchor'
import DOMPurify from 'dompurify'
import katex from '@vscode/markdown-it-katex'
import { isMermaidCode } from './markdown'
import { highlightCode } from './markdown-document-highlight'

/**
 * 标题锚点 slug，GitHub/VS Code 规则：转小写、去掉字母/数字/空白/下划线/连字符以外的字符、空白换成 -，
 * 中文等非拉丁字母保留。全是标点/emoji 的标题得到空串，退回 section（重复时由插件追加 -1、-2）
 */
export function slugifyHeading(text) {
  return String(text).trim().toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}\s_-]/gu, '')
    .replace(/\s/g, '-') || 'section'
}

const md = new MarkdownIt({
  html: true, // 内嵌 HTML 照常渲染，安全由最后的 DOMPurify 兜底
  breaks: false, // CommonMark 标准：单个换行视为空格，与 VS Code 预览一致
  linkify: true // 裸 URL 自动转链接
})

function renderCodeBlock(code, lang) {
  const highlighted = highlightCode(code, lang)
  const langClass = lang ? ` language-${md.utils.escapeHtml(lang)}` : ''
  return `<pre><code class="hljs${langClass}">${highlighted || md.utils.escapeHtml(code)}</code></pre>\n`
}

// 围栏代码块：mermaid 走现有图表占位（由 v-mermaid 指令渲染），其余按语言高亮。
// 没写语言时才按内容检测 mermaid，避免 ```js 里恰好以 graph 开头的代码被误判。
// 必须在 use(katex) 之前设置：katex 插件 use 时会接管 ```math，其余围栏交还给这里
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const code = token.content || ''
  const lang = (token.info || '').trim().split(/\s+/)[0].toLowerCase()
  if (lang === 'mermaid' || (!lang && isMermaidCode(code))) {
    const source = code.trim()
    return `<div class="mermaid-block" data-mermaid-source="${encodeURIComponent(source)}">${md.utils.escapeHtml(source)}</div>\n`
  }
  return renderCodeBlock(code, lang)
}

// 缩进代码块（4 空格缩进）：与无语言的围栏代码块同样式
md.renderer.rules.code_block = (tokens, idx) => renderCodeBlock(tokens[idx].content || '', '')

// 插件统一在这里注册（自定义渲染规则要写在这句之前：有的插件会在 use 时接管已有规则）
md.use(taskLists, { enabled: false, label: true }) // 任务列表：静态展示，checkbox 只读
  .use(footnote)
  .use(mark)
  .use(anchor, { slugify: slugifyHeading, tabIndex: false })
  .use(katex, { throwOnError: false, enableFencedBlocks: true }) // 公式：$行内$、$$块级$$、```math；出错时显示带错误色的源码

// 脚注编号去掉方括号，和 GitHub 一样显示成上标数字
md.renderer.rules.footnote_caption = (tokens, idx) => {
  const meta = tokens[idx].meta
  return String(meta.id + 1) + (meta.subId > 0 ? `:${meta.subId}` : '')
}

const SANITIZE_CONFIG = {
  USE_PROFILES: { html: true, svg: true, mathMl: true },
  // KaTeX 的 MathML 输出用到 semantics/annotation，DOMPurify 默认不在白名单里
  ADD_TAGS: ['semantics', 'annotation'],
  ADD_ATTR: ['encoding'],
  FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form', 'base', 'meta', 'link'],
  // id/name 统一加 user-content- 前缀（GitHub 同款），防止标题 id 覆盖 document 上的同名属性；
  // 页内 # 跳转由 markdown-document-dom.js 补前缀查找
  SANITIZE_NAMED_PROPS: true,
  // 只放行 http/https/mailto 和相对路径；img 的 data: 图片由 DOMPurify 的 DATA_URI_TAGS 单独放行
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i
}

let purifier = null

// DOMPurify 需要真实 DOM：浏览器里用 window，单测里由测试注入 jsdom 的 window。
// 用独立实例挂 hook，不污染 mermaid 等其他库共用的默认实例
function getPurifier() {
  if (purifier) return purifier
  if (typeof window === 'undefined') return null
  const instance = DOMPurify(window)
  if (!instance.isSupported) return null
  instance.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && /^(?:https?|mailto):/i.test(node.getAttribute('href') || '')) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
    // 任务列表以外的表单控件没有意义：一律变成只读 checkbox
    if (node.tagName === 'INPUT') {
      node.setAttribute('type', 'checkbox')
      node.setAttribute('disabled', '')
    }
  })
  purifier = instance
  return purifier
}

/**
 * 渲染整篇 Markdown 笔记为已过滤的 HTML
 * @param {string} markdown - 原始 markdown 文本
 * @param {object} options
 * @param {string} options.axiosBaseURL - axios 基础 URL，用于处理相对路径图片
 * @returns {string} 已过滤的 HTML；没有可用 DOM（SSR）时返回空串
 */
export function renderMarkdownDocument(markdown, options = {}) {
  if (!markdown) return ''
  const sanitizer = getPurifier()
  // 宁可输出空，也不输出未过滤的 HTML；三处页面的笔记内容都在浏览器端加载，SSR 不会走到这里
  if (!sanitizer) return ''
  return sanitizer.sanitize(md.render(markdown.replace(/^﻿/, '')), SANITIZE_CONFIG)
}
