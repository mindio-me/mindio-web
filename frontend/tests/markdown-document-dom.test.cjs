const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

const dom = new JSDOM('<!doctype html><html><body></body></html>')
const { window } = dom
// jsdom 没实现 scrollIntoView：打桩记录被滚动到的元素
let scrolledTo = null
window.HTMLElement.prototype.scrollIntoView = function () { scrolledTo = this }

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { findAnchorTarget, handleAnchorClick, handleImageError } = loadEsmReal('utils/markdown-document-dom.js')

function mount(html) {
  const container = window.document.createElement('div')
  container.innerHTML = html
  window.document.body.appendChild(container)
  container.addEventListener('click', e => handleAnchorClick(container, e))
  container.addEventListener('error', e => handleImageError(e, (key, p) => `${key}:${p.name}`), true)
  return container
}

function click(el) {
  const event = new window.MouseEvent('click', { bubbles: true, cancelable: true })
  el.dispatchEvent(event)
  return event
}

test('# 链接按 user-content- 前缀找到中文标题并滚动，不触发默认跳转', () => {
  const c = mount('<h2 id="user-content-中文-标题">T</h2><a href="#%E4%B8%AD%E6%96%87-%E6%A0%87%E9%A2%98"><span>go</span></a>')
  scrolledTo = null
  const event = click(c.querySelector('span'))
  assert.equal(event.defaultPrevented, true)
  assert.equal(scrolledTo, c.querySelector('h2'))
})

test('脚注链接 #fn1 找到 user-content-fn1；也兼容没有前缀的 id', () => {
  const c = mount('<li id="user-content-fn1">n</li><b id="raw">r</b><a id="a1" href="#fn1">1</a><a id="a2" href="#raw">r</a>')
  assert.equal(findAnchorTarget(c, '#fn1'), c.querySelector('li'))
  assert.equal(findAnchorTarget(c, '#raw'), c.querySelector('b'))
})

test('找不到目标时只阻止跳转、不报错；外链不拦截', () => {
  const c = mount('<a id="miss" href="#nope">x</a><a id="ext" href="https://example.com">e</a>')
  scrolledTo = null
  assert.equal(click(c.querySelector('#miss')).defaultPrevented, true)
  assert.equal(scrolledTo, null)
  assert.equal(click(c.querySelector('#ext')).defaultPrevented, false)
})

test('畸形的百分号编码不抛异常', () => {
  const c = mount('<a href="#%E4%ZZ">x</a>')
  assert.doesNotThrow(() => click(c.querySelector('a')))
})

test('本地图片加载失败：隐藏图片，显示"未随导入上传"文案（文件名解码）', () => {
  const c = mount('<img src="http://h/api/a%20b.png" data-md-local="a%20b.png" alt="pic">')
  const img = c.querySelector('img')
  img.dispatchEvent(new window.Event('error'))
  assert.equal(img.style.display, 'none')
  const fallback = img.nextElementSibling
  assert.equal(fallback.className, 'md-image-fallback')
  assert.equal(fallback.textContent, 'markdownDocument.imageNotImported:a b.png')
})

test('远程图片加载失败：显示"加载失败"文案（优先 alt）；重复 error 不重复插入', () => {
  const c = mount('<img src="https://x.com/r.png" alt="remote">')
  const img = c.querySelector('img')
  img.dispatchEvent(new window.Event('error'))
  img.dispatchEvent(new window.Event('error'))
  assert.equal(img.nextElementSibling.textContent, 'markdownDocument.imageLoadFailed:remote')
  assert.equal(c.querySelectorAll('.md-image-fallback').length, 1)
})

test('文案用 textContent 插入，不解析 HTML', () => {
  const c = mount('<img src="x.png" data-md-local="&lt;b&gt;x.png" alt="">')
  c.querySelector('img').dispatchEvent(new window.Event('error'))
  assert.equal(c.querySelector('.md-image-fallback b'), null)
})
