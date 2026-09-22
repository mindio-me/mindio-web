<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="achievements-page">
    <div class="workspace-layout">
      <!-- ========== 左侧列表 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-search">
            <el-input v-model="achievementSearch" :placeholder="$t('workspace.achievements.searchPlaceholder')" prefix-icon="el-icon-search" clearable size="small" />
          </div>
        </div>
        <div class="sidebar-section sidebar-notes">
          <div class="sidebar-section-header">
            <span class="section-title">{{ $t('workspace.achievements.myAchievements') }}</span>
            <span class="section-subtitle">{{ filteredAchievements.length }}{{ $t('workspace.achievements.countSuffix') }}</span>
          </div>
          <div v-loading="loading" class="note-list-wrapper">
            <div v-if="filteredAchievements.length > 0" class="note-list">
              <div
                v-for="item in filteredAchievements"
                :key="item.id"
                class="note-list-item"
                :class="{ active: selectedAchievement && selectedAchievement.id === item.id }"
                @click="selectAchievement(item)"
              >
                <div class="note-list-title">{{ item.title }}</div>
                <div class="note-list-meta">
                  <span class="note-list-time">{{ item.type || $t('workspace.achievements.type') }}</span>
                  <el-tag v-if="item.isActive" size="mini" type="success" effect="plain">{{ $t('workspace.achievements.enabled') }}</el-tag>
                </div>
              </div>
            </div>
            <div v-else-if="!loading" class="sidebar-empty"><p>{{ $t('workspace.achievements.empty') }}</p></div>
          </div>
        </div>
      </aside>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main">
        <div v-if="selectedAchievement" class="entity-detail-wrapper">
          <!-- 详情模式 -->
          <div v-if="achievementViewMode === 'detail'" class="entity-detail-view">
            <!-- 详情头部 -->
            <div class="entity-detail-header">
              <h2 class="entity-detail-title">{{ selectedAchievement.title }}</h2>
              <div class="entity-detail-actions">
                <el-button size="small" icon="el-icon-edit" @click="achievementViewMode = 'edit'">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" plain @click="deleteAchievement">{{ $t('common.delete') }}</el-button>
              </div>
            </div>

            <!-- 详情内容 -->
            <div class="entity-detail-content">
              <!-- 副标题 -->
              <div v-if="selectedAchievement.subtitle" class="detail-field">
                <label class="detail-label">{{ $t('workspace.achievements.subtitle') }}</label>
                <div class="detail-value">{{ selectedAchievement.subtitle }}</div>
              </div>

              <!-- 描述 -->
              <div v-if="selectedAchievement.description" class="detail-field">
                <label class="detail-label">{{ $t('workspace.achievements.description') }}</label>
                <div class="wangeditor-content" v-html="selectedAchievement.description"></div>
              </div>

              <!-- 类型和图标 -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.achievements.type') }}</label>
                    <div class="detail-value">{{ selectedAchievement.type || '-' }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.achievements.icon') }}</label>
                    <div class="detail-value">
                      <i v-if="selectedAchievement.icon" :class="selectedAchievement.icon"></i>
                      <span v-else>-</span>
                    </div>
                  </div>
                </el-col>
              </el-row>

              <!-- 技术栈 -->
              <div v-if="displayTechnologies.length" class="detail-field">
                <label class="detail-label">{{ $t('workspace.achievements.technologies') }}</label>
                <div class="detail-value">
                  <el-tag
                    v-for="(tech, idx) in displayTechnologies"
                    :key="idx"
                    size="small"
                    style="margin-right: 8px; margin-bottom: 8px;"
                  >{{ tech }}</el-tag>
                </div>
              </div>

              <!-- 状态信息 -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.achievements.status') }}</label>
                    <div class="detail-value">
                      <el-tag size="small">{{ selectedAchievement.status === 'done' ? $t('workspace.achievements.statusDone') : $t('workspace.achievements.statusActive') }}</el-tag>
                    </div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.achievements.displayOrder') }}</label>
                    <div class="detail-value">{{ selectedAchievement.displayOrder || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.achievements.isActive') }}</label>
                    <div class="detail-value">
                      <el-tag :type="selectedAchievement.isActive ? 'success' : 'info'" size="small">{{ selectedAchievement.isActive ? $t('workspace.achievements.yes') : $t('workspace.achievements.no') }}</el-tag>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>

          <!-- 编辑模式 -->
          <div v-else-if="achievementViewMode === 'edit'" class="entity-form-wrapper">
            <div class="entity-form-header">
              <h2 class="entity-form-title">{{ $t('workspace.achievements.editTitle') }}</h2>
              <div class="entity-form-actions">
                <el-button size="small" @click="cancelAchievementEdit">{{ $t('common.cancel') }}</el-button>
                <el-button type="primary" size="small" :loading="achievementSaving" @click="saveAchievement">{{ $t('common.save') }}</el-button>
              </div>
            </div>
            <el-form :model="achievementForm" label-position="top" class="entity-form">
              <el-form-item :label="$t('workspace.notes.title')">
                <el-input v-model="achievementForm.title" :placeholder="$t('workspace.achievements.namePlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.achievements.subtitle')">
                <el-input v-model="achievementForm.subtitle" :placeholder="$t('workspace.achievements.subtitlePlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.achievements.description')">
                <div id="achievementRichTextEditor" class="achievement-rich-text-editor"></div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.achievements.type')">
                    <el-input v-model="achievementForm.type" :placeholder="$t('workspace.achievements.typePlaceholder')" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.achievements.status')">
                    <el-select v-model="achievementForm.status" style="width: 100%">
                      <el-option value="active" :label="$t('workspace.achievements.statusActive')" />
                      <el-option value="done" :label="$t('workspace.achievements.statusDone')" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.achievements.icon')">
                    <el-input v-model="achievementForm.icon" :placeholder="$t('workspace.achievements.iconPlaceholder')" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Icon Color">
                    <el-select v-model="achievementForm.iconVariant" style="width: 100%">
                      <el-option value="purple" label="Purple" />
                      <el-option value="green" label="Green" />
                      <el-option value="blue" label="Blue" />
                      <el-option value="orange" label="Orange" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item :label="$t('workspace.achievements.technologies')">
                <div class="dynamic-tags">
                  <el-tag
                    v-for="(tech, idx) in achievementForm.technologies"
                    :key="idx"
                    closable
                    size="small"
                    @close="achievementForm.technologies.splice(idx, 1)"
                  >{{ tech }}</el-tag>
                  <el-input
                    v-if="achievementTagInputVisible"
                    ref="achievementTagInput"
                    v-model="achievementTagInputValue"
                    size="small"
                    class="tag-input"
                    @keyup.enter.native="addAchievementTag"
                    @blur="addAchievementTag"
                  />
                  <el-button v-else size="small" class="tag-add-btn" @click="showAchievementTagInput">{{ $t('workspace.achievements.addTag') }}</el-button>
                </div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.achievements.displayOrder')">
                    <el-input-number v-model="achievementForm.displayOrder" :min="0" size="small" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.achievements.isActive')">
                    <el-switch v-model="achievementForm.isActive" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </div>
        <div v-else class="note-main-empty">
          <i class="el-icon-trophy empty-icon"></i>
          <p class="empty-text">{{ $t('workspace.achievements.selectEmpty') }}</p>
        </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside class="workspace-right">
        <div class="right-panel" v-if="selectedAchievement">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.achievements.rightPanelTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.achievements.createdAt') }}</span><span class="meta-value">{{ formatDate(selectedAchievement.createdAt) }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.achievements.modifiedAt') }}</span><span class="meta-value">{{ formatDate(selectedAchievement.modifiedAt) }}</span></div>
              <div class="right-meta-item" v-if="selectedAchievement.type"><span class="meta-label">{{ $t('workspace.achievements.type') }}</span><span class="meta-value">{{ selectedAchievement.type }}</span></div>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AchievementsPage',
  layout: 'workspace',
  data() {
    return {
      loading: false,
      achievements: [],
      selectedAchievement: null,
      achievementForm: {
        title: '',
        subtitle: '',
        description: '',
        type: '',
        status: 'active',
        icon: '',
        iconVariant: 'purple',
        technologies: [],
        displayOrder: 0,
        isActive: true
      },
      achievementEditor: null,
      achievementSaving: false,
      achievementSearch: '',
      achievementTagInputVisible: false,
      achievementTagInputValue: '',
      achievementViewMode: 'edit' // 'detail' | 'edit'
    }
  },
  watch: {
    achievementViewMode(newMode) {
      if (newMode === 'edit') {
        this.$nextTick(() => {
          if (process.client) {
            this.initAchievementEditor()
          }
        })
      }
    }
  },
  computed: {
    filteredAchievements() {
      if (!this.achievementSearch) return this.achievements
      const kw = this.achievementSearch.toLowerCase()
      return this.achievements.filter(a => (a.title || '').toLowerCase().includes(kw))
    },
    displayTechnologies() {
      if (!this.selectedAchievement?.technologies) return []
      return Array.isArray(this.selectedAchievement.technologies)
        ? this.selectedAchievement.technologies
        : this.selectedAchievement.technologies.split(',').map(t => t.trim()).filter(t => t)
    }
  },
  mounted() {
    this.loadAchievements()
    // 监听 layout 触发的创建事件
    this.$nuxt.$on('workspace:create:achievements', this.createAchievement)
  },
  beforeDestroy() {
    if (this.achievementEditor) {
      try {
        this.achievementEditor.destroy()
      } catch (e) {
        console.warn('Error destroying achievement editor:', e)
      }
      this.achievementEditor = null
    }
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:achievements', this.createAchievement)
  },
  methods: {
    async loadAchievements() {
      this.loading = true
      try {
        this.achievements = await this.$achievementService.getMyAchievements()
        // 自动选中第一个成就
        if (this.achievements.length > 0 && !this.selectedAchievement) {
          this.selectAchievement(this.achievements[0])
        }
      } catch (error) {
        this.$message.error(this.$t('workspace.achievements.loadFailed'))
        this.achievements = []
      } finally {
        this.loading = false
      }
    },
    selectAchievement(item) {
      this.selectedAchievement = item
      this.achievementViewMode = 'edit'

      // 预填充编辑表单
      this.achievementForm = {
        title: item.title || '',
        subtitle: item.subtitle || '',
        description: item.description || '',
        type: item.type || '',
        status: item.status || 'active',
        icon: item.icon || '',
        iconVariant: item.iconVariant || 'purple',
        technologies: Array.isArray(item.technologies)
          ? [...item.technologies]
          : (item.technologies ? item.technologies.split(',').map(t => t.trim()).filter(t => t) : []),
        displayOrder: item.displayOrder || 0,
        isActive: item.isActive !== false
      }

      // achievementViewMode 默认即为 'edit'，值不变时 watcher 不会触发，
      // 因此这里显式初始化编辑器（同时覆盖切换成就时刷新编辑器内容）
      if (this.achievementViewMode === 'edit') {
        this.$nextTick(() => {
          if (process.client) {
            this.initAchievementEditor()
          }
        })
      }
    },
    cancelAchievementEdit() {
      this.achievementViewMode = 'detail'
      if (this.achievementEditor) {
        try {
          this.achievementEditor.destroy()
        } catch (e) {
          console.warn('Error destroying achievement editor:', e)
        }
        this.achievementEditor = null
      }
    },
    async createAchievement() {
      try {
        const data = { title: this.$t('workspace.achievements.newAchievementTitle'), description: this.$t('workspace.achievements.newAchievementDesc'), isActive: true }
        const result = await this.$achievementService.createAchievement(data)
        await this.loadAchievements()
        const created = this.achievements.find(a => a.id === result.id) || this.achievements[0]
        if (created) this.selectAchievement(created)
        this.$message.success(this.$t('workspace.achievements.createSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.achievements.createFailed'))
      }
    },
    async saveAchievement() {
      if (!this.selectedAchievement || !this.achievementForm.title) return

      // 从编辑器获取内容
      if (this.achievementEditor) {
        this.achievementForm.description = this.achievementEditor.txt.html()
      }

      this.achievementSaving = true
      try {
        // 将技术栈数组转换为逗号分隔的字符串
        const submitData = {
          ...this.achievementForm,
          technologies: Array.isArray(this.achievementForm.technologies)
            ? this.achievementForm.technologies.join(',')
            : this.achievementForm.technologies
        }

        await this.$achievementService.updateAchievement(this.selectedAchievement.id, submitData)
        this.$message.success(this.$t('workspace.achievements.saveSuccess'))
        const idx = this.achievements.findIndex(a => a.id === this.selectedAchievement.id)
        if (idx >= 0) Object.assign(this.achievements[idx], submitData)
        this.selectedAchievement = { ...this.selectedAchievement, ...submitData }

        // 切换回详情模式
        this.achievementViewMode = 'detail'

        // 销毁编辑器
        if (this.achievementEditor) {
          try {
            this.achievementEditor.destroy()
          } catch (e) {
            console.warn('Error destroying achievement editor:', e)
          }
          this.achievementEditor = null
        }
      } catch (error) {
        this.$message.error(this.$t('workspace.achievements.saveFailed'))
      } finally {
        this.achievementSaving = false
      }
    },
    deleteAchievement() {
      if (!this.selectedAchievement) return
      this.$confirm(this.$t('workspace.achievements.deleteConfirm', { name: this.selectedAchievement.title }), this.$t('workspace.achievements.confirmTitle'), {
        confirmButtonText: this.$t('common.confirm'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning'
      }).then(async () => {
        try {
          await this.$achievementService.deleteAchievement(this.selectedAchievement.id)
          this.$message.success(this.$t('workspace.achievements.deleteSuccess'))
          this.selectedAchievement = null
          await this.loadAchievements()
        } catch (error) {
          this.$message.error(this.$t('workspace.achievements.deleteFailed'))
        }
      }).catch(() => {})
    },
    showAchievementTagInput() {
      this.achievementTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.achievementTagInput) {
          this.$refs.achievementTagInput.focus()
        }
      })
    },
    addAchievementTag() {
      const val = this.achievementTagInputValue.trim()
      if (val && !this.achievementForm.technologies.includes(val)) {
        this.achievementForm.technologies.push(val)
      }
      this.achievementTagInputVisible = false
      this.achievementTagInputValue = ''
    },
    initAchievementEditor() {
      if (this.achievementViewMode !== 'edit') return

      if (this.achievementEditor) {
        try {
          this.achievementEditor.destroy()
        } catch (e) {
          console.warn('Error destroying editor:', e)
        }
        this.achievementEditor = null
      }

      if (!process.client) return

      this.$nextTick(() => {
        this.$nextTick(() => {
          const editorContainer = document.getElementById('achievementRichTextEditor')
          if (!editorContainer) {
            console.warn('Editor container not found')
            return
          }

          import('wangeditor').then((WangEditor) => {
            const E = WangEditor.default || WangEditor
            this.achievementEditor = new E('#achievementRichTextEditor')
            this.achievementEditor.config.placeholder = this.$t('workspace.achievements.editorPlaceholder')
            this.achievementEditor.config.zIndex = 1000
            this.achievementEditor.config.height = 400
            this.achievementEditor.config.onchange = (html) => {
              this.achievementForm.description = html
            }
            this.achievementEditor.create()
            if (this.achievementForm.description) {
              this.achievementEditor.txt.html(this.achievementForm.description)
            }
            setTimeout(() => {
              if (this.achievementEditor && this.achievementEditor.txt) {
                try {
                  this.achievementEditor.txt.focus()
                } catch (e) {
                  // 忽略焦点错误
                }
              }
            }, 100)
          }).catch((error) => {
            console.error('Failed to load wangeditor:', error)
          })
        })
      })
    },
    formatDate(time) {
      if (!time) return '-'
      return new Date(time).toLocaleDateString()
    }
  }
}
</script>

