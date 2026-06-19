<template>
  <div class="tags-page">
    <div class="workspace-layout">
      <!-- ========== 左侧列表 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-search">
            <el-input v-model="tagSearch" :placeholder="$t('workspace.tags.searchPlaceholder')" prefix-icon="el-icon-search" clearable size="small" />
          </div>
        </div>
        <div class="sidebar-section sidebar-notes">
          <div class="sidebar-section-header">
            <span class="section-title">{{ $t('workspace.tags.allTags') }}</span>
            <span class="section-subtitle">{{ filteredTagList.length }}{{ $t('workspace.tags.countSuffix') }}</span>
          </div>
          <div v-loading="loading" class="note-list-wrapper">
            <div v-if="filteredTagList.length > 0" class="note-list">
              <div
                v-for="tag in filteredTagList"
                :key="tag.id"
                class="note-list-item"
                :class="{ active: selectedTag && selectedTag.id === tag.id }"
                @click="selectTag(tag)"
              >
                <div class="note-list-title">
                  <i class="el-icon-collection-tag"></i> {{ tag.name }}
                </div>
              </div>
            </div>
            <div v-else-if="!loading" class="sidebar-empty"><p>{{ $t('workspace.tags.empty') }}</p></div>
          </div>
        </div>
      </aside>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main">
        <div v-if="selectedTag" class="entity-form-wrapper">
          <div class="entity-form-header">
            <h2 class="entity-form-title">{{ $t('workspace.tags.editTitle') }}</h2>
            <div class="entity-form-actions">
              <el-button type="primary" size="small" :loading="tagSaving" @click="saveTag">{{ $t('common.save') }}</el-button>
              <el-button type="danger" size="small" plain @click="deleteTag">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
          <el-form :model="tagForm" label-position="top" class="entity-form">
            <el-form-item :label="$t('workspace.tags.fieldName')">
              <el-input v-model="tagForm.name" :placeholder="$t('workspace.tags.namePlaceholder')" maxlength="50" show-word-limit />
            </el-form-item>
          </el-form>
        </div>
        <div v-else class="note-main-empty">
          <i class="el-icon-collection-tag empty-icon"></i>
          <p class="empty-text">{{ $t('workspace.tags.selectEmpty') }}</p>
        </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside class="workspace-right">
        <div class="right-panel" v-if="selectedTag">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.tags.rightPanelTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.tags.metaName') }}</span><span class="meta-value">{{ selectedTag.name }}</span></div>
              <div class="right-meta-item"><span class="meta-label">ID</span><span class="meta-value">{{ selectedTag.id }}</span></div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 标签创建对话框 -->
    <el-dialog
      :title="tagDialogMode === 'create' ? $t('workspace.tags.dialogCreate') : $t('workspace.tags.dialogEdit')"
      :visible.sync="tagDialogVisible"
      width="400px"
    >
      <el-form :model="tagDialogForm">
        <el-form-item :label="$t('workspace.tags.fieldName')">
          <el-input v-model="tagDialogForm.name" :placeholder="$t('workspace.tags.namePlaceholder')" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="tagDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="tagSaving" @click="saveTagFromDialog">{{ $t('common.confirm') }}</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'TagsPage',
  layout: 'workspace',
  data() {
    return {
      loading: false,
      tagList: [],
      selectedTag: null,
      tagForm: { name: '' },
      tagSaving: false,
      tagDialogVisible: false,
      tagDialogMode: 'create',
      tagDialogForm: { name: '' },
      tagSearch: ''
    }
  },
  computed: {
    filteredTagList() {
      if (!this.tagSearch) return this.tagList
      const kw = this.tagSearch.toLowerCase()
      return this.tagList.filter(t => (t.name || '').toLowerCase().includes(kw))
    }
  },
  mounted() {
    this.loadTagList()
    // 监听 layout 触发的创建事件
    this.$nuxt.$on('workspace:create:tags', this.showCreateTagDialog)
  },
  beforeDestroy() {
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:tags', this.showCreateTagDialog)
  },
  methods: {
    async loadTagList() {
      this.loading = true
      try {
        const { data } = await this.$axios.get('/v1/tags')
        this.tagList = data || []
      } catch (error) {
        this.$message.error(this.$t('workspace.tags.loadFailed'))
        this.tagList = []
      } finally {
        this.loading = false
      }
    },
    selectTag(tag) {
      if (!tag || !tag.id) return
      // 从原始 tagList 中找到对应的标签，确保引用一致
      const originalTag = this.tagList.find(t => t.id === tag.id) || tag
      this.selectedTag = originalTag
      this.tagForm = { name: originalTag.name }
    },
    showCreateTagDialog() {
      this.tagDialogMode = 'create'
      this.tagDialogForm = { name: '' }
      this.tagDialogVisible = true
    },
    async saveTag() {
      if (!this.selectedTag || !this.tagForm.name) return
      this.tagSaving = true
      try {
        await this.$axios.put(`/v1/tags/${this.selectedTag.id}`, this.tagForm)
        this.$message.success(this.$t('workspace.tags.updateSuccess'))
        this.selectedTag.name = this.tagForm.name
        const idx = this.tagList.findIndex(t => t.id === this.selectedTag.id)
        if (idx >= 0) this.tagList[idx].name = this.tagForm.name
      } catch (error) {
        this.$message.error(this.$t('workspace.tags.updateFailed'))
      } finally {
        this.tagSaving = false
      }
    },
    async saveTagFromDialog() {
      if (!this.tagDialogForm.name) return
      this.tagSaving = true
      try {
        await this.$axios.post('/v1/tags', this.tagDialogForm)
        this.$message.success(this.$t('workspace.tags.createSuccess'))
        this.tagDialogVisible = false
        await this.loadTagList()
      } catch (error) {
        this.$message.error(error.response?.data?.message || this.$t('workspace.tags.createFailed'))
      } finally {
        this.tagSaving = false
      }
    },
    deleteTag() {
      if (!this.selectedTag) return
      this.$confirm(this.$t('workspace.tags.deleteConfirm', { name: this.selectedTag.name }), this.$t('workspace.tags.confirmTitle'), {
        confirmButtonText: this.$t('common.confirm'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning'
      }).then(async () => {
        try {
          await this.$axios.delete(`/v1/tags/${this.selectedTag.id}`)
          this.$message.success(this.$t('workspace.tags.deleteSuccess'))
          this.selectedTag = null
          this.tagForm = { name: '' }
          await this.loadTagList()
        } catch (error) {
          this.$message.error(this.$t('workspace.tags.deleteFailed'))
        }
      }).catch(() => {})
    }
  }
}
</script>

