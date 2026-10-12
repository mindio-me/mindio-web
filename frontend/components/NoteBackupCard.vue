<!--
  Copyright (c) 2026 Fasong Wu
  SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-card class="settings-card" shadow="never">
    <div slot="header" class="card-header">
      <span>{{ $t('workspace.noteBackup.cardTitle') }}</span>
    </div>
    <p class="section-tip">{{ $t('workspace.noteBackup.cardIntro') }}</p>
    <div class="inline-actions">
      <el-button type="primary" size="small" :loading="exporting" @click="exportAll">
        <i class="el-icon-download"></i> {{ $t('workspace.noteBackup.exportAll') }}
      </el-button>
    </div>
  </el-card>
</template>

<script>
import { downloadBlob, backupFileName, isDesktopRuntime, errorMessageOf } from '~/utils/note-backup'

export default {
  name: 'NoteBackupCard',
  data() {
    return { exporting: false }
  },
  methods: {
    async exportAll() {
      this.exporting = true
      try {
        const { blob, noteCount, missingAssets } = await this.$noteService.exportNotes({
          all: true,
          exportedFrom: isDesktopRuntime() ? 'desktop' : 'web'
        })
        downloadBlob(blob, backupFileName())
        this.$message.success(missingAssets > 0
          ? this.$t('workspace.noteBackup.exportMissingAssets', { n: noteCount, m: missingAssets })
          : this.$t('workspace.noteBackup.exportDone', { n: noteCount }))
      } catch (e) {
        this.$message.error(this.$t('workspace.noteBackup.exportFailed') + await errorMessageOf(e, ''))
      } finally {
        this.exporting = false
      }
    }
  }
}
</script>
