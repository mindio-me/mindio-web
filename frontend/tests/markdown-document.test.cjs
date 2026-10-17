const assert = require('node:assert/strict')
const { test } = require('node:test')
const { JSDOM } = require('jsdom')

// DOMPurify 需要 DOM：单测里用 jsdom 的 window 充当浏览器环境
globalThis.window = new JSDOM('<!doctype html><html><body></body></html>').window

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { renderMarkdownDocument, slugifyHeading } = loadEsmReal('utils/markdown-document.js')
const render = (src, options) => renderMarkdownDocument(src, options)

test('空输入返回空串', () => {
  assert.equal(render(''), '')
  assert.equal(render(null), '')
})

test('单个换行按 CommonMark 视为空格，空行分段', () => {
  const html = render('a\nb\n\nc')
  assert.ok(!html.includes('<br'))
  assert.match(html, /<p>a\nb<\/p>\s*<p>c<\/p>/)
})

test('GFM：表格、删除线、裸链接、只读任务列表', () => {
  const html = render('| a | b |\n|---|:-:|\n| 1 | 2 |\n\n~~del~~ https://example.com\n\n- [x] done\n- [ ] todo')
  assert.match(html, /<table>/)
  assert.match(html, /<th style="text-align:center">b<\/th>/)
  assert.match(html, /<s>del<\/s>/)
  assert.match(html, /<a href="https:\/\/example.com" target="_blank" rel="noopener noreferrer">/)
  assert.match(html, /<input class="task-list-item-checkbox" checked="" disabled="" type="checkbox">/)
})

test('==高亮== 渲染为 mark', () => {
  assert.match(render('a ==hi== b'), /<mark>hi<\/mark>/)
})

test('脚注：上标数字不带方括号，id 带 user-content- 前缀', () => {
  const html = render('text[^1]\n\n[^1]: note')
  assert.match(html, /<sup class="footnote-ref"><a href="#fn1" id="user-content-fnref1">1<\/a><\/sup>/)
  assert.match(html, /<li class="footnote-item" id="user-content-fn1">/)
})

test('标题锚点：中文 slug、重复标题加后缀、id 带 user-content- 前缀', () => {
  const html = render('# Title\n## 中文 标题!\n## 中文 标题!')
  assert.match(html, /<h1 id="user-content-title">Title<\/h1>/)
  assert.match(html, /<h2 id="user-content-中文-标题">/)
  assert.match(html, /<h2 id="user-content-中文-标题-1">/)
})

test('全是标点/emoji 的标题退回 section 且不重复', () => {
  assert.equal(slugifyHeading('!!!'), 'section')
  const html = render('# !!!\n# ???')
  assert.match(html, /id="user-content-section"/)
  assert.match(html, /id="user-content-section-1"/)
})

test('外链和 mailto 新标签页打开，# 页内链接不加 target', () => {
  const html = render('[e](https://example.com) [m](mailto:a@b.c) [h](#x)')
  assert.match(html, /<a href="mailto:a@b.c" target="_blank" rel="noopener noreferrer">m<\/a>/)
  assert.match(html, /<a href="#x">h<\/a>/)
})

test('内嵌 HTML 经过滤后渲染', () => {
  const html = render('<details><summary>S</summary>hidden</details>\n\n<kbd>K</kbd><br><sup>1</sup>')
  assert.match(html, /<details><summary>S<\/summary>hidden<\/details>/)
  assert.match(html, /<kbd>K<\/kbd><br><sup>1<\/sup>/)
})

