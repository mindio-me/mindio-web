<template>
  <div class="resources-page">
    <div class="workspace-layout">
      <!-- ========== 左侧栏 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-search">
            <el-input v-model="resourceSearch" :placeholder="$t('workspace.resources.searchPlaceholder')" prefix-icon="el-icon-search" clearable size="small" />
          </div>
        </div>
        <div class="sidebar-section sidebar-categories">
          <div class="sidebar-section-header">
            <span class="section-title">{{ $t('workspace.resources.categoryLabel') }}</span>
          </div>
          <div class="category-filter-list">
            <button
              v-for="category in availableCategories"
              :key="category.value"
              @click="selectedCategory = category.value"
              :class="['category-filter-btn', { active: selectedCategory === category.value }]"
            >
              {{ category.label }}
            </button>
          </div>
        </div>
      </aside>

      <!-- ========== 中间内容区 ========== -->
      <main class="workspace-main">
        <div v-loading="loading" class="resources-content">
          <div v-if="filteredResources.length === 0 && !loading" class="empty-state">
            <i class="el-icon-search empty-icon"></i>
            <p class="empty-text">{{ $t('workspace.resources.noMatch') }}</p>
            <button v-if="resourceSearch || selectedCategory !== 'all'" @click="clearFilters" class="clear-filters-btn">{{ $t('workspace.resources.clearFilters') }}</button>
          </div>
          <div v-else class="resources-grid">
            <a
              v-for="resource in filteredResources"
              :key="resource.id || resource.name"
              :href="normalizeUrl(resource.url)"
              target="_blank"
              rel="noopener"
              class="resource-card"
              :title="resource.description"
              @contextmenu.prevent="handleRightClick(resource, $event)"
            >
              <div class="resource-icon">
                <i :class="resource.icon || 'el-icon-link'"></i>
              </div>
              <h3 class="resource-title">{{ resource.name }}</h3>
              <!-- <div class="resource-tooltip">{{ resource.description }}</div> -->
            </a>
          </div>
        </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside class="workspace-right">
        <div class="right-panel">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.resources.statsTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item">
                <span class="meta-label">{{ $t('workspace.resources.totalCount') }}</span>
                <span class="meta-value">{{ resources.length }}</span>
              </div>
              <div class="right-meta-item">
                <span class="meta-label">{{ $t('workspace.resources.currentCount') }}</span>
                <span class="meta-value">{{ filteredResources.length }}</span>
              </div>
              <div class="right-meta-item" v-if="selectedCategory !== 'all'">
                <span class="meta-label">{{ $t('workspace.resources.currentCategory') }}</span>
                <span class="meta-value">{{ getCategoryLabel(selectedCategory) }}</span>
              </div>
            </div>
          </div>
          <div class="right-section" v-if="selectedResource">
            <h3 class="right-title">{{ $t('workspace.resources.infoTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.resources.createdAt') }}</span><span class="meta-value">{{ formatDate(selectedResource.createdAt) }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.resources.modifiedAt') }}</span><span class="meta-value">{{ formatDate(selectedResource.modifiedAt) }}</span></div>
              <div class="right-meta-item" v-if="selectedResource.category"><span class="meta-label">{{ $t('workspace.resources.category') }}</span><span class="meta-value">{{ selectedResource.category }}</span></div>
              <div class="right-meta-item" v-if="selectedResource.url">
                <span class="meta-label">{{ $t('workspace.resources.linkLabel') }}</span>
                <a :href="normalizeUrl(selectedResource.url)" target="_blank" class="meta-link">{{ $t('workspace.resources.visit') }}</a>
              </div>
            </div>
            <div class="right-section-actions">
              <el-button size="small" icon="el-icon-edit" @click="openEditDialog">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" plain icon="el-icon-delete" @click="deleteResource">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
        </div>
      </aside>

      <!-- ========== 右键菜单 ========== -->
      <div
        v-show="contextMenuVisible"
        class="context-menu"
        :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }"
      >
        <div class="context-menu-item" @click="openEditDialog">
          <i class="el-icon-edit"></i> {{ $t('common.edit') }}
        </div>
        <div class="context-menu-item context-menu-item--danger" @click="deleteResource">
          <i class="el-icon-delete"></i> {{ $t('common.delete') }}
        </div>
        <div class="context-menu-divider"></div>
        <div class="context-menu-item" @click="openInNewTab">
          <i class="el-icon-link"></i> {{ $t('workspace.resources.openNewTab') }}
        </div>
      </div>

      <!-- ========== 新建/编辑对话框 ========== -->
      <el-dialog :title="dialogMode === 'create' ? $t('workspace.resources.dialogCreate') : $t('workspace.resources.dialogEdit')" :visible.sync="editDialogVisible" width="500px" append-to-body>
        <el-form :model="editForm" label-width="80px">
          <el-form-item :label="$t('workspace.resources.fieldName')">
            <el-input v-model="editForm.name" :placeholder="$t('workspace.resources.namePlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('workspace.resources.fieldUrl')">
            <el-input v-model="editForm.url" :placeholder="$t('workspace.resources.urlPlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('workspace.resources.fieldDescription')">
            <el-input v-model="editForm.description" type="textarea" :rows="3" :placeholder="$t('workspace.resources.descPlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('workspace.resources.fieldCategory')" required>
            <el-input v-model="editForm.category" :placeholder="$t('workspace.resources.categoryPlaceholder')" />
            <div v-if="existingCategories.length" class="category-quick-select">
              <span
                v-for="cat in existingCategories"
                :key="cat"
                :class="['category-chip', { active: editForm.category === cat }]"
                @click="editForm.category = cat"
              >{{ cat }}</span>
            </div>
          </el-form-item>
          <el-form-item :label="$t('workspace.resources.fieldIcon')">
            <el-input v-model="editForm.icon" :placeholder="$t('workspace.resources.iconPlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('workspace.resources.fieldTags')">
            <el-input v-model="editForm.tags" :placeholder="$t('workspace.resources.tagsPlaceholder')" />
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button @click="editDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="saveResource">{{ $t('common.save') }}</el-button>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ResourcesPage',
  layout: 'workspace',
  data() {
    return {
      loading: false,
      resources: [],
      selectedResource: null,
      resourceSearch: '',
      selectedCategory: 'all',
      // 右键菜单
      contextMenuVisible: false,
      contextMenuX: 0,
      contextMenuY: 0,
      // 新建/编辑对话框
      editDialogVisible: false,
      dialogMode: 'edit',
      saving: false,
      editForm: {
        id: '',
        name: '',
        url: '',
        description: '',
        category: '',
        icon: '',
        tags: ''
      }
    }
  },
  computed: {
    existingCategories() {
      return this.availableCategories.filter(c => c.value !== 'all' && c.value !== '未分类').map(c => c.value)
    },
    // 提取所有可用类别
    availableCategories() {
      const categories = new Set()
      this.resources.forEach(r => {
        if (r.category) {
          categories.add(r.category)
        }
      })
      
      const categoryList = [
        { value: 'all', label: this.$t('workspace.resources.categoryAll') }
      ]
      
      Array.from(categories).sort().forEach(cat => {
        categoryList.push({
          value: cat,
          label: cat
        })
      })
      
      return categoryList
    },
    // 过滤后的资源
    filteredResources() {
      let filtered = this.resources
      
      // 分类筛选
      if (this.selectedCategory !== 'all') {
        filtered = filtered.filter(r => r.category === this.selectedCategory)
      }
      
      // 搜索筛选
      if (this.resourceSearch && this.resourceSearch.trim()) {
        const query = this.resourceSearch.toLowerCase().trim()
        filtered = filtered.filter(r => {
          const nameMatch = (r.name || '').toLowerCase().includes(query)
          const descMatch = (r.description || '').toLowerCase().includes(query)
          const tagsMatch = (r.tags || '').toLowerCase().includes(query)
          return nameMatch || descMatch || tagsMatch
        })
      }
      
      // 按显示顺序排序（如果有）
      return filtered.sort((a, b) => {
        if (a.displayOrder !== undefined && b.displayOrder !== undefined) {
          return a.displayOrder - b.displayOrder
        }
        return (a.name || '').localeCompare(b.name || '')
      })
    }
  },
  mounted() {
    this.loadResources()
    document.addEventListener('click', this.hideContextMenu)
    this.$nuxt.$on('workspace:create:resources', this.showCreateDialog)
  },
  beforeDestroy() {
    document.removeEventListener('click', this.hideContextMenu)
    this.$nuxt.$off('workspace:create:resources', this.showCreateDialog)
  },
  methods: {
    async loadResources() {
      this.loading = true
      try {
        this.resources = await this.$resourceService.getAllResources()
      } catch (error) {
        this.$message.error(this.$t('workspace.resources.loadFailed'))
        this.resources = []
      } finally {
        this.loading = false
      }
    },
    clearFilters() {
      this.resourceSearch = ''
      this.selectedCategory = 'all'
    },
    getCategoryLabel(categoryValue) {
      const category = this.availableCategories.find(c => c.value === categoryValue)
      return category ? category.label : categoryValue
    },
    handleRightClick(resource, event) {
      // 选中资源
      this.selectedResource = resource
      // 显示右键菜单
      this.contextMenuX = event.clientX
      this.contextMenuY = event.clientY
      this.contextMenuVisible = true
    },
    hideContextMenu() {
      this.contextMenuVisible = false
    },
    showCreateDialog() {
      this.dialogMode = 'create'
      this.editForm = { id: '', name: '', url: '', description: '', category: '', icon: '', tags: '' }
      this.editDialogVisible = true
    },
    openEditDialog() {
      if (!this.selectedResource) return
      this.hideContextMenu()
      this.dialogMode = 'edit'
      // 填充表单
      this.editForm = {
        id: this.selectedResource.id,
        name: this.selectedResource.name || '',
        url: this.selectedResource.url || '',
        description: this.selectedResource.description || '',
        category: this.selectedResource.category || '',
        icon: this.selectedResource.icon || '',
        tags: this.selectedResource.tags || ''
      }
      this.editDialogVisible = true
    },
    async saveResource() {
      this.saving = true
      const payload = {
        name: this.editForm.name,
        url: this.editForm.url,
        description: this.editForm.description,
        category: this.editForm.category,
        icon: this.editForm.icon,
        tags: this.editForm.tags
      }
      try {
        if (this.dialogMode === 'create') {
          await this.$resourceService.createResource(payload)
          this.$message.success(this.$t('workspace.resources.createSuccess'))
        } else {
          if (!this.editForm.id) return
          await this.$resourceService.updateResource(this.editForm.id, payload)
          this.$message.success(this.$t('workspace.resources.saveSuccess'))
        }
        this.editDialogVisible = false
        this.selectedResource = null
        await this.loadResources()
      } catch (error) {
        this.$message.error(this.dialogMode === 'create' ? this.$t('workspace.resources.createFailed') : this.$t('workspace.resources.saveFailed'))
      } finally {
        this.saving = false
      }
    },
    deleteResource() {
      if (!this.selectedResource) return
      this.hideContextMenu()
      this.$confirm(this.$t('workspace.resources.deleteConfirm', { name: this.selectedResource.name }), this.$t('workspace.resources.confirmTitle'), {
        confirmButtonText: this.$t('common.confirm'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning'
      }).then(async () => {
        try {
          await this.$resourceService.deleteResource(this.selectedResource.id)
          this.$message.success(this.$t('workspace.resources.deleteSuccess'))
          this.selectedResource = null
          await this.loadResources()
        } catch (error) {
          this.$message.error(this.$t('workspace.resources.deleteFailed'))
        }
      }).catch(() => {})
    },
    openInNewTab() {
      if (!this.selectedResource || !this.selectedResource.url) return
      this.hideContextMenu()
      window.open(this.normalizeUrl(this.selectedResource.url), '_blank')
    },
    normalizeUrl(url) {
      if (!url) return ''
      if (/^https?:\/\//i.test(url)) return url
      return 'https://' + url
    },
    formatDate(time) {
      if (!time) return '-'
      return new Date(time).toLocaleDateString()
    }
  }
}
</script>

<style scoped lang="scss">
.resources-page {
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

.note-list-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-muted);
}

