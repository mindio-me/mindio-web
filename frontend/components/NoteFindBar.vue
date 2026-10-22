<!-- Copyright (c) 2026 Fasong Wu -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->
<template>
  <!-- 高度为 0 的 sticky 锚点：放在滚动容器顶部，查找框浮在正文右上角，不挤占正文 -->
  <div v-if="visible" class="note-find-anchor">
    <div class="note-find-bar" @keydown.stop>
      <input
        ref="input"
        v-model="query"
        class="note-find-input"
        type="text"
        :placeholder="$t('workspace.notes.findPlaceholder')"
        @input="search"
        @keydown="onKeydown"
      >
      <span class="note-find-count">{{ counter }}</span>
      <button class="note-find-btn" type="button" :title="$t('workspace.notes.findPrev')" :disabled="!total" @click="step(-1)">
        <i class="el-icon-arrow-up"></i>
      </button>
      <button class="note-find-btn" type="button" :title="$t('workspace.notes.findNext')" :disabled="!total" @click="step(1)">
        <i class="el-icon-arrow-down"></i>
      </button>
      <button class="note-find-btn" type="button" :title="$t('workspace.notes.close')" @click="close">
        <i class="el-icon-close"></i>
      </button>
    </div>
  </div>
</template>

<script>
import { locateMatches } from '~/utils/textFind'

// 笔记正文内查找（Ctrl+F）。命中用 CSS Custom Highlight API 上色，不改动正文 DOM——
// Editor.js 的正文是 contenteditable，插 <mark> 会被当成内容保存进笔记。
const HL_ALL = 'note-find'
const HL_CURRENT = 'note-find-current'
const SKIP = 'script, style, textarea, .note-find-anchor'

function highlightSupported() {
  return typeof window !== 'undefined' && window.CSS && CSS.highlights && typeof window.Highlight === 'function'
}

export default {
  name: 'NoteFindBar',
  props: {
    // 返回要查找的根元素（正文区）
    getRoot: { type: Function, required: true }
  },
  data() {
    return { visible: false, query: '', current: 0, total: 0 }
  },
  computed: {
    counter() {
      if (!this.query.trim()) return ''
      return this.total ? `${this.current + 1}/${this.total}` : '0/0'
    }
  },
  created() {
    this._ranges = [] // Range 对象不需要响应式
  },
  beforeDestroy() {
    this.clearHighlights()
  },
  methods: {
    open() {
      const sel = typeof window !== 'undefined' ? String(window.getSelection() || '').trim() : ''
      if (sel && sel.length <= 100 && !sel.includes('\n')) this.query = sel
      this.visible = true
      this.$nextTick(() => {
        if (!this.$refs.input) return
        this.$refs.input.focus()
        this.$refs.input.select()
        this.search()
      })
    },
    close() {
      this.visible = false
      this.clearHighlights()
      this._ranges = []
      this.total = 0
    },
    onKeydown(e) {
      if (e.key === 'Enter') {
        e.preventDefault()
        this.step(e.shiftKey ? -1 : 1)
      } else if (e.key === 'Escape') {
        e.preventDefault()
        this.close()
      }
    },
    search() {
      this.current = 0
      this.collect()
      this.paint()
      this.reveal()
    },
    step(dir) {
      // 正文可能已经被编辑过，每次跳转前重新收集，避免停在失效的位置
      this.collect()
      if (!this.total) return this.paint()
      this.current = (this.current + dir + this.total) % this.total
      this.paint()
      this.reveal()
    },
    collect() {
      const root = this.getRoot()
      const ranges = []
      if (root && this.query.trim()) {
        const blocks = root.querySelectorAll('.ce-block')
        const segments = blocks.length ? Array.from(blocks) : [root]
        segments.forEach((seg) => {
          const nodes = []
          const walker = document.createTreeWalker(seg, NodeFilter.SHOW_TEXT, {
            acceptNode: (n) => (n.parentElement && n.parentElement.closest(SKIP) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT)
          })
          while (walker.nextNode()) nodes.push(walker.currentNode)
          locateMatches(nodes.map((n) => n.nodeValue), this.query).forEach(({ start, end }) => {
            const r = document.createRange()
            r.setStart(nodes[start[0]], start[1])
            r.setEnd(nodes[end[0]], end[1])
            ranges.push(r)
          })
        })
      }
      this._ranges = ranges
      this.total = ranges.length
      if (this.current >= this.total) this.current = 0
    },
    paint() {
      if (!highlightSupported()) return
      if (!this._ranges.length) return this.clearHighlights()
      CSS.highlights.set(HL_ALL, new window.Highlight(...this._ranges))
      CSS.highlights.set(HL_CURRENT, new window.Highlight(this._ranges[this.current]))
    },
    clearHighlights() {
      if (!highlightSupported()) return
      CSS.highlights.delete(HL_ALL)
      CSS.highlights.delete(HL_CURRENT)
    },
    reveal() {
      const r = this._ranges[this.current]
      if (!r) return
      const el = r.startContainer.parentElement
      if (!el) return
      // 命中在折叠起来的 <details>（如转写面板的原文）里时先展开
      let d = el.closest('details')
      while (d) {
        d.open = true
        d = d.parentElement && d.parentElement.closest('details')
      }
      el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
  }
}
</script>

<style scoped lang="scss">
.note-find-anchor {
  position: sticky;
  top: 0;
  height: 0;
  z-index: 20;
}
.note-find-bar {
  position: absolute;
  top: 4px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 14px var(--shadow-color);
}
.note-find-input {
  width: 200px;
  font-size: 13px;
  padding: 4px 8px;
  border: 1px solid var(--input-border);
  border-radius: 4px;
  background: var(--input-bg);
  color: var(--text-color);
  outline: none;
  &:focus { border-color: var(--color-action); }
}
.note-find-count {
  min-width: 44px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.note-find-btn {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  width: 26px;
  height: 26px;
  border-radius: 4px;
  cursor: pointer;
  &:hover:not(:disabled) { background: var(--bg-secondary); color: var(--text-color); }
  &:disabled { opacity: 0.4; cursor: default; }
}
</style>

<style lang="scss">
/* ::highlight 作用于整个文档，不能放 scoped 里 */
::highlight(note-find) { background-color: rgba(255, 213, 79, 0.45); color: inherit; }
::highlight(note-find-current) { background-color: #ff9632; color: #000; }
</style>
