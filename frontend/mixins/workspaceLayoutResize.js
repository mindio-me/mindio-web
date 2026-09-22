/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * Workspace 三栏框架的「拖拽调宽 + 宽度持久化 + 窄屏让位」行为。
 *
 * 使用页面须：
 *  - mixins: [workspaceLayoutResize]
 *  - 定义 wsLayoutOptions() 返回配置对象（见 DEFAULTS）
 *  - .workspace-layout 上绑 :style="wsLayoutStyle"
 *    和 :class="{ 'col-resizing': wsColResizing }"
 *  - 三栏之间放 <div class="col-resizer"
 *      @pointerdown="wsStartResize('left'|'right', $event)">
 *  - 页面若有 data.leftPanelCollapsed / data.rightPanelCollapsed（或返回它们的 computed），
 *    会被自动读取以决定是否输出对应侧的轨道
 *
 * wsLayoutOptions() 可选字段：
 *  - hasLeft  (默认 true)  ：是否有左侧栏；false 时布局为 main | 右栏（如客户合作页）
 *  - hasRight (默认 true)  ：是否有右侧栏
 *  - .workspace-layout 上按 hasLeft/hasRight 加 --no-left / --no-right 修饰类作 SSR/窄屏兜底
 */
export const WS_NARROW_QUERY = '(max-width: 1024px)'

const DEFAULTS = {
  storageKey: null,
  hasLeft: true,
  hasRight: true,
  minWidth: 200,
  maxWidth: 480,
  middleMin: 120, // 拖动时中间内容区允许被压到的最小宽度（防止两栏挤爆布局）
  defaultLeft: 280,
  defaultRight: 260,
  narrowQuery: WS_NARROW_QUERY,
}

