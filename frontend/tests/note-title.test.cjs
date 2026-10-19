const assert = require('node:assert/strict')
const { test } = require('node:test')

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { isDefaultNoteTitle, noteTitleLabel, formatNoteTitleList } = loadEsmReal('utils/noteTitle.js')

test('中英文两种默认标题都算默认标题，前后空格不影响', () => {
  assert.equal(isDefaultNoteTitle('未命名笔记'), true)
  assert.equal(isDefaultNoteTitle(' Untitled Note '), true)
  assert.equal(isDefaultNoteTitle('未命名笔记 2'), false)
  assert.equal(isDefaultNoteTitle(''), false)
  assert.equal(isDefaultNoteTitle(null), false)
})

test('显示标题：空标题和任一语言的默认标题都显示当前语言的"未命名"', () => {
  assert.equal(noteTitleLabel('未命名笔记', 'Untitled Note'), 'Untitled Note')
  assert.equal(noteTitleLabel('Untitled Note', '未命名笔记'), '未命名笔记')
  assert.equal(noteTitleLabel('', 'Untitled Note'), 'Untitled Note')
  assert.equal(noteTitleLabel('   ', 'Untitled Note'), 'Untitled Note')
  assert.equal(noteTitleLabel(undefined, 'Untitled Note'), 'Untitled Note')
  assert.equal(noteTitleLabel('My note', 'Untitled Note'), 'My note')
})

test('标题列表：中文用书名号和顿号，其它语言用引号和逗号', () => {
  const notes = [{ title: 'A' }, { title: '未命名笔记' }]
  assert.equal(formatNoteTitleList(notes, '未命名笔记', 'zh-CN'), '《A》、《未命名笔记》')
  assert.equal(formatNoteTitleList(notes, 'Untitled Note', 'en'), '"A", "Untitled Note"')
})