test('XSS：脚本、事件属性、javascript: 及其变形、危险标签全部去除', () => {
  const html = render([
    '<script>alert(1)</script>',
    '<img src="https://x/a.png" onerror="alert(1)">',
    '<a href="JAVASCRIPT:alert(1)">c</a>',
    '<a href="java\tscript:alert(1)">t</a>',
    '<a href="&#106;avascript:alert(1)">d</a>',
    '<iframe src="https://x"></iframe>',
    '<svg onload="alert(1)"><circle/></svg>',
    '<form action="https://x"><input type="text" value="v"></form>',
    '<style>body{display:none}</style>',
    '[md](javascript:alert(1))'
  ].join('\n\n'))
  assert.ok(!/<script/i.test(html))
  assert.ok(!/onerror|onload/i.test(html))
  assert.ok(!/href="\s*java/i.test(html))
  assert.ok(!/<iframe|<form|<style/i.test(html))
  assert.match(html, /<input[^>]*type="checkbox"[^>]*>/)
  assert.ok(!/href="javascript/i.test(html))
})

test('没有可用 DOM 时返回空串而不是未过滤 HTML', () => {
  const saved = globalThis.window
  delete globalThis.window
  try {
    const fresh = loadEsmReal('utils/markdown-document.js', {}, {})
    assert.equal(fresh.renderMarkdownDocument('<b>x</b>'), '')
  } finally {
    globalThis.window = saved
  }
})

test('围栏代码块按语言高亮，未知语言原样转义', () => {
  const js = render('```js\nconst a = 1 // <b>\n```')
  assert.match(js, /<pre><code class="hljs language-js"><span class="hljs-keyword">const<\/span>/)
  assert.match(js, /&lt;b&gt;/)
  const unknown = render('```nosuchlang\n<x>\n```')
  assert.match(unknown, /<pre><code class="hljs language-nosuchlang">&lt;x&gt;\n<\/code><\/pre>/)
  const plain = render('```\nplain <y>\n```')
  assert.match(plain, /<pre><code class="hljs">plain &lt;y&gt;\n<\/code><\/pre>/)
})

test('缩进代码块与围栏代码块同样式', () => {
  assert.match(render('    indented <z>'), /<pre><code class="hljs">indented &lt;z&gt;\n<\/code><\/pre>/)
})

test('mermaid 代码块输出现有图表占位（无语言标记时按内容检测）', () => {
  const tagged = render('```mermaid\ngraph TD\nA-->B\n```')
  assert.match(tagged, /<div class="mermaid-block" data-mermaid-source="graph%20TD%0AA--%3EB">/)
  const detected = render('```\nsequenceDiagram\nA->>B: hi\n```')
  assert.match(detected, /class="mermaid-block"/)
  const jsNotMermaid = render('```js\ngraph = 1\n```')
  assert.ok(!jsNotMermaid.includes('mermaid-block'))
})

test('KaTeX：行内、块级、```math，过滤后 MathML 结构保留', () => {
  const html = render('inline $x^2$\n\n$$\sqrt{a}$$\n\n```math\nE=mc^2\n```')
  assert.match(html, /<span class="katex">/)
  assert.match(html, /<p class="katex-block">/)
  assert.match(html, /<annotation encoding="application\/x-tex">x\^2<\/annotation>/)
  assert.match(html, /E=mc\^2\s*<\/annotation>/)
})

test('KaTeX 语法错误不抛异常', () => {
  assert.doesNotThrow(() => render('$\frac{a$ and $$\bad{$$'))
})

test('美元金额不被当成公式', () => {
  const html = render('costs $5 and $10 total')
  assert.ok(!html.includes('katex'))
  assert.match(html, /costs \$5 and \$10 total/)
})

// 回归护栏：贴近真实笔记的 300KB 文档（约 180 个公式、1800 个代码块）。jsdom 比浏览器慢数倍，
// 计划编写时实测 jsdom 下约 3 秒（时间主要花在 DOMPurify 解析 KaTeX 的大量 span 上），这里留余量到 8 秒
test('300KB 真实规模文档在 8 秒内渲染完（jsdom）', () => {
  const prose = '## 小节\n\n一段正文 **粗体** `code` [链接](https://example.com)，再写一些中文内容让段落更像真实笔记，这里补足长度。\n\n- 列表项一\n- 列表项二\n\n```js\nfunction f(a) { return a * 2 }\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n'
  const withMath = '行内公式 $x^2$ 与块级公式：\n\n$$a^2+b^2=c^2$$\n\n'
  let doc = ''
  for (let i = 1; doc.length < 300 * 1024; i++) doc += prose + (i % 10 === 0 ? withMath : '')
  const start = Date.now()
  const html = render(doc)
  assert.ok(html.length > doc.length)
  assert.ok(Date.now() - start < 8000, `took ${Date.now() - start}ms`)
})
