/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Obsidian / GitHub 导出文档常见语法的 markdown-it 规则，只给 markdown-document.js 用：
 * - 文首 YAML front matter：隐藏（VS Code 预览默认行为）
 * - GitHub 提示块 > [!NOTE] 与 Obsidian callout > [!type] 标题：渲染成 github-markdown-css 的 markdown-alert 结构
 * - ![[x.png]] 图片嵌入 → 图片；其余 [[wikilink]] → 纯文本（与后端 MarkdownSourceNormalizer 规则一致）
 * 都写成 markdown-it 规则而不是源码预处理，代码块和行内代码里的同样写法天然不受影响。
 */

// key 不限 ASCII（Obsidian 属性名可以是中文）；以 # 开头的是 YAML 注释行
const FRONTMATTER_KEY = /^[^\s:#][^:]*:/
const FRONTMATTER_COMMENT = /^#/

// 只认文首：第一行是 ---，第一个非空、非注释行是 key: value，并且有 --- 或 ... 闭合；否则交给分割线等其他规则
function frontMatter(state, startLine, endLine, silent) {
  if (startLine !== 0 || state.blkIndent !== 0) return false
  const lineText = n => state.src.slice(state.bMarks[n] + state.tShift[n], state.eMarks[n]).trimEnd()
  if (lineText(0) !== '---') return false
  let first = 1
  while (first < endLine && (lineText(first) === '' || FRONTMATTER_COMMENT.test(lineText(first)))) first++
  if (first >= endLine || !FRONTMATTER_KEY.test(lineText(first))) return false
  let end = first
  while (end < endLine && lineText(end) !== '---' && lineText(end) !== '...') end++
  if (end >= endLine) return false
  if (silent) return true
  state.line = end + 1
  return true
}

// Obsidian 的 callout 类型归并到 GitHub 的五种提示块配色；未知类型按 note
const ALERT_KINDS = {
  note: 'note', info: 'note', todo: 'note', abstract: 'note', summary: 'note', tldr: 'note', quote: 'note', cite: 'note',
  tip: 'tip', hint: 'tip', success: 'tip', check: 'tip', done: 'tip',
  important: 'important', example: 'important', question: 'important', help: 'important', faq: 'important',
  warning: 'warning', attention: 'warning',
  caution: 'caution', danger: 'caution', error: 'caution', failure: 'caution', fail: 'caution', missing: 'caution', bug: 'caution'
}
// [!type] 后可跟 Obsidian 的折叠标记 + / -
const ALERT_MARKER = /^\[!([A-Za-z]+)\][+-]?[ \t]*/

// 引用块第一段以 [!type] 开头时：blockquote 换成 div.markdown-alert，第一行剩余内容（可含行内格式）作标题，
// 没写标题时用类型名；第一行之后的内容留在原段落里，段落空了就隐藏
function alerts(state) {
  const tokens = state.tokens
  for (let i = 0; i < tokens.length; i++) {
    if (tokens[i].type !== 'blockquote_open') continue
    const paragraphOpen = tokens[i + 1]
    const inline = tokens[i + 2]
    if (!paragraphOpen || paragraphOpen.type !== 'paragraph_open' || !inline || inline.type !== 'inline') continue
    const first = inline.children && inline.children[0]
    if (!first || first.type !== 'text') continue
    const match = ALERT_MARKER.exec(first.content)
    if (!match) continue

    let depth = 0
    let close = -1
    for (let j = i; j < tokens.length; j++) {
      if (tokens[j].type === 'blockquote_open') depth++
      else if (tokens[j].type === 'blockquote_close' && --depth === 0) { close = j; break }
    }
    if (close < 0) continue

    const rawType = match[1].toLowerCase()
    tokens[i].tag = 'div'
    tokens[i].attrJoin('class', `markdown-alert markdown-alert-${ALERT_KINDS[rawType] || 'note'}`)
    tokens[close].tag = 'div'

    first.content = first.content.slice(match[0].length)
    const children = inline.children
    const lineBreak = children.findIndex(c => c.type === 'softbreak' || c.type === 'hardbreak')
    const titleChildren = lineBreak < 0 ? children.slice() : children.slice(0, lineBreak)
    const rest = lineBreak < 0 ? [] : children.slice(lineBreak + 1)
    const hasTitle = titleChildren.some(c => c.type !== 'text' || c.content.trim() !== '')

    const titleInline = new state.Token('inline', '', 0)
    titleInline.content = ''
    if (hasTitle) {
      titleInline.children = titleChildren
    } else {
      const text = new state.Token('text', '', 0)
      text.content = rawType.charAt(0).toUpperCase() + rawType.slice(1)
      titleInline.children = [text]
    }
    const titleOpen = new state.Token('paragraph_open', 'p', 1)
    titleOpen.attrSet('class', 'markdown-alert-title')
    const titleClose = new state.Token('paragraph_close', 'p', -1)

    inline.children = rest
    if (rest.length === 0) {
      paragraphOpen.hidden = true
      tokens[i + 3].hidden = true // 对应的 paragraph_close
    }
    tokens.splice(i + 1, 0, titleOpen, titleInline, titleClose)
  }
}

const IMAGE_EXT = /\.(png|jpe?g|gif|webp|svg|bmp)$/i

// [[目标#标题|别名]] 与 ![[嵌入]]；目标里不能有方括号和换行
function wikilink(state, silent) {
  const src = state.src
  let pos = state.pos
  const embed = src.charCodeAt(pos) === 0x21 /* ! */
  if (embed) pos++
  if (src.charCodeAt(pos) !== 0x5B /* [ */ || src.charCodeAt(pos + 1) !== 0x5B) return false
  const close = src.indexOf(']]', pos + 2)
  if (close < 0) return false
  const inner = src.slice(pos + 2, close)
  if (!inner || /[[\]\n]/.test(inner)) return false
  const pipe = inner.indexOf('|')
  const alias = pipe < 0 ? '' : inner.slice(pipe + 1).trim()
  const target = (pipe < 0 ? inner : inner.slice(0, pipe)).split('#')[0].trim()
  if (!target) return false
  if (!silent) {
    if (embed && IMAGE_EXT.test(target)) {
      // 图片嵌入的 |300 之类尺寸修饰忽略
      const token = state.push('image', 'img', 0)
      token.attrs = [['src', target], ['alt', '']]
      token.children = []
      token.content = ''
    } else {
      const token = state.push('text', '', 0)
      token.content = alias || target
    }
  }
  state.pos = close + 2
  return true
}

export function markdownDocumentSyntax(md) {
  md.block.ruler.before('hr', 'front_matter', frontMatter) // 必须先于分割线规则，否则 --- 被当成 hr
  md.inline.ruler.before('link', 'wikilink', wikilink) // 必须先于链接/图片规则
  md.core.ruler.after('inline', 'alerts', alerts) // 需要已解析好的行内 token（标题保留行内格式）
}
