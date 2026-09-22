<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    title="创建英文翻译版本"
    :visible.sync="dialogVisible"
    width="440px"
    :close-on-click-modal="false"
    @close="onClose"
  >
    <div v-if="!done && !loading">
      <p class="dialog-desc">AI 将基于当前笔记生成一篇英文版本，作为独立笔记保存。</p>

      <el-radio-group v-model="mode" class="mode-group">
        <el-radio label="FAITHFUL" class="mode-radio">
          <span class="mode-label">忠实翻译</span>
          <span class="mode-hint">逐段翻译，保留原文结构，适合正式文档</span>
        </el-radio>
        <el-radio label="LINKEDIN" class="mode-radio">
          <span class="mode-label">LinkedIn 改写</span>
          <span class="mode-hint">以原文为素材，重写为适合 LinkedIn 发布的英文文章</span>
        </el-radio>
      </el-radio-group>
    </div>

    <div v-if="loading" class="status-area">
      <i class="el-icon-loading status-icon"></i>
      <span>AI 翻译中，请稍候…</span>
    </div>

    <div v-if="done && !error" class="status-area status-success">
      <i class="el-icon-circle-check status-icon success-icon"></i>
      <span>翻译完成！已生成新笔记。</span>
    </div>

    <div v-if="error" class="status-area status-error">
      <i class="el-icon-circle-close status-icon error-icon"></i>
      <span>翻译失败：{{ error }}</span>
    </div>

    <span slot="footer">
      <template v-if="!done">
        <el-button @click="onClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="onTranslate">
          开始翻译
        </el-button>
      </template>
      <template v-else>
        <el-button @click="onClose">关闭</el-button>
        <el-button v-if="!error" type="primary" @click="onViewNew">
          查看新笔记
        </el-button>
        <el-button v-if="error" @click="retry">重试</el-button>
      </template>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'TranslationDialog',

  props: {
    noteId: { type: Number, default: null }
  },

  data() {
    return {
      dialogVisible: false,
      mode: 'FAITHFUL',
      loading: false,
      done: false,
      error: null,
      newNote: null
    }
  },

  methods: {
    open() {
      this.dialogVisible = true
      this.loading = false
      this.done = false
      this.error = null
      this.newNote = null
      this.mode = 'FAITHFUL'
    },

    onClose() {
      this.dialogVisible = false
      this.$emit('close')
    },

    async onTranslate() {
      if (!this.noteId) return
      this.loading = true
      this.error = null
      try {
        this.newNote = await this.$aiService.translateNote(this.noteId, this.mode, 'en')
        this.done = true
      } catch (e) {
        this.error = e?.response?.data?.message || e?.message || '未知错误'
        this.done = true
      } finally {
        this.loading = false
      }
    },

    onViewNew() {
      if (this.newNote) {
        this.$emit('translated', this.newNote)
      }
      this.dialogVisible = false
    },

    retry() {
      this.done = false
      this.error = null
      this.newNote = null
    }
  }
}
</script>

<style scoped>
.dialog-desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 16px;
}

.mode-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mode-radio {
  display: flex;
  align-items: flex-start;
  height: auto;
  white-space: normal;
  line-height: 1.4;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  transition: border-color 0.2s;
}

.mode-radio.is-checked {
  border-color: #409eff;
  background: #f0f7ff;
}

.mode-label {
  font-weight: 500;
  font-size: 13px;
  display: block;
}

.mode-hint {
  color: #909399;
  font-size: 12px;
  display: block;
  margin-top: 2px;
}

.status-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  font-size: 14px;
  color: #606266;
}

.status-icon {
  font-size: 24px;
}

.success-icon { color: #67c23a; }
.error-icon   { color: #f56c6c; }
</style>
