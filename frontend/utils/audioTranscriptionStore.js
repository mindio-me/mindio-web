/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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

async function refresh(noteId) {
  const entry = notes.get(noteId)
  if (!entry) return
  if (!entry.inflight) {
    entry.inflight = entry.service.list(noteId)
      .then((list) => {
        entry.records = new Map((list || []).map((r) => [r.blockId, r]))
      })
      .catch(() => {})
      .finally(() => { entry.inflight = null })
  }
  await entry.inflight
  if (!notes.has(noteId)) return
  entry.subs.forEach((s) => s.cb(entry.records.get(s.blockId) || null))
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
    entry = { subs: new Set(), records: new Map(), timer: null, inflight: null, service }
    notes.set(key, entry)
  }
  const sub = { blockId, cb }
  entry.subs.add(sub)
  refresh(key)
  return () => {
    entry.subs.delete(sub)
    if (!entry.subs.size) {
      clearTimeout(entry.timer)
      notes.delete(key)
    }
  }
}

// 录音胶囊 / 聊天面板 / 面板自己的按钮触发后：立刻重拉一次，之后按需轮询
onChanged((noteId) => {
  if (notes.has(noteId)) refresh(noteId)
})

export default { notifyChanged, onChanged, subscribe }
