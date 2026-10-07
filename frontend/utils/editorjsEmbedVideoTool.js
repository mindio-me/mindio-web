/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { resolveVideoEmbed as resolveEmbed, fetchVimeoPoster, getTweetResizeHeight, isYouTubeEmbed } from './videoEmbedResolver'
import { createVideoFacade } from './editorjsUiHelpers'

class EmbedVideoTool {
  static get toolbox() {
    return {
      title: '网络视频',
      icon: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <polygon points="10 8 16 12 10 16 10 8"/>
      </svg>`
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, api, readOnly }) {
    this.data = data || {}
    this.api = api
    this.readOnly = readOnly
    this._element = null
    // 只是渲染态，不持久化——重新打开笔记时应该总是先显示封面图（见 _renderEmbed 的注释），
    // 不应该记住"上次点开播放过"这件事。
    this._playing = false
    this._onMessage = null
  }

  render() {
    const wrapper = document.createElement('div')
    wrapper.classList.add('embed-video-tool')

    if (this.data.embedUrl) {
      this._renderEmbed(wrapper)
    } else if (!this.readOnly) {
      this._renderInputUI(wrapper)
    }

    this._element = wrapper
    return wrapper
  }

  // 重新打开笔记时默认只显示封面图（秒开的一张图片），不直接加载第三方播放器 iframe——
  // 那才是嵌入视频重新打开笔记时慢的真正原因（YouTube/B站播放器整页的加载耗时，不是我们
  // 在等什么网络请求）。点击封面才真正挂载 iframe，跟其它笔记里的普通图片一样快。
  _renderEmbed(wrapper) {
    wrapper.style.display = 'flex'
    wrapper.style.flexDirection = 'column'
    wrapper.style.alignItems = 'center'

    const frame = document.createElement('div')
    frame.classList.add('embed-video-tool__frame')

    if (this.data.fixedWidth) {
      frame.setAttribute('data-fixed', 'true')
      frame.style.cssText = `width:${this.data.fixedWidth}px;max-width:100%;height:${this.data.fixedHeight}px;border-radius:8px`
    } else {
      frame.style.cssText = `width:${this.data.widthPercent || 100}%;aspect-ratio:${this.data.aspectRatio || '16/9'};border-radius:8px`
    }

    // X 推文例外：拿不到封面图，占位只能是一块黑底，看不出是哪条推文；而推文卡片本身就是
    // 预览（头像/正文/视频缩略图，不会自动播放），所以直接加载卡片，用 loading=lazy 让屏幕外
    // 的卡片滚到附近才加载，尽量不拖慢打开笔记。
    const isTweet = /^https:\/\/platform\.twitter\.com\//.test(this.data.embedUrl)
    if (this._playing || isTweet) {
      const iframe = document.createElement('iframe')
      if (isTweet) iframe.loading = 'lazy'
      iframe.src = this.data.embedUrl
      iframe.setAttribute('frameborder', '0')
      iframe.setAttribute('allowfullscreen', 'true')
      iframe.setAttribute('scrolling', 'no')
      iframe.setAttribute('referrerpolicy', 'unsafe-url')
      iframe.style.cssText = 'width:100%;height:100%;border-radius:8px;display:block'
      frame.appendChild(iframe)
      if (this.data.autoHeight) this._listenResize(iframe, frame)
    } else {
      frame.appendChild(createVideoFacade(this.data.posterUrl, () => {
        this._playing = true
        wrapper.innerHTML = ''
        this._renderEmbed(wrapper)
      }, 'embed-video-tool__facade', { youtube: isYouTubeEmbed(this.data.embedUrl) }))
    }

    wrapper.appendChild(frame)

    if (this.data.caption) {
      const caption = document.createElement('div')
      caption.classList.add('embed-video-tool__caption')
      caption.textContent = this.data.caption
      wrapper.appendChild(caption)
    }
  }

  // 推文卡片按 iframe 发来的 resize 消息自适应高度（见 getTweetResizeHeight）。量到的高度写回 data，
  // 下次重新打开笔记时封面占位的高度就和真实卡片一致，不会点开后跳一下。
  _listenResize(iframe, frame) {
    this._removeResizeListener()
    this._onMessage = (e) => {
      const height = getTweetResizeHeight(e, iframe)
      if (!height) return
      frame.style.height = `${height}px`
      this.data.fixedHeight = height
    }
    window.addEventListener('message', this._onMessage)
  }

  _removeResizeListener() {
    if (this._onMessage) {
      window.removeEventListener('message', this._onMessage)
      this._onMessage = null
    }
  }

  _renderInputUI(wrapper) {
    const row = document.createElement('div')
    row.classList.add('embed-video-tool__input-row')

    const input = document.createElement('input')
    input.type = 'text'
    input.placeholder = '粘贴 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频链接...'
    input.classList.add('embed-video-tool__input')

    const btn = document.createElement('button')
    btn.type = 'button'
    btn.textContent = '嵌入'
    btn.classList.add('embed-video-tool__btn')

    const error = document.createElement('div')
    error.classList.add('embed-video-tool__error')

    const doEmbed = () => {
      const url = input.value.trim()
      if (!url) return
      const result = resolveEmbed(url)
      if (result) {
        this.data.embedUrl = result.embedUrl
        this.data.sourceUrl = url
        this.data.posterUrl = result.posterUrl || null
        if (result.fixedWidth) {
          this.data.fixedWidth = result.fixedWidth
          this.data.fixedHeight = result.fixedHeight
          this.data.autoHeight = result.autoHeight
        } else {
          this.data.aspectRatio = result.aspectRatio
          if (!this.data.widthPercent) {
            this.data.widthPercent = result.defaultWidth
          }
        }
        // 刚粘贴完链接，用户就是要看这个视频，直接挂播放器；封面占位只用于重新打开笔记时秒开
        this._playing = true
        wrapper.innerHTML = ''
        this._renderEmbed(wrapper)
        // Vimeo 封面要异步调接口拿，拿到后存进 data（此时已在播放，不用重绘）；只发生在插入的这一刻，
        // posterUrl 会随 save() 一起持久化，以后重新打开不会再发这个请求。
        if (result.needsOembedPoster) {
          fetchVimeoPoster(url).then((posterUrl) => {
            if (!posterUrl) return
            this.data.posterUrl = posterUrl
            if (!this._playing) {
              wrapper.innerHTML = ''
              this._renderEmbed(wrapper)
            }
          })
        }
      } else {
        error.textContent = '无法识别链接，请粘贴 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频地址'
      }
    }

    btn.addEventListener('click', doEmbed)
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') doEmbed() })

    row.appendChild(input)
    row.appendChild(btn)
    wrapper.appendChild(row)
    wrapper.appendChild(error)

    setTimeout(() => input.focus(), 0)
  }

  save() {
    return {
      embedUrl: this.data.embedUrl || '',
      sourceUrl: this.data.sourceUrl || '',
      posterUrl: this.data.posterUrl || null,
      caption: this.data.caption || '',
      widthPercent: this.data.fixedWidth ? null : (this.data.widthPercent || 100),
      aspectRatio: this.data.aspectRatio || '16/9',
      fixedWidth: this.data.fixedWidth || null,
      fixedHeight: this.data.fixedHeight || null,
      autoHeight: !!this.data.autoHeight
    }
  }

  destroy() {
    this._removeResizeListener()
  }

  validate(data) {
    return !!(data && data.embedUrl && data.embedUrl.trim())
  }
}

export default EmbedVideoTool
