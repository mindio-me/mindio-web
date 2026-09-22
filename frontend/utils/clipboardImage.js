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
  const file = files.find(isImageFile)
  if (file) return { file }

  const items = Array.from(clipboardData.items || [])
  for (const item of items) {
    if (item.kind !== 'file') continue
    const type = item.type || ''
    if (type && !type.startsWith('image/')) continue
    const itemFile = item.getAsFile()
    if (isImageFile(itemFile)) return { file: itemFile }
  }

  const htmlItem = items.find((item) => item.kind === 'string' && item.type === 'text/html')
  if (htmlItem) {
    const html = await getItemString(htmlItem)
    const src = extractImageSrcFromHtml(html)
    if (src.startsWith('data:image/')) {
      const dataFile = dataUrlToFile(src)
      if (dataFile) return { file: dataFile }
    }
    if (/^https?:\/\//i.test(src)) return { url: src }
  }

  return null
}
