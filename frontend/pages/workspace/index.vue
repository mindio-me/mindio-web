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
  layout: 'workspace',
  mounted() {
    // 如果直接访问 /workspace，重定向到 /workspace/notes
    if (this.$route.path === '/workspace') {
      this.$router.replace('/workspace/notes')
    }
  }
}
</script>
