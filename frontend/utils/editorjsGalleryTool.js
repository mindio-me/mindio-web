/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
import { createEl, createButton, createVideoFacade } from './editorjsUiHelpers'
import { resolveVideoEmbed, fetchVimeoPoster, getTweetIdFromEmbedUrl, isYouTubeEmbed } from './videoEmbedResolver'
import { openMediaLightbox } from './editorjsMediaLightbox'

/** 在浏览器里试加载一个地址，能解码成图片就算图片（不看扩展名），超时按失败处理 */
function loadsAsImage(url, timeoutMs = 10000) {
  return new Promise((resolve) => {
    const img = new Image()
    const timer = setTimeout(() => { img.src = ''; resolve(false) }, timeoutMs)
    img.onload = () => { clearTimeout(timer); resolve(img.naturalWidth > 0) }
    img.onerror = () => { clearTimeout(timer); resolve(false) }
    img.src = url
  })
}

export default class GalleryTool {
  static get toolbox() {
    return {
      title: '媒体画廊',
      icon: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>'
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, config, api, readOnly, block }) {
    this.api = api
    this.block = block
    this.readOnly = readOnly
    this.config = config || {}
    this.data = { items: Array.isArray(data?.items) ? data.items : [] }
    this.wrapper = null
    this._posterRequested = new WeakSet()
    this._closeLightbox = null
  }

  render() {
    this.wrapper = createEl('div', 'cdx-gallery')
    this._renderGrid()
    if (!this.readOnly) {
      const toolbar = createEl('div', 'cdx-gallery__toolbar')
      const fileInput = createEl('input', null, { type: 'file', accept: 'image/*,audio/*', multiple: true, style: 'display:none' })
      fileInput.addEventListener('change', (e) => this._handleFiles(e.target.files))
      toolbar.appendChild(createButton('上传图片/音频', () => fileInput.click()))
      toolbar.appendChild(fileInput)
      toolbar.appendChild(createButton('粘贴链接（图片/视频）', () => this._openLinkInput()))
      this.wrapper.appendChild(toolbar)
    }
    return this.wrapper
  }

  // 格子永远是缩略图，不在格子里播放：图片点开看原图，视频点开在弹窗里直接播放（见 editorjsMediaLightbox）。
  // 音频本身能在格子里播，留在格子里，不进弹窗。
  _renderGrid() {
    const existing = this.wrapper.querySelector('.cdx-gallery__grid')
    if (existing) existing.remove()
    const grid = createEl('div', 'cdx-gallery__grid')
    this.data.items.forEach((item, idx) => {
      const card = createEl('div', 'cdx-gallery__card')
      // 格子是固定大小的区域，媒体比例和它不一致时在里面居中（图片 contain，视频留黑边）
      const media = createEl('div', 'cdx-gallery__media')
      card.appendChild(media)
      if (item.type === 'video') {
        media.classList.add('cdx-gallery__media--video')
        media.appendChild(createVideoFacade(item.posterUrl, () => this._openLightbox(item), 'cdx-gallery__video-facade', { youtube: isYouTubeEmbed(item.embedUrl) }))
        if (getTweetIdFromEmbedUrl(item.embedUrl) && !item.posterUrl) this._fetchTweetPoster(item)
      } else if (item.type === 'audio') {
        media.appendChild(createEl('audio', 'cdx-gallery__audio', { src: item.url, controls: true }))
      } else {
        media.classList.add('cdx-gallery__media--zoomable')
        media.appendChild(createEl('img', 'cdx-gallery__image', { src: item.url, alt: item.caption || '' }))
        media.addEventListener('click', () => this._openLightbox(item))
      }
      if (item.caption) card.appendChild(createEl('div', 'cdx-gallery__caption', { textContent: item.caption }))
      if (!this.readOnly) {
        card.appendChild(createButton('×', () => { this.data.items.splice(idx, 1); this._renderGrid() }, 'cdx-gallery__delete'))
      }
      grid.appendChild(card)
    })
    this.wrapper.insertBefore(grid, this.wrapper.firstChild)
  }

  _openLightbox(item) {
    const viewable = this.data.items.filter((it) => it.type !== 'audio')
    if (this._closeLightbox) this._closeLightbox()
    this._closeLightbox = openMediaLightbox(viewable, viewable.indexOf(item), {
      fetchTweetVideo: this.config.fetchTweetVideo
    })
  }

