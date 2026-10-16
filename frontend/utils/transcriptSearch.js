/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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
