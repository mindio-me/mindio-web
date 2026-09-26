/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

function isImageFile(file) {
  if (!file) return false
  const type = file.type || ''
  const name = file.name || ''
  return type.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp|svg|heic|heif|avif)$/i.test(name)
}

function dataUrlToFile(dataUrl, filename = 'pasted-image.png') {
  const match = dataUrl.match(/^data:(image\/[^;,]+)(;base64)?,(.*)$/i)
  if (!match) return null

  const mime = match[1]
  const isBase64 = !!match[2]
  const payload = match[3]
  const binary = isBase64 ? atob(payload) : decodeURIComponent(payload)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }

  const ext = mime.split('/')[1]?.replace('jpeg', 'jpg') || 'png'
  return new File([bytes], filename.replace(/\.\w+$/, `.${ext}`), { type: mime })
}

function getItemString(item) {
  return new Promise((resolve) => {
    try {
      item.getAsString((value) => resolve(value || ''))
    } catch (e) {
      resolve('')
    }
  })
}

function extractImageSrcFromHtml(html) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return doc.querySelector('img')?.getAttribute('src') || ''
}

// 从网页"复制图片"时，clipboardData.files/items 里的位图通常是浏览器对当前渲染帧做的
// 静态光栅化快照（type 多为 image/png），GIF 动画在这一步就已经丢失了；text/html 里的
// <img src> 才指向原始文件，命中 gif 时要优先用它换取动画，而不是直接用光栅位图
function looksLikeGif(src) {
  return /^data:image\/gif/i.test(src) || /\.gif(?:[?#]|$)/i.test(src)
}

export function clipboardMayContainImage(clipboardData) {
  if (!clipboardData) return false

  const files = Array.from(clipboardData.files || [])
  if (files.some(isImageFile)) return true

  const items = Array.from(clipboardData.items || [])
  return items.some((item) => {
    if (item.kind !== 'file') return false
    const type = item.type || ''
    return type === '' || type.startsWith('image/')
  })
}

export async function getClipboardImagePayload(clipboardData) {
  if (!clipboardData) return null

  const files = Array.from(clipboardData.files || [])
  let file = files.find(isImageFile) || null

  const items = Array.from(clipboardData.items || [])
  if (!file) {
    for (const item of items) {
      if (item.kind !== 'file') continue
      const type = item.type || ''
      if (type && !type.startsWith('image/')) continue
      const itemFile = item.getAsFile()
      if (isImageFile(itemFile)) {
        file = itemFile
        break
      }
    }
  }

  const htmlItem = items.find((item) => item.kind === 'string' && item.type === 'text/html')
  const htmlSrc = htmlItem ? extractImageSrcFromHtml(await getItemString(htmlItem)) : ''

  // 剪贴板位图不是 gif（说明它是光栅化快照），但 html 源指向 gif：优先取 html 源保留动画，
  // 光栅位图留作 url 拉取失败时的兜底
  if (htmlSrc && looksLikeGif(htmlSrc) && !(file && file.type === 'image/gif')) {
    if (htmlSrc.startsWith('data:image/')) {
      const dataFile = dataUrlToFile(htmlSrc)
      if (dataFile) return { file: dataFile }
    } else if (/^https?:\/\//i.test(htmlSrc)) {
      return { url: htmlSrc, fallbackFile: file }
    }
  }

  if (file) return { file }

  if (htmlSrc) {
    if (htmlSrc.startsWith('data:image/')) {
      const dataFile = dataUrlToFile(htmlSrc)
      if (dataFile) return { file: dataFile }
    }
    if (/^https?:\/\//i.test(htmlSrc)) return { url: htmlSrc }
  }

  return null
}

/**
 * 按 payload 上传：优先用 payload.file/url；url 拉取失败且有 fallbackFile（gif 优先场景的光栅快照）时降级重试
 */
export async function uploadClipboardImage(uploadService, payload, model, pid) {
  if (payload.file) {
    return uploadService.uploadLocal(payload.file, model, pid)
  }
  try {
    return await uploadService.uploadRemote(payload.url, model, pid)
  } catch (err) {
    if (payload.fallbackFile) {
      return uploadService.uploadLocal(payload.fallbackFile, model, pid)
    }
    throw err
  }
}
