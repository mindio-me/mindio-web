<template>
  <div class="projects-page">
    <div class="workspace-layout" :class="{ 'right-collapsed': rightPanelCollapsed }">
      <!-- ========== 左侧列表 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-section">
          <div class="sidebar-search">
            <el-input v-model="projectSearch" :placeholder="$t('workspace.projects.searchPlaceholder')" prefix-icon="el-icon-search" clearable size="small" />
          </div>
        </div>
        <div class="sidebar-section sidebar-notes">
          <div class="sidebar-section-header">
            <span class="section-title">{{ $t('workspace.projects.myProjects') }}</span>
            <span class="section-subtitle">{{ filteredProjects.length }}{{ $t('workspace.projects.countSuffix') }}</span>
          </div>
          <div v-loading="loading" class="note-list-wrapper">
            <div v-if="filteredProjects.length > 0" class="note-list">
              <div
                v-for="item in filteredProjects"
                :key="item.id"
                class="note-list-item"
                :class="{ active: selectedProject && selectedProject.id === item.id }"
                @click="selectProject(item)"
              >
                <div class="note-list-title">{{ item.name }}</div>
                <div class="note-list-meta">
                  <span class="note-list-time">{{ item.category || $t('workspace.projects.uncategorized') }}</span>
                  <el-tag v-if="item.isFeatured" size="mini" type="warning" effect="plain">{{ $t('workspace.projects.featured') }}</el-tag>
                </div>
              </div>
            </div>
            <div v-else-if="!loading" class="sidebar-empty"><p>{{ $t('workspace.projects.empty') }}</p></div>
          </div>
        </div>
      </aside>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main" :class="{ 'workspace-main--fullscreen': isFullscreen }">
        <div v-if="selectedProject" class="entity-detail-wrapper">
          <!-- 详情模式 -->
          <div v-if="projectViewMode === 'detail'" class="entity-detail-view">
            <!-- 详情头部 -->
            <div class="entity-detail-header">
              <h2 class="entity-detail-title">{{ selectedProject.name }}</h2>
              <div class="entity-detail-actions">
                <el-button size="small" icon="el-icon-edit" @click="projectViewMode = 'edit'">{{ $t('common.edit') }}</el-button>
                <el-button v-if="!isFullscreen" size="small" icon="el-icon-full-screen" @click="isFullscreen = true">{{ $t('workspace.notes.fullscreen') }}</el-button>
                <el-button v-if="isFullscreen" size="small" type="warning" icon="el-icon-close" @click="isFullscreen = false">{{ $t('workspace.notes.exitFullscreen') }}</el-button>
                <button
                  v-if="!isFullscreen"
                  class="panel-toggle-btn"
                  :title="rightPanelCollapsed ? $t('workspace.projects.expandPanel') : $t('workspace.projects.collapsePanel')"
                  @click="rightPanelCollapsed = !rightPanelCollapsed"
                >
                  <i :class="rightPanelCollapsed ? 'el-icon-d-arrow-left' : 'el-icon-d-arrow-right'"></i>
                </button>
                <el-button size="small" type="danger" plain @click="deleteProject">{{ $t('common.delete') }}</el-button>
              </div>
            </div>

            <!-- 详情内容 -->
            <div class="entity-detail-content">
              <!-- 简称 -->
              <div v-if="selectedProject.shortName" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.shortName') }}</label>
                <div class="detail-value">{{ selectedProject.shortName }}</div>
              </div>

              <!-- 副标题 -->
              <div v-if="selectedProject.subtitle" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.subtitle') }}</label>
                <div class="detail-value">{{ selectedProject.subtitle }}</div>
              </div>

              <!-- 描述 -->
              <div v-if="selectedProject.description" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.description') }}</label>
                <div class="detail-value">{{ selectedProject.description }}</div>
              </div>

              <!-- 正文内容（富文本或 Markdown） -->
              <div v-if="selectedProject.content" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.content') }}</label>
                <div
                  v-if="selectedProject.contentType === 'markdown'"
                  class="markdown-content-display"
                  v-mermaid
                  v-html="renderMarkdown(selectedProject.content)"
                ></div>
                <div
                  v-else
                  class="wangeditor-content"
                  v-html="selectedProject.content"
                ></div>
              </div>

              <!-- 基本信息（两列） -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.category') }}</label>
                    <div class="detail-value">{{ selectedProject.category || '-' }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.icon') }}</label>
                    <div class="detail-value">
                      <i v-if="selectedProject.icon" :class="selectedProject.icon"></i>
                      <span v-else>-</span>
                    </div>
                  </div>
                </el-col>
              </el-row>

              <!-- 链接（两列） -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.projectUrl') }}</label>
                    <div class="detail-value">
                      <a v-if="selectedProject.projectUrl" :href="selectedProject.projectUrl" target="_blank" rel="noopener">{{ selectedProject.projectUrl }}</a>
                      <span v-else>-</span>
                    </div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="detail-field">
                    <label class="detail-label">GitHub</label>
                    <div class="detail-value">
                      <a v-if="selectedProject.githubUrl" :href="selectedProject.githubUrl" target="_blank" rel="noopener">{{ selectedProject.githubUrl }}</a>
                      <span v-else>-</span>
                    </div>
                  </div>
                </el-col>
              </el-row>

              <!-- 封面图片 -->
              <div v-if="selectedProject.imageUrl" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.coverImage') }}</label>
                <div class="detail-value">
                  <img :src="selectedProject.imageUrl" alt="cover" style="max-width: 400px; border-radius: 8px;" />
                </div>
              </div>

              <!-- 技术栈 -->
              <div v-if="displayTechnologies.length" class="detail-field">
                <label class="detail-label">{{ $t('workspace.projects.technologies') }}</label>
                <div class="detail-value">
                  <el-tag v-for="(tech, idx) in displayTechnologies" :key="idx" size="small" style="margin-right: 8px; margin-bottom: 8px;">{{ tech }}</el-tag>
                </div>
              </div>

              <!-- 状态信息（三列） -->
              <el-row :gutter="16" class="detail-meta-row">
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.displayOrder') }}</label>
                    <div class="detail-value">{{ selectedProject.displayOrder || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.publicField') }}</label>
                    <div class="detail-value">
                      <el-tag :type="selectedProject.isPublic ? 'success' : 'info'" size="small">{{ selectedProject.isPublic ? $t('workspace.projects.yes') : $t('workspace.projects.no') }}</el-tag>
                    </div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="detail-field">
                    <label class="detail-label">{{ $t('workspace.projects.isFeatured') }}</label>
                    <div class="detail-value">
                      <el-tag :type="selectedProject.isFeatured ? 'warning' : 'info'" size="small">{{ selectedProject.isFeatured ? $t('workspace.projects.yes') : $t('workspace.projects.no') }}</el-tag>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>

          <!-- 编辑模式 -->
          <div v-else-if="projectViewMode === 'edit'" class="entity-form-wrapper">
            <div class="entity-form-header">
              <h2 class="entity-form-title">{{ $t('workspace.projects.editTitle') }}</h2>
              <div class="entity-form-actions">
                <el-button size="small" @click="cancelProjectEdit">{{ $t('common.cancel') }}</el-button>
                <el-button type="primary" size="small" :loading="projectSaving" @click="saveProject">{{ $t('common.save') }}</el-button>
              </div>
            </div>
            <el-form :model="projectForm" label-position="top" class="entity-form">
              <el-row :gutter="16">
                <el-col :span="16">
                  <el-form-item :label="$t('workspace.notes.title')">
                    <el-input v-model="projectForm.name" :placeholder="$t('workspace.projects.namePlaceholder')" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.projects.shortName')">
                    <el-input v-model="projectForm.shortName" :placeholder="$t('workspace.projects.shortNamePlaceholder')" maxlength="20" show-word-limit />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item :label="$t('workspace.projects.subtitle')">
                <el-input v-model="projectForm.subtitle" :placeholder="$t('workspace.projects.subtitlePlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.projects.description')">
                <el-input v-model="projectForm.description" type="textarea" :rows="3" :placeholder="$t('workspace.projects.descriptionPlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('workspace.projects.content')">
                <div id="projectRichTextEditor" class="project-rich-text-editor"></div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.projects.category')">
                    <el-input v-model="projectForm.category" :placeholder="$t('workspace.projects.categoryPlaceholder')" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.projects.icon')">
                    <el-input v-model="projectForm.icon" :placeholder="$t('workspace.projects.iconPlaceholder')" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item :label="$t('workspace.projects.coverImageLabel')">
                <el-input v-model="projectForm.imageUrl" placeholder="https://..." />
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.projects.projectUrl')">
                    <el-input v-model="projectForm.projectUrl" placeholder="https://..." />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item :label="$t('workspace.projects.githubLink')">
                    <el-input v-model="projectForm.githubUrl" placeholder="https://github.com/..." />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item :label="$t('workspace.projects.technologies')">
                <div class="dynamic-tags">
                  <el-tag v-for="(tech, idx) in projectForm.technologies" :key="idx" closable size="small" @close="projectForm.technologies.splice(idx, 1)">{{ tech }}</el-tag>
                  <el-input v-if="projectTagInputVisible" ref="projectTagInput" v-model="projectTagInputValue" size="small" class="tag-input" @keyup.enter.native="addProjectTag" @blur="addProjectTag" />
                  <el-button v-else size="small" class="tag-add-btn" @click="showProjectTagInput">{{ $t('workspace.projects.addTag') }}</el-button>
                </div>
              </el-form-item>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.projects.displayOrder')">
                    <el-input-number v-model="projectForm.displayOrder" :min="0" size="small" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.projects.publicField')">
                    <el-switch v-model="projectForm.isPublic" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="$t('workspace.projects.isFeatured')">
                    <el-switch v-model="projectForm.isFeatured" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </div>
        <div v-else class="note-main-empty">
          <i class="el-icon-folder-opened empty-icon"></i>
          <p class="empty-text">{{ $t('workspace.projects.selectEmpty') }}</p>
        </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside v-show="!rightPanelCollapsed" class="workspace-right">
        <div class="right-panel" v-if="selectedProject">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.projects.rightPanelTitle') }}</h3>
            <div class="right-meta-list">
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.projects.createdAt') }}</span><span class="meta-value">{{ formatDate(selectedProject.createdAt) }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('workspace.projects.modifiedAt') }}</span><span class="meta-value">{{ formatDate(selectedProject.modifiedAt) }}</span></div>
              <div class="right-meta-item" v-if="selectedProject.category"><span class="meta-label">{{ $t('workspace.projects.category') }}</span><span class="meta-value">{{ selectedProject.category }}</span></div>
              <div class="right-meta-item"><span class="meta-label">{{ $t('common.view') }}</span><span class="meta-value">{{ selectedProject.isPublic ? $t('workspace.projects.statusPublic') : $t('workspace.projects.statusPrivate') }}</span></div>
            </div>
          </div>
        </div>
      </aside>
    </div>

  </div>
</template>

<script>
import { renderMarkdown as renderMd } from '~/utils/markdown'

export default {
  name: 'ProjectsPage',
  layout: 'workspace',
  data() {
    return {
      loading: false,
      projects: [],
      selectedProject: null,
      projectForm: {
        name: '',
        shortName: '',
        subtitle: '',
        description: '',
        icon: '',
        imageUrl: '',
        projectUrl: '',
        githubUrl: '',
        category: '',
        technologies: [],
        content: '',
        contentType: 'richtext',
        isPublic: true,
        isFeatured: false,
        displayOrder: 0
      },
      projectEditor: null,
      projectSaving: false,
      projectSearch: '',
      projectTagInputVisible: false,
      projectTagInputValue: '',
      projectViewMode: 'detail', // 'detail' | 'edit'

      // 布局控制
      rightPanelCollapsed: false,
      isFullscreen: false
    }
  },
  computed: {
    filteredProjects() {
      if (!this.projectSearch) return this.projects
      const kw = this.projectSearch.toLowerCase()
      return this.projects.filter(p => (p.name || '').toLowerCase().includes(kw))
    },
    displayTechnologies() {
      if (!this.selectedProject?.technologies) return []
      return Array.isArray(this.selectedProject.technologies)
        ? this.selectedProject.technologies
        : this.selectedProject.technologies.split(',').map(t => t.trim()).filter(t => t)
    }
  },
  watch: {
    projectViewMode(newMode) {
      if (newMode === 'edit') {
        this.$nextTick(() => {
          if (process.client) {
            this.initProjectEditor()
          }
        })
      }
    }
  },
  mounted() {
    this.loadProjects()
    // 监听 layout 触发的创建事件
    this.$nuxt.$on('workspace:create:projects', this.createProject)
    // ESC 退出全屏
    this._onEsc = (e) => { if (e.key === 'Escape' && this.isFullscreen) this.isFullscreen = false }
    document.addEventListener('keydown', this._onEsc)
  },
  beforeDestroy() {
    if (this.projectEditor) {
      try {
        this.projectEditor.destroy()
      } catch (e) {
        console.warn('Error destroying project editor:', e)
      }
      this.projectEditor = null
    }
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:projects', this.createProject)
    if (this._onEsc) document.removeEventListener('keydown', this._onEsc)
  },
  methods: {
    async loadProjects() {
      this.loading = true
      try {
        this.projects = await this.$projectService.getMyProjects()
        // 自动选中第一个项目
        if (this.projects.length > 0 && !this.selectedProject) {
          this.selectProject(this.projects[0])
        }
      } catch (error) {
        this.$message.error(this.$t('workspace.projects.loadFailed'))
        this.projects = []
      } finally {
        this.loading = false
      }
    },
    selectProject(item) {
      this.selectedProject = item
      this.projectViewMode = 'detail'

      // 预填充编辑表单
      this.projectForm = {
        name: item.name || '',
        shortName: item.shortName || '',
        subtitle: item.subtitle || '',
        description: item.description || '',
        icon: item.icon || '',
        imageUrl: item.imageUrl || '',
        projectUrl: item.projectUrl || '',
        githubUrl: item.githubUrl || '',
        category: item.category || '',
        technologies: Array.isArray(item.technologies)
          ? [...item.technologies]
          : (item.technologies ? item.technologies.split(',').map(t => t.trim()).filter(t => t) : []),
        content: item.content || '',
        contentType: item.contentType || 'richtext',
        isPublic: item.isPublic !== false,
        isFeatured: item.isFeatured || false,
        displayOrder: item.displayOrder || 0
      }
    },
    cancelProjectEdit() {
      this.projectViewMode = 'detail'
      if (this.projectEditor) {
        try {
          this.projectEditor.destroy()
        } catch (e) {
          console.warn('Error destroying project editor:', e)
        }
        this.projectEditor = null
      }
    },
    async createProject() {
      try {
        const data = { name: this.$t('workspace.projects.newProjectName'), description: this.$t('workspace.projects.newProjectDesc'), isPublic: true }
        const result = await this.$projectService.createProject(data)
        await this.loadProjects()
        const created = this.projects.find(p => p.id === result.id) || this.projects[0]
        if (created) this.selectProject(created)
        this.$message.success(this.$t('workspace.projects.createSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.projects.createFailed'))
      }
    },
    async saveProject() {
      if (!this.selectedProject || !this.projectForm.name) return

      // 从编辑器获取内容
      if (this.projectEditor && this.projectForm.contentType === 'richtext') {
        this.projectForm.content = this.projectEditor.txt.html()
      }

      this.projectSaving = true
      try {
        // 将技术栈数组转换为逗号分隔的字符串
        const submitData = {
          ...this.projectForm,
          technologies: Array.isArray(this.projectForm.technologies)
            ? this.projectForm.technologies.join(',')
            : this.projectForm.technologies
        }

        await this.$projectService.updateProject(this.selectedProject.id, submitData)
        this.$message.success(this.$t('workspace.projects.saveSuccess'))

        // 更新本地列表
        const idx = this.projects.findIndex(p => p.id === this.selectedProject.id)
        if (idx >= 0) Object.assign(this.projects[idx], submitData)

        // 更新选中对象
        this.selectedProject = { ...this.selectedProject, ...submitData }

        // 切换回详情模式
        this.projectViewMode = 'detail'

        // 销毁编辑器
        if (this.projectEditor) {
          try {
            this.projectEditor.destroy()
          } catch (e) {
            console.warn('Error destroying project editor:', e)
          }
          this.projectEditor = null
        }
      } catch (error) {
        this.$message.error(this.$t('workspace.projects.saveFailed'))
      } finally {
        this.projectSaving = false
      }
    },
    deleteProject() {
      if (!this.selectedProject) return
      this.$confirm(this.$t('workspace.projects.deleteConfirm', { name: this.selectedProject.name }), this.$t('workspace.projects.confirmTitle'), {
        confirmButtonText: this.$t('common.confirm'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning'
      }).then(async () => {
        try {
          await this.$projectService.deleteProject(this.selectedProject.id)
          this.$message.success(this.$t('workspace.projects.deleteSuccess'))
          this.selectedProject = null
          await this.loadProjects()
        } catch (error) {
          this.$message.error(this.$t('workspace.projects.deleteFailed'))
        }
      }).catch(() => {})
    },
    showProjectTagInput() {
      this.projectTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.projectTagInput) {
          this.$refs.projectTagInput.focus()
        }
      })
    },
    addProjectTag() {
      const val = this.projectTagInputValue.trim()
      if (val && !this.projectForm.technologies.includes(val)) {
        this.projectForm.technologies.push(val)
      }
      this.projectTagInputVisible = false
      this.projectTagInputValue = ''
    },
    initProjectEditor() {
      if (this.projectViewMode !== 'edit') return

      if (this.projectEditor) {
        try {
          this.projectEditor.destroy()
        } catch (e) {
          console.warn('Error destroying editor:', e)
        }
        this.projectEditor = null
      }

      if (!process.client) return

      this.$nextTick(() => {
        this.$nextTick(() => {
          const editorContainer = document.getElementById('projectRichTextEditor')
          if (!editorContainer) {
            console.warn('Editor container not found')
            return
          }

          import('wangeditor').then((WangEditor) => {
            const E = WangEditor.default || WangEditor
            this.projectEditor = new E('#projectRichTextEditor')
            this.projectEditor.config.placeholder = this.$t('workspace.projects.editorPlaceholder')
            this.projectEditor.config.zIndex = 1000
            this.projectEditor.config.height = 500
            this.projectEditor.config.onchange = (html) => {
              this.projectForm.content = html
            }
            this.projectEditor.create()
            if (this.projectForm.content) {
              this.projectEditor.txt.html(this.projectForm.content)
            }
            setTimeout(() => {
              if (this.projectEditor && this.projectEditor.txt) {
                try {
                  this.projectEditor.txt.focus()
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
    },
    /**
     * 渲染 Markdown 内容
     */
    renderMarkdown(markdown) {
      return renderMd(markdown, { axiosBaseURL: this.$axios?.defaults?.baseURL || '' })
    }
  }
}
</script>

<style scoped lang="scss">
.projects-page {
  background: transparent;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1.5fr) 260px;
  gap: 16px;
  height: calc(100vh - 110px);
  transition: grid-template-columns 0.3s ease;

  &.right-collapsed {
    grid-template-columns: 280px minmax(0, 1fr);
  }
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

      a {
        color: #667eea;
        text-decoration: none;

        &:hover {
          text-decoration: underline;
        }
      }
    }
  }

  .detail-meta-row {
    margin-bottom: 16px;
  }

  .markdown-content-display {
    padding: 12px;
    border: 1px solid var(--border-color);
    border-radius: 4px;
    background: var(--bg-secondary);
    min-height: 100px;
    font-size: 15px;
    color: var(--text-color);
    line-height: 1.8;

    h1, h2, h3, h4 {
      margin: 1.2em 0 0.6em;
      font-weight: 600;
      color: var(--text-color);
      &:first-child { margin-top: 0; }
    }
    h1 { 
      font-size: 1.8em; 
      border-bottom: 1px solid var(--border-color); 
      padding-bottom: 0.3em; 
    }
    h2 { 
      font-size: 1.5em; 
      border-bottom: 1px solid var(--border-color); 
      padding-bottom: 0.3em; 
    }
    h3 { font-size: 1.25em; }
    h4 { font-size: 1.1em; }

    blockquote {
      margin: 1em 0;
      padding: 0.5em 1em;
      border-left: 4px solid var(--primary-color, #667eea);
      background: var(--bg-tertiary, rgba(102, 126, 234, 0.1));
      color: var(--text-secondary);
    }

    pre.md-code-block {
      margin: 1em 0;
      padding: 1em;
      background: var(--bg-tertiary, #1e293b);
      border-radius: 6px;
      overflow-x: auto;
      code {
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 13px;
        color: var(--text-color);
      }
    }

    code.md-inline-code {
      padding: 0.2em 0.4em;
      background: var(--bg-tertiary, rgba(0, 0, 0, 0.1));
      border-radius: 4px;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.9em;
      color: var(--primary-color, #667eea);
    }

    a {
      color: var(--primary-color, #667eea);
      text-decoration: none;
      &:hover { text-decoration: underline; }
    }

    img.md-image {
      max-width: 100%;
      height: auto;
      border-radius: 4px;
      margin: 1em 0;
    }

    ul {
      margin: 1em 0;
      padding-left: 2em;
      li { margin: 0.3em 0; }
    }

    hr.md-hr {
      margin: 1.5em 0;
      border: none;
      border-top: 1px solid var(--border-color);
    }

    strong { font-weight: 600; }
    em { font-style: italic; }

    p {
      margin: 0.5em 0;
    }
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

.project-rich-text-editor {
  // min-height: 400px;
  border: 1px solid var(--input-border);
  border-radius: 4px;
  background: var(--input-bg);
  position: relative;
  z-index: 1;
}

.project-rich-text-editor .w-e-text-container {
  min-height: 400px;
}

.project-rich-text-editor .w-e-toolbar,
.project-rich-text-editor .w-e-text-container {
  position: relative;
  z-index: 1;
}

.project-rich-text-editor {
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

// 折叠/展开按钮
.panel-toggle-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.2s;
  vertical-align: middle;

  i { font-size: 14px; }

  &:hover {
    background: var(--bg-secondary);
    color: var(--text-color);
  }
}

// 全屏模式：让 workspace-main 直接覆盖整个视口
.workspace-main--fullscreen {
  position: fixed !important;
  inset: 0;
  z-index: 2000;
  background: var(--bg-color);
  border-radius: 0 !important;
  border: none !important;
  overflow-y: auto;
  padding: 16px 32px;
}
</style>

