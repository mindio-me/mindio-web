/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 转写面板之间、以及录音胶囊 / 聊天面板通知面板"这个块的转写状态变了"的极简事件总线。
// Task 11 在这里补上按笔记拉取和轮询。
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

export default { notifyChanged, onChanged }
