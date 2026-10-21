const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { test } = require('node:test')

const { loadEsmReal } = require('./helpers/load-esm-real.cjs')
const { editorjsI18nConfig } = loadEsmReal('utils/editorjsI18n.js')

const FRONTEND = path.join(__dirname, '..')
const zh = editorjsI18nConfig('zh-CN').messages

// 工具文件 → 注册名（editorjsHost / 公开笔记页里 tools 的键，也就是 Editor.js 翻译的命名空间）
const TOOLS = {
  'utils/editorjsGalleryTool.js': ['tools', 'mediaGallery'],
  'utils/editorjsMediaLightbox.js': ['tools', 'mediaGallery'],
  'utils/editorjsAudioTool.js': ['tools', 'audio'],
  'utils/editorjsRecordTool.js': ['tools', 'audioRecord'],
  'utils/editorjsVideoTool.js': ['tools', 'video'],
  'utils/editorjsEmbedVideoTool.js': ['tools', 'embed'],
  'utils/editorjsReferencesTool.js': ['tools', 'references'],
  'utils/editorjsTimelineTool.js': ['tools', 'timeline'],
  'utils/editorjs-markdown-block.js': ['tools', 'markdown'],
  'utils/editorjsMathTool.js': ['tools', 'math'],
  'utils/editorjsCodeWrapTune.js': ['blockTunes', 'codeWrap'],
  'utils/editorjsMediaCopy.js': ['blockTunes', 'mediaCopy']
}

// 抓 _t('...') / _t("...") 以及灯箱里的 t('...')，三元表达式里的两个分支都要抓到
const KEY_RE = /\b_?t\((?:[^()'"]*\?\s*)?(['"])((?:(?!\1).)+)\1(?:\s*:\s*(['"])((?:(?!\3).)+)\3)?/g

function keysOf(file) {
  const source = fs.readFileSync(path.join(FRONTEND, file), 'utf8')
  const keys = new Set()
  for (const m of source.matchAll(KEY_RE)) {
    keys.add(m[2])
    if (m[4]) keys.add(m[4])
  }
  return keys
}

for (const [file, [section, name]] of Object.entries(TOOLS)) {
  test(`${file} 里要翻译的英文都有中文（${section}.${name}）`, () => {
    const keys = keysOf(file)
    assert.ok(keys.size > 0, '没抓到任何翻译键，正则可能失效了')
    const dict = zh[section][name] || {}
    const missing = [...keys].filter(k => !(k in dict))
    assert.deepEqual(missing, [])
  })
}

test('自定义工具在工具栏里的英文名都有中文', () => {
  for (const file of Object.keys(TOOLS)) {
    const source = fs.readFileSync(path.join(FRONTEND, file), 'utf8')
    const m = source.match(/static get toolbox\(\) \{\s*return \{\s*title: '([^']+)'/)
    if (m) assert.ok(m[1] in zh.toolNames, `${file}: ${m[1]}`)
  }
})

test('自定义工具里不再有写死的中文界面文案', () => {
  const CJK = /[一-鿿]/
  for (const file of Object.keys(TOOLS)) {
    const lines = fs.readFileSync(path.join(FRONTEND, file), 'utf8').split(/\r?\n/)
    const offending = lines
      .map(line => line.replace(/\/\/.*$/, '').replace(/^\s*(\/?\*).*$/, ''))
      .filter(line => CJK.test(line) && !/console\.(log|warn|error)/.test(line))
    assert.deepEqual(offending, [], file)
  }
})

test('英文界面传空字典（重置 Editor.js 的全局字典）', () => {
  assert.deepEqual(editorjsI18nConfig('en'), { messages: {} })
})
