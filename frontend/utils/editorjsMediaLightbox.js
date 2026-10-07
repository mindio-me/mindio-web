/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { createEl, createButton } from './editorjsUiHelpers'
import { getTweetIdFromEmbedUrl, getTweetResizeHeight, withAutoplay } from './videoEmbedResolver'

const escapeAttr = (s) => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')

/** 按宽高比生成一个尽量大、但不超出视口的媒体框 */
function createFrame(aspectRatio) {
  const [w, h] = String(aspectRatio || '16/9').split('/').map(Number)
  const ratio = w > 0 && h > 0 ? w / h : 16 / 9
  const frame = createEl('div', 'media-lightbox__frame')
  frame.style.aspectRatio = `${w || 16} / ${h || 9}`
  frame.style.width = `min(90vw, calc(82vh * ${ratio}))`
  return frame
}

/**
 * video.twimg.com 有防盗链：带别的站点的 Referer 会 403，不带 Referer 正常返回。<video> 元素没有
 * referrerpolicy 属性，所以把播放器放进一个 srcdoc 内嵌页，在里面声明 no-referrer。
 */
function createNoReferrerVideo(videoUrl) {
  const iframe = createEl('iframe', 'media-lightbox__media', { allowFullscreen: true })
  iframe.setAttribute('allow', 'autoplay; fullscreen')
  iframe.srcdoc = '<!doctype html><meta name="referrer" content="no-referrer">' +
    '<style>html,body{margin:0;height:100%;background:#000}video{display:block;width:100%;height:100%}</style>' +
    `<video src="${escapeAttr(videoUrl)}" controls autoplay playsinline></video>`
  return iframe
}

/**
 * 媒体画廊的弹窗查看器：图片看原图，视频在弹窗里直接播放（平台播放器带 autoplay=1；
 * X 推文用原生播放器播 mp4 直链，取不到直链时退回推文卡片）。
 * 交互对齐常见相册灯箱：深色遮罩、右上角关闭、左右切换（箭头 / ←→）、底部计数和说明，
 * Esc 或点遮罩关闭。返回关闭函数，调用方销毁时可以顺手关掉。
 */
export function openMediaLightbox(items, startIndex, { fetchTweetVideo } = {}) {
  if (!items.length) return () => {}
  let index = Math.max(0, Math.min(startIndex, items.length - 1))
  // 每次切换加一，异步请求回来时对不上就丢弃，避免切走后又把上一项的视频塞进来
  let renderSeq = 0
  let onMessage = null

  const overlay = createEl('div', 'media-lightbox', { tabIndex: -1 })
  overlay.setAttribute('role', 'dialog')
  overlay.setAttribute('aria-modal', 'true')
  const stage = createEl('div', 'media-lightbox__stage')
  const footer = createEl('div', 'media-lightbox__footer')
  const counter = createEl('span', 'media-lightbox__counter')
  const caption = createEl('span', 'media-lightbox__caption')
  const original = createEl('a', 'media-lightbox__original', { textContent: '查看原图', target: '_blank', rel: 'noopener noreferrer' })
  footer.append(counter, caption, original)
  const closeBtn = createButton('×', () => close(), 'media-lightbox__close')
  closeBtn.setAttribute('aria-label', '关闭')
  overlay.append(stage, footer, closeBtn)
  if (items.length > 1) {
    const prevBtn = createButton('‹', () => go(-1), 'media-lightbox__nav media-lightbox__nav--prev')
    const nextBtn = createButton('›', () => go(1), 'media-lightbox__nav media-lightbox__nav--next')
    prevBtn.setAttribute('aria-label', '上一个')
    nextBtn.setAttribute('aria-label', '下一个')
    overlay.append(prevBtn, nextBtn)
  }

  const clearStage = () => {
    // 移除 iframe / video 即停止播放
    stage.innerHTML = ''
    if (onMessage) {
      window.removeEventListener('message', onMessage)
      onMessage = null
    }
  }

  const showTweetCard = (item) => {
    const frame = createEl('div', 'media-lightbox__frame media-lightbox__frame--tweet')
    const iframe = createEl('iframe', 'media-lightbox__media', { src: item.embedUrl, frameBorder: '0', allowFullscreen: true, scrolling: 'no' })
    frame.appendChild(iframe)
    onMessage = (e) => {
      const height = getTweetResizeHeight(e, iframe)
      if (height) frame.style.height = `${height}px`
    }
    window.addEventListener('message', onMessage)
    stage.appendChild(frame)
  }

  const showTweet = (item, tweetId, seq) => {
    if (!fetchTweetVideo) {
      showTweetCard(item)
      return
    }
    // 取直链期间先显示封面，避免一片空白
    const loading = createFrame('16/9')
    loading.classList.add('media-lightbox__loading')
    if (item.posterUrl) loading.style.backgroundImage = `url(${item.posterUrl})`
    stage.appendChild(loading)
    fetchTweetVideo(tweetId).then((video) => {
      if (seq !== renderSeq) return
      clearStage()
      if (video && video.videoUrl) {
        const frame = createFrame(video.aspectRatio)
        frame.appendChild(createNoReferrerVideo(video.videoUrl))
        stage.appendChild(frame)
      } else {
        showTweetCard(item)
      }
    })
  }

  const render = () => {
    const seq = ++renderSeq
    clearStage()
    const item = items[index]
    counter.textContent = items.length > 1 ? `${index + 1} / ${items.length}` : ''
    caption.textContent = item.caption || ''
    original.style.display = item.type === 'image' ? '' : 'none'
    if (item.type === 'image') {
      original.href = item.url
      stage.appendChild(createEl('img', 'media-lightbox__img', { src: item.url, alt: item.caption || '' }))
      return
    }
    const tweetId = getTweetIdFromEmbedUrl(item.embedUrl)
    if (tweetId) {
      showTweet(item, tweetId, seq)
      return
    }
    // 抖音播放器是竖屏固定比例，其余平台按 16:9
    const frame = createFrame(/open\.douyin\.com/.test(item.embedUrl) ? '9/20' : '16/9')
    const iframe = createEl('iframe', 'media-lightbox__media', { src: withAutoplay(item.embedUrl), frameBorder: '0', allowFullscreen: true })
    iframe.setAttribute('allow', 'autoplay; fullscreen; encrypted-media; picture-in-picture')
    iframe.setAttribute('referrerpolicy', 'unsafe-url')
    frame.appendChild(iframe)
    stage.appendChild(frame)
  }

  const go = (step) => {
    index = (index + step + items.length) % items.length
    render()
  }

  // 捕获阶段拦截，免得 ←/→/Esc 被编辑器当成光标移动或其他快捷键处理
  const onKey = (e) => {
    if (e.key === 'Escape') close()
    else if (e.key === 'ArrowLeft' && items.length > 1) go(-1)
    else if (e.key === 'ArrowRight' && items.length > 1) go(1)
    else return
    e.preventDefault()
    e.stopPropagation()
  }

  const previousOverflow = document.body.style.overflow
  const previousFocus = document.activeElement
  let closed = false
  function close() {
    if (closed) return
    closed = true
    clearStage()
    window.removeEventListener('keydown', onKey, true)
    overlay.remove()
    document.body.style.overflow = previousOverflow
    if (previousFocus && previousFocus.focus) previousFocus.focus({ preventScroll: true })
  }

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay || e.target === stage) close()
  })
  window.addEventListener('keydown', onKey, true)
  document.body.style.overflow = 'hidden'
  document.body.appendChild(overlay)
  overlay.focus()
  render()
  return close
}
