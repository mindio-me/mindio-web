<!-- Copyright (c) 2026 Fasong Wu -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->
<template>
  <el-drawer
    :visible.sync="open"
    direction="rtl"
    size="640px"
    :with-header="false"
    :append-to-body="true"
    class="global-chat-drawer"
  >
    <ChatPanel v-if="hasOpened" @close="open = false" />
  </el-drawer>
</template>

<script>
export default {
  name: 'GlobalChatDrawer',
  data() {
    return { open: false, hasOpened: false }
  },
  mounted() {
    this.$nuxt.$on('workspace:chat:toggle', this.toggleOpen)
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:chat:toggle', this.toggleOpen)
  },
  methods: {
    toggleOpen() {
      // 笔记列表主页面（精确匹配 /workspace/notes）AI 停靠右栏，由页面自己处理 toggle，
      // 抽屉不响应（不分宽窄屏——页面自己的侧栏停靠逻辑本就不区分宽窄屏，这里如果只在
      // 窄屏时抢着弹一次，会和侧栏同时出现两份 AI 面板）；编辑/查看/新建笔记等独立路由
      // 页没有侧栏停靠，仍然走抽屉。
      if (this.$route.path === '/workspace/notes') return
      this.open = !this.open
      if (this.open) this.hasOpened = true
    },
  },
}
</script>
