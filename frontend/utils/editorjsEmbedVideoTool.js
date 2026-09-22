/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { resolveVideoEmbed as resolveEmbed, fetchVimeoPoster } from './videoEmbedResolver'
import { createVideoFacade } from './editorjsUiHelpers'

class EmbedVideoTool {
  static get toolbox() {
    return {
      title: '嵌入视频3',
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
      frame.style.cssText = `width:${this.data.fixedWidth}px;height:${this.data.fixedHeight}px;border-radius:8px`
    } else {
      frame.style.cssText = `width:${this.data.widthPercent || 100}%;aspect-ratio:${this.data.aspectRatio || '16/9'};border-radius:8px`
    }

    if (this._playing) {
      const iframe = document.createElement('iframe')
      iframe.src = this.data.embedUrl
      iframe.setAttribute('frameborder', '0')
      iframe.setAttribute('allowfullscreen', 'true')
      iframe.setAttribute('scrolling', 'no')
      iframe.setAttribute('referrerpolicy', 'unsafe-url')
      iframe.style.cssText = 'width:100%;height:100%;border-radius:8px;display:block'
      frame.appendChild(iframe)
    } else {
      frame.appendChild(createVideoFacade(this.data.posterUrl, () => {
        this._playing = true
        wrapper.innerHTML = ''
        this._renderEmbed(wrapper)
      }, 'embed-video-tool__facade'))
    }

    wrapper.appendChild(frame)

    if (this.data.caption) {
      const caption = document.createElement('div')
      caption.classList.add('embed-video-tool__caption')
      caption.textContent = this.data.caption
      wrapper.appendChild(caption)
    }
  }

  _renderInputUI(wrapper) {
    const row = document.createElement('div')
    row.classList.add('embed-video-tool__input-row')

    const input = document.createElement('input')
    input.type = 'text'
    input.placeholder = '粘贴 YouTube / Bilibili / Vimeo / 抖音 视频链接...'
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
        } else {
          this.data.aspectRatio = result.aspectRatio
          if (!this.data.widthPercent) {
            this.data.widthPercent = result.defaultWidth
          }
        }
        wrapper.innerHTML = ''
        this._renderEmbed(wrapper)
        // Vimeo 封面要异步调接口拿，拿到后原地补上并重绘一次；只发生在插入的这一刻，
        // posterUrl 会随 save() 一起持久化，以后重新打开不会再发这个请求。
        if (result.needsOembedPoster) {
          fetchVimeoPoster(url).then((posterUrl) => {
            if (posterUrl && !this._playing) {
              this.data.posterUrl = posterUrl
              wrapper.innerHTML = ''
              this._renderEmbed(wrapper)
            }
          })
        }
      } else {
        error.textContent = '无法识别链接，请粘贴 YouTube / Bilibili / Vimeo / 抖音 视频地址'
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
      fixedHeight: this.data.fixedHeight || null
    }
  }

  validate(data) {
    return !!(data && data.embedUrl && data.embedUrl.trim())
  }
}

export default EmbedVideoTool
