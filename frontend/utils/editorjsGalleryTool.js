/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
import { createEl, createButton, createVideoFacade } from './editorjsUiHelpers'
import { resolveVideoEmbed, fetchVimeoPoster } from './videoEmbedResolver'

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

  constructor({ data, config, api, readOnly }) {
    this.api = api
    this.readOnly = readOnly
    this.config = config || {}
    this.data = { items: Array.isArray(data?.items) ? data.items : [] }
    this.wrapper = null
    // 哪些视频卡片已经被点开播放过——只是渲染态，不持久化，重新打开笔记时应该总是先显示
    // 封面图（见 createVideoFacade 的注释）。用 WeakSet 存 item 引用，不用下标，避免删除
    // 卡片导致下标错位。
    this._playingItems = new WeakSet()
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
      toolbar.appendChild(createButton('粘贴链接（图片/视频）', () => this._openLinkInput(toolbar)))
      this.wrapper.appendChild(toolbar)
    }
    return this.wrapper
  }

  _renderGrid() {
    const existing = this.wrapper.querySelector('.cdx-gallery__grid')
    if (existing) existing.remove()
    const grid = createEl('div', 'cdx-gallery__grid')
    this.data.items.forEach((item, idx) => {
      const card = createEl('div', 'cdx-gallery__card')
      if (item.type === 'video') {
        if (this._playingItems.has(item)) {
          card.appendChild(createEl('iframe', 'cdx-gallery__video', { src: item.embedUrl, frameBorder: '0', allowFullscreen: true }))
        } else {
          card.appendChild(createVideoFacade(item.posterUrl, () => {
            this._playingItems.add(item)
            this._renderGrid()
          }, 'cdx-gallery__video-facade'))
        }
      } else if (item.type === 'audio') {
        card.appendChild(createEl('audio', 'cdx-gallery__audio', { src: item.url, controls: true }))
      } else {
        card.appendChild(createEl('img', 'cdx-gallery__image', { src: item.url, alt: item.caption || '' }))
      }
      if (item.caption) card.appendChild(createEl('div', 'cdx-gallery__caption', { textContent: item.caption }))
      if (!this.readOnly) {
        card.appendChild(createButton('×', () => { this.data.items.splice(idx, 1); this._renderGrid() }, 'cdx-gallery__delete'))
      }
      grid.appendChild(card)
    })
    this.wrapper.insertBefore(grid, this.wrapper.firstChild)
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

  _openLinkInput(toolbar) {
    if (toolbar.querySelector('.cdx-gallery__link-row')) return
    const row = createEl('div', 'cdx-gallery__link-row')
    const input = createEl('input', 'cdx-gallery__link-input', { placeholder: '粘贴图片链接或 YouTube/B站/Vimeo/抖音 视频链接…' })
    const doAdd = async () => {
      const url = input.value.trim()
      if (!url) return
      const embed = resolveVideoEmbed(url)
      if (embed) {
        const item = { type: 'video', embedUrl: embed.embedUrl, caption: '', posterUrl: embed.posterUrl || null }
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
      if (this.config.uploader?.uploadByUrl) {
        try {
          const result = await this.config.uploader.uploadByUrl(url)
          if (result?.success) {
            this.data.items.push({ type: 'image', url: result.file.url, caption: '' })
            row.remove()
            this._renderGrid()
            return
          }
        } catch (e) { /* fallthrough to error message below */ }
      }
      this.api.notifier?.show({ message: '无法识别这个链接，请检查后重试', style: 'error' })
    }
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') doAdd() })
    row.append(input, createButton('添加', doAdd))
    toolbar.appendChild(row)
  }

  save() {
    return { items: this.data.items }
  }

  validate(savedData) {
    return Array.isArray(savedData.items)
  }
}