<style scoped lang="scss">
.tags-page {
  background: transparent;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1.5fr) 260px;
  gap: 16px;
  height: calc(100vh - 110px);
}

.workspace-sidebar {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 12px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
  height: 100%;
  max-height: 100%;
}

.sidebar-section + .sidebar-section {
  border-top: 1px solid var(--border-color);
  padding-top: 8px;
}

.sidebar-search {
  margin-bottom: 8px;
}

.sidebar-section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.section-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.sidebar-notes {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  height: 0;
}

.note-list-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.note-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  padding-right: 4px;
  padding-bottom: 4px;
  min-height: 0;
  height: 100%;

  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 3px;
    &:hover {
      background: var(--text-muted);
    }
  }
  scrollbar-width: thin;
  scrollbar-color: var(--border-color) transparent;
}

.note-list-item {
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 4px;
  min-width: fit-content;
  width: 100%;

  &:hover {
    background: var(--bg-secondary);
  }
  &.active {
    background: rgba(102, 126, 234, 0.12);
    border: 1px solid #667eea;
  }
}

.note-list-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
}

.sidebar-empty {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 4px;
}

.workspace-main {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 16px 20px;
  overflow-y: auto;
}

.note-main-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;

  .empty-icon {
    font-size: 40px;
    color: var(--text-placeholder);
    margin-bottom: 10px;
  }
  .empty-text {
    font-size: 14px;
    color: var(--text-muted);
  }
}

.entity-form-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.entity-form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.entity-form-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
}

.entity-form-actions {
  display: flex;
  gap: 8px;
}

.entity-form {
  flex: 1;
  /* 移除 overflow-y: auto，由父容器 .workspace-main 管理滚动 */
  padding-right: 4px;

  ::v-deep .el-form-item__label {
    font-size: 13px;
    color: var(--text-secondary);
    padding-bottom: 4px;
  }
}

.workspace-right {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 12px 12px 8px;
  overflow-y: auto;
}

.right-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.right-section {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
  &:last-child {
    border-bottom: none;
  }
}

.right-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.right-meta-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.right-meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.meta-label {
  color: var(--text-muted);
}

.meta-value {
  color: var(--text-secondary);
  font-weight: 500;
}

.entity-form-wrapper {
  ::v-deep .el-input__inner {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
    color: var(--text-color) !important;

    &:focus {
      border-color: #667eea !important;
    }
  }

  ::v-deep .el-input__inner::placeholder {
    color: var(--text-muted) !important;
  }

  ::v-deep .el-input__count,
  ::v-deep .el-input__count-inner {
    background: transparent !important;
    color: var(--text-muted) !important;
  }
}

@media screen and (max-width: 1024px) {
  .workspace-layout {
    grid-template-columns: 260px minmax(0, 1.5fr);
  }
  .workspace-right {
    display: none;
  }
}

@media screen and (max-width: 768px) {
  .workspace-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
