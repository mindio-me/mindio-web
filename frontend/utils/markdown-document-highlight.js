/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * 代码块语法高亮：highlight.js 核心 + 常用语言子集（控制打包体积），只给 markdown-document.js 用。
 * 语言别名（js/ts/html/sh/yml/py 等）由各语言定义自带，getLanguage 能识别。
 */

import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import css from 'highlight.js/lib/languages/css'
import diff from 'highlight.js/lib/languages/diff'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import go from 'highlight.js/lib/languages/go'
import ini from 'highlight.js/lib/languages/ini'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import php from 'highlight.js/lib/languages/php'
import plaintext from 'highlight.js/lib/languages/plaintext'
import powershell from 'highlight.js/lib/languages/powershell'
import python from 'highlight.js/lib/languages/python'
import ruby from 'highlight.js/lib/languages/ruby'
import rust from 'highlight.js/lib/languages/rust'
import scss from 'highlight.js/lib/languages/scss'
import shell from 'highlight.js/lib/languages/shell'
import sql from 'highlight.js/lib/languages/sql'
import swift from 'highlight.js/lib/languages/swift'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'

const LANGUAGES = {
  bash, c, cpp, csharp, css, diff, dockerfile, go, ini, java, javascript, json, kotlin, markdown,
  php, plaintext, powershell, python, ruby, rust, scss, shell, sql, swift, typescript, xml, yaml
}
Object.entries(LANGUAGES).forEach(([name, language]) => hljs.registerLanguage(name, language))

/**
 * @param {string} code - 代码原文
 * @param {string} lang - 围栏代码块的语言标记（已转小写）
 * @returns {string} 高亮后的 HTML（已转义）；语言为空或未注册时返回空串，由调用方自行转义原文
 */
export function highlightCode(code, lang) {
  if (!lang || !hljs.getLanguage(lang)) return ''
  try {
    return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
  } catch (e) {
    return ''
  }
}
