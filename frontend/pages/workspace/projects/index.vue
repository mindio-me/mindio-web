<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="projects-page">
    <div
      class="workspace-layout"
      :class="{ 'right-collapsed': rightPanelCollapsed, 'col-resizing': wsColResizing }"
      :style="wsLayoutStyle"
    >
      <!-- ========== 左侧列表 ========== -->
      <aside v-show="!leftPanelCollapsed" class="workspace-sidebar">
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

      <div v-show="!wsIsNarrow && !leftPanelCollapsed" class="col-resizer" @pointerdown="wsStartResize('left', $event)"></div>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main" :class="{ 'workspace-main--fullscreen': isFullscreen }">
        <PanelCollapseToggle
          v-if="!isFullscreen"
          side="left"
          :collapsed="leftPanelCollapsed"
          :expand-title="$t('workspace.projects.expandSidebar')"
          :collapse-title="$t('workspace.projects.collapseSidebar')"
          @toggle="leftPanelCollapsed = !leftPanelCollapsed"
        />
        <div v-if="selectedProject" class="entity-form-wrapper">
          <div class="project-cover" :class="{ 'is-empty': !projectForm.imageUrl }" @click="triggerCoverUpload">
            <img v-if="projectForm.imageUrl" :src="projectForm.imageUrl" alt="cover" class="project-cover-img" />
            <i v-else class="el-icon-plus project-cover-upload-icon"></i>
            <span class="project-cover-hint">
              {{ selectedProject.id ? (projectForm.imageUrl ? $t('workspace.projects.coverUploadChange') : $t('workspace.projects.coverUploadEmpty')) : $t('workspace.projects.coverUploadNeedSaveFirst') }}
            </span>
          </div>
          <input ref="coverFileInput" type="file" accept="image/*" style="display:none" @change="handleCoverFileChange" />

          <div class="project-icon-block" :title="$t('workspace.projects.iconEditHint')">
            <i v-if="projectForm.icon" :class="projectForm.icon"></i>
            <el-popover placement="bottom" width="240" trigger="click">
              <el-input v-model="projectForm.icon" :placeholder="$t('workspace.projects.iconPlaceholder')" size="small" />
              <i slot="reference" class="el-icon-edit project-icon-edit-trigger"></i>
            </el-popover>
          </div>

          <div class="project-title-block">
            <input
              type="text"
              class="project-title-input"
              v-model="projectForm.name"
              :placeholder="$t('workspace.projects.namePlaceholder')"
            />
            <input
              type="text"
              class="project-subtitle-input"
              v-model="projectForm.subtitle"
              :placeholder="$t('workspace.projects.subtitlePlaceholder')"
            />
          </div>

          <div v-if="projectForm.highlightMetric || highlightBannerEditing" class="project-highlight-banner">
            <i class="el-icon-star-on"></i>
            <el-input
              v-model="projectForm.highlightMetric"
              size="small"
              :placeholder="$t('workspace.projects.highlightMetricPlaceholder')"
              @blur="highlightBannerEditing = false"
            />
          </div>
          <el-button
            v-else
            size="mini"
            type="text"
            class="project-highlight-add-btn"
            @click="highlightBannerEditing = true"
          >+ {{ $t('workspace.projects.highlightMetricEn') }}</el-button>

          <div class="entity-form-header">
            <h2 class="entity-form-title">{{ selectedProject.name }}</h2>
            <div class="entity-form-actions">
              <span class="save-status" :class="saveStatus.icon === 'el-icon-warning' ? 'is-error' : ''">
                <i :class="saveStatus.icon"></i>{{ saveStatus.text }}
              </span>
              <el-button v-if="!isFullscreen" size="small" icon="el-icon-full-screen" @click="isFullscreen = true">{{ $t('workspace.notes.fullscreen') }}</el-button>
              <el-button v-if="isFullscreen" size="small" type="warning" icon="el-icon-close" @click="isFullscreen = false">{{ $t('workspace.notes.exitFullscreen') }}</el-button>
              <el-button size="small" type="danger" plain @click="deleteProject">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
            <div class="project-properties">
              <div class="properties-summary" @click="propertiesExpanded = !propertiesExpanded">
                <el-tag size="mini" effect="plain">{{ projectForm.category || $t('workspace.projects.category') }}</el-tag>
                <el-tag size="mini" :type="projectForm.isPublic ? 'success' : 'info'" effect="plain">{{ projectForm.isPublic ? $t('workspace.projects.statusPublic') : $t('workspace.projects.statusPrivate') }}</el-tag>
                <el-tag v-if="projectForm.isFeatured" size="mini" type="warning" effect="plain">{{ $t('workspace.projects.isFeatured') }}</el-tag>
                <i :class="propertiesExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" class="properties-toggle-icon"></i>
              </div>
              <div v-if="propertiesExpanded" class="properties-expanded">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item :label="$t('workspace.projects.category')">
                      <el-input v-model="projectForm.category" :placeholder="$t('workspace.projects.categoryPlaceholder')" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item :label="$t('workspace.projects.shortName')">
                      <el-input v-model="projectForm.shortName" :placeholder="$t('workspace.projects.shortNamePlaceholder')" maxlength="20" show-word-limit />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item :label="$t('workspace.projects.nameZh')">
                      <el-input v-model="projectForm.nameZh" :placeholder="$t('workspace.projects.nameZhPlaceholder')" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item :label="$t('workspace.projects.subtitleZh')">
                      <el-input v-model="projectForm.subtitleZh" :placeholder="$t('workspace.projects.subtitleZhPlaceholder')" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item :label="$t('workspace.projects.highlightMetricZh')">
                  <el-input v-model="projectForm.highlightMetricZh" :placeholder="$t('workspace.projects.highlightMetricZhPlaceholder')" maxlength="300" show-word-limit />
                </el-form-item>
                <el-form-item :label="$t('workspace.projects.descriptionEn')">
                  <el-input v-model="projectForm.description" type="textarea" :rows="3" :placeholder="$t('workspace.projects.descriptionPlaceholder')" />
                </el-form-item>
                <el-form-item :label="$t('workspace.projects.descriptionZh')">
                  <el-input v-model="projectForm.descriptionZh" type="textarea" :rows="3" :placeholder="$t('workspace.projects.descriptionZhPlaceholder')" />
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
                <el-form-item :label="$t('workspace.projects.technologiesEn')">
                  <div class="dynamic-tags">
                    <el-tag v-for="(tech, idx) in projectForm.technologies" :key="idx" closable size="small" @close="projectForm.technologies.splice(idx, 1)">{{ tech }}</el-tag>
                    <el-input v-if="projectTagInputVisible" ref="projectTagInput" v-model="projectTagInputValue" size="small" class="tag-input" @keyup.enter.native="addProjectTag" @blur="addProjectTag" />
                    <el-button v-else size="small" class="tag-add-btn" @click="showProjectTagInput">{{ $t('workspace.projects.addTag') }}</el-button>
                  </div>
                </el-form-item>
                <el-form-item :label="$t('workspace.projects.technologiesZh')">
                  <div class="dynamic-tags">
                    <el-tag v-for="(tech, idx) in projectForm.technologiesZh" :key="idx" closable size="small" @close="projectForm.technologiesZh.splice(idx, 1)">{{ tech }}</el-tag>
                    <el-input v-if="projectZhTagInputVisible" ref="projectZhTagInput" v-model="projectZhTagInputValue" size="small" class="tag-input" @keyup.enter.native="addProjectZhTag" @blur="addProjectZhTag" />
                    <el-button v-else size="small" class="tag-add-btn" @click="showProjectZhTagInput">{{ $t('workspace.projects.addTag') }}</el-button>
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
                      <el-switch v-model="projectForm.isPublic" @change="saveFieldsImmediately" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item :label="$t('workspace.projects.isFeatured')">
                      <el-switch v-model="projectForm.isFeatured" @change="saveFieldsImmediately" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>
            </div>
            <el-form :model="projectForm" label-position="top" class="entity-form">
              <el-form-item :label="$t('workspace.projects.content')">
                <div ref="projectEditorContainer" class="editorjs-workspace-container project-editorjs-container"></div>
              </el-form-item>
            </el-form>
        </div>
        <div v-else class="note-main-empty">
          <i class="el-icon-folder-opened empty-icon"></i>
          <p class="empty-text">{{ $t('workspace.projects.selectEmpty') }}</p>
        </div>
        <PanelCollapseToggle
          v-if="!isFullscreen"
          side="right"
          :collapsed="rightPanelCollapsed"
          :expand-title="$t('workspace.projects.expandPanel')"
          :collapse-title="$t('workspace.projects.collapsePanel')"
          @toggle="rightPanelCollapsed = !rightPanelCollapsed"
        />
      </main>

      <div v-show="!wsIsNarrow && !rightPanelCollapsed" class="col-resizer" @pointerdown="wsStartResize('right', $event)"></div>

      <!-- ========== 右侧信息 ========== -->
      <aside v-show="!rightPanelCollapsed" class="workspace-right" :class="{ 'workspace-right--ai': aiPanelDocked }">
        <ChatPanel v-if="aiPanelDocked" @close="aiPanelActive = false" />
        <div class="right-panel" v-if="!aiPanelDocked && selectedProject">
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
import workspaceLayoutResize from '~/mixins/workspaceLayoutResize'
import workspaceAiDock from '~/mixins/workspaceAiDock'
import editorjsHost from '~/mixins/editorjsHost'

