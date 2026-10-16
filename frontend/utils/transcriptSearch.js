/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { el } from './transcriptDom'

// 单条录音内的查找：命中高亮、上下跳转。全站搜索已经能搜到转写内容，这里只管一条录音内部。

/** 所有命中 { line, from, to }；大小写不敏感，同一行内不重叠；空白查询返回 [] */
export function findMatches(texts, query) {
  const q = String(query || '').trim().toLowerCase()
  if (!q) return []
  const hits = []
  texts.forEach((t, line) => {
    const s = String(t || '').toLowerCase()
    let from = s.indexOf(q)
    while (from !== -1) {
      hits.push({ line, from, to: from + q.length })
      from = s.indexOf(q, from + q.length)
    }
  })
  return hits
}

/** 原文区标题下方的搜索框；输入只回调，不重建自身，保证不失焦 */
export function mountSearchBox(parent, { query, onQuery, onStep }) {
  const wrap = el('span', 'atp-search')
  const input = el('input')
  input.type = 'search'
  input.placeholder = '搜索原文'
  input.value = query || ''
  const counter = el('span', 'atp-search-count')
  const up = el('button', 'atp-btn', '↑')
  const down = el('button', 'atp-btn', '↓')
  up.type = down.type = 'button'
  input.addEventListener('input', () => onQuery(input.value))
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === 'ArrowDown') {
      e.preventDefault()
      onStep(e.shiftKey ? -1 : 1)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      onStep(-1)
    }
  })
  up.addEventListener('click', () => onStep(-1))
  down.addEventListener('click', () => onStep(1))
  wrap.appendChild(input)
  wrap.appendChild(counter)
  wrap.appendChild(up)
  wrap.appendChild(down)
  parent.appendChild(wrap)
  return {
    setCounter(current, total) {
      counter.textContent = total ? `${current + 1}/${total}` : (input.value.trim() ? '0/0' : '')
    }
  }
}
