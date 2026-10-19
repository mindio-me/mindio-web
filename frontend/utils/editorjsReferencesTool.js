/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
import { createEl, createButton, createTabs, toolTranslator } from './editorjsUiHelpers'

const KIND_ICON = { link: '🔗', file: '📄', note: '📝' }

export default class ReferencesTool {
  static get toolbox() {
    return {
      title: 'References',
      icon: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 12h6M9 16h6M9 8h1"/><path d="M4 4h16v16H4z"/></svg>'
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, config, api, readOnly }) {
    this.api = api
    this._t = toolTranslator(api)
    this.readOnly = readOnly
    this.config = config || {}
    this.data = { items: Array.isArray(data?.items) ? data.items : [] }
    this.wrapper = null
  }

  render() {
    this.wrapper = createEl('div', 'cdx-references')
    this._renderList()
    if (!this.readOnly) {
      this.wrapper.appendChild(createButton(this._t('+ Add reference'), () => this._openAddForm(), 'cdx-references__add-btn'))
    }
    return this.wrapper
  }

  _renderList() {
    const existing = this.wrapper.querySelector('.cdx-references__list')
    if (existing) existing.remove()
    const list = createEl('ul', 'cdx-references__list')
    this.data.items.forEach((item, idx) => {
      const li = createEl('li', 'cdx-references__item')
      const link = createEl('a', 'cdx-references__link', {
        textContent: `${KIND_ICON[item.kind] || '🔗'} ${item.title || this._t('(Untitled)')}`,
        href: item.kind === 'note'
          ? (item.noteId != null ? `/workspace/notes/${item.noteId}` : `/workspace/notes/uid/${item.noteUid}`)
          : (item.url || '#'),
        target: item.kind === 'note' ? '_self' : '_blank',
        rel: 'noopener noreferrer'
      })
      li.appendChild(link)
      if (item.note) {
        li.appendChild(createEl('div', 'cdx-references__note', { textContent: item.note }))
      }
      if (!this.readOnly) {
        const del = createButton('×', () => {
          this.data.items.splice(idx, 1)
          this._renderList()
        }, 'cdx-references__delete')
        li.appendChild(del)
      }
      list.appendChild(li)
    })
    this.wrapper.insertBefore(list, this.wrapper.firstChild)
  }

  _openAddForm() {
    if (this.wrapper.querySelector('.cdx-references__form')) return
    const form = createEl('div', 'cdx-references__form')
    const body = createEl('div', 'cdx-references__form-body')
    form.appendChild(createTabs(
      [
        { key: 'clip', label: this._t('Search clips') },
        { key: 'note', label: this._t('Search notes') },
        { key: 'manual', label: this._t('Add manually') }
      ],
      (key) => this._renderFormBody(body, key)
    ))
    form.appendChild(body)
    this._renderFormBody(body, 'clip')
    this.wrapper.appendChild(form)
  }

  _renderFormBody(body, mode) {
    body.innerHTML = ''
    if (mode === 'manual') {
      body.appendChild(this._buildManualForm())
    } else {
      body.appendChild(this._buildSearchForm(mode))
    }
  }

  _buildManualForm() {
    const wrap = createEl('div', 'cdx-references__manual')
    const titleInput = createEl('input', 'cdx-references__input', { placeholder: this._t('Title') })
    const urlInput = createEl('input', 'cdx-references__input', { placeholder: this._t('URL') })
    const noteInput = createEl('input', 'cdx-references__input', { placeholder: this._t('Note (optional)') })
    const fileInput = createEl('input', null, { type: 'file', style: 'display:none' })
    let uploadedFile = null // {url, name} —— 选完文件后先记住，等点"添加"时才真正入 items，跟链接走同一个确认步骤

    fileInput.addEventListener('change', async () => {
      const file = fileInput.files[0]
      if (!file || !this.config.uploader?.uploadByFile) return
      fileStatus.textContent = this._t('Uploading…')
      try {
        const result = await this.config.uploader.uploadByFile(file)
        if (result?.success) {
          uploadedFile = { url: result.file.url, name: file.name }
          fileStatus.textContent = `${this._t('Selected: ')}${file.name}`
          if (!titleInput.value.trim()) titleInput.value = file.name
        } else {
          fileStatus.textContent = this._t('Upload failed, please try again')
        }
      } catch (e) {
        fileStatus.textContent = this._t('Upload failed, please try again')
      }
    })
    const fileStatus = createEl('span', 'cdx-references__file-status', { textContent: this._t('No file chosen') })
    const fileRow = createEl('div', 'cdx-references__file-row')
    fileRow.append(createButton(this._t('Choose a file to upload'), () => fileInput.click()), fileStatus, fileInput)

    wrap.append(titleInput, urlInput, fileRow, noteInput)
    wrap.appendChild(createButton(this._t('Add'), () => {
      if (!titleInput.value.trim()) return
      if (uploadedFile) {
        this._addItem({ kind: 'file', title: titleInput.value.trim(), url: uploadedFile.url, note: noteInput.value.trim() })
      } else {
        this._addItem({ kind: 'link', title: titleInput.value.trim(), url: urlInput.value.trim(), note: noteInput.value.trim() })
      }
    }))
    return wrap
  }

  _buildSearchForm(mode) {
    const wrap = createEl('div', 'cdx-references__search')
    const input = createEl('input', 'cdx-references__input', { placeholder: this._t('Search by keyword…') })
    const results = createEl('div', 'cdx-references__results')
    const doSearch = async () => {
      const keyword = input.value.trim()
      if (!keyword) return
      results.textContent = this._t('Searching…')
      const endpoint = mode === 'clip' ? '/v1/clips' : '/v1/notes'
      try {
        const url = `${this.config.axiosBaseURL || ''}${endpoint}?keyword=${encodeURIComponent(keyword)}`
        const res = await fetch(url, { headers: this.config.getAuthHeader ? this.config.getAuthHeader() : {} })
        const body = await res.json()
        const list = body.content || body || []
        results.innerHTML = ''
        list.forEach((entity) => {
          const row = createButton(entity.title, () => {
            if (mode === 'clip') {
              this._addItem({ kind: 'link', title: entity.title, url: entity.sourceUrl || '', sourcedFromClipId: entity.id })
            } else {
              this._addItem({ kind: 'note', title: entity.title, noteId: entity.id })
            }
          }, 'cdx-references__result-item')
          results.appendChild(row)
        })
        if (list.length === 0) results.textContent = this._t('No matches found')
      } catch (e) {
        results.textContent = this._t('Search failed, please try again')
      }
    }
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') doSearch() })
    wrap.append(input, createButton(this._t('Search'), doSearch), results)
    return wrap
  }

  _addItem(item) {
    this.data.items.push(item)
    this.wrapper.querySelector('.cdx-references__form')?.remove()
    this._renderList()
  }

  save() {
    return { items: this.data.items }
  }

  validate(savedData) {
    return Array.isArray(savedData.items)
  }
}
