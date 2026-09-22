/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

const SERVICES = [
  {
    name: 'bilibili',
    regex: /bilibili\.com\/video\/(BV\w+)/,
    embed: (m) => `https://player.bilibili.com/player.html?bvid=${m[1]}&danmaku=0`,
    aspectRatio: '16/9'
  },
  {
    name: 'youtube',
    regex: /(?:youtu\.be\/|youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|v\/|shorts\/))([^?&\s/]+)/,
    embed: (m) => `https://www.youtube.com/embed/${m[1]}`,
    aspectRatio: '16/9'
  },
  {
    name: 'vimeo',
    regex: /vimeo\.com\/(\d+)/,
    embed: (m) => `https://player.vimeo.com/video/${m[1]}`,
    aspectRatio: '16/9'
  },
  {
    name: 'douyin',
    regex: /douyin\.com.*?(?:\/video\/|[?&]modal_id=)(\d+)/,
    embed: (m) => `https://open.douyin.com/player/video?vid=${m[1]}&autoplay=0`,
    fixedWidth: 324,
    fixedHeight: 720
  }
]

function resolveEmbed(url) {
  for (const svc of SERVICES) {
    const m = svc.regex.exec(url)
    if (m) return {
      embedUrl: svc.embed(m),
      aspectRatio: svc.aspectRatio || '16/9',
      defaultWidth: svc.defaultWidth || 100,
      fixedWidth: svc.fixedWidth || null,
      fixedHeight: svc.fixedHeight || null
    }
  }
  return null
}

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

  _renderEmbed(wrapper) {
    wrapper.style.display = 'flex'
    wrapper.style.flexDirection = 'column'
    wrapper.style.alignItems = 'center'

    const frame = document.createElement('div')
    frame.classList.add('embed-video-tool__frame')

    const iframe = document.createElement('iframe')
    iframe.src = this.data.embedUrl
    iframe.setAttribute('frameborder', '0')
    iframe.setAttribute('allowfullscreen', 'true')
    iframe.setAttribute('scrolling', 'no')
    iframe.setAttribute('referrerpolicy', 'unsafe-url')

    if (this.data.fixedWidth) {
      frame.setAttribute('data-fixed', 'true')
      iframe.style.cssText = `width:${this.data.fixedWidth}px;height:${this.data.fixedHeight}px;border-radius:8px;display:block`
    } else {
      frame.style.width = (this.data.widthPercent || 100) + '%'
      iframe.style.cssText = `width:100%;aspect-ratio:${this.data.aspectRatio || '16/9'};border-radius:8px;display:block`
    }

    frame.appendChild(iframe)
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