export default {
  data() {
    return {
      wsLeftWidth: DEFAULTS.defaultLeft,
      wsRightWidth: DEFAULTS.defaultRight,
      wsColResizing: false,
      wsIsNarrow: false,
    }
  },
  computed: {
    // 注意：无响应式依赖，计算一次后永久缓存——当前两个调用方都返回字面量对象，OK；
    // 若将来某页面的 wsLayoutOptions() 依赖响应式数据，这里不会重算。
    wsOpts() {
      const custom = this.wsLayoutOptions ? this.wsLayoutOptions() : {}
      return { ...DEFAULTS, ...custom }
    },
    wsLayoutStyle() {
      if (this.wsIsNarrow) return {}
      const o = this.wsOpts
      const L = `${this.wsLeftWidth}px`
      const R = `${this.wsRightWidth}px`
      const leftCollapsed = this.leftPanelCollapsed === true
      const rightCollapsed = this.rightPanelCollapsed === true
      // 8px 的 gutter 轨道即 .col-resizer；与 main.scss 的兜底 grid 保持一致
      const left = (!o.hasLeft || leftCollapsed) ? '' : `${L} 8px `
      const right = (!o.hasRight || rightCollapsed) ? '' : ` 8px ${R}`
      return { gridTemplateColumns: `${left}minmax(0, 1fr)${right}` }
    },
  },
  created() {
    const o = this.wsOpts
    this.wsLeftWidth = o.defaultLeft
    this.wsRightWidth = o.defaultRight
    if (process.client) this._wsRead()
  },
  mounted() {
    this._wsUpdateNarrow()
    window.addEventListener('resize', this._wsUpdateNarrow)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this._wsUpdateNarrow)
    window.removeEventListener('pointermove', this._wsOnMove)
    window.removeEventListener('pointerup', this._wsOnEnd)
    window.removeEventListener('pointercancel', this._wsOnEnd)
    if (this._wsResizerEl) {
      this._wsResizerEl.classList.remove('ws-col-resizer-active')
      this._wsResizerEl = null
    }
    document.body.style.userSelect = ''
    document.body.style.cursor = ''
  },
  methods: {
    _wsClamp(px) {
      return Math.min(
        this.wsOpts.maxWidth,
        Math.max(this.wsOpts.minWidth, Math.round(px))
      )
    },
    // 拖动时按方向夹取：除 min/max 外，还保证不把中间内容区挤到比 middleMin 更窄。
    // 两条分隔线各自独立——谁也不会把中间挤没、导致布局溢出、看着像联动。
    _wsClampSide(side, px) {
      const o = this.wsOpts
      const lo = o.minWidth
      const hasLeft = o.hasLeft && this.leftPanelCollapsed !== true
      const hasRight = o.hasRight && this.rightPanelCollapsed !== true
      let hi = o.maxWidth
      if (this._wsContainerW) {
        const gutters = (hasLeft ? 8 : 0) + (hasRight ? 8 : 0)
        let sibling = 0
        if (side === 'left' && hasRight) sibling = this.wsRightWidth
        else if (side === 'right' && hasLeft) sibling = this.wsLeftWidth
        hi = Math.min(hi, this._wsContainerW - sibling - gutters - o.middleMin)
      }
      hi = Math.max(hi, lo)
      return Math.min(hi, Math.max(lo, Math.round(px)))
    },
    _wsUpdateNarrow() {
      if (!process.client) return
      this.wsIsNarrow = window.matchMedia(this.wsOpts.narrowQuery).matches
    },
    _wsRead() {
      try {
        if (!this.wsOpts.storageKey) return
        const raw = localStorage.getItem(this.wsOpts.storageKey)
        if (!raw) return
        const v = JSON.parse(raw)
        if (v && typeof v.left === 'number' && typeof v.right === 'number') {
          this.wsLeftWidth = this._wsClamp(v.left)
          this.wsRightWidth = this._wsClamp(v.right)
        }
      } catch (e) { /* localStorage 不可用或内容损坏，用默认值 */ }
    },
    _wsSave() {
      try {
        if (!this.wsOpts.storageKey) return
        localStorage.setItem(this.wsOpts.storageKey, JSON.stringify({
          left: this.wsLeftWidth,
          right: this.wsRightWidth,
        }))
      } catch (e) { /* ignore */ }
    },
    wsStartResize(side, e) {
      if (this.wsIsNarrow || (e.button != null && e.button !== 0)) return
      e.preventDefault()
      this._wsSide = side
      this._wsStartX = e.clientX
      this._wsStartLeft = this.wsLeftWidth
      this._wsStartRight = this.wsRightWidth
      const resizerEl = e.currentTarget
      const layoutEl = resizerEl && resizerEl.closest
        ? resizerEl.closest('.workspace-layout')
        : null
      this._wsContainerW = layoutEl ? layoutEl.clientWidth : 0
      // 只给正在拖的这一条 resizer 加高亮类（直接操作 DOM，不经 Vue 响应式），
      // 另一条 resizer 元素完全不受影响——两条线互不联动
      this._wsResizerEl = resizerEl
      if (this._wsResizerEl) this._wsResizerEl.classList.add('ws-col-resizer-active')
      this.wsColResizing = true
      document.body.style.userSelect = 'none'
      document.body.style.cursor = 'col-resize'
      window.addEventListener('pointermove', this._wsOnMove)
      window.addEventListener('pointerup', this._wsOnEnd)
      window.addEventListener('pointercancel', this._wsOnEnd)
    },
    _wsOnMove(e) {
      const dx = e.clientX - this._wsStartX
      if (this._wsSide === 'left') {
        this.wsLeftWidth = this._wsClampSide('left', this._wsStartLeft + dx)
      } else {
        this.wsRightWidth = this._wsClampSide('right', this._wsStartRight - dx)
      }
    },
    _wsOnEnd() {
      this.wsColResizing = false
      if (this._wsResizerEl) {
        this._wsResizerEl.classList.remove('ws-col-resizer-active')
        this._wsResizerEl = null
      }
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
      window.removeEventListener('pointermove', this._wsOnMove)
      window.removeEventListener('pointerup', this._wsOnEnd)
      window.removeEventListener('pointercancel', this._wsOnEnd)
      this._wsSave()
    },
  },
}
