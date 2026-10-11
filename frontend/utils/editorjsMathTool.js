/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { renderTex } from './mathRender'

/**
 * EditorJS 块级公式：数据 { tex }。编辑态是 TeX 源码框 + 实时预览，只读态只显示渲染结果。
 * 本文件由 editorjsHost 动态 import，KaTeX 随之按需加载。
 */
export default class MathTool {
  static get toolbox() {
    return {
      title: 'Math',
      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 4H6l6 8-6 8h12"/></svg>'
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  static get enableLineBreaks() {
    return true
  }

  constructor({ data, readOnly }) {
    this.readOnly = readOnly
    this.data = { tex: (data && data.tex) || '' }
    this.input = null
    this.preview = null
  }

  render() {
    const wrapper = document.createElement('div')
    wrapper.classList.add('cdx-math-block')
    if (!this.readOnly) {
      this.input = document.createElement('textarea')
      this.input.classList.add('cdx-math-block__input')
      this.input.placeholder = 'TeX，例如 E = mc^2'
      this.input.value = this.data.tex
      this._fitRows()
      this.input.addEventListener('input', () => {
        this.data.tex = this.input.value
        this._fitRows()
        this._renderPreview()
      })
      wrapper.appendChild(this.input)
    }
    this.preview = document.createElement('div')
    this.preview.classList.add('cdx-math-block__preview')
    wrapper.appendChild(this.preview)
    this._renderPreview()
    return wrapper
  }

  _fitRows() {
    this.input.rows = Math.max(2, this.input.value.split('\n').length)
  }

  _renderPreview() {
    this.preview.innerHTML = renderTex(this.data.tex)
  }

  save() {
    return { tex: this.input ? this.input.value : this.data.tex }
  }
}
