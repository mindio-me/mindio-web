const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

const { window } = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:3000/workspace/notes' })
globalThis.window = window
globalThis.document = window.document
globalThis.DOMParser = window.DOMParser

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { buildClipboardHtml, parseBlockCopyMarker, mediaWidthElement } = loadEsmReal('utils/editorjsMediaCopy.js')

const parse = html => new DOMParser().parseFromString(html, 'text/html')

const image = {
  file: { url: 'http://localhost:8080/uploads/public/note/a.gif' },
  caption: 'He said "hi" & <b>bold</b>',
  withBorder: true,
  withBackground: false,
  stretched: false,
  widthPercent: 60
}
const video = { url: 'http://localhost:8080/uploads/public/note/v.mp4', caption: '演示', widthPercent: 80 }
const embed = {
  embedUrl: 'https://platform.twitter.com/embed/Tweet.html?id=1',
  sourceUrl: 'https://x.com/someone/status/1',
  posterUrl: null,
  caption: '',
  widthPercent: null,
  aspectRatio: '16/9',
  fixedWidth: 550,
  fixedHeight: 600,
  autoHeight: true
}

test('同站复制的图片/视频/嵌入视频标记能原样解析回块类型和数据', () => {
  assert.deepEqual(parseBlockCopyMarker(buildClipboardHtml('image', image, image.file.url)), { type: 'image', data: image })
  assert.deepEqual(parseBlockCopyMarker(buildClipboardHtml('video', video, video.url)), { type: 'video', data: video })
  assert.deepEqual(parseBlockCopyMarker(buildClipboardHtml('embed', embed, embed.sourceUrl)), { type: 'embed', data: embed })
})

test('外部应用看到的：图片是 img（alt 去标签），视频是指向链接的 a', () => {
  const img = parse(buildClipboardHtml('image', image, image.file.url)).querySelector('img')
  assert.equal(img.getAttribute('src'), image.file.url)
  assert.equal(img.getAttribute('alt'), 'He said "hi" & bold')

  const a = parse(buildClipboardHtml('embed', embed, embed.sourceUrl)).querySelector('a')
  assert.equal(a.getAttribute('href'), embed.sourceUrl)
  assert.equal(a.textContent, embed.sourceUrl)
  assert.equal(parse(buildClipboardHtml('video', video, video.url)).querySelector('a').textContent, '演示')
})

test('只支持图片时的旧标记仍按图片解析', () => {
  const legacy = `<img src="x" data-mindio-image='${JSON.stringify({ origin: 'http://localhost:3000', data: image }).replace(/'/g, '&#39;')}'>`
  assert.deepEqual(parseBlockCopyMarker(legacy), { type: 'image', data: image })
})

test('别的 origin 复制来的标记不认，按普通内容粘贴', () => {
  const html = buildClipboardHtml('video', video, video.url).replace('localhost:3000', 'example.com')
  assert.equal(parseBlockCopyMarker(html), null)
})

test('没有标记、标记损坏、未知块类型或缺链接时返回 null', () => {
  assert.equal(parseBlockCopyMarker(''), null)
  assert.equal(parseBlockCopyMarker('<img src="x.png">'), null)
  assert.equal(parseBlockCopyMarker('<a data-mindio-block="{bad">x</a>'), null)
  const marker = (type, data) => `<a data-mindio-block='${JSON.stringify({ origin: 'http://localhost:3000', type, data })}'>x</a>`
  assert.equal(parseBlockCopyMarker(marker('audio', { url: 'a.mp3' })), null)
  assert.equal(parseBlockCopyMarker(marker('video', { url: '' })), null)
  assert.equal(parseBlockCopyMarker(marker('image', {})), null)
})

test('宽度元素：固定尺寸的嵌入视频没有', () => {
  const holder = document.createElement('div')
  holder.innerHTML = '<div class="embed-video-tool__frame" data-fixed="true"></div>'
  assert.equal(mediaWidthElement(holder, 'embed'), null)
  holder.innerHTML = '<div class="embed-video-tool__frame"></div>'
  assert.ok(mediaWidthElement(holder, 'embed'))
  holder.innerHTML = '<div class="video-tool__video"></div>'
  assert.ok(mediaWidthElement(holder, 'video'))
  assert.equal(mediaWidthElement(holder, 'audio'), null)
})
