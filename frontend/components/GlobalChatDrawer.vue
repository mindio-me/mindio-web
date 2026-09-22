<!-- Copyright (c) 2026 Fasong Wu -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->
<template>
  <el-drawer
    :visible.sync="drawerVisible"
    direction="rtl"
    size="640px"
    :with-header="false"
    :append-to-body="true"
    class="global-chat-drawer"
  >
    <ChatPanel v-if="hasOpened" @close="closePanel" />
  </el-drawer>
</template>

<script>
import chatPanelState from '~/utils/chatPanelState'

export default {
  name: 'GlobalChatDrawer',
  data() {
    return { hasOpened: false }
  },
  computed: {
    // dockMountCount 直接读共享状态（workspaceAiDock mixin 挂载/卸载时维护），不用
    // 事件广播——这个组件在 layout 里排在 <nuxt/> 后面挂载，如果首屏直接落在有右栏
    // 的页面上，事件早发完了它才开始监听会错过；读响应式字段没有这个先后顺序问题
    dockMounted() {
      return chatPanelState.dockMountCount > 0
    },
    // watch 只能挂在 this 上能访问到的响应式属性上，chatPanelState 是外部 import 进来的
    // 单例，不是 this 的一部分，所以要包一层 computed 才能被下面的 watch 订阅到
    sharedOpen() {
      return chatPanelState.open
    },
    // 抽屉只在"开关是开的，且当前没有页面自己在管右栏"时才显示；有右栏的页面
    // （笔记、成就、剪藏……凡是接了 workspaceAiDock mixin 的）会自己停靠，抽屉让位，
    // 不然会同时出现两份
    drawerVisible: {
      get() {
        return chatPanelState.open && !this.dockMounted
      },
      set(v) {
        chatPanelState.open = v
      },
    },
  },
  watch: {
    // 开关在任何地方（比如某个页面的右栏）变成打开状态，这里都要把 hasOpened 锁上，
    // 抽屉的 ChatPanel 才会惰性挂载一次——不然从有右栏的页面打开、切到没有右栏的
    // 页面时，抽屉会因为 hasOpened 还是 false 而显示空白
    sharedOpen(v) {
      if (v) this.hasOpened = true
    },
  },
  mounted() {
    this.$nuxt.$on('workspace:chat:toggle', this.toggleOpen)
    if (this.sharedOpen) this.hasOpened = true
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:chat:toggle', this.toggleOpen)
  },
  methods: {
    toggleOpen() {
      // 有右栏的页面自己处理这次 toggle（写的是同一个 chatPanelState.open），这里
      // 让开、不重复切换，否则一次点击会被两边各切一次、等于没切
      if (this.dockMounted) return
      chatPanelState.open = !chatPanelState.open
    },
    closePanel() {
      chatPanelState.open = false
    },
  },
}
</script>
