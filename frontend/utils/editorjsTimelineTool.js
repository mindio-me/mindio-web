/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
import { createEl, createButton } from './editorjsUiHelpers'

export default class TimelineTool {
  static get toolbox() {
    return {
      title: '时间线',
      icon: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="2" x2="12" y2="22"/><circle cx="12" cy="6" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="18" r="2"/></svg>'
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, api, readOnly }) {
    this.api = api
    this.readOnly = readOnly
    this.data = { items: Array.isArray(data?.items) ? data.items : [] }
    this.wrapper = null
  }

  render() {
    this.wrapper = createEl('div', 'cdx-timeline')
    this._renderList()
    if (!this.readOnly) {
      this.wrapper.appendChild(createButton('+ 添加时间点', () => this._openAddForm(), 'cdx-timeline__add-btn'))
    }
    return this.wrapper
  }

  _sortedItems() {
    return this.data.items
      .map((item, originalIndex) => ({ item, originalIndex }))
      .sort((a, b) => (a.item.date || '').localeCompare(b.item.date || ''))
  }

  _renderList() {
    const existing = this.wrapper.querySelector('.cdx-timeline__list')
    if (existing) existing.remove()
    const list = createEl('ul', 'cdx-timeline__list')
    this._sortedItems().forEach(({ item, originalIndex }) => {
      const li = createEl('li', 'cdx-timeline__item')
      li.appendChild(createEl('div', 'cdx-timeline__date', { textContent: item.date }))
      li.appendChild(createEl('div', 'cdx-timeline__title', { textContent: item.title }))
      if (item.description) li.appendChild(createEl('div', 'cdx-timeline__description', { textContent: item.description }))
      if (item.link) {
        li.appendChild(createEl('a', 'cdx-timeline__link', { textContent: '🔗', href: item.link, target: '_blank', rel: 'noopener noreferrer' }))
      }
      if (!this.readOnly) {
        li.appendChild(createButton('×', () => {
          this.data.items.splice(originalIndex, 1)
          this._renderList()
        }, 'cdx-timeline__delete'))
      }
      list.appendChild(li)
    })
    this.wrapper.insertBefore(list, this.wrapper.firstChild)
  }

  _openAddForm() {
    if (this.wrapper.querySelector('.cdx-timeline__form')) return
    const form = createEl('div', 'cdx-timeline__form')
    const dateInput = createEl('input', 'cdx-timeline__input', { placeholder: '日期（如 2024-01 或 2024-01-15）' })
    const titleInput = createEl('input', 'cdx-timeline__input', { placeholder: '发生了什么' })
    const descInput = createEl('textarea', 'cdx-timeline__input', { placeholder: '详细说明（可选）' })
    const linkInput = createEl('input', 'cdx-timeline__input', { placeholder: '相关链接（可选）' })
    form.append(dateInput, titleInput, descInput, linkInput)
    form.appendChild(createButton('添加', () => {
      if (!dateInput.value.trim() || !titleInput.value.trim()) return
      this.data.items.push({
        date: dateInput.value.trim(),
        title: titleInput.value.trim(),
        description: descInput.value.trim(),
        link: linkInput.value.trim()
      })
      form.remove()
      this._renderList()
    }))
    this.wrapper.appendChild(form)
  }

  save() {
    return { items: this.data.items }
  }

  validate(savedData) {
    return Array.isArray(savedData.items)
  }
}
