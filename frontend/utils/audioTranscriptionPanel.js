/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { subscribe, notifyChanged } from './audioTranscriptionStore'

// 音频块下方的"转写面板"：摘要优先、原文折叠、所有时间戳可点击跳转。
// Editor.js 工具是原生 DOM，这里也用原生 DOM（<details> 折叠），样式向 Element UI 靠拢。
// 所有模型 / 转写文本只通过 textContent / createTextNode 进入 DOM，绝不使用 innerHTML 拼接。
// 详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md "前端" 一节。

function injectStyles() {
  if (document.getElementById('audio-transcription-panel-styles')) return
  const el = document.createElement('style')
  el.id = 'audio-transcription-panel-styles'
  el.textContent = `
.atp { margin-top: 10px; font-size: 13px; color: #303133; line-height: 1.7; }
.atp-status { display: flex; align-items: center; gap: 8px; color: #909399; font-size: 12.5px; }
.atp-status.error { color: #f56c6c; }
.atp-btn { border: 1px solid #dcdfe6; background: #fff; color: #606266; border-radius: 4px; padding: 2px 10px; font-size: 12px; cursor: pointer; }
.atp-btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.atp-spin { width: 12px; height: 12px; border: 2px solid #dcdfe6; border-top-color: #409eff; border-radius: 50%; animation: atp-spin .8s linear infinite; }
@keyframes atp-spin { to { transform: rotate(360deg); } }
.atp-overview { margin: 8px 0; font-weight: 500; }
.atp details { border-top: 1px solid #ebeef5; padding: 6px 0; }
.atp summary { cursor: pointer; color: #606266; font-weight: 500; user-select: none; }
.atp-topic { margin-top: 6px; font-weight: 500; }
.atp ul { margin: 4px 0 4px 18px; padding: 0; }
.atp-ts { color: #409eff; cursor: pointer; font-variant-numeric: tabular-nums; margin-right: 6px; }
.atp-ts:hover { text-decoration: underline; }
.atp-quote-cat { color: #909399; margin-left: 4px; }
.atp-seg { display: inline-flex; border: 1px solid #dcdfe6; border-radius: 4px; overflow: hidden; margin-left: 8px; vertical-align: middle; }
.atp-seg button { border: 0; background: #fff; padding: 0 8px; font-size: 12px; cursor: pointer; color: #606266; }
.atp-seg button.on { background: #409eff; color: #fff; }
.atp-line { padding: 2px 0 2px 8px; border-left: 3px solid transparent; }
.atp-line.marked { border-left-color: #e6a23c; background: #fdf6ec; }
.atp-legacy { white-space: pre-wrap; color: #888; }
`
  document.head.appendChild(el)
}

