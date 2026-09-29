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
// <img src> 才指向原始文件，命中 gif 时要优先用它换取动画。但有些 CDN 地址完全看不出扩展名
// （如 LinkedIn 的 /dms/image/v2/... 后面只跟一串数字和签名参数），这种"看不出是什么格式"
// 的地址也一并交给后端按真实 Content-Type 判断，其余明确写了扩展名的（包括 .webp）维持原判断，
// 不额外绕一趟后端
function pathnameOf(src) {
  try {
    return new URL(src).pathname
  } catch (e) {
    return src.split(/[?#]/)[0]
  }
}

function looksLikeGif(src) {
  return /^data:image\/gif/i.test(src) || /\.gif$/i.test(pathnameOf(src))
}

function hasKnownExtension(src) {
  return /\.[a-z0-9]{2,5}$/i.test(pathnameOf(src))
}

// 剪贴板位图本身就是这些格式时，拿到的已经是原始文件，不必再绕一趟后端
const ANIMATABLE_MIME = ['image/gif', 'image/webp']

function mayHoldAnimation(src) {
  if (/^data:/i.test(src)) return /^data:image\/gif/i.test(src)
  return looksLikeGif(src) || !hasKnownExtension(src)
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

  // 剪贴板位图不是原始动图格式（说明它是光栅化快照），而 html 源又没明说自己是静态格式：
  // 优先取 html 源，让后端按真实 Content-Type 抓取，光栅位图留作 url 拉取失败时的兜底
  if (htmlSrc && mayHoldAnimation(htmlSrc) && !(file && ANIMATABLE_MIME.includes(file.type))) {
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
