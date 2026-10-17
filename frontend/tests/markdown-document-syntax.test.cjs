const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

globalThis.window = new JSDOM('<!doctype html><html><body></body></html>').window

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { renderMarkdownDocument: render } = loadEsmReal('utils/markdown-document.js')

test('文首 YAML front matter 隐藏', () => {
  const html = render('---\ntitle: x\ntags: [a]\n---\n# Title\n')
  assert.ok(!html.includes('title: x'))
  assert.ok(!html.includes('<hr'))
  assert.match(html, /<h1 id="user-content-title">Title<\/h1>/)
})

test('CRLF 文件的 front matter 同样隐藏，开头 BOM 也去掉', () => {
  const html = render('﻿---\r\ntitle: a\r\n---\r\n# H\r\n')
  assert.ok(!html.includes('title: a'))
  assert.match(html, /<h1 id="user-content-h">H<\/h1>/)
})

test('不是 key: value 开头的 --- 仍是分割线；不在文首的不处理', () => {
  assert.match(render('---\n\ntext'), /<hr>/)
  const later = render('intro\n\n---\ntitle: x\n---\n')
  assert.ok(later.includes('title: x'))
})

test('没有闭合的 front matter 不吞掉正文', () => {
  const html = render('---\ntitle: x\nbody text')
  assert.ok(html.includes('body text'))
})

test('GitHub 提示块：五种类型，默认标题为类型名', () => {
  for (const type of ['NOTE', 'TIP', 'IMPORTANT', 'WARNING', 'CAUTION']) {
    const html = render(`> [!${type}]\n> body **b**`)
    const kind = type.toLowerCase()
    assert.match(html, new RegExp(`<div class="markdown-alert markdown-alert-${kind}">`))
    assert.match(html, new RegExp(`<p class="markdown-alert-title">${type.charAt(0)}${kind.slice(1)}</p>`))
    assert.match(html, /<p>body <strong>b<\/strong><\/p>/)
    assert.ok(!html.includes('<blockquote'))
  }
})

test('Obsidian callout：自定义标题（含行内格式）、折叠标记、类型映射', () => {
  const html = render('> [!danger]- My *title*\n> line1\n> line2')
  assert.match(html, /<div class="markdown-alert markdown-alert-caution">/)
  assert.match(html, /<p class="markdown-alert-title">My <em>title<\/em><\/p>/)
  assert.match(html, /<p>line1\nline2<\/p>/)
})

test('未知类型按 note 处理；只有标题没有正文', () => {
  const html = render('> [!foo]')
  assert.match(html, /<div class="markdown-alert markdown-alert-note">\s*<p class="markdown-alert-title">Foo<\/p>\s*<\/div>/)
})

test('普通引用块不受影响', () => {
  assert.match(render('> just a quote'), /<blockquote>\s*<p>just a quote<\/p>\s*<\/blockquote>/)
})

test('双链退化为纯文本：有别名显示别名，#标题部分去掉', () => {
  const html = render('see [[Page|Alias]] and [[Other#Heading]] and [[Plain]]')
  assert.match(html, /<p>see Alias and Other and Plain<\/p>/)
})

test('![[图片]] 变成图片，非图片嵌入退化为文本', () => {
  const html = render('![[pic.png|300]] ![[Some Note]]')
  assert.match(html, /<img src="[^"]*pic\.png"/)
  assert.ok(html.includes('Some Note'))
})

test('代码块和行内代码里的 Obsidian/GitHub 语法原样保留', () => {
  const html = render('`[[x]] ==y==`\n\n```\n> [!NOTE]\n![[a.png]]\n---\ntitle: z\n```')
  assert.match(html, /<code>\[\[x\]\] ==y==<\/code>/)
  assert.match(html, /&gt; \[!NOTE\]\n!\[\[a\.png\]\]\n---\ntitle: z/)
  assert.ok(!html.includes('markdown-alert'))
})