.note-list-time {
  flex: 1;
}

.sidebar-empty {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 4px;
}

.sidebar-categories {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.category-filter-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  padding-right: 4px;
  flex: 1;
  min-height: 0;

  &::-webkit-scrollbar {
    width: 6px;
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

.category-filter-btn {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--card-bg-color);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;

  &:hover {
    border-color: #667eea;
    color: #667eea;
    background: rgba(102, 126, 234, 0.05);
  }

  &.active {
    background: #667eea;
    border-color: #667eea;
    color: white;
  }
}

.workspace-main {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 16px 20px;
  overflow-y: auto;
}

.resources-content {
  height: 100%;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;

  .empty-icon {
    font-size: 48px;
    color: var(--text-placeholder);
    margin-bottom: 16px;
  }
  .empty-text {
    font-size: 16px;
    color: var(--text-muted);
    margin-bottom: 16px;
  }
}

.clear-filters-btn {
  padding: 10px 20px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #5568d3;
  }
}

// Resources Grid Layout
.resources-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
}

.resource-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px 12px;
  text-align: center;
  text-decoration: none;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
  cursor: pointer;

  &:hover {
    border-color: #667eea;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);

    .resource-icon {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      transform: scale(1.05);

      i {
        color: white;
      }
    }

    .resource-tooltip {
      opacity: 1;
      visibility: visible;
      transform: translateX(-50%) translateY(0);
    }
  }
}

