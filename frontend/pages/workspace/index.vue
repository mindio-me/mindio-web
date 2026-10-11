<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div>
    <!-- 重定向到笔记页面 -->
  </div>
</template>

<script>
export default {
  name: 'WorkspaceIndex',
  layout: 'workspace'
  // 跳转到 /workspace/notes 由 workspace 布局统一处理；这里再跳一次会和布局的跳转互相取消，
  // 报 "Navigation cancelled ... with a new navigation"
}
</script>
