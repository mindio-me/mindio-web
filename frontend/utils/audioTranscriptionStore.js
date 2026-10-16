/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { MessageBox } from 'element-ui'

// 转写面板之间、以及录音胶囊 / 聊天面板通知面板"这个块的转写状态变了"的极简事件总线。
// 同时负责按笔记拉取转写记录（同一笔记的多个订阅共用一次请求），有 PENDING / PROCESSING 时轮询。
const changeListeners = new Set()

export function notifyChanged(noteId, blockId) {
  changeListeners.forEach((cb) => {
    try {
      cb(String(noteId), blockId)
    } catch (e) {}
  })
}

export function onChanged(cb) {
  changeListeners.add(cb)
  return () => changeListeners.delete(cb)
}

const POLL_MS = 5000
const notes = new Map() // noteId -> { subs: Set<{blockId, cb}>, records: Map, timer, inflight, service }

function isBusy(r) {
  return r && (r.status === 'PENDING' || r.status === 'PROCESSING')
}

// 已有请求在途时只打"脏"标记：在途请求可能早于刚发生的触发，结束后必须再拉一次才拿得到新状态
async function refresh(noteId) {
  const entry = notes.get(noteId)
  if (!entry) return
  if (entry.inflight) {
    entry.dirty = true
    return
  }
  entry.dirty = false
  clearTimeout(entry.timer)
  entry.timer = null
  entry.inflight = Promise.resolve()
    .then(() => entry.service.list(noteId))
    .then((list) => {
      entry.records = new Map((list || []).map((r) => [r.blockId, r]))
    })
    .catch((e) => { console.warn('拉取转写记录失败:', e) })
  await entry.inflight
  entry.inflight = null
  if (notes.get(noteId) !== entry) return
  if (entry.dirty) {
    refresh(noteId)
    return
  }
  ;[...entry.subs].forEach((s) => s.cb(entry.records.get(s.blockId) || null))
  schedule(noteId)
}

function schedule(noteId) {
  const entry = notes.get(noteId)
  if (!entry) return
  clearTimeout(entry.timer)
  const busy = [...entry.subs].some((s) => isBusy(entry.records.get(s.blockId)))
  entry.timer = busy ? setTimeout(() => refresh(noteId), POLL_MS) : null
}

/** 订阅某个音频块的转写记录；返回取消订阅函数。最后一个订阅者离开时停止轮询 */
export function subscribe(noteId, blockId, service, cb) {
  const key = String(noteId)
  let entry = notes.get(key)
  if (!entry) {
    entry = { subs: new Set(), records: new Map(), timer: null, inflight: null, dirty: false, service }
    notes.set(key, entry)
  }
  const sub = { blockId, cb }
  entry.subs.add(sub)
  refresh(key)
  return () => {
    entry.subs.delete(sub)
    if (!entry.subs.size) {
      clearTimeout(entry.timer)
      entry.timer = null
      if (notes.get(key) === entry) notes.delete(key)
    }
  }
}

// 录音胶囊 / 聊天面板 / 面板自己的按钮触发后：立刻重拉一次，之后按需轮询
onChanged((noteId) => {
  if (notes.has(noteId)) refresh(noteId)
})

// 原文编辑中有未保存的改动：笔记页切换 / 路由离开前确认；网页版另挂 beforeunload。
// 桌面版（Electron）不挂 beforeunload：Electron 会静默阻止关窗且不弹任何提示。
const unsavedEdits = new Set()

function onBeforeUnload(e) {
  e.preventDefault()
  e.returnValue = ''
}

function syncBeforeUnload() {
  if (typeof window === 'undefined' || window.mindioDesktop) return
  if (unsavedEdits.size) window.addEventListener('beforeunload', onBeforeUnload)
  else window.removeEventListener('beforeunload', onBeforeUnload)
}

export function setUnsavedEdit(key, dirty) {
  if (dirty) unsavedEdits.add(key)
  else unsavedEdits.delete(key)
  syncBeforeUnload()
}

export function hasUnsavedEdits() {
  return unsavedEdits.size > 0
}

/** 有未保存的原文修改时弹确认；用户选择离开返回 true */
export async function confirmDiscardEdits() {
  if (!hasUnsavedEdits()) return true
  try {
    await MessageBox.confirm('录音原文还有未保存的修改，离开后会丢失。', '放弃修改？', {
      type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑'
    })
    return true
  } catch (e) {
    return false
  }
}

export default { notifyChanged, onChanged, subscribe, setUnsavedEdit, hasUnsavedEdits, confirmDiscardEdits }
