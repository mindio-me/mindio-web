/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 笔记分享卡片（website card）的前端合成：1200×630，背景 + 底部暗色遮罩 + 标题。
// 成品在前端用 canvas 画好再上传，避免服务器端各环境装中文字体。

export const SHARE_CARD_WIDTH = 1200
export const SHARE_CARD_HEIGHT = 630

/** 8 组预设渐变（起色、止色），按 135° 方向铺满卡片 */
export const SHARE_CARD_GRADIENTS = [
  ['#667eea', '#764ba2'],
  ['#2b5876', '#4e4376'],
  ['#0f2027', '#2c5364'],
  ['#11998e', '#38ef7d'],
  ['#ee0979', '#ff6a00'],
  ['#c94b4b', '#4b134f'],
  ['#1d976c', '#93f9b9'],
  ['#373b44', '#4286f4']
]

const TITLE_FONT = '600 56px "PingFang SC","Microsoft YaHei","Noto Sans CJK SC",sans-serif'
const TITLE_LINE_HEIGHT = 72
const SIDE_PADDING = 64
const BOTTOM_PADDING = 64
const MAX_TITLE_LINES = 2
const ELLIPSIS = '…'

export function defaultGradientIndex(noteId) {
  const n = Math.abs(Math.floor(Number(noteId) || 0))
  return n % SHARE_CARD_GRADIENTS.length
}

/**
 * 通过 axios 以 blob 方式取图并转成 objectURL。
 * 开发环境 API 跨源，直接 <img> 跨源图画进 canvas 会污染画布导致 toBlob 失败，所以背景图一律走这里。
 * 根相对地址（如 /api/uploads/...）按页面同源请求，不再拼 axios 的 baseURL（否则会变成 /api/api/...）。
 */
export async function loadImageAsObjectUrl(axios, url) {
  if (!url) throw new Error('empty image url')
  const config = { responseType: 'blob', suppressErrorToast: true }
  if (url.startsWith('/') && !url.startsWith('//')) config.baseURL = ''
  const response = await axios.get(url, config)
  return URL.createObjectURL(response.data)
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = src
  })
}

/** cover 方式：等比缩放到铺满画布，居中裁掉多余部分 */
function drawCover(ctx, img, width, height) {
  const iw = img.naturalWidth || img.width
  const ih = img.naturalHeight || img.height
  if (!iw || !ih) return
  const scale = Math.max(width / iw, height / ih)
  const sw = width / scale
  const sh = height / scale
  const sx = (iw - sw) / 2
  const sy = (ih - sh) / 2
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, width, height)
}

function drawGradient(ctx, index, width, height) {
  const pair = SHARE_CARD_GRADIENTS[index] || SHARE_CARD_GRADIENTS[0]
  const g = ctx.createLinearGradient(0, 0, width, height)
  g.addColorStop(0, pair[0])
  g.addColorStop(1, pair[1])
  ctx.fillStyle = g
  ctx.fillRect(0, 0, width, height)
}

const CJK_RE = /[⺀-鿿豈-﫿＀-￯぀-ヿ가-힯]/

/** 切词：CJK 字符逐字成词，其余连续非空白字符（拉丁单词、数字）整体成词；spaceBefore 表示该词前原文有空格 */
function tokenizeTitle(text) {
  const tokens = []
  let word = ''
  let space = false
  const flush = () => {
    if (word) tokens.push({ text: word, spaceBefore: space })
    if (word) space = false
    word = ''
  }
  for (const ch of Array.from(text)) {
    if (/\s/.test(ch)) {
      flush()
      space = true
    } else if (CJK_RE.test(ch)) {
      flush()
      tokens.push({ text: ch, spaceBefore: space })
      space = false
    } else {
      word += ch
    }
  }
  flush()
  return tokens
}

/** 按词折行（拉丁词不拆开，CJK 可逐字断），最多 maxLines 行；超出时最后一行截断并补省略号。单词比整行还宽时退化为逐字拆 */
export function wrapTitle(ctx, text, maxWidth, maxLines = MAX_TITLE_LINES) {
  const tokens = tokenizeTitle(String(text || '').trim())
  const fits = (s) => ctx.measureText(s).width <= maxWidth
  const lines = []
  let current = ''
  for (const tok of tokens) {
    const joined = current ? current + (tok.spaceBefore ? ' ' : '') + tok.text : tok.text
    if (fits(joined)) {
      current = joined
      continue
    }
    if (current) {
      lines.push(current)
      current = ''
    }
    if (fits(tok.text)) {
      current = tok.text
      continue
    }
    // 单个词超宽：逐字拆
    for (const ch of Array.from(tok.text)) {
      if (current && !fits(current + ch)) {
        lines.push(current)
        current = ch
      } else {
        current += ch
      }
    }
  }
  if (current) lines.push(current)
  if (lines.length <= maxLines) return lines

  // 行数超限：最后一行截断加省略号
  const kept = lines.slice(0, maxLines)
  let last = kept[maxLines - 1]
  while (last && !fits(last + ELLIPSIS)) {
    last = Array.from(last).slice(0, -1).join('')
  }
  kept[maxLines - 1] = last.replace(/\s+$/, '') + ELLIPSIS
  return kept
}

/**
 * 合成卡片，返回 1200×630 JPEG（质量 0.9）。
 * @param {object} opts
 * @param {string|null} opts.backgroundObjectUrl 背景图 objectURL（无则画渐变）
 * @param {number} opts.gradient 渐变预设编号（无背景时使用）
 * @param {string} opts.title 卡片标题
 * @param {boolean} opts.overlayTitle 是否在图上叠加标题
 */
export async function composeShareCard({ backgroundObjectUrl, gradient, title, overlayTitle }) {
  const width = SHARE_CARD_WIDTH
  const height = SHARE_CARD_HEIGHT
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')

  if (backgroundObjectUrl) {
    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, width, height)
    const img = await loadImage(backgroundObjectUrl)
    drawCover(ctx, img, width, height)
  } else {
    drawGradient(ctx, gradient, width, height)
  }

  const text = String(title || '').trim()
  if (overlayTitle && text) {
    if (backgroundObjectUrl) {
      // 底部 45% 线性暗色遮罩，保证标题在任意背景上可读
      const maskTop = height * 0.55
      const mask = ctx.createLinearGradient(0, maskTop, 0, height)
      mask.addColorStop(0, 'rgba(0,0,0,0)')
      mask.addColorStop(1, 'rgba(0,0,0,0.72)')
      ctx.fillStyle = mask
      ctx.fillRect(0, maskTop, width, height - maskTop)
    }

    // 等字体就绪再量宽，否则首次合成可能用回退字体折行
    if (document.fonts && document.fonts.load) {
      try { await document.fonts.load(TITLE_FONT, text) } catch (e) { /* 用系统回退字体 */ }
    }
    ctx.font = TITLE_FONT
    ctx.fillStyle = '#ffffff'
    ctx.textBaseline = 'alphabetic'
    ctx.textAlign = 'left'
    ctx.shadowColor = 'rgba(0,0,0,0.35)'
    ctx.shadowBlur = 8
    ctx.shadowOffsetY = 2

    const lines = wrapTitle(ctx, text, width - SIDE_PADDING * 2)
    const baselineLast = height - BOTTOM_PADDING
    lines.forEach((line, idx) => {
      const y = baselineLast - (lines.length - 1 - idx) * TITLE_LINE_HEIGHT
      ctx.fillText(line, SIDE_PADDING, y)
    })
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error('canvas toBlob failed'))
    }, 'image/jpeg', 0.9)
  })
}