export default {
  name: 'ProjectsPage',
  layout: 'workspace',
  mixins: [workspaceLayoutResize, workspaceAiDock, editorjsHost],
  data() {
    return {
      loading: false,
      projects: [],
      selectedProject: null,
      projectForm: {
        name: '',
        nameZh: '',
        shortName: '',
        subtitle: '',
        subtitleZh: '',
        highlightMetric: '',
        highlightMetricZh: '',
        description: '',
        descriptionZh: '',
        icon: '',
        imageUrl: '',
        projectUrl: '',
        githubUrl: '',
        category: '',
        technologies: [],
        technologiesZh: [],
        content: '',
        contentType: 'richtext',
        isPublic: true,
        isFeatured: false,
        displayOrder: 0
      },
      projectSearch: '',
      projectTagInputVisible: false,
      projectTagInputValue: '',
      projectZhTagInputVisible: false,
      projectZhTagInputValue: '',
      saveStatus: { icon: 'el-icon-check', text: '' },
      saveTimeout: null,
      hasUnsavedChanges: false,
      _suppressAutosave: false,
      highlightBannerEditing: false,
      propertiesExpanded: false,

      // 布局控制
      leftPanelCollapsed: false,
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
    },
    displayTechnologiesZh() {
      if (!this.selectedProject?.technologiesZh) return []
      return Array.isArray(this.selectedProject.technologiesZh)
        ? this.selectedProject.technologiesZh
        : this.selectedProject.technologiesZh.split(',').map(t => t.trim()).filter(t => t)
    }
  },
  watch: {
    projectForm: {
      deep: true,
      handler() {
        if (this._suppressAutosave || !this.selectedProject) return
        this.hasUnsavedChanges = true
        this.updateSaveStatus('saving')
        this.debouncedSave()
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
    this.destroyEditorJsHost()
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:projects', this.createProject)
    if (this._onEsc) document.removeEventListener('keydown', this._onEsc)
  },
  methods: {
    wsLayoutOptions() {
      return { storageKey: 'mindio:workspace:projects:colWidths', hasRight: true }
    },
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
    async selectProject(item) {
      // 如果上一个项目还有未落盘的防抖改动（2 秒窗口内就切走了），先同步存掉，
      // 不然 selectedProject/projectForm 一旦被下面的赋值替换成新项目，原来挂起的
      // debouncedSave() 定时器触发时存的就是新项目自己未改动的数据，上一个项目
      // 最后一次编辑会被静默丢弃（而不是错误地污染新项目——新项目保存的是它自己
      // 没改过的值，是空操作，但旧项目的改动就这么没了）
      if (this.hasUnsavedChanges && this.selectedProject) {
        clearTimeout(this.saveTimeout)
        await this.saveToBackend()
      }
      this._suppressAutosave = true
      this.selectedProject = item

      // 预填充编辑表单
      this.projectForm = {
        name: item.name || '',
        nameZh: item.nameZh || '',
        shortName: item.shortName || '',
        subtitle: item.subtitle || '',
        subtitleZh: item.subtitleZh || '',
        highlightMetric: item.highlightMetric || '',
        highlightMetricZh: item.highlightMetricZh || '',
        description: item.description || '',
        descriptionZh: item.descriptionZh || '',
        icon: item.icon || '',
        imageUrl: item.imageUrl || '',
        projectUrl: item.projectUrl || '',
        githubUrl: item.githubUrl || '',
        category: item.category || '',
        technologies: Array.isArray(item.technologies)
          ? [...item.technologies]
          : (item.technologies ? item.technologies.split(',').map(t => t.trim()).filter(t => t) : []),
        technologiesZh: Array.isArray(item.technologiesZh)
          ? [...item.technologiesZh]
          : (item.technologiesZh ? item.technologiesZh.split(',').map(t => t.trim()).filter(t => t) : []),
        content: item.content || '',
        contentType: item.contentType || 'richtext',
        isPublic: item.isPublic !== false,
        isFeatured: item.isFeatured || false,
        displayOrder: item.displayOrder || 0
      }

      this.$nextTick(async () => {
        // initProjectEditor() 最后一步会把 contentType 写成 'editorjs'（也落在
        // 被深度 watch 的 projectForm 上）——必须等它（含这次写入触发的 watcher
        // 排队、以及下面这次 $nextTick 冲刷）都结束，才能解除抑制，否则刚打开/
        // 切换项目就会立刻触发一次没意义的自动保存
        if (process.client) {
          await this.initProjectEditor()
          await this.$nextTick()
        }
        this._suppressAutosave = false
      })
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
    updateSaveStatus(status) {
      const map = {
        saving: { icon: 'el-icon-loading', text: this.$t('workspace.notes.saving') },
        saved: { icon: 'el-icon-check', text: this.$t('workspace.notes.saved') },
        error: { icon: 'el-icon-warning', text: this.$t('workspace.notes.saveFailed') }
      }
      this.saveStatus = map[status] || { icon: 'el-icon-edit', text: '' }
    },
    debouncedSave() {
      clearTimeout(this.saveTimeout)
      this.saveTimeout = setTimeout(() => { this.saveToBackend() }, 2000)
    },
    buildProjectSubmitData() {
      return {
        ...this.projectForm,
        technologies: Array.isArray(this.projectForm.technologies)
          ? this.projectForm.technologies.join(',')
          : this.projectForm.technologies,
        technologiesZh: Array.isArray(this.projectForm.technologiesZh)
          ? this.projectForm.technologiesZh.join(',')
          : this.projectForm.technologiesZh
      }
    },
    async saveToBackend() {
      if (!this.selectedProject || !this.projectForm.name) return
      if (this.editor) {
        try {
          const outputData = await this.editor.save()
          // projectForm 被深度 watch 用来驱动自动保存——这里写回 content/contentType
          // 如果不加抑制标记，会立刻被同一个 watcher 当成"新的改动"，排一次新的
          // debouncedSave()，而那次保存又会再写一遍 content，形成"保存→触发watcher→
          // 再保存"的死循环（自动保存状态一直在"保存中"闪烁、页面像在不停刷新）。
          // 用和 selectProject 里同样的 _suppressAutosave + $nextTick 套路，
          // 等 watcher 这一轮跑完再放开抑制
          this._suppressAutosave = true
          this.projectForm.content = JSON.stringify(outputData)
          this.projectForm.contentType = 'editorjs'
          await this.$nextTick()
          this._suppressAutosave = false
        } catch (e) {
          console.error('读取项目正文内容失败:', e)
        }
      }
      this.updateSaveStatus('saving')
      try {
        const submitData = this.buildProjectSubmitData()
        await this.$projectService.updateProject(this.selectedProject.id, submitData)
        this.hasUnsavedChanges = false
        this.updateSaveStatus('saved')
        const idx = this.projects.findIndex(p => p.id === this.selectedProject.id)
        if (idx >= 0) Object.assign(this.projects[idx], submitData)
        Object.assign(this.selectedProject, submitData)
      } catch (error) {
        this.updateSaveStatus('error')
      }
    },
    // 给 isPublic/isFeatured 这类离散开关用：不等 2 秒防抖，立即落盘
    saveFieldsImmediately() {
      clearTimeout(this.saveTimeout)
      this.saveToBackend()
    },
    triggerCoverUpload() {
      if (!this.selectedProject) return
      this.$refs.coverFileInput.click()
    },
    async handleCoverFileChange(e) {
      const file = e.target.files && e.target.files[0]
      e.target.value = '' // 允许连续选同一个文件也能触发 change
      if (!file || !this.selectedProject) return
      try {
        const result = await this.$uploadService.uploadLocal(file, 'project', this.selectedProject.id)
        this.projectForm.imageUrl = result.url || result.fileUrl || result
        this.saveFieldsImmediately()
      } catch (error) {
        this.$message.error(this.$t('workspace.projects.coverUploadFailed'))
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
    showProjectZhTagInput() {
      this.projectZhTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.projectZhTagInput) {
          this.$refs.projectZhTagInput.focus()
        }
      })
    },
    addProjectZhTag() {
      const val = this.projectZhTagInputValue.trim()
      if (val && !this.projectForm.technologiesZh.includes(val)) {
        this.projectForm.technologiesZh.push(val)
      }
      this.projectZhTagInputVisible = false
      this.projectZhTagInputValue = ''
    },
    async initProjectEditor() {
      if (!process.client || !this.selectedProject) return
      this.destroyEditorJsHost()

      let editorData = null
      if (this.projectForm.content) {
        if (this.projectForm.contentType === 'editorjs') {
          try {
            editorData = JSON.parse(this.projectForm.content)
          } catch (e) {
            console.warn('项目正文不是合法的 EditorJS JSON，按空文档处理:', e)
            editorData = null
          }
        } else {
          // 旧的 richtext/markdown 内容——不做自动转换，按空文档处理，
          // 由用户手工把旧内容搬进新编辑器（spec 已确认现存记录很少，手工迁移即可）
          editorData = null
        }
      }

      await this.$nextTick()
      await this.initEditorJsHost({
        container: this.$refs.projectEditorContainer,
        model: 'project',
        entityId: this.selectedProject.id,
        data: editorData,
        locale: this.$i18n.locale,
        axiosBaseURL: this.$axios?.defaults?.baseURL || '',
        authHeader: () => this.$auth?.strategy?.token?.get() || '',
        placeholders: {
          editor: this.$t('workspace.projects.editorPlaceholder'),
          header: this.$t('workspace.projects.editorHeaderPlaceholder'),
          code: this.$t('workspace.projects.editorCodePlaceholder'),
          quote: this.$t('workspace.projects.editorQuotePlaceholder'),
          quoteCaption: this.$t('workspace.projects.editorQuoteCaptionPlaceholder'),
          warningTitle: this.$t('workspace.projects.editorWarningTitlePlaceholder'),
          warningMessage: this.$t('workspace.projects.editorWarningMessagePlaceholder'),
          attachButton: this.$t('workspace.projects.editorAttachButtonText'),
          attachError: this.$t('workspace.projects.editorAttachErrorMessage'),
          clipboardImageReadFailed: () => this.$t('workspace.projects.clipboardImageReadFailed'),
          uploadingImage: (size) => this.$t('workspace.projects.uploadingImage', { size }),
          imageTooLarge: (size) => this.$t('workspace.projects.imageTooLarge', { size }),
          imagePasteUploadFailed: (size) => this.$t('workspace.projects.imagePasteUploadFailed', { size })
        },
        onChange: () => {
          if (this._suppressAutosave) return
          this.hasUnsavedChanges = true
          this.updateSaveStatus('saving')
          this.debouncedSave()
        },
        includeRecordTool: false
      })
      this.projectForm.contentType = 'editorjs'
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
  height: 100%;
  overflow: hidden;
}

// 三栏框架样式（.workspace-layout / -sidebar / -main / -right / .right-collapsed
// / .col-resizer / @media 1024 / @media 768）见 assets/styles/main.scss

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
      font-size: 15px;
      font-weight: bold;
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
    font-size: 15px;
    font-weight: bold;
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

.project-editorjs-container {
  min-height: 300px;
  padding: 8px 0;

  // 这个页面内容列比笔记页窄，复用笔记页那套 60px 的块左侧预留间距（给
  // +/拖拽手柄图标留空间）显得过空——缩小到一半，"+"菜单弹出位置跟着改
  ::v-deep .ce-block__content {
    padding-left: 30px;
  }

  ::v-deep .ce-toolbox {
    left: 30px !important;
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

.save-status {
  font-size: 12px;
  color: var(--text-muted);
  margin-right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &.is-error {
    color: #f56c6c;
  }
}

.project-cover {
  height: 120px;
  border-radius: 8px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  margin-bottom: 16px;

  &.is-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    background: var(--bg-secondary);
    border: 1px dashed var(--border-color);
    transition: border-color 0.2s;

    &:hover {
      border-color: #667eea;
    }
  }

  .project-cover-upload-icon {
    font-size: 22px;
    color: var(--text-muted);
  }

  .project-cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .project-cover-hint {
    position: absolute;
    right: 10px;
    bottom: 10px;
    font-size: 11px;
    background: rgba(0, 0, 0, 0.4);
    color: #fff;
    padding: 2px 8px;
    border-radius: 10px;
  }

  &.is-empty .project-cover-hint {
    position: static;
    background: transparent;
    color: var(--text-muted);
    font-size: 12px;
  }
}

.project-icon-block {
  width: 44px;
  height: 44px;
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  margin-top: -38px;
  margin-left: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  position: relative;

  .project-icon-edit-trigger {
    position: absolute;
    right: -4px;
    bottom: -4px;
    font-size: 12px;
    background: var(--bg-secondary);
    border-radius: 50%;
    padding: 2px;
    cursor: pointer;
  }
}

.project-title-block {
  margin-top: 10px;

  .project-title-input {
    display: block;
    width: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: 22px;
    font-weight: 700;
    color: var(--text-color);
  }

  .project-subtitle-input {
    display: block;
    width: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: var(--text-muted);
    margin-top: 4px;
  }
}

.project-highlight-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(255, 193, 7, 0.12);
  color: #b8860b;

  .el-input {
    flex: 1;
  }
}

.project-highlight-add-btn {
  margin-top: 10px;
}

.project-properties {
  margin: 14px 0;
  border-top: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
  padding: 10px 0;
}

.properties-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-wrap: wrap;
  max-width: 100%;
  overflow: hidden;

  .el-tag {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .properties-toggle-icon {
    margin-left: auto;
    color: var(--text-muted);
  }
}

.properties-expanded {
  margin-top: 12px;
}
</style>

