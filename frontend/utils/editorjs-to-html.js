/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { renderMarkdown } from './markdown'
import { parseListItems } from './editorjs-list'
import { renderTex } from './mathRender'
import { getTweetIdFromEmbedUrl } from './videoEmbedResolver'

function esc(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function formatFileSize(bytes) {
  const n = Number(bytes)
  if (!n || n <= 0) return ''
  if (n >= 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB'
  return (n / 1024).toFixed(1) + ' KB'
}

function renderListItems(items, tag) {
  if (!items || !items.length) return ''
  const rows = items.map(item => {
    const children = item.items && item.items.length
      ? renderListItems(item.items, tag)
      : ''
    return `<li>${item.content || ''}${children}</li>`
  }).join('\n')
  return `<${tag} class="pdf-list">\n${rows}\n</${tag}>`
}

// 打印窗口是 blob: 页面，嵌入播放器拿不到 Referer（YouTube 会报"视频播放器配置错误"），
// 纸上也放不了视频：嵌入视频打印成封面图（没有封面时是占位框）+ 原视频链接
function renderVideoCard(posterUrl, link) {
  const cover = posterUrl
    ? `<img src="${esc(posterUrl)}" alt="">`
    : '<span class="pdf-video-card__placeholder">&#9654;</span>'
  return `<a class="pdf-video-card" href="${esc(link)}">${cover}</a>` +
    `<div class="pdf-video-card__link"><a href="${esc(link)}">${esc(link)}</a></div>`
}

function renderBlock(block) {
  if (!block || !block.type) return ''
  const d = block.data || {}

  switch (block.type) {
    case 'paragraph':
      return d.text != null ? `<p class="pdf-paragraph">${d.text}</p>` : ''

    case 'header': {
      const lvl = Math.min(Math.max(parseInt(d.level) || 2, 1), 6)
      return `<h${lvl} class="pdf-header">${d.text || ''}</h${lvl}>`
    }

    case 'list': {
      const tag = d.style === 'ordered' ? 'ol' : 'ul'
      const items = parseListItems(d.items || [])
      return renderListItems(items, tag)
    }

    case 'code':
      return `<pre class="pdf-code"><code>${esc(d.code || '')}</code></pre>`

    case 'quote': {
      const caption = d.caption ? `<cite>${d.caption}</cite>` : ''
      return `<blockquote class="pdf-quote"><p>${d.text || ''}</p>${caption}</blockquote>`
    }

    case 'table': {
      const rows = d.content || []
      if (!rows.length) return ''
      let thead = '', tbody = ''
      if (d.withHeadings && rows.length > 0) {
        thead = '<thead><tr>' + rows[0].map(c => `<th>${c}</th>`).join('') + '</tr></thead>'
        tbody = '<tbody>' + rows.slice(1).map(r =>
          '<tr>' + r.map(c => `<td>${c}</td>`).join('') + '</tr>'
        ).join('') + '</tbody>'
      } else {
        tbody = '<tbody>' + rows.map(r =>
          '<tr>' + r.map(c => `<td>${c}</td>`).join('') + '</tr>'
        ).join('') + '</tbody>'
      }
      return `<table class="pdf-table">${thead}${tbody}</table>`
    }

    case 'image': {
      const file = d.file || {}
      const url = file.url || d.url || ''
      if (!url) return ''
      const caption = d.caption ? `<figcaption>${d.caption}</figcaption>` : ''
      return `<figure class="pdf-image"><img src="${esc(url)}" alt="${esc(d.caption || '')}">${caption}</figure>`
    }

    case 'checklist': {
      const items = d.items || []
      if (!items.length) return ''
      const rows = items.map(item =>
        `<li class="pdf-checklist-item${item.checked ? ' pdf-checklist-item--checked' : ''}">${item.text || ''}</li>`
      ).join('\n')
      return `<ul class="pdf-checklist">\n${rows}\n</ul>`
    }

    case 'attaches': {
      const file = d.file || {}
      if (!file.url) return ''
      const name = d.title || file.name || '附件'
      const ext = file.extension ? file.extension.toUpperCase() : ''
      const sizeLabel = formatFileSize(file.size)
      const meta = [ext, sizeLabel].filter(Boolean).join(' · ')
      return `<a class="pdf-attach" href="${esc(file.url)}"><span class="pdf-attach__name">${esc(name)}</span>${meta ? `<span class="pdf-attach__meta">${esc(meta)}</span>` : ''}</a>`
    }

    case 'linkTool': {
      const meta = d.meta || {}
      const link = d.link || ''
      if (!link) return ''
      const image = meta.image && meta.image.url
        ? `<div class="pdf-link-tool__image" style="background-image:url(${esc(meta.image.url)})"></div>`
        : ''
      const title = meta.title ? `<div class="pdf-link-tool__title">${esc(meta.title)}</div>` : ''
      const description = meta.description ? `<div class="pdf-link-tool__description">${esc(meta.description)}</div>` : ''
      let host = link
      try { host = new URL(link).hostname } catch (e) { /* keep raw link as fallback */ }
      return `<a class="pdf-link-tool" href="${esc(link)}">${image}<div class="pdf-link-tool__content">${title}${description}<span class="pdf-link-tool__host">${esc(host)}</span></div></a>`
    }

    case 'warning': {
      const title = d.title ? `<div class="pdf-warning__title">${d.title}</div>` : ''
      const message = d.message ? `<div class="pdf-warning__message">${d.message}</div>` : ''
      return `<div class="pdf-warning">${title}${message}</div>`
    }

    case 'references': {
      const items = d.items || []
      if (!items.length) return ''
      const rows = items.map(item => {
        const href = item.kind === 'note' ? '#' : esc(item.url || '')
        const noteText = item.note ? `<span class="pdf-ref__note">（${esc(item.note)}）</span>` : ''
        return `<li class="pdf-ref__item"><a href="${href}">${esc(item.title || '')}</a>${noteText}</li>`
      }).join('\n')
      return `<ul class="pdf-references">\n${rows}\n</ul>`
    }

    case 'mediaGallery': {
      const items = d.items || []
      if (!items.length) return ''
      const cards = items.map(item => {
        if (item.type === 'video') {
          return `<div class="pdf-gallery__card">${renderVideoCard(item.posterUrl, item.sourceUrl || item.embedUrl || '')}</div>`
        }
        if (item.type === 'audio') {
          return `<div class="pdf-gallery__card"><audio controls src="${esc(item.url || '')}"></audio></div>`
        }
        return `<div class="pdf-gallery__card"><img src="${esc(item.url || '')}" alt="${esc(item.caption || '')}"></div>`
      }).join('\n')
      return `<div class="pdf-gallery">\n${cards}\n</div>`
    }

    case 'timeline': {
      const items = d.items || []
      if (!items.length) return ''
      const sorted = [...items].sort((a, b) => (a.date || '').localeCompare(b.date || ''))
      const rows = sorted.map(item => {
        const desc = item.description ? `<div class="pdf-timeline__desc">${esc(item.description)}</div>` : ''
        const link = item.link ? ` <a href="${esc(item.link)}" class="pdf-timeline__link">🔗</a>` : ''
        return `<div class="pdf-timeline__item"><div class="pdf-timeline__date">${esc(item.date || '')}</div><div class="pdf-timeline__title">${esc(item.title || '')}${link}</div>${desc}</div>`
      }).join('\n')
      return `<div class="pdf-timeline">\n${rows}\n</div>`
    }

    case 'delimiter':
      return '<hr class="pdf-delimiter">'

    case 'markdown':
      if (!d.markdown) return ''
      return `<div class="pdf-markdown">${renderMarkdown(d.markdown)}</div>`

    case 'math': {
      const html = renderTex(d.tex)
      return html ? `<div class="pdf-math">${html}</div>` : ''
    }

    case 'video': {
      if (!d.url) return ''
      const caption = d.caption ? `<figcaption>${esc(d.caption)}</figcaption>` : ''
      return `<figure class="pdf-video"><video controls src="${esc(d.url)}" style="max-width:100%"></video>${caption}</figure>`
    }

    case 'embed': {
      const src = d.embedUrl || d.embed
      if (!src) return ''
      const caption = d.caption ? `<figcaption>${esc(d.caption)}</figcaption>` : ''
      return `<figure class="pdf-embed">${renderVideoCard(d.posterUrl, d.sourceUrl || src)}${caption}</figure>`
    }

    default:
      return `<!-- pdf-export: unsupported block type "${esc(block.type)}" -->`
  }
}

export function editorjsToHtml(editorjsData) {
  if (!editorjsData) return ''
  const blocks = Array.isArray(editorjsData.blocks) ? editorjsData.blocks : []
  return blocks.map(renderBlock).join('\n')
}

/**
 * 单个嵌入的推文块不存封面（只有画廊会取），打印出来只有占位框。导出前现取一次封面，
 * 只用于这次打印，返回新对象、不改笔记数据；取不到（网络/私有推文）就保持占位框。
 * fetchPoster(tweetId) => Promise<posterUrl|null>
 */
export async function fillMissingTweetPosters(editorjsData, fetchPoster) {
  const data = JSON.parse(JSON.stringify(editorjsData || { blocks: [] }))
  const targets = []
  for (const block of data.blocks || []) {
    if (block.type === 'embed' && block.data) targets.push(block.data)
    if (block.type === 'mediaGallery' && block.data) {
      for (const item of block.data.items || []) if (item.type === 'video') targets.push(item)
    }
  }
  await Promise.all(targets.map(async (target) => {
    const tweetId = !target.posterUrl && getTweetIdFromEmbedUrl(target.embedUrl)
    if (!tweetId) return
    try {
      const posterUrl = await fetchPoster(tweetId)
      if (posterUrl) target.posterUrl = posterUrl
    } catch (e) {
      // 取不到就保持占位框
    }
  }))
  return data
}
