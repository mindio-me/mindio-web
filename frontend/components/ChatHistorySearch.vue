<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="chat-history-search">
    <el-input
      ref="input"
      v-model="query"
      size="small"
      clearable
      prefix-icon="el-icon-search"
      :placeholder="$t('workspace.chat.searchPlaceholder')"
      @input="onInput"
    />
    <div class="chat-history-search-results">
      <div v-if="loading" class="chat-history-search-state"><i class="el-icon-loading"></i></div>
      <div v-else-if="error" class="chat-history-search-state">{{ $t('workspace.chat.searchFailed') }}</div>
      <div v-else-if="searched && !hits.length" class="chat-history-search-state">{{ $t('workspace.chat.searchNoResults') }}</div>
      <template v-else>
      <div
        v-for="hit in hits"
        :key="hit.messageId"
        class="chat-history-search-hit"
        @click="$emit('select', hit)"
      >
        <div class="chat-history-search-hit-meta">
          <span class="chat-history-search-hit-title">{{ hit.conversationTitle }}</span>
          <span class="chat-history-search-hit-date">{{ formatDate(hit.createdAt) }}</span>
        </div>
        <div class="chat-history-search-hit-snippet">
          <span class="chat-history-search-hit-role">{{ hit.role === 'USER' ? $t('workspace.chat.searchRoleUser') : $t('workspace.chat.title') }}：</span>
          <!-- 逐段渲染、命中处用 <mark>，不走 v-html -->
          <template v-for="(part, idx) in splitSnippet(hit.snippet)">
            <mark v-if="part.hit" :key="idx">{{ part.text }}</mark>
            <span v-else :key="idx">{{ part.text }}</span>
          </template>
        </div>
      </div>
      </template>
    </div>
  </div>
</template>

<script>
// 聊天记录搜索：在全部会话里按关键词找消息，点一条命中由 ChatPanel 打开对应会话并滚到那条消息
export default {
  name: 'ChatHistorySearch',
  data() {
    return { query: '', hits: [], loading: false, error: false, searched: false }
  },
  beforeDestroy() {
    clearTimeout(this._timer)
  },
  methods: {
    focus() {
      this.$nextTick(() => this.$refs.input && this.$refs.input.focus())
    },
    onInput() {
      clearTimeout(this._timer)
      this._timer = setTimeout(this.search, 300)
    },
    async search() {
      const q = this.query.trim()
      const seq = (this._seq = (this._seq || 0) + 1)
      if (!q) {
        this.hits = []
        this.searched = false
        this.error = false
        return
      }
      this.loading = true
      this.error = false
      try {
        const hits = await this.$globalChatService.searchMessages(q)
        if (seq !== this._seq) return // 只认最后一次输入的结果
        this.hits = hits || []
      } catch (e) {
        if (seq === this._seq) this.error = true
      } finally {
        if (seq === this._seq) {
          this.loading = false
          this.searched = true
        }
      }
    },
    splitSnippet(snippet) {
      const text = String(snippet || '')
      const q = this.query.trim().toLowerCase()
      if (!q) return [{ text, hit: false }]
      const parts = []
      const lower = text.toLowerCase()
      let pos = 0
      let i = lower.indexOf(q)
      while (i !== -1) {
        if (i > pos) parts.push({ text: text.slice(pos, i), hit: false })
        parts.push({ text: text.slice(i, i + q.length), hit: true })
        pos = i + q.length
        i = lower.indexOf(q, pos)
      }
      if (pos < text.length) parts.push({ text: text.slice(pos), hit: false })
      return parts
    },
    formatDate(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      const pad = (n) => (n < 10 ? '0' : '') + n
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.chat-history-search-results {
  margin-top: 8px;
  max-height: 400px;
  overflow-y: auto;
}
.chat-history-search-state {
  padding: 16px 8px;
  color: var(--text-muted, #909399);
  font-size: 13px;
  text-align: center;
}
.chat-history-search-hit {
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
}
.chat-history-search-hit:hover {
  background: var(--bg-secondary, #f5f7fa);
}
.chat-history-search-hit-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted, #909399);
}
.chat-history-search-hit-title {
  font-weight: 600;
  color: var(--text-secondary, #606266);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-history-search-hit-date {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.chat-history-search-hit-snippet {
  margin-top: 2px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-color, #303133);
  overflow-wrap: anywhere;
}
.chat-history-search-hit-role {
  color: var(--text-muted, #909399);
}
.chat-history-search-hit-snippet mark {
  background: rgba(255, 213, 79, 0.55);
  color: inherit;
  border-radius: 2px;
}
</style>
