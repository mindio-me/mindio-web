/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { renderMarkdown } from './markdown'
import { parseListItems } from './editorjs-list'

function esc(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
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

function renderBlock(block) {
  if (!block || !block.type) return ''
  const d = block.data || {}

  switch (block.type) {
    case 'paragraph':
      return d.text != null ? `<p class="pdf-paragraph">${d.text}</p>` : ''

    case 'header': {
      const lvl = Math.min(Math.max(parseInt(d.level) || 2, 1), 3)
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

    case 'delimiter':
      return '<hr class="pdf-delimiter">'

    case 'markdown':
      if (!d.markdown) return ''
      return `<div class="pdf-markdown">${renderMarkdown(d.markdown)}</div>`

    case 'video': {
      if (!d.url) return ''
      const caption = d.caption ? `<figcaption>${esc(d.caption)}</figcaption>` : ''
      return `<figure class="pdf-video"><video controls src="${esc(d.url)}" style="max-width:100%"></video>${caption}</figure>`
    }

    case 'embed': {
      const src = d.embedUrl || d.embed
      if (!src) return ''
      const caption = d.caption ? `<figcaption>${esc(d.caption)}</figcaption>` : ''
      return `<figure class="pdf-embed"><iframe src="${esc(src)}" height="${d.height || 360}" frameborder="0" allowfullscreen style="width:100%"></iframe>${caption}</figure>`
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
