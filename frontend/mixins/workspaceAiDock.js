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
 *  - 右栏模板里：
 *      <aside class="workspace-right" :class="{ 'workspace-right--ai': aiPanelActive }">
 *        <ChatPanel v-if="aiPanelActive" @close="aiPanelActive = false" />
 *        <template v-if="!aiPanelActive"> ...原来的右栏内容... </template>
 *      </aside>
 *
 * 顶栏 AI 图标点击（workspace:chat:toggle）会被这个 mixin 接管，在本页把 AI 切到
 * 右栏；GlobalChatDrawer 那边靠 chatPanelState.dockMountCount 这个共享计数知道"当前
 * 有页面自己在管右栏"，从而让开、不重复切换、也不会跟右栏同时弹出抽屉。
 */
export default {
  computed: {
    // 全局AI助手开关，不是本页私有状态——读写的是 chatPanelState.open，所以切到
    // 别的 tab（有右栏的显示右栏，没有的走抽屉）时状态是跟着走的
    aiPanelActive: {
      get() { return chatPanelState.open },
      set(v) { chatPanelState.open = v },
    },
  },
  watch: {
    aiPanelActive(v) {
      // 进 AI 模式右栏至少 420，拖过更宽的不动，跟笔记页保持一致的体验
      if (v && this.wsRightWidth < 420) {
        this.wsRightWidth = this._wsClamp(420)
      }
    },
  },
  mounted() {
    this.$nuxt.$on('workspace:chat:toggle', this._wsAiToggle)
    // 告诉 GlobalChatDrawer："这个页面自己有右栏在管 AI，你让开"——直接改共享计数
    // 而不是发事件，避免 GlobalChatDrawer 挂载顺序比页面晚、错过首屏那次通知
    chatPanelState.dockMountCount++
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:chat:toggle', this._wsAiToggle)
    chatPanelState.dockMountCount = Math.max(0, chatPanelState.dockMountCount - 1)
  },
  methods: {
    _wsAiToggle() {
      // 窄屏这一栏本就不显示，交给抽屉兜底
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
