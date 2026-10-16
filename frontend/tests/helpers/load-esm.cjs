const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { transformSync } = require('@babel/core')

/**
 * 把 nuxt-frontend 下的 ES 模块转成 CommonJS 后执行，返回其导出；deps 用来替换 import 的模块。
 * 注意：不用 vm.runInNewContext——新沙箱的 Array/Object 与主 realm 不同，deepStrictEqual 会误判。
 * 这里用包装函数在主 realm 里执行，只通过参数注入 module/require 等少量全局。
 */
function loadEsm(file, deps = {}) {
  const abs = path.join(__dirname, '..', '..', file)
  const source = fs.readFileSync(abs, 'utf8')
  const { code } = transformSync(source, {
    babelrc: false,
    configFile: false,
    plugins: ['@babel/plugin-transform-modules-commonjs']
  })
  const module = { exports: {} }
  const wrapper = vm.runInThisContext(
    `(function (module, exports, require, console, setTimeout, clearTimeout) {\n${code}\n})`,
    { filename: abs }
  )
  wrapper(module, module.exports, (name) => deps[name] || {}, console, setTimeout, clearTimeout)
  return module.exports
}

module.exports = { loadEsm }
