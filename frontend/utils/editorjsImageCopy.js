/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { toolTranslator } from './editorjsUiHelpers'

// 剪贴板 html 里 <img> 上的本站标记：带上复制来源 origin 和原图块数据，
// 粘贴回同一站点的编辑器时直接引用原文件，不再上传一份
const MARKER_ATTR = 'data-mindio-image'

function absoluteUrl(url) {
  try {
    return new URL(url, window.location.href).href
  } catch (e) {
    return url
  }
}

export function buildClipboardHtml(data, url) {
  const img = document.createElement('img')
  img.setAttribute('src', url)
  if (data.caption) img.setAttribute('alt', data.caption.replace(/<[^>]*>/g, ''))
  img.setAttribute(MARKER_ATTR, JSON.stringify({ origin: window.location.origin, data }))
  return img.outerHTML
}

// 外部应用（微信、画图等）只认位图，统一转成 PNG——浏览器剪贴板写入只支持 image/png
async function fetchAsPng(url) {
  const resp = await fetch(url, { mode: 'cors', credentials: 'omit' })
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  const blob = await resp.blob()
  if (blob.type === 'image/png') return blob

  const objectUrl = URL.createObjectURL(blob)
  try {
    const img = new Image()
    img.src = objectUrl
    await img.decode()
    const canvas = document.createElement('canvas')
    canvas.width = img.naturalWidth || 300
    canvas.height = img.naturalHeight || 150
    canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
    return await new Promise((resolve, reject) => {
      canvas.toBlob(b => (b ? resolve(b) : reject(new Error('toBlob failed'))), 'image/png')
    })
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}

// navigator.clipboard 只在安全上下文（https/localhost）可用；局域网 http 访问时退回 execCommand，
// 这条路只能写文本类格式，位图带不上
function copyTextFormats(html, text) {
  const onCopy = (e) => {
    e.clipboardData.setData('text/html', html)
    e.clipboardData.setData('text/plain', text)
    e.preventDefault()
    e.stopImmediatePropagation()
  }
  window.addEventListener('copy', onCopy, true)
  try {
    return document.execCommand('copy')
  } finally {
    window.removeEventListener('copy', onCopy, true)
  }
}

/**
 * 读取剪贴板 html 里的本站图片标记；只认同一 origin 复制出来的，
 * 别的站点/实例复制来的图片仍按普通图片粘贴上传，避免笔记依赖另一台服务器上的文件
 */
export function parseImageCopyMarker(html) {
  if (!html || html.indexOf(MARKER_ATTR) === -1) return null
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const raw = doc.querySelector(`img[${MARKER_ATTR}]`)?.getAttribute(MARKER_ATTR)
  if (!raw) return null
  try {
    const marker = JSON.parse(raw)
    if (marker.origin !== window.location.origin) return null
    if (!marker.data || !marker.data.file || !marker.data.file.url) return null
    return marker.data
  } catch (e) {
    return null
  }
}

/**
 * 图片块「复制图片」Block Tune
 * 剪贴板同时写入 PNG 位图（外部应用）、带本站标记的 html（站内粘贴引用原文件）、图片链接纯文本。
 */
export default class ImageCopyTune {
  static get isTune() {
    return true
  }

  constructor({ api, block, config }) {
    this.block = block
    this._t = toolTranslator(api)
    this.notify = (config && config.notify) || (() => {})
  }

  render() {
    return {
      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
      label: this._t('Copy image'),
      closeOnActivate: true,
      onActivate: () => this._copy()
    }
  }

  save() {
    return {}
  }

  async _copy() {
    // ClipboardItem 各格式都传 Promise，write() 在点击的同步调用栈里发起，Safari 也认这次用户操作
    const dataPromise = this.block.save().then(({ data }) => {
      const result = { ...data }
      const el = this.block.holder && this.block.holder.querySelector('.image-tool__image')
      const wp = el && parseInt(el.style.width, 10)
      if (wp) result.widthPercent = wp
      return result
    })
    const urlPromise = dataPromise.then(d => absoluteUrl(d.file.url))
    const htmlPromise = Promise.all([dataPromise, urlPromise]).then(([d, u]) => buildClipboardHtml(d, u))

    const canWrite = typeof ClipboardItem !== 'undefined' && navigator.clipboard && navigator.clipboard.write
    if (canWrite) {
      const textBlob = (p, type) => p.then(s => new Blob([s], { type }))
      try {
        await navigator.clipboard.write([new ClipboardItem({
          'image/png': urlPromise.then(fetchAsPng),
          'text/html': textBlob(htmlPromise, 'text/html'),
          'text/plain': textBlob(urlPromise, 'text/plain')
        })])
        this.notify('success', this._t('Image copied'))
        return
      } catch (e) {
        console.warn('复制图片位图失败，退回只复制链接:', e)
      }
      try {
        await navigator.clipboard.write([new ClipboardItem({
          'text/html': textBlob(htmlPromise, 'text/html'),
          'text/plain': textBlob(urlPromise, 'text/plain')
        })])
        this.notify('warning', this._t('Copied as link only; the image itself could not be copied'))
        return
      } catch (e) {
        console.warn('剪贴板写入失败，退回 execCommand:', e)
      }
    }

    try {
      const [html, url] = await Promise.all([htmlPromise, urlPromise])
      if (copyTextFormats(html, url)) {
        this.notify('warning', this._t('Copied as link only; the image itself could not be copied'))
        return
      }
    } catch (e) {
      console.error('复制图片失败:', e)
    }
    this.notify('error', this._t('Copy failed'))
  }
}
