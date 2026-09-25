/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * EditorJS Markdown Block 插件
 * 支持实时预览的 Markdown 编辑块
 */

export default class MarkdownBlock {
  /**
   * 静态属性：工具箱配置
   */
  static get toolbox() {
    return {
      title: 'Markdown',
      icon: `<svg width="14" height="14" viewBox="0 0 208 128" xmlns="http://www.w3.org/2000/svg">
        <rect width="198" height="118" x="5" y="5" fill="none" stroke="currentColor" stroke-width="10" rx="10"/>
        <path fill="currentColor" d="M30 98V30h20l20 25 20-25h20v68H90V59L70 84 50 59v39zm125 0-30-33h20V30h20v35h20z"/>
      </svg>`
    }
  }

  /**
   * 允许换行
   */
  static get enableLineBreaks() {
    return true
  }

  /**
   * 允许在只读模式下渲染
   */
  static get isReadOnlySupported() {
    return true
  }

  /**
   * 粘贴配置
   */
  static get pasteConfig() {
    return {
      tags: ['PRE']
    }
  }

  /**
   * 构造函数
   */
  constructor({ data, config, api, readOnly }) {
    this.api = api
    this.readOnly = readOnly
    this.config = config || {}

    // 默认数据
    this.data = {
      markdown: data.markdown || '',
      mode: data.mode || 'split' // 'edit', 'preview', 'split'
    }

    // 防抖定时器
    this._debounceTimer = null
    this._debounceDelay = 300

    // DOM 引用
    this.wrapper = null
    this.textarea = null
    this.preview = null

    // 缓存 renderMarkdown 函数
    this._renderMarkdownFn = null

    // 监听 textarea 宽度变化，用于在宽度变化（如后续图片块异步加载撑高页面
    // 触发滚动条）时重新计算高度
    this._widthObserver = null
    this._lastTextareaWidth = undefined
  }

  /**
   * 获取 Markdown 渲染函数
   */
  async _getMarkdownRenderer() {
    if (!this._renderMarkdownFn) {
      try {
        const mod = await import('./markdown.js')
        this._renderMarkdownFn = mod.renderMarkdown
      } catch (e) {
        console.error('[MarkdownBlock] Failed to load markdown renderer:', e)
        // 降级：返回原始文本
        this._renderMarkdownFn = (text) => `<pre>${text}</pre>`
      }
    }
    return this._renderMarkdownFn
  }

  /**
   * 渲染 Block DOM
   */
  render() {
    this.wrapper = document.createElement('div')
    this.wrapper.classList.add('cdx-markdown-block')

    // 只读模式：只显示渲染结果
    if (this.readOnly) {
      this.wrapper.classList.add('cdx-markdown-block--readonly')
      this._renderPreviewOnly()
      return this.wrapper
    }

    // 编辑模式：创建完整布局
    this._createEditLayout()

    return this.wrapper
  }

  /**
   * 创建只读预览
   */
  async _renderPreviewOnly() {
    const previewContainer = document.createElement('div')
    previewContainer.classList.add('cdx-markdown-block__preview')
    previewContainer.innerHTML = await this._renderMarkdown(this.data.markdown)
    this.wrapper.appendChild(previewContainer)

    // 延迟处理 Mermaid
    this._processMermaidBlocks(previewContainer)
  }

