/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/** 创建一个 DOM 元素，attrs 里的键直接赋给元素属性（不是 setAttribute，方便传 style/textContent） */
export function createEl(tag, className, attrs = {}) {
  const el = document.createElement(tag)
  if (className) el.className = className
  Object.assign(el, attrs)
  return el
}

export function createButton(text, onClick, className = 'cdx-block-ui__btn') {
  const btn = createEl('button', className, { type: 'button', textContent: text })
  btn.addEventListener('click', onClick)
  return btn
}

// 1x1 透明 GIF，没有封面图时占位用
const TRANSPARENT_PIXEL = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'

// YouTube 播放器的红色圆角播放按钮（68x48）
const YOUTUBE_PLAY_ICON = '<svg viewBox="0 0 68 48" width="100%" height="100%">' +
  '<path d="M66.52 7.74c-.78-2.93-2.49-5.41-5.42-6.19C55.79.13 34 0 34 0S12.21.13 6.9 1.55C3.97 2.33 2.27 4.81 1.48 7.74.06 13.05 0 24 0 24s.06 10.95 1.48 16.26c.78 2.93 2.49 5.41 5.42 6.19C12.21 47.87 34 48 34 48s21.79-.13 27.1-1.55c2.93-.78 4.64-3.26 5.42-6.19C67.94 34.95 68 24 68 24s-.06-10.95-1.48-16.26z" fill="#f00"/>' +
  '<path d="M45 24 27 14v20" fill="#fff"/></svg>'

/**
 * 视频"点击播放"占位卡片：默认只显示封面图+播放按钮（一张普通图片，秒开），不直接加载
 * 第三方播放器 iframe——那才是嵌入视频重新打开笔记时慢的真正原因（YouTube/B站播放器整页
 * 的加载耗时，不是我们在等什么网络请求）。点击后调用 onPlay()，由调用方负责换成真正的
 * <iframe>。没有 posterUrl 时（B站/抖音目前没有可靠的公开封面渠道）退化成纯色背景+播放
 * 按钮，不尝试拿真封面。
 */
export function createVideoFacade(posterUrl, onPlay, className = 'cdx-video-facade', { youtube = false } = {}) {
  const facade = createEl('div', className)
  // 封面必须是真正的 <img>，不能用 CSS 背景图：Editor.js 判断块是否为空（Block.isEmpty）时只认
  // img/iframe/video 等媒体标签，纯背景图的占位会被当成空块，在它下方用"+"插入新块时整块被
  // 替换掉。没有封面的平台放一张透明图，外观不变（仍是纯色背景+播放按钮）。
  facade.appendChild(createEl('img', `${className}__poster`, { src: posterUrl || TRANSPARENT_PIXEL, alt: '' }))
  // YouTube 用和它播放器一样的红色圆角按钮（lite-youtube-embed 的做法），刚嵌入时看到的是真播放器，
  // 重新打开看到的是占位，两者按钮一致就不会让人觉得"变了"；其他平台用通用的圆形播放按钮
  facade.appendChild(youtube
    ? createEl('div', `${className}__play ${className}__play--youtube`, { innerHTML: YOUTUBE_PLAY_ICON })
    : createEl('div', `${className}__play`, {
      innerHTML: '<svg viewBox="0 0 24 24" width="28" height="28" fill="#fff"><path d="M8 5v14l11-7z"/></svg>'
    }))
  facade.addEventListener('click', onPlay)
  return facade
}

/**
 * 一组切换按钮（比如"从收藏搜/从笔记搜/手动添加"），点击后 onSwitch(key) 回调，
 * 调用方负责根据 key 切换实际展示的表单内容。
 */
export function createTabs(tabs, onSwitch) {
  const wrapper = createEl('div', 'cdx-block-ui__tabs')
  let active = tabs[0].key
  const buttons = tabs.map(({ key, label }) => {
    const btn = createButton(label, () => {
      active = key
      buttons.forEach((b) => b.classList.toggle('cdx-block-ui__tab--active', b.dataset.key === key))
      onSwitch(key)
    }, 'cdx-block-ui__tab')
    btn.dataset.key = key
    if (key === active) btn.classList.add('cdx-block-ui__tab--active')
    wrapper.appendChild(btn)
    return btn
  })
  return wrapper
}
