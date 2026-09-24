/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import chatPanelState from '~/utils/chatPanelState'

/**
 * 给有右栏（workspace-right）的页面接入全局AI助手停靠。
 *
 * 使用页面须：
 *  - mixins: [workspaceAiDock]（一般跟 workspaceLayoutResize 一起用，因为要用到
 *    rightPanelCollapsed / wsIsNarrow / wsRightWidth）
 *  - 右栏模板里（注意判断用 aiPanelDocked 而不是 aiPanelActive）：
 *      <aside class="workspace-right" :class="{ 'workspace-right--ai': aiPanelDocked }">
 *        <ChatPanel v-if="aiPanelDocked" @close="aiPanelActive = false" />
 *        <template v-if="!aiPanelDocked"> ...原来的右栏内容... </template>
 *      </aside>
 *
 * 顶栏 AI 图标点击（workspace:chat:toggle）会被这个 mixin 接管，在本页把 AI 切到
 * 右栏；GlobalChatDrawer 那边靠 chatPanelState.dockMountCount 这个共享计数知道"当前
 * 有页面自己在管右栏"，从而让开、不重复切换、也不会跟右栏同时弹出抽屉。
 *
 * 注意窄屏（wsIsNarrow）：这时右栏本就不作为常驻栏显示，停靠是无效的，所以计数里
 * 不能把自己算进去——否则抽屉以为"有人管着"而让开，右栏又显示不出来，点 AI 图标
 * 会两边都不接、变成死键。宽窄屏切换时这个登记状态要跟着变，见 _wsAiSyncDock。
 */
export default {
  computed: {
    // 全局AI助手开关，不是本页私有状态——读写的是 chatPanelState.open，所以切到
    // 别的 tab（有右栏的显示右栏，没有的走抽屉）时状态是跟着走的
    aiPanelActive: {
      get() { return chatPanelState.open },
      set(v) { chatPanelState.open = v },
    },
    // 本页此刻该不该把 AI 画在右栏：开关开着 + 不是窄屏。窄屏下右栏不作为常驻栏
    // 显示、由抽屉兜底，这时右栏里不能再挂一份 ChatPanel——否则收藏夹这种"窄屏可以
    // 手动滑出右栏"的页面会同时出现抽屉和右栏两份面板
    aiPanelDocked() {
      return this.aiPanelActive && !this.wsIsNarrow
    },
  },
  watch: {
    aiPanelActive(v) {
      // 进 AI 模式右栏至少 420，拖过更宽的不动，跟笔记页保持一致的体验
      if (v && this.wsRightWidth < 420) {
        this.wsRightWidth = this._wsClamp(420)
      }
    },
    // 拖窗口把宽屏拖成窄屏（或反过来）时，"本页算不算有效停靠"要跟着变
    wsIsNarrow() {
      this._wsAiSyncDock()
    },
  },
  created() {
    // 非响应式的本地标记，记录"本页当前有没有登记为有效停靠"，避免重复加减计数。
    // 必须显式初始化成 false，否则 _wsAiSetDocked(false) 会因为 false !== undefined
    // 而误减一次别人的计数
    this._wsAiDocked = false
  },
  mounted() {
    this.$nuxt.$on('workspace:chat:toggle', this._wsAiToggle)
    // 告诉 GlobalChatDrawer："这个页面自己有右栏在管 AI，你让开"——直接改共享计数
    // 而不是发事件，避免 GlobalChatDrawer 挂载顺序比页面晚、错过首屏那次通知
    this._wsAiSyncDock()
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:chat:toggle', this._wsAiToggle)
    this._wsAiSetDocked(false)
  },
  methods: {
    // 只有"能真的把 AI 显示在右栏"时才登记，窄屏不算
    _wsAiSyncDock() {
      this._wsAiSetDocked(!this.wsIsNarrow)
    },
    _wsAiSetDocked(docked) {
      if (docked === this._wsAiDocked) return
      this._wsAiDocked = docked
      chatPanelState.dockMountCount = Math.max(
        0,
        chatPanelState.dockMountCount + (docked ? 1 : -1)
      )
    },
    _wsAiToggle() {
      // 窄屏这一栏本就不作为常驻栏显示，这时本页没登记停靠，抽屉会接管这次点击
      if (this.wsIsNarrow) return
      // 右栏当前收起：一步到位——展开右栏并强制开 AI（否则要点两次）
      if (this.rightPanelCollapsed) {
        this.rightPanelCollapsed = false
        this.aiPanelActive = true
        return
      }
      this.aiPanelActive = !this.aiPanelActive
    },
  },
}
