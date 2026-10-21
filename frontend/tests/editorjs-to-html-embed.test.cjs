const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

globalThis.window = new JSDOM('<!doctype html><html><body></body></html>').window

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { editorjsToHtml } = loadEsmReal('utils/editorjs-to-html.js')

const render = block => editorjsToHtml({ blocks: [block] })

// 打印窗口是 blob: 页面，嵌入播放器拿不到 Referer（YouTube 报"视频播放器配置错误"），
// 纸上也放不了视频：打印时嵌入视频一律输出封面 + 原视频链接，不放 iframe
test('嵌入视频打印成封面图和原视频链接，不出 iframe', () => {
  const html = render({
    type: 'embed',
    data: {
      embedUrl: 'https://www.youtube.com/embed/abc',
      sourceUrl: 'https://www.youtube.com/watch?v=abc',
      posterUrl: 'https://i.ytimg.com/vi/abc/hqdefault.jpg',
      caption: '演示 <视频>'
    }
  })
  assert.ok(!html.includes('<iframe'))
  assert.ok(html.includes('src="https://i.ytimg.com/vi/abc/hqdefault.jpg"'))
  assert.ok(html.includes('href="https://www.youtube.com/watch?v=abc"'))
  assert.ok(html.includes('演示 &lt;视频&gt;'))
})

test('没有封面的嵌入视频（推文）打印成占位框加链接', () => {
  const html = render({
    type: 'embed',
    data: { embedUrl: 'https://platform.twitter.com/embed/Tweet.html?id=1', sourceUrl: 'https://x.com/a/status/1', posterUrl: null }
  })
  assert.ok(!html.includes('<iframe'))
  assert.ok(!html.includes('<img'))
  assert.ok(html.includes('href="https://x.com/a/status/1"'))
})

test('没有原页面地址时退回用播放器地址做链接', () => {
  const html = render({ type: 'embed', data: { embedUrl: 'https://player.bilibili.com/player.html?bvid=BV1' } })
  assert.ok(html.includes('href="https://player.bilibili.com/player.html?bvid=BV1"'))
})

test('画廊里的视频同样打印成封面，不出 iframe', () => {
  const html = render({
    type: 'mediaGallery',
    data: {
      items: [
        { type: 'video', embedUrl: 'https://www.youtube.com/embed/abc', posterUrl: 'https://i.ytimg.com/vi/abc/hqdefault.jpg' },
        { type: 'video', embedUrl: 'https://platform.twitter.com/embed/Tweet.html?id=1', posterUrl: null }
      ]
    }
  })
  assert.ok(!html.includes('<iframe'))
  assert.ok(html.includes('src="https://i.ytimg.com/vi/abc/hqdefault.jpg"'))
  assert.ok(html.includes('href="https://platform.twitter.com/embed/Tweet.html?id=1"'))
})

const { fillMissingTweetPosters } = loadEsmReal('utils/editorjs-to-html.js')

test('导出前给没有封面的推文补封面：单个嵌入和画廊都补，已有封面和非推文不动，原数据不改', async () => {
  const tweet = id => `https://platform.twitter.com/embed/Tweet.html?id=${id}&dnt=true`
  const data = {
    blocks: [
      { type: 'embed', data: { embedUrl: tweet('1'), posterUrl: null } },
      { type: 'embed', data: { embedUrl: tweet('2'), posterUrl: 'https://pbs.twimg.com/keep.jpg' } },
      { type: 'embed', data: { embedUrl: 'https://www.youtube.com/embed/abc', posterUrl: null } },
      { type: 'embed', data: { embedUrl: tweet('3'), posterUrl: null } },
      { type: 'mediaGallery', data: { items: [{ type: 'video', embedUrl: tweet('4') }, { type: 'image', url: 'x.png' }] } },
      { type: 'paragraph', data: { text: 'hi' } }
    ]
  }
  const asked = []
  const filled = await fillMissingTweetPosters(data, async (id) => {
    asked.push(id)
    return id === '3' ? null : `https://pbs.twimg.com/${id}.jpg`
  })
  assert.deepEqual(asked.sort(), ['1', '3', '4'])
  assert.equal(filled.blocks[0].data.posterUrl, 'https://pbs.twimg.com/1.jpg')
  assert.equal(filled.blocks[1].data.posterUrl, 'https://pbs.twimg.com/keep.jpg')
  assert.equal(filled.blocks[2].data.posterUrl, null)
  assert.equal(filled.blocks[3].data.posterUrl, null)
  assert.equal(filled.blocks[4].data.items[0].posterUrl, 'https://pbs.twimg.com/4.jpg')
  assert.equal(data.blocks[0].data.posterUrl, null)
  assert.equal(data.blocks[4].data.items[0].posterUrl, undefined)
})

test('取封面出错时当作没取到，不影响导出', async () => {
  const data = { blocks: [{ type: 'embed', data: { embedUrl: 'https://platform.twitter.com/embed/Tweet.html?id=1', posterUrl: null } }] }
  const filled = await fillMissingTweetPosters(data, async () => { throw new Error('network') })
  assert.equal(filled.blocks[0].data.posterUrl, null)
})
