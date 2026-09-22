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

/**
 * 视频"点击播放"占位卡片：默认只显示封面图+播放按钮（一张普通图片，秒开），不直接加载
 * 第三方播放器 iframe——那才是嵌入视频重新打开笔记时慢的真正原因（YouTube/B站播放器整页
 * 的加载耗时，不是我们在等什么网络请求）。点击后调用 onPlay()，由调用方负责换成真正的
 * <iframe>。没有 posterUrl 时（B站/抖音目前没有可靠的公开封面渠道）退化成纯色背景+播放
 * 按钮，不尝试拿真封面。
 */
export function createVideoFacade(posterUrl, onPlay, className = 'cdx-video-facade') {
  const facade = createEl('div', className)
  if (posterUrl) {
    facade.style.backgroundImage = `url(${posterUrl})`
  }
  facade.appendChild(createEl('div', `${className}__play`, {
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
