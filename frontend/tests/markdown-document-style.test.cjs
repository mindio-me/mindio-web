const assert = require('node:assert/strict')
const path = require('node:path')
const { test } = require('node:test')
const sass = require('sass')

const FRONTEND = path.join(__dirname, '..')
const css = sass.compile(path.join(FRONTEND, 'assets/styles/markdown-document.scss'), {
  loadPaths: [path.join(FRONTEND, 'node_modules')]
}).css

// 收集 css 里命中某个完整选择器的所有声明（按出现顺序，后者覆盖前者）
function declarationsFor(selector) {
  const result = {}
  for (const [, selectors, body] of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (!selectors.split(',').map(s => s.trim().replace(/\s+/g, ' ')).includes(selector)) continue
    for (const decl of body.split(';')) {
      const i = decl.indexOf(':')
      if (i > 0) result[decl.slice(0, i).trim()] = decl.slice(i + 1).trim()
    }
  }
  return result
}

// 公开页 .article-page 下的 ::v-deep 通用样式会设置 github-markdown-css 没有设置的属性；
// 这些属性必须在文档样式里显式写回，浅色和深色两套选择器都要有
const RESETS = {
  img: { margin: '0', 'border-radius': '0', 'box-shadow': 'none' },
  blockquote: { background: 'none', 'border-radius': '0' },
  code: { border: '0' },
  table: { 'font-size': 'inherit' },
  th: { background: 'inherit' },
  li: { margin: '0' },
  a: { 'text-decoration-thickness': 'auto' },
  h1: { 'letter-spacing': 'normal' }
}

for (const prefix of ['html body .md-document .markdown-body', 'html.theme-dark body .md-document .markdown-body']) {
  test(`${prefix}：写回公开页 ::v-deep 会漏进来的属性`, () => {
    for (const [tag, props] of Object.entries(RESETS)) {
      const decls = declarationsFor(`${prefix} ${tag}`)
      for (const [prop, value] of Object.entries(props)) {
        assert.equal(decls[prop], value, `${prefix} ${tag} { ${prop} }`)
      }
    }
    assert.equal(declarationsFor(`${prefix} li + li`)['margin-top'], '0.25em')
  })
}
