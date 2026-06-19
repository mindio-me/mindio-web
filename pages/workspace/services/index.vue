ssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss<template>
  <div class="services-page">
    <div class="workspace-layout">
      <!-- ========== 左侧列表 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-search">
            <el-input v-model="serviceSearch" :placeholder="$t('workspace.services.searchPlaceholder')" prefix-icon="el-icon-search" clearable size="small" />
          </div>
        </div>
        <div class="sidebar-section sidebar-notes">
          <div class="sidebar-section-header">
            <span class="section-title">{{ $t('workspace.services.myServices') }}</span>
            <span class="section-subtitle">{{ filteredServices.length }}{{ $t('workspace.services.countSuffix') }}</span>
          </div>
          <div v-loading="loading" class="note-list-wrapper">
            <div v-if="filteredServices.length > 0" class="note-list">
              <div
                v-for="item in filteredServices"
                :key="item.id"
                class="note-list-item"
                :class="{ active: selectedService && selectedService.id === item.id }"
                @click="selectService(item)"
              >
                <div class="note-list-title">{{ item.name }}</div>
                <div class="note-list-meta">
                  <span class="note-list-time">{{ item.category || $t('workspace.services.uncategorized') }}</span>
                  <el-tag v-if="item.isActive" size="mini" type="success" effect="plain">{{ $t('workspace.services.enabled') }}</el-tag>
                </div>
              </div>
            </div>
            <div v-else-if="!loading" class="sidebar-empty"><p>{{ $t('workspace.services.empty') }}</p></div>
          </div>
        </div>
      </aside>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main">
        <div v-if="selectedService" class="entity-detail-wrapper">
          <!-- 详情模式 -->
          <div v-if="serviceViewMode === 'detail'" class="entity-detail-view">
            <!-- 详情头部 -->
            <div class="entity-detail-header">
              <h2 class="entity-detail-title">{{ selectedService.name }}</h2>
              <div class="entity-detail-actions">
                <el-button size="small" icon="el-icon-edit" @click="serviceViewMode = 'edit'">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" plain @click="deleteService">{{ $t('common.delete') }}</el-button>
              </div>
            </div>

            <!-- 详情内容 -->
            <div class="entity-detail-content">
              <!-- 描述 -->
              <div v-if="selectedService.description" class="detail-field">
                <label class="detail-label">{{ $t('workspace.services.description') }}</label>
                <div class="detail-value">{{ selectedService.description }}</div>
              </div>

              <!-- 详细描述 -->
              <div v-if="selectedService.detailedDescription" class="detail-field">
                <label class="detail-label">{{ $t('workspace.services.detailedDescription') }}</label>
                <div class="detail-value" style="white-space: pre-wrap;">{{ selectedService.detailedDescription }}</div>
              </div>

              <!-- 分类和图标 -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.services.category') }}</label>
                    <div class="detail-value">{{ selectedService.category || '-' }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.services.icon') }}</label>
                    <div class="detail-value">
                      <i v-if="selectedService.icon" :class="selectedService.icon"></i>
                      <span v-else>-</span>
                    </div>
                  </div>
                </el-col>
              </el-row>

              <!-- 定价 -->
              <div v-if="selectedService.pricing" class="detail-field">
                <label class="detail-label">{{ $t('workspace.services.pricing') }}</label>
                <div class="detail-value">{{ selectedService.pricing }}</div>
              </div>

              <!-- 功能特性 -->
              <div v-if="displayFeatures.length" class="detail-field">
                <label class="detail-label">{{ $t('workspace.services.features') }}</label>
                <div class="detail-value">
                  <el-tag
                    v-for="(feat, idx) in displayFeatures"
                    :key="idx"
                    size="small"
                    style="margin-right: 8px; margin-bottom: 8px;"
                  >{{ feat }}</el-tag>
                </div>
              </div>

              <!-- 状态信息 -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.services.displayOrder') }}</label>
                    <div class="detail-value">{{ selectedService.displayOrder || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.services.isActive') }}</label>
                    <div class="detail-value">
                      <el-tag :type="selectedService.isActive ? 'success' : 'info'" size="small">{{ selectedService.isActive ? $t('workspace.services.yes') : $t('workspace.services.no') }}</el-tag>
                    </div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.services.isFeatured') }}</label>
                    <div class="detail-value">
                      <el-tag :type="selectedService.isFeatured ? 'warning' : 'info'" size="small">{{ selectedService.isFeatured ? $t('workspace.services.yes') : $t('workspace.services.no') }}</el-tag>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>

          <!-- 编辑模式 -->
          <div v-else-if="serviceViewMode === 'edit'" class="entity-form-wrapper">
            <div class="entity-form-header">
              <h2 class="entity-form-title">{{ $t('workspace.services.editTitle') }}</h2>
              <div class="entity-form-actions">
                <el-button size="small" @click="cancelServiceEdit">{{ $t('common.cancel') }}</el-button>
                <el-button type="primary" size="small" :loading="serviceSaving" @click="saveService">{{ $t('common.save') }}</el-button>
              </div>
            </div>
            <el-form :model="serviceForm" label-position="top" class="entity-form">
              <el-form-item :label="$t('workspace.notes.title')">
                <el-input v-model="serviceForm.name" :placeholder="$t('workspace.services.namePlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.services.description')">
                <el-input v-model="serviceForm.description" type="textarea" :rows="2" :placeholder="$t('workspace.services.descPlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.services.detailedDescription')">
                <el-input v-model="serviceForm.detailedDescription" type="textarea" :rows="4" :placeholder="$t('workspace.services.detailPlaceholder')" />
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.services.category')">
                    <el-input v-model="serviceForm.category" placeholder="e.g.: Development, Design" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.services.icon')">
                    <el-input v-model="serviceForm.icon" :placeholder="$t('workspace.services.iconPlaceholder')" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item :label="$t('workspace.services.pricing')">
                <el-input v-model="serviceForm.pricing" :placeholder="$t('workspace.services.pricingPlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.services.features')">
                <div class="dynamic-tags">
                  <el-tag
                    v-for="(feat, idx) in serviceForm.features"
                    :key="idx"
                    closable
                    size="small"
                    @close="serviceForm.features.splice(idx, 1)"
                  >{{ feat }}</el-tag>
                  <el-input
                    v-if="serviceTagInputVisible"
                    ref="serviceTagInput"
                    v-model="serviceTagInputValue"
                    size="small"
                    class="tag-input"
                    @keyup.enter.native="addServiceTag"
                    @blur="addServiceTag"
                  />
                  <el-button v-else size="small" class="tag-add-btn" @click="showServiceTagInput">{{ $t('workspace.services.addTag') }}</el-button>
                </div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.services.displayOrder')">
                    <el-input-number v-model="serviceForm.displayOrder" :min="0" size="small" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.services.isActive')">
                    <el-switch v-model="serviceForm.isActive" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.services.isFeatured')">
                    <el-switch v-model="serviceForm.isFeatured" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </div>
        <div v-else class="note-main-empty">
          <i class="el-icon-service empty-icon"></i>
          <p class="empty-text">{{ $t('workspace.services.selectEmpty') }}</p>
        </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside class="workspace-right">
        <div class="right-panel" v-if="selectedService">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.services.rightPanelTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.services.createdAt') }}</span><span class="meta-value">{{ formatDate(selectedService.createdAt) }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.services.modifiedAt') }}</span><span class="meta-value">{{ formatDate(selectedService.modifiedAt) }}</span></div>
              <div class="right-meta-item" v-if="selectedService.category"><span class="meta-label">{{ $t('workspace.services.category') }}</span><span class="meta-value">{{ selectedService.category }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.services.status') }}</span><span class="meta-value">{{ selectedService.isActive ? $t('workspace.services.statusActive') : $t('workspace.services.statusInactive') }}</span></div>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ServicesPage',
  layout: 'workspace',
  data() {
    return {
      loading: false,
      services: [],
      selectedService: null,
      serviceForm: {
        name: '',
        description: '',
        detailedDescription: '',
        icon: '',
        category: '',
        features: [],
        pricing: '',
        isActive: true,
        isFeatured: false,
        displayOrder: 0
      },
      serviceSaving: false,
      serviceSearch: '',
      serviceTagInputVisible: false,
      serviceTagInputValue: '',
      serviceViewMode: 'detail' // 'detail' | 'edit'
    }
  },
  computed: {
    filteredServices() {
      if (!this.serviceSearch) return this.services
      const kw = this.serviceSearch.toLowerCase()
      return this.services.filter(s => (s.name || '').toLowerCase().includes(kw))
    },
    displayFeatures() {
      if (!this.selectedService?.features) return []
      return Array.isArray(this.selectedService.features)
        ? this.selectedService.features
        : this.selectedService.features.split(',').map(f => f.trim()).filter(f => f)
    }
  },
  mounted() {
    this.loadServices()
    // 监听 layout 触发的创建事件
    this.$nuxt.$on('workspace:create:services', this.createService)
  },
  beforeDestroy() {
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:services', this.createService)
  },
  methods: {
    async loadServices() {
      this.loading = true
      try {
        this.services = await this.$serviceService.getAllServices()
        // 自动选中第一个服务
        if (this.services.length > 0 && !this.selectedService) {
          this.selectService(this.services[0])
        }
      } catch (error) {
        this.$message.error(this.$t('workspace.services.loadFailed'))
        this.services = []
      } finally {
        this.loading = false
      }
    },
    selectService(item) {
      this.selectedService = item
      this.serviceViewMode = 'detail'

      // 预填充编辑表单
      this.serviceForm = {
        name: item.name || '',
        description: item.description || '',
        detailedDescription: item.detailedDescription || '',
        icon: item.icon || '',
        category: item.category || '',
        features: Array.isArray(item.features)
          ? [...item.features]
          : (item.features ? item.features.split(',').map(f => f.trim()).filter(f => f) : []),
        pricing: item.pricing || '',
        isActive: item.isActive !== false,
        isFeatured: item.isFeatured || false,
        displayOrder: item.displayOrder || 0
      }
    },
    cancelServiceEdit() {
      this.serviceViewMode = 'detail'
    },
    async createService() {
      try {
        const data = { name: this.$t('workspace.services.newServiceName'), description: this.$t('workspace.services.newServiceDesc'), isActive: true }
        const result = await this.$serviceService.createService(data)
        await this.loadServices()
        const created = this.services.find(s => s.id === result.id) || this.services[0]
        if (created) this.selectService(created)
        this.$message.success(this.$t('workspace.services.createSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.services.createFailed'))
      }
    },
    async saveService() {
      if (!this.selectedService || !this.serviceForm.name) return
      this.serviceSaving = true
      try {
        // 将功能特性数组转换为逗号分隔的字符串
        const submitData = {
          ...this.serviceForm,
          features: Array.isArray(this.serviceForm.features)
            ? this.serviceForm.features.join(',')
            : this.serviceForm.features
        }

        await this.$serviceService.updateService(this.selectedService.id, submitData)
        this.$message.success(this.$t('workspace.services.saveSuccess'))
        const idx = this.services.findIndex(s => s.id === this.selectedService.id)
        if (idx >= 0) Object.assign(this.services[idx], submitData)
        this.selectedService = { ...this.selectedService, ...submitData }

        // 切换回详情模式
        this.serviceViewMode = 'detail'
      } catch (error) {
        this.$message.error(this.$t('workspace.services.saveFailed'))
      } finally {
        this.serviceSaving = false
      }
    },
    deleteService() {
      if (!this.selectedService) return
      this.$confirm(this.$t('workspace.services.deleteConfirm', { name: this.selectedService.name }), this.$t('workspace.services.confirmTitle'), {
        confirmButtonText: this.$t('common.confirm'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning'
      }).then(async () => {
        try {
          await this.$serviceService.deleteService(this.selectedService.id)
          this.$message.success(this.$t('workspace.services.deleteSuccess'))
          this.selectedService = null
          await this.loadServices()
        } catch (error) {
          this.$message.error(this.$t('workspace.services.deleteFailed'))
        }
      }).catch(() => {})
    },
    showServiceTagInput() {
      this.serviceTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.serviceTagInput) {
          this.$refs.serviceTagInput.focus()
        }
      })
    },
    addServiceTag() {
      const val = this.serviceTagInputValue.trim()
      if (val && !this.serviceForm.features.includes(val)) {
        this.serviceForm.features.push(val)
      }
      this.serviceTagInputVisible = false
      this.serviceTagInputValue = ''
    },
    formatDate(time) {
      if (!time) return '-'
      return new Date(time).toLocaleDateString()
    }
  }
}
</script>

