/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * v-mermaid 指令
 * 扫描元素内的 .mermaid-block，懒加载 mermaid.js 并渲染为 SVG
 * 主题切换时自动重新渲染
 */

let mermaidPromise = null
let mermaidInstance = null
let currentTheme = null

// 跟踪所有使用 v-mermaid 的元素，用于主题切换时重新渲染
const trackedElements = new Set()

function getMermaidConfig() {
  const isDark = document.documentElement.classList.contains('theme-dark')
  return {
    startOnLoad: false,
    theme: isDark ? 'dark' : 'default',
    securityLevel: 'strict',
    fontFamily: "'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif",
    flowchart: {
      padding: 16,
      nodeSpacing: 50,
      rankSpacing: 60,
      htmlLabels: true,
      useMaxWidth: false
    }
  }
}

function loadMermaid() {
  if (mermaidPromise) return mermaidPromise
  mermaidPromise = import('mermaid').then(m => {
    const mermaid = m.default || m
    const config = getMermaidConfig()
    currentTheme = config.theme
    mermaid.initialize(config)
    mermaidInstance = mermaid
    return mermaid
  })
  return mermaidPromise
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

async function renderMermaidBlocks(el, forceRerender) {
  const selector = forceRerender
    ? '.mermaid-block[data-mermaid-source]'
    : '.mermaid-block:not([data-rendered])'
  const blocks = el.querySelectorAll(selector)
  if (blocks.length === 0) return

  let mermaid
  try {
    mermaid = await loadMermaid()
  } catch (e) {
    console.error('[v-mermaid] Failed to load mermaid library:', e)
    return
  }

  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i]
    const source = decodeURIComponent(block.dataset.mermaidSource || '')
    if (!source) continue

    // 清除之前的渲染状态
    if (forceRerender) {
      block.removeAttribute('data-rendered')
      block.classList.remove('mermaid-rendered', 'mermaid-error')
    }

    try {
      const id = 'mermaid-' + Date.now() + '-' + i
      const { svg } = await mermaid.render(id, source)
      block.innerHTML = svg
      block.setAttribute('data-rendered', 'true')
      block.classList.add('mermaid-rendered')
    } catch (e) {
      console.warn('[v-mermaid] Render failed:', e)
      block.classList.add('mermaid-error')
      block.innerHTML =
        `<pre class="md-code-block"><code>${escapeHtml(source)}</code></pre>` +
        `<p style="color:#F56C6C;font-size:12px;margin:4px 0 0;">Mermaid 图表渲染失败</p>`
    }
  }
}

// 主题切换时重新初始化 mermaid 并重新渲染所有图表
async function onThemeChange() {
  if (!mermaidInstance) return

  const config = getMermaidConfig()
  if (config.theme === currentTheme) return

  currentTheme = config.theme
  mermaidInstance.initialize(config)

  // 重新渲染所有已跟踪的元素
  for (const el of trackedElements) {
    if (document.contains(el)) {
      await renderMermaidBlocks(el, true)
    } else {
      trackedElements.delete(el)
    }
  }
}

// 监听 <html> 的 class 变化来检测主题切换
let observer = null
function setupThemeObserver() {
  if (observer || typeof MutationObserver === 'undefined') return
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
        onThemeChange()
        break
      }
    }
  })
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
}

export default {
  inserted(el) {
    trackedElements.add(el)
    setupThemeObserver()
    renderMermaidBlocks(el, false)
  },
  componentUpdated(el) {
    renderMermaidBlocks(el, false)
  },
  unbind(el) {
    trackedElements.delete(el)
  }
}
