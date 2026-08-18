/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * 代码块「自动换行」Block Tune
 * 默认沿用 @editorjs/code 自带样式（不换行、横向滚动），
 * 开启后给该代码块加 cdx-code--wrap class，让长行换行显示。
 */
export default class CodeWrapTune {
  static get isTune() {
    return true
  }

  constructor({ data, block }) {
    this.block = block
    this.wrapEnabled = !!(data && data.wrap)
    // 构造时机早于 EditorJS 把渲染好的 DOM 挂到 block.holder（先 composeTunes() 再 this.holder = this.compose()），
    // 此刻读 block.holder 还是 undefined。用微任务延后到当前构造调用栈结束后再应用，届时 holder 已就绪。
    Promise.resolve().then(() => this._applyClass())
  }

  render() {
    return {
      icon: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="17" y2="12"/><path d="M17 12h2a2 2 0 0 1 0 4h-4"/><polyline points="13 14 11 16 13 18"/><line x1="3" y1="18" x2="9" y2="18"/></svg>',
      label: this.wrapEnabled ? '取消自动换行' : '自动换行',
      isActive: this.wrapEnabled,
      closeOnActivate: true,
      onActivate: () => {
        this.wrapEnabled = !this.wrapEnabled
        this._applyClass()
        // 初始加载时的高度由页面级 setupCodeBlockAutoResize 统一处理（那时块已挂载好，量得准）；
        // 这里只处理运行时切换——此时块早已挂载，可以放心量 scrollHeight
        this._resizeTextarea()
      }
    }
  }

  save() {
    return { wrap: this.wrapEnabled }
  }

  _applyClass() {
    if (this.block && this.block.holder) {
      this.block.holder.classList.toggle('cdx-code--wrap', this.wrapEnabled)
    }
  }

  _resizeTextarea() {
    const textarea = this.block && this.block.holder && this.block.holder.querySelector('.ce-code__textarea')
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = Math.max(textarea.scrollHeight, 60) + 'px'
  }
}