  /**
   * 创建编辑布局
   */
  _createEditLayout() {
    // 工具栏
    const toolbar = this._createToolbar()
    this.wrapper.appendChild(toolbar)

    // 内容区域容器
    const contentArea = document.createElement('div')
    contentArea.classList.add('cdx-markdown-block__content')
    contentArea.classList.add(`cdx-markdown-block__content--${this.data.mode}`)

    // 编辑区
    const editorPane = document.createElement('div')
    editorPane.classList.add('cdx-markdown-block__editor-pane')

    this.textarea = document.createElement('textarea')
    this.textarea.classList.add('cdx-markdown-block__textarea')
    this.textarea.value = this.data.markdown
    // 程序化赋值 .value 会把光标移到末尾；用户还没手动点过输入框时，
    // 之后 _setMode 里的 focus() 会触发浏览器把光标位置滚动进视口，
    // 也就是滚到内容末尾而不是顶部——这里把光标复位到开头，避免切换模式时页面自动跳到底部
    this.textarea.selectionStart = this.textarea.selectionEnd = 0
    this.textarea.placeholder = '输入 Markdown 内容...'
    this.textarea.spellcheck = false
    this.textarea.autocomplete = 'off'
    this.textarea.autocorrect = 'off'
    this.textarea.autocapitalize = 'off'

    // 监听输入
    this.textarea.addEventListener('input', () => this._onTextareaInput())
    this.textarea.addEventListener('input', () => this._autoResizeTextarea())

    // 高度只在构造时和输入时重算，但 textarea 的可用宽度会因为跟它无关的原因
    // 在那之后才变化——典型场景：块后面跟着一张图片，图片异步加载完成后撑高整个
    // 页面，.editor-main 的纵向滚动条出现/消失，textarea 的实际宽度随之变化，
    // 导致软换行行数变了，但内联 style.height 还停在旧宽度量出的值上，
    // 于是 overflow:hidden 就把新增的换行内容裁掉，看起来像块「缩小」了。
    // 用 ResizeObserver 盯 textarea 自身的宽度变化来重新量高度；只在宽度真的变了
    // 才重算，避免我们自己写 style.height 触发的高度变化又反过来触发一轮重算。
    if (typeof ResizeObserver !== 'undefined') {
      this._widthObserver = new ResizeObserver((entries) => {
        for (const entry of entries) {
          const newWidth = entry.contentRect.width
          if (this._lastTextareaWidth !== undefined && newWidth !== this._lastTextareaWidth) {
            this._autoResizeTextarea()
          }
          this._lastTextareaWidth = newWidth
        }
      })
      this._widthObserver.observe(this.textarea)
    }

    editorPane.appendChild(this.textarea)
    contentArea.appendChild(editorPane)

    // 预览区
    const previewPane = document.createElement('div')
    previewPane.classList.add('cdx-markdown-block__preview-pane')

    this.preview = document.createElement('div')
    this.preview.classList.add('cdx-markdown-block__preview')
    this._updatePreview()

    previewPane.appendChild(this.preview)
    contentArea.appendChild(previewPane)

    this.wrapper.appendChild(contentArea)

    // 初始化时调整高度
    setTimeout(() => this._autoResizeTextarea(), 0)
  }

  /**
   * 创建工具栏
   */
  _createToolbar() {
    const toolbar = document.createElement('div')
    toolbar.classList.add('cdx-markdown-block__toolbar')

    // 模式切换按钮组
    const modeGroup = document.createElement('div')
    modeGroup.classList.add('cdx-markdown-block__mode-group')

    const modes = [
      { id: 'edit', label: '编辑', icon: '&#9998;' },
      { id: 'split', label: '分栏', icon: '&#9783;' },
      { id: 'preview', label: '预览', icon: '&#128065;' }
    ]

    modes.forEach(mode => {
      const btn = document.createElement('button')
      btn.type = 'button'
      btn.classList.add('cdx-markdown-block__mode-btn')
      btn.dataset.mode = mode.id
      if (this.data.mode === mode.id) {
        btn.classList.add('cdx-markdown-block__mode-btn--active')
      }
      btn.innerHTML = `<span class="mode-icon">${mode.icon}</span><span class="mode-label">${mode.label}</span>`
      btn.addEventListener('click', () => this._setMode(mode.id))
      modeGroup.appendChild(btn)
    })

    toolbar.appendChild(modeGroup)

    return toolbar
  }

  /**
   * 切换显示模式
   */
  _setMode(mode) {
    this.data.mode = mode

    // 更新按钮状态
    const buttons = this.wrapper.querySelectorAll('.cdx-markdown-block__mode-btn')
    buttons.forEach(btn => {
      btn.classList.toggle(
        'cdx-markdown-block__mode-btn--active',
        btn.dataset.mode === mode
      )
    })

    // 更新内容区域样式
    const content = this.wrapper.querySelector('.cdx-markdown-block__content')
    content.className = 'cdx-markdown-block__content'
    content.classList.add(`cdx-markdown-block__content--${mode}`)

    // 切换到预览或分栏模式时更新预览
    if (mode === 'preview' || mode === 'split') {
      this._updatePreview()
    }

    // 切换到编辑或分栏模式时聚焦文本框
    if (mode === 'edit' || mode === 'split') {
      setTimeout(() => {
        if (this.textarea) {
          this.textarea.focus()
        }
      }, 0)
    }
  }

