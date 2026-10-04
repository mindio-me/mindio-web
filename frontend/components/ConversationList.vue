<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="conversation-list">
    <div class="conversation-list-header">
      <span>{{ $t('workspace.chat.conversationList') }}</span>
      <button class="conversation-list-new-btn" @click="$emit('new-conversation')">
        <i class="el-icon-plus"></i> {{ $t('workspace.chat.newConversation') }}
      </button>
    </div>
    <div v-if="conversations.length === 0" class="conversation-list-empty">
      {{ $t('workspace.chat.conversationListEmpty') }}
    </div>
    <div
      v-for="c in conversations"
      :key="c.id"
      class="conversation-list-item"
      :class="{ 'is-active': c.id === activeConversationId }"
      @click="$emit('select', c.id)"
    >
      <span v-if="renamingId !== c.id" class="conversation-list-item-title">{{ c.title }}</span>
      <el-input
        v-else
        v-model="renamingTitle"
        size="mini"
        class="conversation-list-item-rename-input"
        @click.native.stop
        @keydown.enter.native="confirmRename(c)"
        @blur="confirmRename(c)"
      />
      <div class="conversation-list-item-actions" @click.stop>
        <i class="el-icon-edit" :title="$t('workspace.chat.rename')" @click="startRename(c)"></i>
        <el-popconfirm
          :title="$t('workspace.chat.deleteConfirm')"
          @confirm="$emit('delete', c.id)"
        >
          <i slot="reference" class="el-icon-delete" :title="$t('workspace.chat.delete')"></i>
        </el-popconfirm>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ConversationList',
  props: {
    conversations: { type: Array, default: () => [] },
    activeConversationId: { type: Number, default: null },
  },
  data() {
    return {
      renamingId: null,
      renamingTitle: '',
    }
  },
  methods: {
    startRename(c) {
      this.renamingId = c.id
      this.renamingTitle = c.title
    },
    confirmRename(c) {
      if (this.renamingId !== c.id) return
      const title = this.renamingTitle.trim()
      this.renamingId = null
      if (title && title !== c.title) {
        this.$emit('rename', { id: c.id, title })
      }
    },
  },
}
</script>

<style scoped>
.conversation-list {
  max-height: 400px;
  overflow-y: auto;
}
.conversation-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px 8px;
  font-weight: 600;
}
.conversation-list-new-btn {
  border: none;
  background: none;
  color: var(--el-color-primary, var(--color-action));
  cursor: pointer;
  font-size: 13px;
}
.conversation-list-empty {
  padding: 16px 8px;
  color: var(--text-secondary, #909399);
  font-size: 13px;
  text-align: center;
}
.conversation-list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.conversation-list-item:hover {
  background: var(--hover-bg, #f5f7fa);
}
.conversation-list-item.is-active {
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.conversation-list-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conversation-list-item-rename-input {
  flex: 1;
}
.conversation-list-item-actions {
  display: none;
  gap: 8px;
  margin-left: 8px;
}
.conversation-list-item:hover .conversation-list-item-actions {
  display: flex;
}
</style>