.resource-icon {
  width: 40px;
  height: 40px;
  background: var(--tag-bg);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;

  i {
    font-size: 20px;
    color: #667eea;
    transition: all 0.2s ease;
  }
}

.resource-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
  line-height: 1.3;
  text-align: center;
  word-break: break-word;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.resource-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: var(--tooltip-bg, #1a202c);
  color: var(--tooltip-color, white);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
  width: 200px;
  text-align: left;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  pointer-events: none;

  &::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 5px solid transparent;
    border-top-color: var(--tooltip-bg, #1a202c);
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

.meta-link {
  color: #667eea;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}


// Responsive Design
@media screen and (max-width: 1400px) {
  .resources-grid {
    grid-template-columns: repeat(8, 1fr);
  }
}

@media screen and (max-width: 1200px) {
  .resources-grid {
    grid-template-columns: repeat(6, 1fr);
  }
}

@media screen and (max-width: 1024px) {
  .workspace-layout {
    grid-template-columns: 260px minmax(0, 1.5fr);
  }
  .workspace-right {
    display: none;
  }
  .resources-grid {
    grid-template-columns: repeat(5, 1fr);
    gap: 10px;
  }
  .resource-card {
    padding: 14px 10px;
  }
  .resource-icon {
    width: 36px;
    height: 36px;
    i {
      font-size: 18px;
    }
  }
  .resource-title {
    font-size: 12px;
  }
}

@media screen and (max-width: 768px) {
  .workspace-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .resources-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
  .resource-card {
    padding: 12px 8px;
    gap: 6px;
  }
  .resource-icon {
    width: 32px;
    height: 32px;
    i {
      font-size: 16px;
    }
  }
  .resource-title {
    font-size: 11px;
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }
  .resource-tooltip {
    width: 160px;
    font-size: 11px;
    padding: 6px 10px;
  }
}

@media screen and (max-width: 480px) {
  .resources-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
  }
  .resource-card {
    padding: 10px 6px;
  }
  .resource-icon {
    width: 28px;
    height: 28px;
    i {
      font-size: 14px;
    }
  }
  .resource-title {
    font-size: 10px;
  }
}

// 右键菜单样式
.context-menu {
  position: fixed;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 6px 0;
  min-width: 140px;
  z-index: 2000;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text-color);
  cursor: pointer;
  transition: all 0.15s;

  i {
    font-size: 14px;
    color: var(--text-secondary);
  }

  &:hover {
    background: var(--bg-secondary);
  }

  &--danger {
    color: #f56c6c;

    i {
      color: #f56c6c;
    }

    &:hover {
      background: rgba(245, 108, 108, 0.1);
    }
  }
}

.context-menu-divider {
  height: 1px;
  background: var(--border-color);
  margin: 6px 0;
}

.category-quick-select {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  line-height: 20px;
}

.category-chip {
  padding: 3px 10px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--card-bg-color);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: #667eea;
    color: #667eea;
    background: rgba(102, 126, 234, 0.06);
  }

  &.active {
    border-color: #667eea;
    background: #667eea;
    color: white;
  }
}

// 右侧面板操作按钮
.right-section-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}
</style>