<style scoped lang="scss">
.achievements-page {
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
  // border: 1px solid var(--border-color);
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
  // border-radius: 8px;
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
    // border: 1px solid #667eea;
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
  // border: 1px solid var(--border-color);
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

.achievement-rich-text-editor {
  border: 1px solid var(--input-border);
  border-radius: 4px;
  background: var(--input-bg);
  position: relative;
  z-index: 1;

  .w-e-text-container {
    min-height: 400px;
  }

  .w-e-toolbar,
  .w-e-text-container {
    position: relative;
    z-index: 1;
  }

  ::v-deep .w-e-toolbar {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
  }

  ::v-deep .w-e-text-container {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
  }

  ::v-deep .w-e-text {
    background: var(--input-bg) !important;
    color: var(--text-color) !important;
  }

  ::v-deep .w-e-text p,
  ::v-deep .w-e-text div,
  ::v-deep .w-e-text span,
  ::v-deep .w-e-text-container p,
  ::v-deep .w-e-text-container div,
  ::v-deep .w-e-text-container span {
    color: var(--text-color) !important;
  }

  /* 暗色模式：覆盖 WangEditor CSS 中的浅色背景 */
  ::v-deep .w-e-text blockquote {
    background-color: var(--bg-tertiary) !important;
    border-left-color: #4a6fa5;
    color: var(--text-secondary);
  }
  ::v-deep .w-e-text code {
    background-color: var(--bg-tertiary) !important;
    color: var(--text-color);
  }
  ::v-deep .w-e-text table th {
    background-color: var(--bg-tertiary) !important;
  }
  ::v-deep .w-e-text table,
  ::v-deep .w-e-text table td,
  ::v-deep .w-e-text table th {
    border-color: var(--border-color) !important;
  }

  ::v-deep .w-e-text-container .placeholder {
    color: var(--text-muted) !important;
  }

  ::v-deep .w-e-toolbar .w-e-menu {
    color: var(--text-secondary);

    &:hover {
      background: var(--bg-secondary);
    }
  }

  ::v-deep .w-e-menu i {
    color: var(--text-secondary);
  }
}

.workspace-right {
  background: var(--card-bg-color);
  // border: 1px solid var(--border-color);
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
