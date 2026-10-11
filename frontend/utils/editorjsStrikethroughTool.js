/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * EditorJS 删除线行内工具：包裹/去掉 <s>。sanitize 放行 <s>，否则编辑器会把转换来的删除线清洗掉。
 */
export default class StrikethroughTool {
  static get isInline() {
    return true
  }

  static get title() {
    return 'Strikethrough'
  }

  static get sanitize() {
    return { s: {} }
  }

  constructor({ api }) {
    this.api = api
    this.button = null
    this.tag = 'S'
  }

  render() {
    this.button = document.createElement('button')
    this.button.type = 'button'
    this.button.classList.add(this.api.styles.inlineToolButton)
    this.button.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M5 12h14"/><path d="M16 6.5C15.2 5 13.7 4 12 4c-2.5 0-4 1.5-4 3.3 0 1.4.9 2.3 2.4 2.9"/><path d="M8 17.5c.8 1.5 2.3 2.5 4 2.5 2.5 0 4-1.5 4-3.3 0-.9-.3-1.6-.9-2.2"/></svg>'
    return this.button
  }

  surround(range) {
    if (!range) return
    const existing = this.api.selection.findParentTag(this.tag)
    if (existing) {
      this._unwrap(existing)
      return
    }
    const s = document.createElement(this.tag)
    s.appendChild(range.extractContents())
    range.insertNode(s)
    this.api.selection.expandToTag(s)
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
