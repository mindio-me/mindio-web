/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { MessageBox } from 'element-ui'
import { subscribe, notifyChanged, setUnsavedEdit } from './audioTranscriptionStore'
import { el } from './transcriptDom'
import { createPlayback, SPEED_OPTIONS } from './transcriptPlayback'
import { speakerText, diffEdits, hasFailedPlaceholder, mountEditor } from './transcriptEditor'
import { findMatches, mountSearchBox } from './transcriptSearch'

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
.atp-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; color: #606266; font-size: 12.5px; margin: 6px 0; }
.atp-bar select { font-size: 12px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 1px 4px; background: #fff; color: #606266; }
.atp-bar label { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }
.atp-lines { max-height: 360px; overflow-y: auto; position: relative; }
.atp-line.playing { background: #ecf5ff; }
.atp-spk { display: inline-block; font-size: 11.5px; padding: 0 6px; border-radius: 3px; margin-right: 6px; cursor: pointer; color: #fff; }
.atp-spk-input { font-size: 12px; width: 7em; margin-right: 6px; }
.atp-tools { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
.atp-search { display: inline-flex; align-items: center; gap: 4px; }
.atp-search input { font-size: 12px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 1px 6px; width: 12em; }
.atp-search-count { color: #909399; font-size: 12px; min-width: 3em; }
.atp-line mark { background: #fdf6ec; color: inherit; padding: 0; }
.atp-line mark.current { background: #f5c26b; }
.atp-edit-line { display: flex; align-items: flex-start; gap: 6px; }
.atp-ts-static { color: #909399; font-variant-numeric: tabular-nums; padding-top: 3px; }
.atp-edit-input { flex: 1; font: inherit; border: 1px solid #dcdfe6; border-radius: 4px; padding: 2px 6px; resize: vertical; }
.atp-btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
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

/** "**关键词**：说明" → 加粗关键词；先按纯文本拼节点，不用 innerHTML，避免注入 */
function richText(text) {
  const frag = document.createDocumentFragment()
  String(text).split(/(\*\*[^*]+\*\*)/).forEach((part) => {
    if (/^\*\*[^*]+\*\*$/.test(part)) frag.appendChild(el('strong', null, part.slice(2, -2)))
    else if (part) frag.appendChild(document.createTextNode(part))
  })
  return frag
}

export function mountTranscriptionPanel({ container, audio, noteId, blockId, legacy, readOnly, service: rawService }) {
  injectStyles()
  // 只读（公开笔记页等）：兜底拒绝所有写操作，只放行 list；UI 上写入口本来就已隐藏，这里防止漏网
  const service = readOnly && rawService
    ? {
        list: (...args) => rawService.list(...args),
        trigger: () => Promise.reject(new Error('read-only')),
        update: () => Promise.reject(new Error('read-only')),
        saveUtterances: () => Promise.reject(new Error('read-only'))
      }
    : rawService
  const root = el('div', 'atp')
  container.appendChild(root)
  let record = null
  const playback = createPlayback(audio)
  const SCENES = [['MEETING', '会议'], ['LECTURE', '课堂'], ['INTERVIEW', '访谈'], ['MEMO', '闪念'], ['SYSTEM_AUDIO', '内录']]
  const SPEAKER_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6', '#16a085', '#d35400', '#2c3e50', '#c0392b']
  const isBusy = () => !!record && (record.status === 'PENDING' || record.status === 'PROCESSING')
  const names = () => (record && record.speakerNames) || {}
  const speakerName = (n) => names()[n] || `说话人 ${n}`
  const multiSpeaker = (utts) => new Set(utts.map((u) => u.speaker).filter((s) => s != null)).size > 1
  let transcriptMode = 'polished'
  const openState = { keyPoints: true }
  let searchQuery = ''
  let searchIndex = 0
  let searchBox = null
  let editing = null // { texts() }
  const editKey = noteId + ':' + blockId

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
    if (btn && !editing) btn.disabled = false
    notifyChanged(noteId, blockId)
  }
  const button = (label, stage) => {
    const b = el('button', 'atp-btn', label)
    b.type = 'button'
    b.addEventListener('click', async () => {
      if (stage === 'FULL' && record && record.editedAt) {
        try {
          await MessageBox.confirm('重新转写会覆盖你对原文的修改，确定继续？', '重新转写', { type: 'warning' })
        } catch (e) {
          return
        }
      }
      trigger(stage, b)
    })
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
    // 到这里一定是 DONE：原文里还有失败占位句就始终给出"重新转写"（只重新整理过时 errorKind 可能被清掉）
    const placeholders = hasFailedPlaceholder(r.utterances)
    if (r.errorKind === 'SUMMARY_FAILED') {
      line.classList.add('error')
      line.appendChild(el('span', null, r.errorMessage || '整理失败'))
      if (!readOnly) line.appendChild(button('重新整理', 'SUMMARY'))
      if (!readOnly && placeholders) line.appendChild(button('重新转写', 'FULL'))
      return line
    }
    if (r.errorKind === 'PARTIAL_SEGMENTS' || r.errorKind === 'NO_SPEECH' || placeholders) {
      line.appendChild(el('span', null, r.errorMessage || (readOnly ? '部分片段转写失败' : '部分片段转写失败，可以重新转写')))
      if (!readOnly) line.appendChild(button('重新转写', 'FULL'))
      return line
    }
    return null
  }

  function renderBar() {
    const r = record
    const bar = el('div', 'atp-bar')
    if (!readOnly) {
      const sceneLabel = el('label', null, '场景：')
      const select = el('select', 'atp-scene')
      if (!r.scene) select.appendChild(el('option', null, '未判断')).value = ''
      SCENES.forEach(([v, label]) => {
        const o = el('option', null, label)
        o.value = v
        if (r.scene === v) o.selected = true
        select.appendChild(o)
      })
      // 只有完整完成（DONE）的记录能换场景重新整理；编辑中由 lockForEditing 禁用
      select.disabled = r.status !== 'DONE'
      select.addEventListener('change', async () => {
        if (editing) return
        const v = select.value
        try {
          await MessageBox.confirm('将按新场景重新整理，约需 1–2 分钟', '切换场景', { type: 'info' })
        } catch (e) {
          render()
          return
        }
        try {
          await service.update(noteId, blockId, { scene: v })
        } catch (e) {
          console.warn('切换场景失败:', e)
          triggerError = '切换场景失败，请稍后重试'
          render() // 记录没变时订阅回调不会重绘，这里自己把错误显示出来并复原下拉框
        }
        notifyChanged(noteId, blockId)
      })
      sceneLabel.appendChild(select)
      bar.appendChild(sceneLabel)
    }
    const speedSel = el('select')
    SPEED_OPTIONS.forEach((v) => {
      const o = el('option', null, v + 'x')
      o.value = String(v)
      if (v === playback.getSpeed()) o.selected = true
      speedSel.appendChild(o)
    })
    speedSel.addEventListener('change', () => playback.setSpeed(parseFloat(speedSel.value)))
    bar.appendChild(speedSel)
    if ((r.utterances || []).length) {
      const skip = el('label')
      const box = el('input')
      box.type = 'checkbox'
      box.checked = playback.getSkipSilence()
      box.addEventListener('change', () => playback.setSkipSilence(box.checked))
      skip.appendChild(box)
      skip.appendChild(document.createTextNode('跳过静音'))
      bar.appendChild(skip)
    }
    return bar
  }

  function speakerChip(n) {
    const chip = el('span', 'atp-spk', speakerName(n))
    chip.style.background = SPEAKER_COLORS[(n - 1) % SPEAKER_COLORS.length]
    // 处理中改名会被后台整理完成时的整条保存冲掉（后端也会 409），编辑原文时也不让改
    if (readOnly || isBusy()) return chip
    chip.title = '点击改名'
    chip.addEventListener('click', (e) => {
      e.stopPropagation()
      if (editing || isBusy()) return
      const input = el('input', 'atp-spk-input')
      input.value = names()[n] || ''
      input.placeholder = `说话人 ${n}`
      chip.replaceWith(input)
      input.focus()
      let done = false
      const commit = async (save) => {
        if (done) return
        done = true
        if (save) {
          const next = { ...names() }
          const v = input.value.trim()
          if (v) next[n] = v
          else delete next[n]
          try {
            record = await service.update(noteId, blockId, { speakerNames: next })
          } catch (err) {
            console.warn('改名失败:', err)
          }
        }
        render()
      }
      input.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter') commit(true)
        else if (ev.key === 'Escape') commit(false)
      })
      input.addEventListener('blur', () => commit(true))
    })
    return chip
  }

  function renderSummary(s) {
    const frag = document.createDocumentFragment()
    if (s.overview) frag.appendChild(el('div', 'atp-overview', speakerText(s.overview, names())))
    const items = [
      section('keyPoints', '要点', (s.keyPoints || []).length, (d) => {
        s.keyPoints.forEach((k) => {
          if (k.topic) d.appendChild(el('div', 'atp-topic', speakerText(k.topic, names())))
          const ul = el('ul')
          ;(k.points || []).forEach((p) => { const li = el('li'); li.appendChild(richText(speakerText(p, names()))); ul.appendChild(li) })
          d.appendChild(ul)
        })
      }),
      section('chapters', '章节', (s.chapters || []).length, (d) => {
        const ul = el('ul')
        s.chapters.forEach((c) => {
          const li = el('li')
          li.appendChild(ts(c.start))
          li.appendChild(el('strong', null, speakerText(c.title, names())))
          if (c.desc) li.appendChild(document.createTextNode(' ' + speakerText(c.desc, names())))
          ul.appendChild(li)
        })
        d.appendChild(ul)
      }),
      section('highlights', '重点片段', (s.highlights || []).length, (d) => {
        const ul = el('ul')
        s.highlights.forEach((h) => { const li = el('li'); li.appendChild(ts(h.start)); li.appendChild(document.createTextNode(speakerText(h.text, names()))); ul.appendChild(li) })
        d.appendChild(ul)
      }),
      section('quotes', '金句', (s.quotes || []).length, (d) => {
        const ul = el('ul')
        s.quotes.forEach((q) => {
          const li = el('li')
          li.appendChild(ts(q.start))
          li.appendChild(document.createTextNode('“' + speakerText(q.text, names()) + '”'))
          if (q.category) li.appendChild(el('span', 'atp-quote-cat', '（' + q.category + '）'))
          ul.appendChild(li)
        })
        d.appendChild(ul)
      }),
      section('todos', '待办', (s.todos || []).length, (d) => {
        const ul = el('ul')
        s.todos.forEach((t) => ul.appendChild(el('li', null, speakerText(t, names()))))
        d.appendChild(ul)
      })
    ]
    items.forEach((i) => i && frag.appendChild(i))
    return frag
  }

  function appendHighlighted(line, text, lineHits, allHits) {
    let pos = 0
    lineHits.forEach((h) => {
      if (h.from > pos) line.appendChild(document.createTextNode(text.slice(pos, h.from)))
      line.appendChild(el('mark', allHits.indexOf(h) === searchIndex ? 'current' : null, text.slice(h.from, h.to)))
      pos = h.to
    })
    if (pos < text.length) line.appendChild(document.createTextNode(text.slice(pos)))
  }

  // 逐行渲染原文 / 优化版（含搜索高亮）；只重绘行容器，不动搜索框，输入不失焦
  function fillLines(box, items, usePolished, utts) {
    const windows = ((record && record.summary && record.summary.highlights) || []).map((h) => [h.start, h.end])
    const inMark = (u) => windows.some(([s, e]) => u.end >= s && u.start <= e)
    const showSpeakers = multiSpeaker(utts)
    // 优化版段落取段首那句的说话人：段落 start 等于某句 start
    const speakerAt = (item) => {
      if (!usePolished) return item.speaker
      const u = utts.find((x) => x.start === item.start)
      return u ? u.speaker : null
    }
    const hits = findMatches(items.map((u) => u.text), searchQuery)
    if (searchIndex >= hits.length) searchIndex = 0
    const lines = []
    let lastSpeaker = null
    items.forEach((u, idx) => {
      const line = el('div', 'atp-line' + (!usePolished && inMark(u) ? ' marked' : ''))
      line.appendChild(ts(u.start))
      const sp = showSpeakers ? speakerAt(u) : null
      if (sp != null && sp !== lastSpeaker) line.appendChild(speakerChip(sp))
      if (sp != null) lastSpeaker = sp
      appendHighlighted(line, u.text, hits.filter((h) => h.line === idx), hits)
      box.appendChild(line)
      lines.push({ start: u.start, el: line })
    })
    playback.bind({ utterances: utts, lines, box })
    if (searchBox) searchBox.setCounter(searchIndex, hits.length)
    return { lines, hits }
  }

  function renderTranscript(r) {
    const utts = r.utterances || []
    const polished = r.polished || []
    if (!utts.length && !polished.length) return null
    return section('transcript', '原文', utts.length || polished.length, (d) => {
      const head = d.querySelector('summary')
      head.textContent = '原文'
      if (polished.length && utts.length) {
        const seg = el('span', 'atp-seg')
        ;[['polished', 'AI优化版'], ['raw', '原文']].forEach(([mode, label]) => {
          const b = el('button', transcriptMode === mode ? 'on' : '', label)
          b.type = 'button'
          b.addEventListener('click', (e) => {
            e.preventDefault()
            if (editing) return
            transcriptMode = mode
            render()
          })
          seg.appendChild(b)
        })
        head.appendChild(seg)
      }
      const usePolished = polished.length && (transcriptMode === 'polished' || !utts.length)
      const items = usePolished ? polished : utts
      const tools = el('div', 'atp-tools')
      d.appendChild(tools)
      const box = el('div', 'atp-lines')
      d.appendChild(box)
      const redraw = () => {
        box.textContent = ''
        return fillLines(box, items, usePolished, utts)
      }
      searchBox = mountSearchBox(tools, {
        query: searchQuery,
        onQuery: (q) => { searchQuery = q; searchIndex = 0; redraw() },
        onStep: (dir) => {
          const total = findMatches(items.map((u) => u.text), searchQuery).length
          if (!total) return
          searchIndex = (searchIndex + dir + total) % total
          const { lines, hits } = redraw()
          const target = lines[hits[searchIndex].line]
          if (target) {
            box.scrollTop = Math.max(0, target.el.offsetTop - box.clientHeight / 3)
            if (audio) audio.currentTime = target.start / 1000 // 跳到该句开头，不自动播放
          }
        }
      })
      redraw()
      if (!readOnly && !usePolished) {
        const editBtn = el('button', 'atp-btn', '编辑')
        editBtn.type = 'button'
        editBtn.disabled = r.status !== 'DONE' || hasFailedPlaceholder(utts)
        if (hasFailedPlaceholder(utts)) editBtn.title = '有转写失败的片段，请先重新转写'
        editBtn.addEventListener('click', () => startEditing(tools, box, utts))
        tools.appendChild(editBtn)
      }
    })
  }

  // 编辑中 render() 不重建面板：会触发重绘或后端改动的控件（原文/优化版切换、场景、状态行按钮）先禁用，退出编辑时 render 复原
  function lockForEditing() {
    root.querySelectorAll('.atp-seg button, select.atp-scene, .atp-status .atp-btn').forEach((c) => { c.disabled = true })
  }

  function startEditing(tools, box, utts) {
    tools.textContent = ''
    const finish = () => {
      editing = null
      setUnsavedEdit(editKey, false)
      render()
    }
    playback.bind({ utterances: utts, lines: [], box: null }) // 原文行被换成输入框：只保留跳过静音
    editing = mountEditor(box, utts, { fmt, onDirty: (dirty) => setUnsavedEdit(editKey, dirty) })
    lockForEditing()
    const doneBtn = el('button', 'atp-btn primary', '完成')
    const cancelBtn = el('button', 'atp-btn', '取消')
    doneBtn.type = cancelBtn.type = 'button'
    const hint = el('span', 'atp-status')
    doneBtn.addEventListener('click', async () => {
      const edits = diffEdits(utts, editing.texts())
      if (!edits.length) return finish()
      doneBtn.disabled = true
      try {
        record = await service.saveUtterances(noteId, blockId, edits)
        finish()
        notifyChanged(noteId, blockId)
      } catch (e) {
        doneBtn.disabled = false
        const conflict = e && e.response && e.response.status === 409
        hint.textContent = conflict ? '录音正在处理，修改未保存' : '保存失败，请稍后重试'
        hint.classList.add('error')
      }
    })
    cancelBtn.addEventListener('click', async () => {
      if (diffEdits(utts, editing.texts()).length) {
        try {
          await MessageBox.confirm('放弃对原文的修改？', '取消编辑', { type: 'warning' })
        } catch (e) {
          return
        }
      }
      finish()
    })
    tools.appendChild(doneBtn)
    tools.appendChild(cancelBtn)
    tools.appendChild(hint)
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
    if (editing) return // 重建会冲掉编辑框、让 editing 永远不归零；退出编辑（finish）时再 render
    root.textContent = ''
    const status = renderStatus()
    if (status) root.appendChild(status)
    const hasUtterances = !!record && (record.utterances || []).length > 0
    if (!record || (record.status === 'FAILED' && !hasUtterances) || (record.status !== 'DONE' && record.status !== 'FAILED' && !hasUtterances)) {
      if (!record || record.status === 'FAILED') root.appendChild(renderLegacy())
      playback.bind({ utterances: [], lines: [], box: null })
      return
    }
    root.appendChild(renderBar())
    if (record.summary) root.appendChild(renderSummary(record.summary))
    const t = renderTranscript(record)
    if (t) root.appendChild(t)
  }

  render()
  let seenConnected = false
  let unsubscribe = null
  let lastJson = null
  const stop = () => {
    if (unsubscribe) unsubscribe()
    unsubscribe = null
  }
  if (noteId && service) {
    unsubscribe = subscribe(noteId, blockId, service, (r) => {
      // Editor.js 重渲染 / 替换块时不一定调用 destroy()：根节点曾挂载、现已脱离文档就自行退订
      if (root.isConnected) seenConnected = true
      else if (seenConnected) { stop(); playback.destroy(); setUnsavedEdit(editKey, false); return }
      record = r
      // 同一笔记里别的录音在处理时每 5 秒都会回调：记录没变就不重绘，免得滚动位置、搜索框焦点、改名输入框被冲掉
      const json = JSON.stringify(r)
      if (json === lastJson) return
      lastJson = json
      if (editing) return // 编辑中只更新数据，不冲掉输入框；退出编辑时再 render
      render()
    })
  }

  return {
    destroy() {
      stop()
      playback.destroy()
      setUnsavedEdit(editKey, false)
      root.remove()
    }
  }
}
