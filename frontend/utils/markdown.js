/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * 共享 Markdown 渲染工具函数
 * 支持 Mermaid 图表块检测
 */

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 检测代码内容是否为 Mermaid 图表语法
 */
export function isMermaidCode(code) {
  if (!code) return false
  const trimmed = code.trim()
  return /^(graph |flowchart |sequenceDiagram|classDiagram|stateDiagram|erDiagram|gantt|pie|gitgraph|journey|mindmap|timeline|sankey|xychart|block-beta)/.test(trimmed)
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

  const baseUrl = options.axiosBaseURL || ''

  let html = markdown
    // Mermaid 代码块（必须在通用代码块之前匹配）
    .replace(/```mermaid\n([\s\S]*?)```/g, (match, code) => {
      const source = code.trim()
      return `<div class="mermaid-block" data-mermaid-source="${encodeURIComponent(source)}">${escapeHtml(source)}</div>`
    })
    // 代码块（必须在最前面，避免代码块内的内容被其他规则匹配）
    .replace(/```(\w+)?\n([\s\S]*?)```/g, (match, lang, code) => `<pre class="md-code-block"><code>${escapeHtml(code)}</code></pre>`)
    // 行内代码（内容做 HTML 转义，防止 <p>、<ul> 等被浏览器解析）
    .replace(/`([^`]+)`/g, (match, code) => `<code class="md-inline-code">${escapeHtml(code)}</code>`)
    // 图片 - 必须在链接之前处理，避免被链接规则匹配
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (match, alt, url) => {
      let imageUrl = (url || '').trim()
      if (imageUrl && !imageUrl.startsWith('http://') && !imageUrl.startsWith('https://') && !imageUrl.startsWith('data:') && !imageUrl.startsWith('/')) {
        imageUrl = baseUrl + (imageUrl.startsWith('/') ? imageUrl : '/' + imageUrl)
      }
      const escapedAlt = (alt || '').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
      const escapedUrl = imageUrl.replace(/"/g, '&quot;')
      return `<img src="${escapedUrl}" alt="${escapedAlt}" class="md-image" style="max-width: 100%; max-height: 600px; width: auto; height: auto; border-radius: 4px; margin: 1em 0;" onerror="this.style.display='none'; this.nextElementSibling && (this.nextElementSibling.style.display='block');" /><span style="display:none;color:#999;font-size:12px;">图片加载失败: ${escapedAlt || escapedUrl}</span>`
    })
    // 标题
    .replace(/^#### (.*$)/gm, '<h4>$1</h4>')
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^# (.*$)/gm, '<h1>$1</h1>')
    // 引用
    .replace(/^> (.*$)/gm, '<blockquote>$1</blockquote>')
    // 粗体
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // 斜体
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    // 链接（必须在图片之后，避免匹配图片语法）
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
    // 无序列表
    .replace(/^- (.*$)/gm, '<li>$1</li>')
    // 水平线
    .replace(/^---$/gm, '<hr class="md-hr" />')
    // 换行
    .replace(/\n/g, '<br>')
  // 合并连续的li标签
  html = html.replace(/(<li>.*?<\/li>)(<br>)?/g, '$1')
  html = html.replace(/(<li>.*?<\/li>)+/g, '<ul>$&</ul>')
  return html
}
