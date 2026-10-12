/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/** 触发浏览器下载；桌面版 Electron 没注册 will-download 时会弹出系统保存对话框 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

export function backupFileName(date = new Date()) {
  const pad = n => String(n).padStart(2, '0')
  return `mindio-notes-${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}.mindio`
}

export function isDesktopRuntime() {
  return typeof window !== 'undefined' && !!window.mindioDesktop
}

/** responseType: 'blob' 时错误体也是 Blob，要先读成文本再取 message */
export async function errorMessageOf(error, fallback) {
  const data = error && error.response && error.response.data
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text())
      if (parsed && parsed.message) return parsed.message
    } catch (e) {
      // 不是 JSON，用兜底文案
    }
    return fallback
  }
  return (data && data.message) || (error && error.message) || fallback
}
