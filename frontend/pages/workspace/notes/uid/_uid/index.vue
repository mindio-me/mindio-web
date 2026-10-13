<!--
  Copyright (c) 2026 Fasong Wu
  SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="note-uid-page">
    <el-result
      v-if="notFound"
      icon="info"
      :title="$t('workspace.noteBackup.refNotFoundTitle')"
      :sub-title="$t('workspace.noteBackup.refNotFoundDesc')"
    >
      <template slot="extra">
        <el-button type="primary" size="small" @click="$router.push('/workspace/notes')">
          {{ $t('workspace.noteBackup.backToNotes') }}
        </el-button>
      </template>
    </el-result>
    <el-result
      v-else-if="failed"
      icon="warning"
      :title="$t('workspace.noteBackup.refLookupFailed')"
    >
      <template slot="extra">
        <el-button type="primary" size="small" @click="lookup">
          {{ $t('workspace.noteBackup.retry') }}
        </el-button>
      </template>
    </el-result>
    <div v-else v-loading="true" class="note-uid-page__loading"></div>
  </div>
</template>

<script>
/**
 * .mindio 导入后，引用了"当时不在本机"的笔记会写成 /workspace/notes/uid/<uid>。
 * 点击时在这里按 uid 找本机笔记：找到就替换跳转，找不到提示。被引用的笔记以后导入了，这个链接自然就能打开。
 */
export default {
  name: 'NoteByUidPage',
  layout: 'workspace',
  middleware: ['auth'],
  data() {
    return { notFound: false, failed: false }
  },
  mounted() {
    this.lookup()
  },
  methods: {
    async lookup() {
      this.notFound = false
      this.failed = false
      try {
        const { id } = await this.$noteService.findIdByUid(this.$route.params.uid)
        // 导出时保留了路径后缀（如 /edit），跳转时带上
        const suffix = /\/edit\/?$/.test(this.$route.path) ? '/edit' : ''
        this.$router.replace(`/workspace/notes/${id}${suffix}`)
      } catch (e) {
        if (e && e.response && e.response.status === 404) {
          this.notFound = true
        } else {
          this.failed = true
        }
      }
    }
  }
}
</script>

<style scoped>
.note-uid-page {
  padding: 48px 16px;
}
.note-uid-page__loading {
  min-height: 200px;
}
</style>
