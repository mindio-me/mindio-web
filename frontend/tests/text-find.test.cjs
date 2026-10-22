const assert = require('node:assert/strict')
const { test } = require('node:test')
const { loadEsm } = require('./helpers/load-esm.cjs')

const { locateMatches } = loadEsm('utils/textFind.js')

test('locateMatches finds case-insensitive hits within one text node', () => {
  assert.deepEqual(locateMatches(['Foo bar foo'], 'FOO'), [
    { start: [0, 0], end: [0, 3] },
    { start: [0, 8], end: [0, 11] }
  ])
})

test('locateMatches spans text nodes split by inline formatting', () => {
  // "研究<b>助手</b>米欧" 被拆成三个文本节点
  assert.deepEqual(locateMatches(['研究', '助手', '米欧'], '究助'), [
    { start: [0, 1], end: [1, 1] }
  ])
  // 命中恰好结束在节点边界：end 归到前一个节点末尾
  assert.deepEqual(locateMatches(['ab', 'cd'], 'ab'), [{ start: [0, 0], end: [0, 2] }])
  // 命中恰好从节点边界开始：start 归到后一个节点开头
  assert.deepEqual(locateMatches(['ab', 'cd'], 'cd'), [{ start: [1, 0], end: [1, 2] }])
})

test('locateMatches ignores blank queries', () => {
  assert.deepEqual(locateMatches(['abc'], '  '), [])
  assert.deepEqual(locateMatches([], 'a'), [])
})
