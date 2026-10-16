/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { el } from './transcriptDom'

// 原文改错（"编辑 / 完成"模式）与说话人显示。只能改原文的文字，时间和说话人不可改。
// 详见 docs/superpowers/specs/2026-10-16-recording-phase2-design.md。

/** 对比编辑前后的文字（trim 后），返回改过的句子 [{ index, text }] */
export function diffEdits(utterances, texts) {
  const edits = []
  utterances.forEach((u, index) => {
    const next = String(texts[index] == null ? u.text : texts[index]).trim()
    if (next !== String(u.text || '').trim()) edits.push({ index, text: next })
  })
  return edits
}

/** 原文里有"[第 N 段转写失败]"占位句时不能编辑，应先重新转写 */
export function hasFailedPlaceholder(utterances) {
  return (utterances || []).some((u) => String(u.text || '').startsWith('[第 '))
}

/** 总结里的 {{S1}} 换成说话人名字，没改过名的显示"说话人 1" */
export function speakerText(text, names) {
  return String(text == null ? '' : text).replace(/\{\{S(\d+)\}\}/g, (_, n) => {
    const name = names && names[n]
    return name ? String(name) : `说话人 ${n}`
  })
}

/** 把原文行换成可编辑文本（时间戳只读），返回读取当前文字的方法 */
export function mountEditor(box, utts, { onDirty, fmt }) {
  box.textContent = ''
  const inputs = utts.map((u) => {
    const line = el('div', 'atp-line atp-edit-line')
    line.appendChild(el('span', 'atp-ts-static', fmt(u.start)))
    const input = el('textarea', 'atp-edit-input')
    input.value = u.text
    input.rows = 1
    input.addEventListener('input', () => onDirty(diffEdits(utts, inputs.map((i) => i.value)).length > 0))
    line.appendChild(input)
    box.appendChild(line)
    return input
  })
  if (inputs[0]) inputs[0].focus()
  return { texts: () => inputs.map((i) => i.value) }
}
