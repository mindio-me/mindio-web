const fs = require('node:fs')
const path = require('node:path')
const { loadEsm } = require('./load-esm.cjs')

const FRONTEND = path.join(__dirname, '..', '..')
const IMPORT_RE = /^import\s[^'"]*?['"]([^'"]+)['"]/gm

/**
 * 和 loadEsm 一样执行 nuxt-frontend 下的 ES 模块，但不用手工注入依赖：
 * 包名直接 require 真实包，相对路径（./x）递归用本函数加载；overrides 可替换指定依赖。
 */
function loadEsmReal(file, overrides = {}, cache = {}) {
  if (cache[file]) return cache[file]
  const source = fs.readFileSync(path.join(FRONTEND, file), 'utf8')
  const deps = {}
  for (const [, spec] of source.matchAll(IMPORT_RE)) {
    if (spec in overrides) {
      deps[spec] = overrides[spec]
    } else if (spec.startsWith('.')) {
      const target = path.posix.join(path.posix.dirname(file), spec)
      deps[spec] = loadEsmReal(target.endsWith('.js') ? target : `${target}.js`, overrides, cache)
    } else {
      deps[spec] = require(require.resolve(spec, { paths: [FRONTEND] }))
    }
  }
  cache[file] = loadEsm(file, deps)
  return cache[file]
}

module.exports = { loadEsmReal }
