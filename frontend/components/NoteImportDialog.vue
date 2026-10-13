<!--
  Copyright (c) 2026 Fasong Wu
  SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :visible="value"
    :title="preview ? $t('workspace.noteBackup.conflictTitle') : $t('workspace.noteBackup.importOption')"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="!committing"
    :show-close="!committing"
    @close="close"
  >
    <div v-loading="loading" :element-loading-text="$t('workspace.noteBackup.previewing')" class="import-body">
      <template v-if="preview">
        <p class="import-intro">
          {{ $t('workspace.noteBackup.conflictIntro', { n: preview.conflicts.length, m: preview.newNotes.length }) }}
        </p>
        <el-alert
          v-if="preview.missingAssets.length"
          type="warning"
          :closable="false"
          show-icon
          :title="$t('workspace.noteBackup.missingAssetsWarn', { n: preview.missingAssets.length })"
        />
        <el-alert
          v-if="preview.unresolvedNoteRefs > 0"
          type="info"
          :closable="false"
          show-icon
          :title="$t('workspace.noteBackup.unresolvedRefsWarn', { n: preview.unresolvedNoteRefs })"
        />
        <div class="import-bulk">
          <el-button size="mini" @click="setAll('keep')">{{ $t('workspace.noteBackup.keepAll') }}</el-button>
          <el-button size="mini" @click="setAll('overwrite')">{{ $t('workspace.noteBackup.overwriteAll') }}</el-button>
        </div>
        <div class="import-conflicts">
          <div v-for="c in preview.conflicts" :key="c.uid" class="import-conflict">
            <div class="import-versions">
              <div>
                <span class="import-label">{{ $t('workspace.noteBackup.localVersion') }}</span>
                {{ c.localTitle }}
                <span class="import-time">{{ $t('workspace.noteBackup.modifiedAt', { t: formatTime(c.localModifiedAt) }) }}</span>
              </div>
              <div>
                <span class="import-label">{{ $t('workspace.noteBackup.backupVersion') }}</span>
                {{ c.packageTitle }}
                <span class="import-time">{{ $t('workspace.noteBackup.modifiedAt', { t: formatTime(c.packageModifiedAt) }) }}</span>
              </div>
            </div>
            <el-radio-group v-model="choices[c.uid]" size="mini">
              <el-radio-button label="keep">{{ $t('workspace.noteBackup.keepLocal') }}</el-radio-button>
              <el-radio-button label="overwrite">{{ $t('workspace.noteBackup.useBackup') }}</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
    </div>
    <span slot="footer">
      <el-button size="small" :disabled="committing" @click="close">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" size="small" :loading="committing" :disabled="!preview" @click="commit">
        {{ $t('workspace.noteBackup.confirmImport') }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { errorMessageOf } from '~/utils/note-backup'

/**
 * .mindio 导入：选好文件后立即打开对话框显示"预检中"；没有冲突就直接执行并关闭，
 * 有冲突则让用户逐篇选。默认"保留本机"，避免一路点确认就把本机内容覆盖掉。
 */
export default {
  name: 'NoteImportDialog',
  props: {
    value: { type: Boolean, default: false },
    file: { type: typeof File !== 'undefined' ? File : Object, default: null },
    beforeCommit: { type: Function, default: null },
    afterCommit: { type: Function, default: null }
  },
  data() {
    return { loading: false, committing: false, preview: null, choices: {}, runId: 0 }
  },
  watch: {
    // 本组件在笔记页是异步加载的，第一次赋值 file 时可能还没挂载，所以要 immediate
    file: {
      immediate: true,
      handler(f) {
        if (f) this.start(f)
      }
    }
  },
  methods: {
    async start(file) {
      const runId = ++this.runId
      this.preview = null
      this.choices = {}
      this.loading = true
      // 大文件上传和解析要一会儿，先打开对话框显示加载状态
      this.$emit('input', true)
      try {
        const preview = await this.$noteService.previewImport(file)
        // 预检期间用户已取消或又选了别的文件
        if (runId !== this.runId) return
        const choices = {}
        preview.conflicts.forEach(c => { choices[c.uid] = 'keep' })
        this.choices = choices
        this.preview = preview
        if (preview.conflicts.length === 0) {
          await this.commit()
        }
      } catch (e) {
        if (runId !== this.runId) return
        if (e && e.response && e.response.status === 413) {
          this.$message.error(this.$t('workspace.noteBackup.tooLarge'))
        } else {
          this.$message.error(this.$t('workspace.noteBackup.importFailed') + await errorMessageOf(e, ''))
        }
        this.close()
      } finally {
        if (runId === this.runId) this.loading = false
      }
    },
    setAll(choice) {
      Object.keys(this.choices).forEach(uid => { this.choices[uid] = choice })
    },
    async commit() {
      if (this.committing || !this.preview) return
      this.committing = true
      try {
        const overwrite = Object.keys(this.choices).filter(uid => this.choices[uid] === 'overwrite')
        if (this.beforeCommit) await this.beforeCommit()
        const result = await this.$noteService.commitImport(this.preview.importToken, overwrite)
        this.$message.success(this.$t('workspace.noteBackup.importDone', result))
        if (this.preview && this.preview.unresolvedNoteRefs > 0) {
          this.$message.info(this.$t('workspace.noteBackup.unresolvedRefsWarn', { n: this.preview.unresolvedNoteRefs }))
        }
        if (this.afterCommit) await this.afterCommit(result, overwrite)
        this.$emit('imported', result, overwrite)
        this.close()
      } catch (e) {
        this.$message.error(this.$t('workspace.noteBackup.importFailed') + await errorMessageOf(e, ''))
        // 导入令牌提交时即被消费，失败后重试只会报"已过期"，所以直接关闭，让用户重新选文件
        this.close()
      } finally {
        this.committing = false
      }
    },
    formatTime(iso) {
      return iso ? new Date(iso).toLocaleString() : '-'
    },
    close() {
      // 让仍在进行的预检结果作废
      if (!this.preview) this.runId++
      this.loading = false
      this.$emit('input', false)
    }
  }
}
</script>

<style scoped>
.import-body { min-height: 80px; }
.import-intro { margin: 0 0 12px; }
.import-bulk { margin: 12px 0; }
.import-conflicts { max-height: 50vh; overflow-y: auto; }
.import-conflict {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}
.import-versions { flex: 1; min-width: 0; font-size: 13px; line-height: 1.6; }
.import-label { display: inline-block; min-width: 4em; color: #909399; }
.import-time { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
