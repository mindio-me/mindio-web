/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 新建笔记、标题被清空时写入库里的默认标题（标题字段后端要求非空），按当时的界面语言写入，
// 所以库里两种都会有。要和语言包里的 workspace.notes.untitledNote 保持一致。
const DEFAULT_NOTE_TITLES = ['未命名笔记', 'Untitled Note']

export function isDefaultNoteTitle(title) {
  return typeof title === 'string' && DEFAULT_NOTE_TITLES.includes(title.trim())
}

/** 列表/最近笔记等处显示用：空标题或任一语言的默认标题，都显示当前语言的"未命名" */
export function noteTitleLabel(title, untitledLabel) {
  if (!title || !title.trim() || isDefaultNoteTitle(title)) return untitledLabel
  return title
}

/** 把多篇笔记的标题拼成一句话里用的列表：中文用书名号加顿号，其它语言用引号加逗号 */
export function formatNoteTitleList(notes, untitledLabel, locale) {
  const titles = notes.map(n => noteTitleLabel(n.title, untitledLabel))
  return locale === 'zh-CN'
    ? titles.map(t => `《${t}》`).join('、')
    : titles.map(t => `"${t}"`).join(', ')
}