  /**
   * 处理文本输入（防抖）
   */
  _onTextareaInput() {
    this.data.markdown = this.textarea.value

    if (this._debounceTimer) {
      clearTimeout(this._debounceTimer)
    }

    this._debounceTimer = setTimeout(() => {
      this._updatePreview()
    }, this._debounceDelay)
  }

  /**
   * 更新预览区域
   */
  async _updatePreview() {
    if (!this.preview) return

    const html = await this._renderMarkdown(this.data.markdown)
    this.preview.innerHTML = html || '<p class="cdx-markdown-block__empty">预览区域</p>'

    // 处理 Mermaid 图表
    this._processMermaidBlocks(this.preview)
  }

  /**
   * 渲染 Markdown 为 HTML
   */
  async _renderMarkdown(markdown) {
    if (!markdown || !markdown.trim()) {
      return ''
    }

    const renderFn = await this._getMarkdownRenderer()
    const axiosBaseURL = this.config.axiosBaseURL || ''
    return renderFn(markdown, { axiosBaseURL })
  }

  /**
   * 处理预览区中的 Mermaid 图表块
   */
  async _processMermaidBlocks(container) {
    const blocks = container.querySelectorAll('.mermaid-block:not([data-rendered])')
    if (blocks.length === 0) return

    try {
      const mermaid = await import('mermaid').then(m => m.default || m)

      // 根据当前主题配置 mermaid
      const isDark = document.documentElement.classList.contains('theme-dark')
      mermaid.initialize({
        startOnLoad: false,
        theme: isDark ? 'dark' : 'default',
        securityLevel: 'strict'
      })

      for (let i = 0; i < blocks.length; i++) {
        const block = blocks[i]
        const source = decodeURIComponent(block.dataset.mermaidSource || '')
        if (!source) continue

        try {
          const id = 'md-mermaid-' + Date.now() + '-' + i
          const { svg } = await mermaid.render(id, source)
          block.innerHTML = svg
          block.setAttribute('data-rendered', 'true')
          block.classList.add('mermaid-rendered')
        } catch (e) {
          console.warn('[MarkdownBlock] Mermaid render failed:', e)
          block.classList.add('mermaid-error')
        }
      }
    } catch (e) {
      console.error('[MarkdownBlock] Failed to load mermaid:', e)
    }
  }

  /**
   * 自动调整 textarea 高度
   */
  _autoResizeTextarea() {
    if (!this.textarea) return

    // 用 scrollHeight 量实际渲染高度，而不是按 \n 数逻辑行——
    // 长段落软换行会占多行但不含 \n，按行数算的高度会小于实际内容高度，
    // 导致 overflow: hidden 裁切内容，且视口停在上次光标位置而非顶部
    const minHeight = 120
    this.textarea.style.height = 'auto'
    this.textarea.style.height = Math.max(this.textarea.scrollHeight, minHeight) + 'px'
  }

  /**
   * 保存 Block 数据
   */
  save() {
    return {
      markdown: this.data.markdown,
      mode: this.data.mode
    }
  }

  /**
   * 验证数据有效性
   */
  validate(savedData) {
    return true
  }

  /**
   * 销毁时清理
   */
  destroy() {
    if (this._debounceTimer) {
      clearTimeout(this._debounceTimer)
      this._debounceTimer = null
    }
    if (this._widthObserver) {
      this._widthObserver.disconnect()
      this._widthObserver = null
    }
  }

  /**
   * 处理粘贴
   */
  onPaste(event) {
    const content = event.detail.data
    if (content && content.textContent) {
      this.data.markdown = content.textContent
      if (this.textarea) {
        this.textarea.value = this.data.markdown
        this._autoResizeTextarea()
        this._updatePreview()
      }
    }
  }
}
