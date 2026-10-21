/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { toolTranslator } from './editorjsUiHelpers'

// 剪贴板 html 里的本站标记：带上复制来源 origin、块类型和原块数据，
// 粘贴回同一站点的编辑器时直接引用原文件，不再上传一份
const MARKER_ATTR = 'data-mindio-block'
// 早期只支持图片时的标记，只含 origin 和图片块数据
const LEGACY_IMAGE_ATTR = 'data-mindio-image'

// 各类媒体块：宽度所在的 DOM（imageResizer 拖拽改的是它，块数据里的宽度可能还没回写）
// 和给外部应用的链接（嵌入视频给原视频页面，不给播放器地址）
const MEDIA = {
  image: { widthSelector: '.image-tool__image', link: d => d.file && d.file.url },
  video: { widthSelector: '.video-tool__video', link: d => d.url },
  embed: { widthSelector: '.embed-video-tool__frame:not([data-fixed="true"])', link: d => d.sourceUrl || d.embedUrl }
}

function absoluteUrl(url) {
  try {
    return new URL(url, window.location.href).href
  } catch (e) {
    return url
  }
}

function plainCaption(caption) {
  return (caption || '').replace(/<[^>]*>/g, '')
}

/** 媒体块里承载宽度的元素；固定尺寸的嵌入视频（推文卡片等）没有 */
export function mediaWidthElement(holder, type) {
  const media = MEDIA[type]
  return (holder && media && holder.querySelector(media.widthSelector)) || null
}

/** 图片放 <img>（富文本应用可直接显示），视频放链接 */
export function buildClipboardHtml(type, data, link) {
  const el = document.createElement(type === 'image' ? 'img' : 'a')
  if (type === 'image') {
    el.setAttribute('src', link)
    if (data.caption) el.setAttribute('alt', plainCaption(data.caption))
  } else {
    el.setAttribute('href', link)
    el.textContent = plainCaption(data.caption) || link
  }
  el.setAttribute(MARKER_ATTR, JSON.stringify({ origin: window.location.origin, type, data }))
  return el.outerHTML
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

function readMarker(doc) {
  const el = doc.querySelector(`[${MARKER_ATTR}]`)
  if (el) {
    const marker = JSON.parse(el.getAttribute(MARKER_ATTR))
    return { origin: marker.origin, type: marker.type, data: marker.data }
  }
  const legacy = doc.querySelector(`img[${LEGACY_IMAGE_ATTR}]`)
  if (legacy) {
    const marker = JSON.parse(legacy.getAttribute(LEGACY_IMAGE_ATTR))
    return { origin: marker.origin, type: 'image', data: marker.data }
  }
  return null
}

/**
 * 读取剪贴板 html 里的本站媒体块标记，返回 { type, data }；只认同一 origin 复制出来的，
 * 别的站点/实例复制来的仍按普通内容粘贴，避免笔记依赖另一台服务器上的文件
 */
export function parseBlockCopyMarker(html) {
  if (!html || (html.indexOf(MARKER_ATTR) === -1 && html.indexOf(LEGACY_IMAGE_ATTR) === -1)) return null
  try {
    const marker = readMarker(new DOMParser().parseFromString(html, 'text/html'))
    if (!marker || marker.origin !== window.location.origin) return null
    const media = MEDIA[marker.type]
    if (!media || !marker.data || !media.link(marker.data)) return null
    return { type: marker.type, data: marker.data }
  } catch (e) {
    return null
  }
}

/**
 * 图片/视频/嵌入视频块的「复制」Block Tune（按所在块的 block.name 区分）
 * 剪贴板写入带本站标记的 html（站内粘贴引用原文件）和链接纯文本；图片额外写 PNG 位图给外部应用。
 * 视频文件放不进浏览器剪贴板，外部应用只能拿到链接。
 */
export default class MediaCopyTune {
  static get isTune() {
    return true
  }

  constructor({ api, block, config }) {
    this.block = block
    this.type = block && block.name
    this._t = toolTranslator(api)
    this.notify = (config && config.notify) || (() => {})
  }

  render() {
    const isImage = this.type === 'image'
    return {
      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
      label: this._t(isImage ? 'Copy image' : 'Copy video'),
      closeOnActivate: true,
      onActivate: () => this._copy()
    }
  }

  save() {
    return {}
  }

  async _copy() {
    const type = this.type
    const isImage = type === 'image'
    const linkOnly = this._t('Copied as link only; the image itself could not be copied')
    // ClipboardItem 各格式都传 Promise，write() 在点击的同步调用栈里发起，Safari 也认这次用户操作
    const dataPromise = this.block.save().then(({ data }) => {
      const result = { ...data }
      const el = mediaWidthElement(this.block.holder, type)
      const wp = el && parseInt(el.style.width, 10)
      if (wp) result.widthPercent = wp
      return result
    })
    const linkPromise = dataPromise.then(d => absoluteUrl(MEDIA[type].link(d)))
    const htmlPromise = Promise.all([dataPromise, linkPromise]).then(([d, l]) => buildClipboardHtml(type, d, l))

    const canWrite = typeof ClipboardItem !== 'undefined' && navigator.clipboard && navigator.clipboard.write
    if (canWrite) {
      const textBlob = (p, mime) => p.then(s => new Blob([s], { type: mime }))
      const textFormats = () => ({
        'text/html': textBlob(htmlPromise, 'text/html'),
        'text/plain': textBlob(linkPromise, 'text/plain')
      })
      if (isImage) {
        try {
          await navigator.clipboard.write([new ClipboardItem({ 'image/png': linkPromise.then(fetchAsPng), ...textFormats() })])
          this.notify('success', this._t('Image copied'))
          return
        } catch (e) {
          console.warn('复制图片位图失败，退回只复制链接:', e)
        }
      }
      try {
        await navigator.clipboard.write([new ClipboardItem(textFormats())])
        if (isImage) this.notify('warning', linkOnly)
        else this.notify('success', this._t('Video copied'))
        return
      } catch (e) {
        console.warn('剪贴板写入失败，退回 execCommand:', e)
      }
    }

    try {
      const [html, link] = await Promise.all([htmlPromise, linkPromise])
      if (copyTextFormats(html, link)) {
        if (isImage) this.notify('warning', linkOnly)
        else this.notify('success', this._t('Video copied'))
        return
      }
    } catch (e) {
      console.error('复制失败:', e)
    }
    this.notify('error', this._t('Copy failed'))
  }
}
