const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadEsm } = require('./helpers/load-esm.cjs')

const playback = loadEsm('utils/transcriptPlayback.js')
const search = loadEsm('utils/transcriptSearch.js')
const editor = loadEsm('utils/transcriptEditor.js')

const UTTS = [
  { start: 5000, end: 7000, text: '甲' },
  { start: 7500, end: 9000, text: '乙' },
  { start: 15000, end: 16000, text: '丙' }
]

test('findIndexAt locates the sentence at a time', () => {
  assert.equal(playback.findIndexAt(UTTS, 0), -1)
  assert.equal(playback.findIndexAt(UTTS, 5000), 0)
  assert.equal(playback.findIndexAt(UTTS, 7400), 0)
  assert.equal(playback.findIndexAt(UTTS, 12000), 1)
  assert.equal(playback.findIndexAt(UTTS, 99999), 2)
  assert.equal(playback.findIndexAt([], 10), -1)
})

test('silenceSkipTarget jumps only over long gaps', () => {
  assert.equal(playback.silenceSkipTarget(UTTS, 1000), 4700) // 首句前 4 秒空档
  assert.equal(playback.silenceSkipTarget(UTTS, 6000), null) // 句中
  assert.equal(playback.silenceSkipTarget(UTTS, 7200), null) // 0.5 秒短空档
  assert.equal(playback.silenceSkipTarget(UTTS, 10000), 14700) // 6 秒空档
  assert.equal(playback.silenceSkipTarget(UTTS, 14800), null) // 已越过跳转点
  assert.equal(playback.silenceSkipTarget(UTTS, 17000), null) // 末句之后
  assert.equal(playback.silenceSkipTarget([], 17000), null)
})

test('findMatches is case-insensitive and non-overlapping', () => {
  assert.deepEqual(search.findMatches(['Hello hello', '无', 'aaa'], 'hello'), [
    { line: 0, from: 0, to: 5 }, { line: 0, from: 6, to: 11 }
  ])
  assert.deepEqual(search.findMatches(['aaa'], 'aa'), [{ line: 0, from: 0, to: 2 }])
  assert.deepEqual(search.findMatches(['abc'], '  '), [])
})

test('diffEdits returns only changed sentences, trimmed', () => {
  assert.deepEqual(editor.diffEdits(UTTS, ['甲', ' 乙改 ', '丙']), [{ index: 1, text: '乙改' }])
  assert.deepEqual(editor.diffEdits(UTTS, ['甲 ', '乙', '丙']), [])
})

test('hasFailedPlaceholder detects failed segment lines', () => {
  assert.equal(editor.hasFailedPlaceholder(UTTS), false)
  assert.equal(editor.hasFailedPlaceholder([{ text: '[第 2 段转写失败]' }]), true)
})

test('speakerText replaces speaker placeholders', () => {
  assert.equal(editor.speakerText('{{S1}} 和 {{S2}} 讨论', { 1: '张总' }), '张总 和 说话人 2 讨论')
  assert.equal(editor.speakerText('无占位', null), '无占位')
})