export function fmt(ms) {
  const s = Math.max(0, Math.floor((ms || 0) / 1000))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const r = s % 60
  const mm = (m < 10 ? '0' : '') + m
  const ss = (r < 10 ? '0' : '') + r
  return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

function el(tag, cls, text) {
  const e = document.createElement(tag)
  if (cls) e.className = cls
  if (text != null) e.textContent = text
  return e
}

/** "**关键词**：说明" → 加粗关键词；先按纯文本拼节点，不用 innerHTML，避免注入 */
function richText(text) {
  const frag = document.createDocumentFragment()
  String(text).split(/(\*\*[^*]+\*\*)/).forEach((part) => {
    if (/^\*\*[^*]+\*\*$/.test(part)) frag.appendChild(el('strong', null, part.slice(2, -2)))
    else if (part) frag.appendChild(document.createTextNode(part))
  })
  return frag
}

export function mountTranscriptionPanel({ container, audio, noteId, blockId, legacy, readOnly, service }) {
  injectStyles()
  const root = el('div', 'atp')
  container.appendChild(root)
  let record = null
  let transcriptMode = 'polished'
  const openState = { keyPoints: true }

  const seek = (ms) => {
    if (!audio) return
    audio.currentTime = (ms || 0) / 1000
    const p = audio.play()
    if (p && p.catch) p.catch(() => {})
  }
  const ts = (ms) => {
    const a = el('span', 'atp-ts', fmt(ms))
    a.title = '从这里播放'
    a.addEventListener('click', () => seek(ms))
    return a
  }
  let triggerError = ''
  const trigger = async (stage, btn) => {
    if (btn) btn.disabled = true
    triggerError = ''
    try {
      await service.trigger(noteId, blockId, 'MANUAL', stage)
    } catch (e) {
      console.warn('触发转写失败:', e)
      triggerError = '触发失败，请稍后重试'
      render()
    }
    if (btn) btn.disabled = false
    notifyChanged(noteId, blockId)
  }
  const button = (label, stage) => {
    const b = el('button', 'atp-btn', label)
    b.type = 'button'
    b.addEventListener('click', () => trigger(stage, b))
    return b
  }
  const section = (key, title, count, fill) => {
    if (!count) return null
    const d = el('details')
    if (openState[key]) d.open = true
    d.addEventListener('toggle', () => { openState[key] = d.open })
    d.appendChild(el('summary', null, count > 0 && key !== 'keyPoints' ? `${title} (${count})` : title))
    fill(d)
    return d
  }

  function renderStatus() {
    const r = record
    const line = el('div', 'atp-status')
    if (triggerError) line.appendChild(el('span', null, triggerError))
    if (!r) {
      if (readOnly) return null
      line.appendChild(button(legacy && legacy.transcript ? '重新转写' : '转写', 'FULL'))
      return line
    }
    if (r.status === 'PENDING' || r.status === 'PROCESSING') {
      line.appendChild(el('span', 'atp-spin'))
      line.appendChild(el('span', null, (r.utterances || []).length ? '整理中…' : '转写中…'))
      return line
    }
    if (r.status === 'FAILED') {
      line.classList.add('error')
      line.appendChild(el('span', null, r.errorMessage || '转写失败'))
      if (!readOnly) {
        // 逐句原文已经存下来了（ASR 已付费）：只需重做整理，不必整段重新转写
        const hasUtterances = (r.utterances || []).length > 0
        line.appendChild(hasUtterances ? button('重新整理', 'SUMMARY') : button('重试', 'FULL'))
      }
      return line
    }
    if (r.errorKind === 'SUMMARY_FAILED') {
      line.classList.add('error')
      line.appendChild(el('span', null, r.errorMessage || '整理失败'))
      if (!readOnly) line.appendChild(button('重新整理', 'SUMMARY'))
      return line
    }
    if (r.errorKind === 'PARTIAL_SEGMENTS' || r.errorKind === 'NO_SPEECH') {
      line.appendChild(el('span', null, r.errorMessage))
      if (!readOnly) line.appendChild(button('重新转写', 'FULL'))
      return line
    }
    return null
  }

  function renderSummary(s) {
    const frag = document.createDocumentFragment()
    if (s.overview) frag.appendChild(el('div', 'atp-overview', s.overview))
    const items = [
      section('keyPoints', '要点', (s.keyPoints || []).length, (d) => {
        s.keyPoints.forEach((k) => {
          if (k.topic) d.appendChild(el('div', 'atp-topic', k.topic))
          const ul = el('ul')
          ;(k.points || []).forEach((p) => { const li = el('li'); li.appendChild(richText(p)); ul.appendChild(li) })
          d.appendChild(ul)
        })
      }),
      section('chapters', '章节', (s.chapters || []).length, (d) => {
        const ul = el('ul')
        s.chapters.forEach((c) => {
          const li = el('li')
          li.appendChild(ts(c.start))
          li.appendChild(el('strong', null, c.title))
          if (c.desc) li.appendChild(document.createTextNode(' ' + c.desc))
          ul.appendChild(li)
        })
        d.appendChild(ul)
      }),
      section('highlights', '重点片段', (s.highlights || []).length, (d) => {
        const ul = el('ul')
        s.highlights.forEach((h) => { const li = el('li'); li.appendChild(ts(h.start)); li.appendChild(document.createTextNode(h.text)); ul.appendChild(li) })
        d.appendChild(ul)
      }),
      section('quotes', '金句', (s.quotes || []).length, (d) => {
        const ul = el('ul')
        s.quotes.forEach((q) => {
          const li = el('li')
          li.appendChild(ts(q.start))
          li.appendChild(document.createTextNode('“' + q.text + '”'))
          if (q.category) li.appendChild(el('span', 'atp-quote-cat', '（' + q.category + '）'))
          ul.appendChild(li)
        })
        d.appendChild(ul)
      }),
      section('todos', '待办', (s.todos || []).length, (d) => {
        const ul = el('ul')
        s.todos.forEach((t) => ul.appendChild(el('li', null, t)))
        d.appendChild(ul)
      })
    ]
    items.forEach((i) => i && frag.appendChild(i))
    return frag
  }

  function renderTranscript(r) {
    const utts = r.utterances || []
    const polished = r.polished || []
    if (!utts.length && !polished.length) return null
    const windows = ((r.summary && r.summary.highlights) || []).map((h) => [h.start, h.end])
    const inMark = (u) => windows.some(([s, e]) => u.end >= s && u.start <= e)
    return section('transcript', '原文', utts.length || polished.length, (d) => {
      const head = d.querySelector('summary')
      head.textContent = '原文'
      if (polished.length && utts.length) {
        const seg = el('span', 'atp-seg')
        ;[['polished', 'AI优化版'], ['raw', '原文']].forEach(([mode, label]) => {
          const b = el('button', transcriptMode === mode ? 'on' : '', label)
          b.type = 'button'
          b.addEventListener('click', (e) => { e.preventDefault(); transcriptMode = mode; render() })
          seg.appendChild(b)
        })
        head.appendChild(seg)
      }
      const usePolished = polished.length && (transcriptMode === 'polished' || !utts.length)
      ;(usePolished ? polished : utts).forEach((u) => {
        const line = el('div', 'atp-line' + (!usePolished && inMark(u) ? ' marked' : ''))
        line.appendChild(ts(u.start))
        line.appendChild(document.createTextNode(u.text))
        d.appendChild(line)
      })
    })
  }

  function renderLegacy() {
    // 旧录音：只有 transcript / summary 文本、还没有新的转写记录，沿用老样子显示
    const frag = document.createDocumentFragment()
    if (legacy && legacy.summary) frag.appendChild(el('div', 'atp-overview', legacy.summary))
    if (legacy && legacy.transcript) {
      const d = section('legacyTranscript', '逐字稿', 1, (det) => det.appendChild(el('div', 'atp-legacy', legacy.transcript)))
      d.querySelector('summary').textContent = '逐字稿'
      frag.appendChild(d)
    }
    return frag
  }

  function render() {
    root.textContent = ''
    const status = renderStatus()
    if (status) root.appendChild(status)
    const hasUtterances = !!record && (record.utterances || []).length > 0
    if (!record || (record.status === 'FAILED' && !hasUtterances) || (record.status !== 'DONE' && record.status !== 'FAILED' && !hasUtterances)) {
      if (!record || record.status === 'FAILED') root.appendChild(renderLegacy())
      return
    }
    if (record.summary) root.appendChild(renderSummary(record.summary))
    const t = renderTranscript(record)
    if (t) root.appendChild(t)
  }

  render()
  let seenConnected = false
  let unsubscribe = null
  const stop = () => {
    if (unsubscribe) unsubscribe()
    unsubscribe = null
  }
  if (noteId && service) {
    unsubscribe = subscribe(noteId, blockId, service, (r) => {
      // Editor.js 重渲染 / 替换块时不一定调用 destroy()：根节点曾挂载、现已脱离文档就自行退订
      if (root.isConnected) seenConnected = true
      else if (seenConnected) { stop(); return }
      record = r
      render()
    })
  }

  return {
    destroy() {
      stop()
      root.remove()
    }
  }
}
