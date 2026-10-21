const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

const { window } = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost:3000/workspace/notes' })
globalThis.window = window
globalThis.document = window.document
globalThis.DOMParser = window.DOMParser

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { buildClipboardHtml, parseImageCopyMarker } = loadEsmReal('utils/editorjsImageCopy.js')

const data = {
  file: { url: 'http://localhost:8080/uploads/public/note/a.gif' },
  caption: 'He said "hi" & <b>bold</b>',
  withBorder: true,
  withBackground: false,
  stretched: false,
  widthPercent: 60
}

test('同站复制的标记能原样解析回图片块数据', () => {
  const html = buildClipboardHtml(data, data.file.url)
  assert.deepEqual(parseImageCopyMarker(html), data)
})

test('外部应用看到的是普通 img：src 指向原图，alt 去掉标签', () => {
  const img = new DOMParser().parseFromString(buildClipboardHtml(data, data.file.url), 'text/html').querySelector('img')
  assert.equal(img.getAttribute('src'), data.file.url)
  assert.equal(img.getAttribute('alt'), 'He said "hi" & bold')
})

test('别的 origin 复制来的标记不认，按普通图片粘贴', () => {
  const html = buildClipboardHtml(data, data.file.url).replace('localhost:3000', 'example.com')
  assert.equal(parseImageCopyMarker(html), null)
})

test('没有标记或标记损坏时返回 null', () => {
  assert.equal(parseImageCopyMarker(''), null)
  assert.equal(parseImageCopyMarker('<img src="x.png">'), null)
  assert.equal(parseImageCopyMarker('<img src="x.png" data-mindio-image="{bad">'), null)
  assert.equal(parseImageCopyMarker('<img data-mindio-image=\'{"origin":"http://localhost:3000","data":{}}\'>'), null)
})