  // 推文封面浏览器跨域拿不到，经后端取一次（见 editorjsHost 的 fetchTweetPoster），拿到后存进 item
  // 随笔记持久化，以后打开不再请求。刚插入的和之前插入时没取到封面的推文都走这里，每个 item 只试一次。
  _fetchTweetPoster(item) {
    if (this.readOnly || !this.config.fetchTweetPoster || this._posterRequested.has(item)) return
    const tweetId = getTweetIdFromEmbedUrl(item.embedUrl)
    if (!tweetId) return
    this._posterRequested.add(item)
    this.config.fetchTweetPoster(tweetId).then((posterUrl) => {
      if (!posterUrl) return
      item.posterUrl = posterUrl
      // 不是用户编辑触发的数据变化，主动通知编辑器，让封面随下一次自动保存写进笔记
      this.block?.dispatchChange?.()
      this._renderGrid()
    })
  }

  async _handleFiles(fileList) {
    if (!this.config.uploader?.uploadByFile) return
    for (const file of Array.from(fileList)) {
      const isAudio = file.type.startsWith('audio/')
      try {
        const result = await this.config.uploader.uploadByFile(file)
        if (result?.success) {
          this.data.items.push({ type: isAudio ? 'audio' : 'image', url: result.file.url, caption: '' })
        }
      } catch (e) {
        this.api.notifier?.show({ message: `${file.name} 上传失败`, style: 'error' })
      }
    }
    this._renderGrid()
  }

  // 输入区的样式和"网络视频"块的链接输入保持一致：输入框 + 主色按钮，错误提示显示在下方
  _openLinkInput() {
    const opened = this.wrapper.querySelector('.cdx-gallery__link')
    if (opened) {
      opened.querySelector('input').focus()
      return
    }
    const row = createEl('div', 'cdx-gallery__link')
    const inputRow = createEl('div', 'cdx-gallery__link-row')
    const input = createEl('input', 'cdx-gallery__link-input', {
      type: 'text',
      placeholder: '粘贴图片链接，或 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频链接...'
    })
    const error = createEl('div', 'cdx-gallery__link-error')
    let btn = null
    const setBusy = (busy) => {
      btn.disabled = busy
      btn.textContent = busy ? '添加中...' : '添加'
    }
    const doAdd = async () => {
      const url = input.value.trim()
      if (!url || btn.disabled) return
      error.textContent = ''
      const embed = resolveVideoEmbed(url)
      if (embed) {
        const item = { type: 'video', service: embed.service, embedUrl: embed.embedUrl, caption: '', posterUrl: embed.posterUrl || null }
        this.data.items.push(item)
        row.remove()
        this._renderGrid()
        // Vimeo 封面要异步调接口拿，拿到后原地补上并重绘一次；只发生在插入的这一刻，
        // posterUrl 会随笔记内容一起持久化，以后重新打开不会再发这个请求。
        if (embed.needsOembedPoster) {
          fetchVimeoPoster(url).then((posterUrl) => {
            if (posterUrl) {
              item.posterUrl = posterUrl
              this._renderGrid()
            }
          })
        }
        return
      }
      // 不是已知视频平台链接，当图片链接处理：走远程上传接口重新托管
      setBusy(true)
      if (this.config.uploader?.uploadByUrl) {
        try {
          const result = await this.config.uploader.uploadByUrl(url)
          if (result?.success) {
            this.data.items.push({ type: 'image', url: result.file.url, caption: '' })
            row.remove()
            this._renderGrid()
            return
          }
        } catch (e) { /* fallthrough to browser probe below */ }
      }
      // 后端拉不到（防盗链、拒绝非浏览器 UA、后端连不上外网等）时，在浏览器里试加载一次：
      // 能显示成图片就直接引用原地址，不管链接里有没有扩展名。代价是原站删图后这张会失效。
      if (/^https?:\/\//i.test(url) && await loadsAsImage(url)) {
        this.data.items.push({ type: 'image', url, caption: '' })
        row.remove()
        this._renderGrid()
        return
      }
      setBusy(false)
      error.textContent = '无法识别链接，请粘贴图片地址，或 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频地址'
    }
    btn = createButton('添加', doAdd, 'cdx-gallery__link-btn')
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') doAdd() })
    inputRow.append(input, btn)
    row.append(inputRow, error)
    this.wrapper.appendChild(row)
    setTimeout(() => input.focus(), 0)
  }

  save() {
    return { items: this.data.items }
  }

  // 块被删除或切换笔记时，顺手关掉还开着的弹窗，免得它停留在别的笔记上
  destroy() {
    if (this._closeLightbox) {
      this._closeLightbox()
      this._closeLightbox = null
    }
  }

  validate(savedData) {
    return Array.isArray(savedData.items)
  }
}
