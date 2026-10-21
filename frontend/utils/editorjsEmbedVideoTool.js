/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { resolveVideoEmbed as resolveEmbed, fetchVimeoPoster, getTweetResizeHeight, getTweetIdFromEmbedUrl, isYouTubeEmbed } from './videoEmbedResolver'
import { createVideoFacade, toolTranslator } from './editorjsUiHelpers'
import { createNoReferrerVideo, attachVideoLoading } from './editorjsMediaLightbox'

class EmbedVideoTool {
  static get toolbox() {
    return {
      title: 'Web Video',
      icon: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <polygon points="10 8 16 12 10 16 10 8"/>
      </svg>`
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, api, readOnly, config, block }) {
    this.data = data || {}
    this.api = api
    // fetchTweetPoster / fetchTweetVideo：推文只显示视频要用到，见 editorjsHost
    this.config = config || {}
    this.block = block
    this._t = toolTranslator(api)
    this.readOnly = readOnly
    this._element = null
    // 只是渲染态，不持久化——重新打开笔记时应该总是先显示封面图（见 _renderEmbed 的注释），
    // 不应该记住"上次点开播放过"这件事。
    this._playing = false
    this._onMessage = null
    // 推文取不到视频直链（纯文字推文、私有、网络问题）时退回推文卡片；同样只是渲染态
    this._tweetCard = false
    this._posterRequested = false
    this._playSeq = 0
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

    // 推文只显示视频：和 YouTube 一样平时是封面，点击后在原位用原生播放器播 mp4 直链（同媒体画廊），
    // 不再加载带头像、正文的整张推文卡片；取不到直链时才退回卡片
    const tweetId = getTweetIdFromEmbedUrl(this.data.embedUrl)
    const tweetVideo = Boolean(tweetId && this.config.fetchTweetVideo && !this._tweetCard)

    if (this.data.fixedWidth && !tweetVideo) {
      frame.setAttribute('data-fixed', 'true')
      frame.style.cssText = `width:${this.data.fixedWidth}px;max-width:100%;height:${this.data.fixedHeight}px;border-radius:8px`
    } else {
      frame.style.cssText = `width:${this.data.widthPercent || 100}%;border-radius:8px`
      this._applyAspectRatio(frame, tweetVideo ? (this.data.videoAspectRatio || '16/9') : (this.data.aspectRatio || '16/9'))
    }

    // 没有取直链能力时（推文卡片的老路子），推文拿不到封面图，占位只能是一块黑底，看不出是哪条
    // 推文；而推文卡片本身就是预览（不会自动播放），所以直接加载卡片，用 loading=lazy 让屏幕外
    // 的卡片滚到附近才加载，尽量不拖慢打开笔记。
    const isTweet = /^https:\/\/platform\.twitter\.com\//.test(this.data.embedUrl)
    if (tweetVideo && this._playing) {
      this._playTweetVideo(frame, tweetId)
    } else if (tweetVideo) {
      frame.appendChild(createVideoFacade(this.data.posterUrl, () => {
        this._playing = true
        this._rerender()
      }, 'embed-video-tool__facade'))
      this._ensureTweetPoster(tweetId)
    } else if (this._playing || isTweet) {
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
        this._rerender()
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

  // 重画前先把拖拽调过的宽度记回 data：拖拽只改 DOM，data 里还是插入时的宽度，直接重画会跳回去
  _rerender() {
    const wrapper = this._element
    if (!wrapper) return
    const frame = wrapper.querySelector('.embed-video-tool__frame')
    if (frame && frame.getAttribute('data-fixed') !== 'true') {
      const wp = parseInt(frame.style.width, 10)
      if (wp) this.data.widthPercent = wp
    }
    wrapper.innerHTML = ''
    this._renderEmbed(wrapper)
  }

  // 竖屏视频按宽度百分比铺开会非常高，限制在约 70% 视口高度以内（收窄宽度，比例不变）
  _applyAspectRatio(frame, aspectRatio) {
    const [w, h] = String(aspectRatio).split('/').map(Number)
    frame.style.aspectRatio = `${w || 16} / ${h || 9}`
    frame.style.maxWidth = w > 0 && h > 0 && w < h ? `calc(70vh * ${w / h})` : ''
  }

  _playTweetVideo(frame, tweetId) {
    const seq = ++this._playSeq
    const loading = attachVideoLoading(frame, null, this.data.posterUrl, this._t, 'embed-video-tool')
    this.config.fetchTweetVideo(tweetId).then((video) => {
      if (seq !== this._playSeq || !frame.isConnected) return
      if (!video || !video.videoUrl) {
        this._tweetCard = true
        this._rerender()
        return
      }
      loading.remove()
      if (video.aspectRatio) {
        this._applyAspectRatio(frame, video.aspectRatio)
        // 记下真实比例，下次封面就按这个比例占位，点开时不会跳一下
        if (video.aspectRatio !== this.data.videoAspectRatio && !this.readOnly) {
          this.data.videoAspectRatio = video.aspectRatio
          this.block?.dispatchChange?.()
        }
      }
      const iframe = createNoReferrerVideo(video.videoUrl, 'embed-video-tool__video')
      frame.appendChild(iframe)
      attachVideoLoading(frame, iframe, this.data.posterUrl, this._t, 'embed-video-tool')
    })
  }

  // 推文封面浏览器跨域拿不到，经后端取一次（同媒体画廊），存进 data 随笔记保存，以后打开不再请求
  _ensureTweetPoster(tweetId) {
    if (this.readOnly || this.data.posterUrl || this._posterRequested || !this.config.fetchTweetPoster) return
    this._posterRequested = true
    this.config.fetchTweetPoster(tweetId).then((posterUrl) => {
      if (!posterUrl) return
      this.data.posterUrl = posterUrl
      // 不是用户编辑触发的数据变化，主动通知编辑器，让封面随下一次自动保存写进笔记
      this.block?.dispatchChange?.()
      if (!this._playing) this._rerender()
    })
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
    input.placeholder = this._t('Paste a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video link...')
    input.classList.add('embed-video-tool__input')

    const btn = document.createElement('button')
    btn.type = 'button'
    btn.textContent = this._t('Embed')
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
        error.textContent = this._t("Can't recognize this link. Paste a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video URL")
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
      // 推文现在按视频显示、可调宽度；fixedWidth/fixedHeight 留给退回推文卡片时用
      widthPercent: this.data.fixedWidth && !getTweetIdFromEmbedUrl(this.data.embedUrl) ? null : (this.data.widthPercent || 100),
      aspectRatio: this.data.aspectRatio || '16/9',
      videoAspectRatio: this.data.videoAspectRatio || null,
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
