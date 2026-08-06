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
    this.textarea.placeholder = '输入 Markdown 内容...'
    this.textarea.spellcheck = false
    this.textarea.autocomplete = 'off'
    this.textarea.autocorrect = 'off'
    this.textarea.autocapitalize = 'off'

    // 监听输入
    this.textarea.addEventListener('input', () => this._onTextareaInput())
    this.textarea.addEventListener('input', () => this._autoResizeTextarea())

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

    const lines = (this.textarea.value || '').split('\n').length
    const lineHeight = 22
    const padding = 32
    const minHeight = 120

    const contentHeight = Math.ceil(lines * lineHeight + padding)
    this.textarea.style.height = Math.max(contentHeight, minHeight) + 'px'
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
