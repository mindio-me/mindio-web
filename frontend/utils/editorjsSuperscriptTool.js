/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * EditorJS 上标行内工具：包裹/去掉 <sup>。Markdown 转换出来的脚注引用是 <sup>1</sup>，
 * 保存时编辑器只放行块工具及其行内工具 sanitize 里声明过的标签，没有这个工具脚注标记会被清洗成纯数字。
 */
export default class SuperscriptTool {
  static get isInline() {
    return true
  }

  static get title() {
    return 'Superscript'
  }

  static get sanitize() {
    return { sup: {} }
  }

  constructor({ api }) {
    this.api = api
    this.button = null
    this.tag = 'SUP'
  }

  render() {
    this.button = document.createElement('button')
    this.button.type = 'button'
    this.button.classList.add(this.api.styles.inlineToolButton)
    this.button.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19l8-8"/><path d="M12 19l-8-8"/><path d="M20 12h-4c0-1.5.4-2 1.5-2.6S20 8 20 6.8c0-1-.9-1.8-2-1.8-.8 0-1.6.5-2 1.2"/></svg>'
    return this.button
  }

  surround(range) {
    if (!range) return
    const existing = this.api.selection.findParentTag(this.tag)
    if (existing) {
      this._unwrap(existing)
      return
    }
    const sup = document.createElement(this.tag)
    sup.appendChild(range.extractContents())
    range.insertNode(sup)
    this.api.selection.expandToTag(sup)
  }

  _unwrap(el) {
    this.api.selection.expandToTag(el)
    const sel = window.getSelection()
    const range = sel.getRangeAt(0)
    const content = range.extractContents()
    el.parentNode.removeChild(el)
    range.insertNode(content)
    sel.removeAllRanges()
    sel.addRange(range)
  }

  checkState() {
    const active = !!this.api.selection.findParentTag(this.tag)
    this.button.classList.toggle(this.api.styles.inlineToolButtonActive, active)
    return active
  }
}