<style scoped lang="scss">
.services-page {
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

.entity-detail-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.entity-detail-view {
  flex: 1;
  /* 移除 overflow-y: auto，由父容器 .workspace-main 管理滚动 */
  padding-right: 4px;
}

.entity-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);

  .entity-detail-title {
    font-size: 20px;
    font-weight: 600;
    color: var(--text-color);
    margin: 0;
  }

  .entity-detail-actions {
    display: flex;
    gap: 8px;
  }
}

.entity-detail-content {
  .detail-field {
    margin-bottom: 20px;

    .detail-label {
      display: block;
      font-size: 13px;
      font-weight: 500;
      color: var(--text-secondary);
      margin-bottom: 8px;
    }

    .detail-value {
      font-size: 14px;
      color: var(--text-color);
      line-height: 1.6;
    }
  }

  .detail-meta-row {
    margin-bottom: 16px;
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

.dynamic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;

  .el-tag {
    margin: 0;
  }
  .tag-input {
    width: 120px;
  }
  .tag-add-btn {
    border-style: dashed;
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

  ::v-deep .el-textarea__inner {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
    color: var(--text-color) !important;

    &:focus {
      border-color: #667eea !important;
    }
  }

  ::v-deep .el-textarea__inner::placeholder {
    color: var(--text-muted) !important;
  }

  ::v-deep .el-input-number {
    .el-input__inner {
      background: var(--input-bg) !important;
      border-color: var(--input-border) !important;
      color: var(--text-color) !important;
    }

    .el-input-number__decrease,
    .el-input-number__increase {
      background: var(--input-bg) !important;
      border-color: var(--input-border) !important;
      color: var(--text-secondary) !important;

      &:hover {
        color: #667eea !important;
      }
    }
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

