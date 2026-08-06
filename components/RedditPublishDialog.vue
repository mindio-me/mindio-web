<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    title="发布到 Reddit"
    :visible.sync="visible"
    width="520px"
    :close-on-click-modal="false"
    @open="onOpen"
    @close="onClose"
  >
    <el-form :model="form" label-position="top">
      <el-form-item label="目标 Subreddit">
        <el-select
          v-model="form.subreddit"
          filterable
          allow-create
          placeholder="选择或输入 subreddit（无需 r/ 前缀）"
          style="width: 100%"
          :loading="loadingSubreddits"
          default-first-option
        >
          <el-option
            v-for="sr in subreddits"
            :key="sr.name"
            :label="`r/${sr.name}  (${sr.subscribers.toLocaleString()} 成员)`"
            :value="sr.name"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="帖子标题">
        <el-input
          v-model="form.title"
          maxlength="300"
          show-word-limit
          placeholder="默认使用笔记标题"
        />
      </el-form-item>
    </el-form>

    <p class="hint">笔记正文将自动转换为 Markdown 格式发布为 self-post。</p>

    <span slot="footer">
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onConfirm">发布</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'RedditPublishDialog',

  props: {
    noteId:    { type: Number, default: null },
    noteTitle: { type: String, default: '' }
  },

  data() {
    return {
      visible:          false,
      loading:          false,
      loadingSubreddits: false,
      subreddits:       [],
      form: {
        subreddit: '',
        title:     ''
      }
    }
  },

  methods: {
    open() {
      this.form.title = this.noteTitle || ''
      this.visible = true
    },

    async onOpen() {
      this.loadingSubreddits = true
      try {
        this.subreddits = await this.$redditService.getSubreddits()
      } catch (e) {
        this.subreddits = []
      } finally {
        this.loadingSubreddits = false
      }
    },

    onClose() {
      this.visible = false
    },

    async onConfirm() {
      if (!this.noteId) {
        this.$message.error('请先选择一条笔记')
        return
      }
      const subreddit = (this.form.subreddit || '').trim().replace(/^r\//, '')
      if (!subreddit) {
        this.$message.error('请选择或输入目标 subreddit')
        return
      }
      this.loading = true
      try {
        const payload = {
          subreddit,
          title: (this.form.title || '').trim() || undefined
        }
        const result = await this.$redditService.publish(this.noteId, payload)
        this.$message.success('已发布到 Reddit')
        this.visible = false
        this.$emit('published', result)
      } catch (err) {
        const msg = err?.response?.data?.message || err.message || '发布失败'
        this.$message.error(msg)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}
</style>
