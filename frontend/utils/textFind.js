/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 笔记正文内查找（Ctrl+F）的纯匹配逻辑，DOM 部分在 components/NoteFindBar.vue。
// 一个"段"是一个块（Editor.js 的 .ce-block）里按顺序排列的若干文本节点；匹配可以跨文本节点
// （比如关键词中间有加粗），但不跨段。

/**
 * parts：同一段里各文本节点的文字。返回所有命中 [{ start: [节点序号, 偏移], end: [节点序号, 偏移] }]，
 * end 为开区间偏移；大小写不敏感，不重叠；空白查询返回 []
 */
export function locateMatches(parts, query) {
  const q = String(query || '').trim().toLowerCase()
  if (!q) return []
  const starts = []
  let text = ''
  parts.forEach((p) => {
    starts.push(text.length)
    text += String(p || '')
  })
  const lower = text.toLowerCase()
  // 全局偏移 → [节点序号, 节点内偏移]；end 落在节点边界时归到前一个节点末尾
  const toPos = (offset, isEnd) => {
    let i = starts.length - 1
    while (i > 0 && (isEnd ? starts[i] >= offset : starts[i] > offset)) i--
    return [i, offset - starts[i]]
  }
  const hits = []
  let from = lower.indexOf(q)
  while (from !== -1) {
    hits.push({ start: toPos(from, false), end: toPos(from + q.length, true) })
    from = lower.indexOf(q, from + q.length)
  }
  return hits
}
