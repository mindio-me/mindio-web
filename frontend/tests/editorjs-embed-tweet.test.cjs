const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

const { window } = new JSDOM('<!doctype html><html><body></body></html>')
globalThis.window = window
globalThis.document = window.document

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { default: EmbedVideoTool } = loadEsmReal('utils/editorjsEmbedVideoTool.js')

const tick = () => new Promise(resolve => setTimeout(resolve, 0))
const tweetData = () => ({
  embedUrl: 'https://platform.twitter.com/embed/Tweet.html?id=123&dnt=true',
  sourceUrl: 'https://x.com/a/status/123',
  posterUrl: null,
  widthPercent: null,
  aspectRatio: '16/9',
  fixedWidth: 550,
  fixedHeight: 700,
  autoHeight: true
})

function mount(data, config, { readOnly = false } = {}) {
  let changes = 0
  const tool = new EmbedVideoTool({ data, config, readOnly, block: { dispatchChange: () => changes++ } })
  const el = tool.render()
  document.body.appendChild(el)
  return { tool, el, changes: () => changes }
}

test('推文平时只显示视频封面，不加载推文卡片；没封面时取一次并存进数据', async () => {
  const { tool, el, changes } = mount(tweetData(), {
    fetchTweetPoster: async () => 'https://pbs.twimg.com/p.jpg',
    fetchTweetVideo: async () => null
  })
  assert.equal(el.querySelector('iframe'), null)
  const frame = el.querySelector('.embed-video-tool__frame')
  assert.notEqual(frame.getAttribute('data-fixed'), 'true')
  await tick()
  assert.equal(tool.save().posterUrl, 'https://pbs.twimg.com/p.jpg')
  assert.equal(changes(), 1)
  assert.equal(el.querySelector('.embed-video-tool__facade__poster').getAttribute('src'), 'https://pbs.twimg.com/p.jpg')
})

test('点击封面在原位播放 mp4，记下视频真实比例', async () => {
  const { tool, el } = mount({ ...tweetData(), posterUrl: 'https://pbs.twimg.com/p.jpg' }, {
    fetchTweetPoster: async () => null,
    fetchTweetVideo: async () => ({ videoUrl: 'https://video.twimg.com/v.mp4', aspectRatio: '9/16' })
  })
  el.querySelector('.embed-video-tool__facade').dispatchEvent(new window.Event('click'))
  assert.ok(el.querySelector('.embed-video-tool__video-loading'))
  await tick()
  const player = el.querySelector('iframe.embed-video-tool__video')
  assert.ok(player)
  assert.ok(player.srcdoc.includes('https://video.twimg.com/v.mp4'))
  assert.equal(tool.save().videoAspectRatio, '9/16')
  // 竖屏视频限制高度：收窄最大宽度
  assert.ok(el.querySelector('.embed-video-tool__frame').style.maxWidth.includes('70vh'))
})

test('取不到视频直链时退回推文卡片', async () => {
  const { el } = mount({ ...tweetData(), posterUrl: 'https://pbs.twimg.com/p.jpg' }, {
    fetchTweetPoster: async () => null,
    fetchTweetVideo: async () => null
  })
  el.querySelector('.embed-video-tool__facade').dispatchEvent(new window.Event('click'))
  await tick()
  const card = el.querySelector('iframe')
  assert.ok(card.src.startsWith('https://platform.twitter.com/embed/Tweet.html'))
  assert.equal(el.querySelector('.embed-video-tool__frame').getAttribute('data-fixed'), 'true')
})

test('没有取直链能力时（未传配置）仍按原来的推文卡片显示', () => {
  const { el } = mount(tweetData(), {})
  assert.ok(el.querySelector('iframe').src.startsWith('https://platform.twitter.com/embed/Tweet.html'))
})

test('推文保存宽度百分比，非推文的固定尺寸嵌入仍不存宽度', () => {
  const { tool } = mount(tweetData(), { fetchTweetVideo: async () => null })
  assert.equal(tool.save().widthPercent, 100)
  const douyin = mount({ embedUrl: 'https://open.douyin.com/player/video?vid=1', fixedWidth: 324, fixedHeight: 720 }, {})
  assert.equal(douyin.tool.save().widthPercent, null)
})

test('重画前把拖拽调过的宽度记回数据', async () => {
  const { tool, el } = mount({ ...tweetData(), posterUrl: 'https://pbs.twimg.com/p.jpg' }, {
    fetchTweetVideo: async () => ({ videoUrl: 'https://video.twimg.com/v.mp4', aspectRatio: '16/9' })
  })
  el.querySelector('.embed-video-tool__frame').style.width = '60%'
  el.querySelector('.embed-video-tool__facade').dispatchEvent(new window.Event('click'))
  assert.equal(el.querySelector('.embed-video-tool__frame').style.width, '60%')
  assert.equal(tool.save().widthPercent, 60)
})
