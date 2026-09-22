/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * 共享 Markdown 渲染工具函数（GFM，基于 markdown-it）
 * 支持 Mermaid 图表块检测
 */

import MarkdownIt from 'markdown-it'
import taskLists from 'markdown-it-task-lists'

/**
 * 检测代码内容是否为 Mermaid 图表语法
 */
export function isMermaidCode(code) {
  if (!code) return false
  const trimmed = code.trim()
  return /^(graph |flowchart |sequenceDiagram|classDiagram|stateDiagram|erDiagram|gantt|pie|gitgraph|journey|mindmap|timeline|sankey|xychart|block-beta)/.test(trimmed)
}

// renderMarkdown() 调用时写入，供下面的 image 渲染规则读取；
// 渲染是同步单次调用，不存在并发写入互相覆盖的问题
let currentBaseUrl = ''

const md = new MarkdownIt({
  html: false, // 不透传原始 HTML，防止笔记内容里混入的标签被当成真实 HTML 渲染
  breaks: true, // 单个换行直接换行，贴合文本框里按回车换行的编辑习惯
  linkify: true // 裸 URL 自动转可点击链接
}).use(taskLists, { enabled: false, label: true }) // 任务列表：静态展示，checkbox 禁用不可点击

function renderCodeBlock(code) {
  return `<pre class="md-code-block"><code>${md.utils.escapeHtml(code)}</code></pre>`
}

// 围栏代码块（```lang ... ```）：mermaid 走图表占位 div，其余走普通代码块
md.renderer.rules.fence = (tokens, idx) => {
  const code = tokens[idx].content || ''
  if (isMermaidCode(code)) {
    const source = code.trim()
    return `<div class="mermaid-block" data-mermaid-source="${encodeURIComponent(source)}">${md.utils.escapeHtml(source)}</div>`
  }
  return renderCodeBlock(code)
}

// 缩进代码块（4 空格缩进，没有反引号）：保持和围栏代码块一致的样式
md.renderer.rules.code_block = (tokens, idx) => renderCodeBlock(tokens[idx].content || '')

// 行内代码：补上既有的 md-inline-code class
md.renderer.rules.code_inline = (tokens, idx) => {
  return `<code class="md-inline-code">${md.utils.escapeHtml(tokens[idx].content || '')}</code>`
}

// 分割线：补上既有的 md-hr class
md.renderer.rules.hr = () => '<hr class="md-hr" />'

// 表格：补上既有的 md-table class（对齐方式仍由 markdown-it 默认的 th/td style 属性处理，未覆写）
md.renderer.rules.table_open = () => '<table class="md-table">'

// 图片：相对路径拼 axiosBaseURL；加载失败时隐藏图片并显示降级文案
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const srcIndex = token.attrIndex('src')
  let url = (srcIndex >= 0 ? token.attrs[srcIndex][1] : '') || ''
  if (url && !url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('data:') && !url.startsWith('/')) {
    url = currentBaseUrl + '/' + url
  }
  const alt = self.renderInlineAsText(token.children || [], options, env)
  const escapedAlt = md.utils.escapeHtml(alt || '')
  const escapedUrl = md.utils.escapeHtml(url)
  return `<img src="${escapedUrl}" alt="${escapedAlt}" class="md-image" style="max-width: 100%; max-height: 600px; width: auto; height: auto; border-radius: 4px; margin: 1em 0;" onerror="this.style.display='none'; this.nextElementSibling && (this.nextElementSibling.style.display='block');" /><span style="display:none;color:#999;font-size:12px;">图片加载失败: ${escapedAlt || escapedUrl}</span>`
}

// 链接：外部链接始终新开标签页，和升级前的行为保持一致
// （分栏编辑模式下预览区点链接不能让 SPA 跳转丢掉旁边 textarea 里未保存的内容）
const defaultLinkOpen = md.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/**
 * 渲染 Markdown 为 HTML
 * @param {string} markdown - 原始 markdown 文本
 * @param {object} options - 配置项
 * @param {string} options.axiosBaseURL - axios 基础 URL，用于处理相对路径图片
 * @returns {string} HTML 字符串
 */
export function renderMarkdown(markdown, options = {}) {
  if (!markdown) return ''
  currentBaseUrl = options.axiosBaseURL || ''
  return md.render(markdown)
}
