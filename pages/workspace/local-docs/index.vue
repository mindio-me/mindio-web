<template>
  <div class="local-docs-page">
    <div class="workspace-layout" :class="{ 'directory-grid-layout': dirViewMode === 'grid' }">
      <!-- ========== 左侧栏 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-header">
          <el-button
            type="primary"
            size="small"
            icon="el-icon-folder-add"
            class="add-directory-button"
            @click="openAddDialog"
          >{{ $t('workspace.localDocs.addDirectory') }}</el-button>
          <!-- <div class="directory-view-switcher">
            <el-tooltip content="目录列表" placement="top">
              <button
                type="button"
                class="directory-view-button"
                :class="{ active: dirViewMode === 'list' }"
                aria-label="目录列表"
                @click="setDirViewMode('list')"
              >
                <i class="el-icon-s-unfold"></i>
              </button>
            </el-tooltip>
            <el-tooltip content="目录卡片" placement="top">
              <button
                type="button"
                class="directory-view-button"
                :class="{ active: dirViewMode === 'grid' }"
                aria-label="目录卡片"
                @click="setDirViewMode('grid')"
              >
                <i class="el-icon-menu"></i>
              </button>
            </el-tooltip>
          </div> -->
        </div>

        <div
          v-loading="loadingDirs"
          class="dir-list"
          :class="{ 'dir-card-grid': dirViewMode === 'grid' }"
        >
          <div
            class="dir-item dir-item-all"
            :class="{ active: isAllSelected }"
            :title="$t('workspace.localDocs.emptyTitle')"
            @click="selectAllDirectories"
          >
            <div class="dir-item-main">
              <i class="el-icon-files dir-icon"></i>
              <div class="dir-item-info">
                <div class="dir-item-name">{{ $t('workspace.localDocs.allDir') }}</div>
                <div class="dir-item-meta">
                  <span class="doc-count">{{ $t('workspace.localDocs.fileCount', { n: totalDocumentCount }) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div
            v-for="dir in directories"
            :key="dir.id"
            class="dir-item"
            :class="{ active: selectedDir && selectedDir.id === dir.id }"
            :title="dir.dirPath"
            @click="selectDir(dir)"
          >
            <div class="dir-item-main">
              <i class="el-icon-folder dir-icon" :class="{ scanning: dir.scanStatus === 'SCANNING' }"></i>
              <div class="dir-item-info">
                <div class="dir-item-name">{{ dirDisplayName(dir) }}</div>
                <div class="dir-item-meta">
                  <span v-if="dir.scanStatus === 'SCANNING'" class="scan-status scanning">
                    <i class="el-icon-loading"></i> {{ $t('workspace.localDocs.scanning') }}
                  </span>
                  <span v-else-if="dir.scanStatus === 'ERROR'" class="scan-status error">
                    <i class="el-icon-warning-outline"></i> {{ $t('workspace.localDocs.scanError') }}
                  </span>
                  <span v-else class="doc-count">{{ $t('workspace.localDocs.fileCount', { n: dir.documentCount }) }}</span>
                </div>
              </div>
            </div>
            <el-dropdown trigger="click" @command="cmd => handleDirCommand(cmd, dir)" @click.native.stop>
              <span class="dir-more-btn" @click.stop><i class="el-icon-more"></i></span>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="rescan" icon="el-icon-refresh">{{ $t('workspace.localDocs.rescan') }}</el-dropdown-item>
                <el-dropdown-item command="delete" icon="el-icon-delete" class="danger-item">{{ $t('workspace.localDocs.deleteDir') }}</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </div>

          <div v-if="!loadingDirs && directories.length === 0" class="sidebar-empty">
            <i class="el-icon-folder-opened"></i>
            <p>{{ $t('workspace.localDocs.noDirs') }}</p>
            <p class="sidebar-empty-hint">{{ $t('workspace.localDocs.noDirsHint') }}</p>
          </div>
        </div>
      </aside>

      <!-- ========== 主内容区 ========== -->
      <main ref="mainScroller" class="workspace-main" @scroll.passive="handleMainScroll">
        <!-- 未选中目录 -->
        <div v-if="!selectedDir" class="main-empty">
          <i class="el-icon-files main-empty-icon"></i>
          <p class="main-empty-title">{{ $t('workspace.localDocs.emptyTitle') }}</p>
          <p class="main-empty-hint">{{ $t('workspace.localDocs.emptyHint') }}</p>
        </div>

        <!-- 已选中目录 -->
        <template v-else>
          <!-- 目录信息行 -->
          <div class="dir-info-bar">
            <div class="dir-info-path">
              <i :class="isAllSelected ? 'el-icon-files' : 'el-icon-folder-opened'"></i>
              <span v-if="isAllSelected" class="dir-info-path-text">{{ $t('workspace.localDocs.allDirsLabel') }}</span>
              <span v-else class="dir-info-path-text" :title="selectedDir.dirPath">{{ selectedDir.dirPath }}</span>
            </div>
            <div class="dir-info-meta">
              <span v-if="isAllSelected">{{ $t('workspace.localDocs.dirCount', { n: directories.length }) }}</span>
              <span v-else-if="selectedDir.lastScanAt">
                {{ $t('workspace.localDocs.lastScan') }}{{ formatDate(selectedDir.lastScanAt) }}
              </span>
              <span v-else class="text-muted">{{ $t('workspace.localDocs.notScanned') }}</span>
              <span class="dir-info-count">{{ $t('workspace.localDocs.totalFiles', { n: isAllSelected ? totalDocumentCount : selectedDir.documentCount }) }}</span>
            </div>
          </div>

          <!-- 错误提示 -->
          <el-alert
            v-if="!isAllSelected && selectedDir.scanStatus === 'ERROR' && selectedDir.lastScanError"
            :title="$t('workspace.localDocs.scanErrorPrefix') + selectedDir.lastScanError"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
          />

          <!-- 工具栏 -->
          <div class="docs-toolbar">
            <div class="docs-toolbar-filters">
              <el-input
                v-model="keyword"
                :placeholder="$t('workspace.localDocs.searchPlaceholder')"
                prefix-icon="el-icon-search"
                size="small"
                clearable
                @input="onSearch"
              />
              <el-select
                v-model="filterType"
                size="small"
                :placeholder="$t('workspace.localDocs.fileType')"
                clearable
                @change="onSearch"
              >
                <el-option :label="$t('workspace.localDocs.all')" value="" />
                <el-option label="PDF" value="pdf" />
                <el-option label="Word" value="docx" />
                <el-option label="Word 97" value="doc" />
                <el-option label="Excel" value="xlsx" />
                <el-option label="Excel 97" value="xls" />
                <el-option label="PPT" value="pptx" />
                <el-option label="PPT 97" value="ppt" />
              </el-select>
            </div>

            <div class="view-switcher">
              <el-tooltip :content="$t('workspace.localDocs.listView')" placement="top">
                <button
                  type="button"
                  class="view-switch-button"
                  :class="{ active: viewMode === 'list' }"
                  :aria-label="$t('workspace.localDocs.listView')"
                  @click="setViewMode('list')"
                >
                  <i class="el-icon-s-unfold"></i>
                </button>
              </el-tooltip>
              <el-tooltip :content="$t('workspace.localDocs.monthView')" placement="top">
                <button
                  type="button"
                  class="view-switch-button"
                  :class="{ active: viewMode === 'month' }"
                  :aria-label="$t('workspace.localDocs.monthView')"
                  @click="setViewMode('month')"
                >
                  <i class="el-icon-menu"></i>
                </button>
              </el-tooltip>
            </div>

            <div class="docs-toolbar-spacer"></div>
          </div>

          <!-- 文档列表 -->
          <div v-if="viewMode === 'list'" v-loading="loadingDocs" class="docs-table-wrapper">
            <el-table
              :data="documents"
              size="small"
              style="width:100%"
              :empty-text="!isAllSelected && selectedDir.scanStatus === 'SCANNING' ? $t('workspace.localDocs.scanningWait') : $t('workspace.localDocs.noMatchDocs')"
            >
              <el-table-column width="40">
                <template slot-scope="{ row }">
                  <i :class="fileIcon(row.fileType)" class="file-type-icon" :style="{ color: fileColor(row.fileType) }"></i>
                </template>
              </el-table-column>
              <el-table-column :label="$t('workspace.localDocs.colFileName')" prop="fileName" min-width="200" show-overflow-tooltip />
              <el-table-column :label="$t('workspace.localDocs.colLocation')" prop="relativePath" min-width="160" show-overflow-tooltip>
                <template slot-scope="{ row }">
                  <span class="rel-path">
                    <template v-if="isAllSelected">{{ row.directoryName }}</template>
                    <template v-if="isAllSelected && relativeDir(row.relativePath)"> / </template>
                    {{ relativeDir(row.relativePath) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column :label="$t('workspace.localDocs.colType')" prop="fileType" width="80">
                <template slot-scope="{ row }">
                  <el-tag size="mini" :type="typeTagType(row.fileType)">{{ row.fileType.toUpperCase() }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="$t('workspace.localDocs.colSize')" width="90">
                <template slot-scope="{ row }">{{ formatSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column :label="$t('workspace.localDocs.colModified')" width="130">
                <template slot-scope="{ row }">{{ formatDate(row.fileLastModified) }}</template>
              </el-table-column>
              <el-table-column :label="$t('workspace.localDocs.colActions')" width="120" fixed="right">
                <template slot-scope="{ row }">
                  <el-button type="text" size="mini" icon="el-icon-view" @click="openDocument(row)">{{ $t('workspace.localDocs.open') }}</el-button>
                  <el-button type="text" size="mini" icon="el-icon-document-copy" @click="copyPath(row)">{{ $t('workspace.localDocs.copyPath') }}</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-pagination
              v-if="total > pageSize"
              layout="prev, pager, next"
              :total="total"
              :page-size="pageSize"
              :current-page.sync="currentPage"
              style="text-align:center;margin-top:16px"
              @current-change="loadDocuments"
            />
          </div>

          <!-- 按月份浏览 -->
          <div v-else v-loading="loadingDocs && documents.length === 0" class="month-view">
            <section v-for="group in monthGroups" :key="group.key" class="month-section">
              <div class="month-heading">
                <span>{{ group.label }}</span>
                <span class="month-count">{{ group.documents.length }}{{ $t('workspace.localDocs.monthCountSuffix') }}</span>
              </div>

              <div class="month-document-grid">
                <article
                  v-for="doc in group.documents"
                  :key="doc.id"
                  class="document-card"
                  :title="doc.absolutePath"
                  tabindex="0"
                  @click="openDocument(doc)"
                  @keydown.enter="openDocument(doc)"
                >
                  <div class="document-card-actions" @click.stop>
                    <el-tooltip :content="$t('workspace.localDocs.copyPathTooltip')" placement="top">
                      <button type="button" class="card-action-button" @click="copyPath(doc)">
                        <i class="el-icon-document-copy"></i>
                      </button>
                    </el-tooltip>
                  </div>
                  <div
                    class="document-icon-shell"
                    :style="{ color: fileColor(doc.fileType), '--file-color': fileColor(doc.fileType) }"
                  >
                    <i :class="fileIcon(doc.fileType)"></i>
                    <span class="document-extension">{{ doc.fileType.toUpperCase() }}</span>
                  </div>
                  <div class="document-card-name">{{ doc.fileName }}</div>
                  <div v-if="isAllSelected" class="document-card-directory">{{ doc.directoryName }}</div>
                  <div class="document-card-date">{{ formatCardDate(doc.fileLastModified) }}</div>
                </article>
              </div>
            </section>

            <div v-if="!loadingDocs && monthGroups.length === 0" class="month-empty">
              <i class="el-icon-folder-opened"></i>
              <span>{{ $t('workspace.localDocs.noMatchMonthDocs') }}</span>
            </div>

            <div v-if="monthGroups.length" class="month-load-state">
              <span v-if="loadingDocs"><i class="el-icon-loading"></i> {{ $t('workspace.localDocs.loadingMore') }}</span>
              <span v-else-if="hasMoreMonth">{{ $t('workspace.localDocs.scrollToLoad') }}</span>
              <span v-else>{{ $t('workspace.localDocs.allLoaded') }}</span>
            </div>
          </div>
        </template>
      </main>
    </div>

    <!-- 添加目录确认 Dialog -->
    <el-dialog
      :title="$t('workspace.localDocs.dialogTitle')"
      :visible.sync="addDialogVisible"
      custom-class="local-doc-directory-dialog"
      top="6vh"
      @close="resetForm"
    >
      <el-form ref="addForm" :model="addForm" :rules="addRules" label-width="80px">
        <!-- 路径输入行 -->
        <el-form-item :label="$t('workspace.localDocs.dirPath')" prop="dirPath">
          <div class="path-input-row">
            <el-input
              v-model="addForm.dirPath"
              :placeholder="$t('workspace.localDocs.dirPathPlaceholder')"
              clearable
              style="flex:1"
              @change="closeBrowser"
            />
            <el-button
              icon="el-icon-folder-opened"
              style="margin-left:8px;flex-shrink:0"
              @click="chooseDirectory"
            >{{ $t('workspace.localDocs.browse') }}</el-button>
          </div>
          <div v-if="hasNativeDirectoryPicker" class="form-hint">
            {{ $t('workspace.localDocs.nativePickerHint') }}
          </div>
        </el-form-item>

        <!-- 目录浏览器 -->
        <div v-if="!hasNativeDirectoryPicker && browserVisible" class="dir-browser">
          <div class="browser-toolbar">
            <!-- 面包屑导航 -->
            <div class="browser-breadcrumb">
              <span
                class="crumb crumb-root"
                @click="navigateTo('')"
              ><i class="el-icon-s-home"></i></span>
              <template v-for="(crumb, i) in breadcrumbs">
                <span :key="'sep-' + i" class="crumb-sep">/</span>
                <span
                  :key="'crumb-' + i"
                  class="crumb"
                  :class="{ 'crumb-last': i === breadcrumbs.length - 1 }"
                  @click="navigateTo(crumb.path)"
                >{{ crumb.name }}</span>
              </template>
            </div>

            <div class="browser-view-switcher">
              <el-tooltip content="列表" placement="top">
                <button
                  type="button"
                  class="browser-view-button"
                  :class="{ active: browserViewMode === 'list' }"
                  aria-label="目录列表"
                  @click="setBrowserViewMode('list')"
                >
                  <i class="el-icon-s-unfold"></i>
                </button>
              </el-tooltip>
              <el-tooltip content="卡片" placement="top">
                <button
                  type="button"
                  class="browser-view-button"
                  :class="{ active: browserViewMode === 'grid' }"
                  aria-label="目录卡片"
                  @click="setBrowserViewMode('grid')"
                >
                  <i class="el-icon-menu"></i>
                </button>
              </el-tooltip>
            </div>
          </div>

          <!-- 目录列表 -->
          <div
            v-loading="browserLoading"
            class="browser-list"
            :class="{ 'browser-card-grid': browserViewMode === 'grid' }"
          >
            <div
              v-for="entry in browserEntries"
              :key="entry.path"
              class="browser-entry"
              @click="navigateTo(entry.path)"
            >
              <i class="el-icon-folder browser-entry-icon"></i>
              <span class="browser-entry-name">{{ entry.name }}</span>
              <i class="el-icon-arrow-right browser-entry-arrow"></i>
            </div>
            <div v-if="!browserLoading && browserEntries.length === 0" class="browser-empty">
              {{ $t('workspace.localDocs.noSubDirs') }}
            </div>
          </div>

          <!-- 选择当前目录按钮 -->
          <div v-if="browserCurrentPath" class="browser-footer">
            <el-button type="primary" size="small" icon="el-icon-check" @click="selectBrowserPath">
              {{ $t('workspace.localDocs.selectCurrent', { name: browserCurrentName }) }}
            </el-button>
          </div>
        </div>

        <el-form-item :label="$t('workspace.localDocs.displayName')">
          <el-input
            v-model="addForm.displayName"
            :placeholder="$t('workspace.localDocs.displayNamePlaceholder')"
            clearable
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="addDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">{{ $t('workspace.localDocs.submitAdd') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
const ALL_DIRECTORIES_ID = 'all'

export default {
  name: 'LocalDocsPage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    return {
      directories: [],
      loadingDirs: false,
      selectedDir: null,

      documents: [],
      total: 0,
      currentPage: 1,
      pageSize: 20,
      loadingDocs: false,
      keyword: '',
      filterType: '',
      searchTimer: null,
      viewMode: 'list',
      monthPage: 0,
      monthPageSize: 60,
      hasMoreMonth: true,

      addDialogVisible: false,
      adding: false,
      addForm: { dirPath: '', displayName: '' },

      browserVisible: false,
      browserLoading: false,
      browserCurrentPath: '',
      browserEntries: [],
      browserViewMode: 'list',
      hasNativeDirectoryPicker: false,
      dirViewMode: 'list',
    }
  },
  computed: {
    addRules() {
      return {
        dirPath: [{ required: true, message: this.$t('workspace.localDocs.dirPathRequired'), trigger: 'blur' }],
      }
    },
    isAllSelected() {
      return this.selectedDir && this.selectedDir.id === ALL_DIRECTORIES_ID
    },
    totalDocumentCount() {
      return this.directories.reduce((total, dir) => total + (Number(dir.documentCount) || 0), 0)
    },
    breadcrumbs() {
      if (!this.browserCurrentPath) return []
      const isWindows = this.browserCurrentPath.includes('\\')
      const parts = this.browserCurrentPath.replace(/\\/g, '/').split('/').filter(Boolean)
      const crumbs = []
      for (let i = 0; i < parts.length; i++) {
        const seg = parts.slice(0, i + 1).join(isWindows ? '\\' : '/')
        const fullPath = isWindows ? seg : '/' + seg
        crumbs.push({ name: parts[i], path: fullPath })
      }
      return crumbs
    },
    browserCurrentName() {
      if (!this.browserCurrentPath) return ''
      const parts = this.browserCurrentPath.replace(/\\/g, '/').split('/')
      return parts.filter(Boolean).pop() || this.browserCurrentPath
    },
    monthGroups() {
      const groups = []
      const groupMap = new Map()
      this.documents.forEach(doc => {
        const date = doc.fileLastModified ? new Date(doc.fileLastModified) : null
        const validDate = date && !Number.isNaN(date.getTime())
        const key = validDate
          ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
          : 'unknown'
        if (!groupMap.has(key)) {
          const group = {
            key,
            label: validDate ? `${date.getFullYear()}/${String(date.getMonth() + 1).padStart(2, '0')}` : this.$t('workspace.localDocs.unknownTime'),
            documents: [],
          }
          groupMap.set(key, group)
          groups.push(group)
        }
        groupMap.get(key).documents.push(doc)
      })
      return groups
    },
  },
  async created() {
    await this.loadDirectories()
  },
  mounted() {
    this.hasNativeDirectoryPicker = !!(
      window.mindioDesktop &&
      typeof window.mindioDesktop.selectDirectory === 'function'
    )
    const savedView = window.localStorage.getItem('local-docs-view-mode')
    if (savedView === 'list' || savedView === 'month') {
      this.viewMode = savedView
    }
    const savedDirView = window.localStorage.getItem('local-docs-directory-view-mode')
    if (savedDirView === 'list' || savedDirView === 'grid') {
      this.dirViewMode = savedDirView
    }
    const savedBrowserView = window.localStorage.getItem('local-docs-browser-view-mode')
    if (savedBrowserView === 'list' || savedBrowserView === 'grid') {
      this.browserViewMode = savedBrowserView
    }
    if (!this.selectedDir) {
      this.selectAllDirectories()
    }
  },
  beforeDestroy() {
    clearTimeout(this.searchTimer)
  },
  methods: {
    async loadDirectories() {
      this.loadingDirs = true
      try {
        this.directories = await this.$localDocService.getDirectories()
      } catch {
        this.$message.error(this.$t('workspace.localDocs.loadDirsFailed'))
      } finally {
        this.loadingDirs = false
      }
    },

    selectDir(dir) {
      this.selectedDir = dir
      this.keyword = ''
      this.filterType = ''
      this.currentPage = 1
      this.documents = []
      this.total = 0
      this.monthPage = 0
      this.hasMoreMonth = true
      this.resetMainScroll()
      this.loadDocuments()
    },

    selectAllDirectories() {
      this.selectDir({
        id: ALL_DIRECTORIES_ID,
        displayName: this.$t('workspace.localDocs.allDir'),
        documentCount: this.totalDocumentCount,
      })
    },

    getDocumentsPage(params) {
      if (this.isAllSelected) {
        return this.$localDocService.getAllDocuments(params)
      }
      return this.$localDocService.getDocuments(this.selectedDir.id, params)
    },

    async loadDocuments(options = {}) {
      if (!this.selectedDir) return
      if (this.viewMode === 'month') {
        await this.loadMonthDocuments({ reset: options.reset !== false })
        return
      }
      this.loadingDocs = true
      try {
        const res = await this.getDocumentsPage({
          keyword: this.keyword || undefined,
          fileType: this.filterType || undefined,
          page: this.currentPage - 1,
          size: this.pageSize,
        })
        this.documents = res.content || []
        this.total = res.totalElements || 0
      } catch {
        this.$message.error(this.$t('workspace.localDocs.loadDocsFailed'))
      } finally {
        this.loadingDocs = false
      }
    },

    async loadMonthDocuments({ reset = false } = {}) {
      if (!this.selectedDir || this.loadingDocs) return
      if (!reset && !this.hasMoreMonth) return

      if (reset) {
        this.monthPage = 0
        this.documents = []
        this.total = 0
        this.hasMoreMonth = true
        this.resetMainScroll()
      }

      this.loadingDocs = true
      try {
        const res = await this.getDocumentsPage({
          keyword: this.keyword || undefined,
          fileType: this.filterType || undefined,
          page: this.monthPage,
          size: this.monthPageSize,
          sortBy: 'fileLastModified',
          direction: 'DESC',
        })
        const incoming = res.content || []
        this.documents = reset ? incoming : [...this.documents, ...incoming]
        this.total = res.totalElements || 0
        this.hasMoreMonth = !res.last && this.documents.length < this.total
        if (this.hasMoreMonth) this.monthPage += 1
      } catch {
        this.$message.error(this.$t('workspace.localDocs.loadDocsFailed'))
      } finally {
        this.loadingDocs = false
      }
    },

    onSearch() {
      clearTimeout(this.searchTimer)
      this.currentPage = 1
      this.searchTimer = setTimeout(() => this.loadDocuments({ reset: true }), 300)
    },

    setViewMode(mode) {
      if (mode === this.viewMode) return
      this.viewMode = mode
      window.localStorage.setItem('local-docs-view-mode', mode)
      this.currentPage = 1
      this.monthPage = 0
      this.hasMoreMonth = true
      this.documents = []
      this.total = 0
      this.resetMainScroll()
      this.loadDocuments({ reset: true })
    },

    setDirViewMode(mode) {
      if (mode === this.dirViewMode) return
      this.dirViewMode = mode
      window.localStorage.setItem('local-docs-directory-view-mode', mode)
    },

    setBrowserViewMode(mode) {
      if (mode === this.browserViewMode) return
      this.browserViewMode = mode
      window.localStorage.setItem('local-docs-browser-view-mode', mode)
    },

    handleMainScroll() {
      if (this.viewMode !== 'month' || this.loadingDocs || !this.hasMoreMonth) return
      const el = this.$refs.mainScroller
      if (!el) return
      const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
      if (distanceToBottom < 320) {
        this.loadMonthDocuments()
      }
    },

    resetMainScroll() {
      this.$nextTick(() => {
        if (this.$refs.mainScroller) this.$refs.mainScroller.scrollTop = 0
      })
    },

    async openAddDialog() {
      if (this.hasNativeDirectoryPicker) {
        const selectedPath = await this.selectNativeDirectory()
        if (!selectedPath) return
        this.addForm.dirPath = selectedPath
      }
      this.addDialogVisible = true
    },

    resetForm() {
      this.addForm = { dirPath: '', displayName: '' }
      this.$refs.addForm && this.$refs.addForm.resetFields()
      this.browserVisible = false
      this.browserCurrentPath = ''
      this.browserEntries = []
    },

    async chooseDirectory() {
      if (this.hasNativeDirectoryPicker) {
        const selectedPath = await this.selectNativeDirectory()
        if (!selectedPath) return
        this.addForm.dirPath = selectedPath
        this.$refs.addForm && this.$refs.addForm.clearValidate('dirPath')
        return
      }
      await this.toggleBrowser()
    },

    async selectNativeDirectory() {
      try {
        return await window.mindioDesktop.selectDirectory()
      } catch (e) {
        this.$message.error(e?.message || this.$t('workspace.localDocs.cannotOpenDir'))
        return null
      }
    },

    async toggleBrowser() {
      if (this.browserVisible) {
        this.browserVisible = false
        return
      }
      this.browserVisible = true
      const startPath = this.addForm.dirPath.trim() || ''
      await this.navigateTo(startPath)
    },

    closeBrowser() {
      this.browserVisible = false
    },

    async navigateTo(path) {
      this.browserLoading = true
      try {
        const res = await this.$localDocService.listDirectory(path)
        this.browserCurrentPath = res.currentPath || ''
        this.browserEntries = res.entries || []
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localDocs.cannotReadDir'))
      } finally {
        this.browserLoading = false
      }
    },

    selectBrowserPath() {
      this.addForm.dirPath = this.browserCurrentPath
      this.browserVisible = false
      this.$refs.addForm && this.$refs.addForm.clearValidate('dirPath')
    },

    async submitAdd() {
      try {
        await this.$refs.addForm.validate()
      } catch {
        return
      }
      this.adding = true
      try {
        const newDir = await this.$localDocService.addDirectory({
          dirPath: this.addForm.dirPath.trim(),
          displayName: this.addForm.displayName.trim() || undefined,
        })
        this.directories.unshift(newDir)
        this.addDialogVisible = false
        this.selectDir(newDir)
        if (newDir.scanStatus === 'ERROR') {
          this.$message.warning(this.$t('workspace.localDocs.addWarning', { msg: newDir.lastScanError }))
        } else {
          this.$message.success(this.$t('workspace.localDocs.addSuccess', { n: newDir.documentCount }))
        }
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localDocs.addFailed'))
      } finally {
        this.adding = false
      }
    },

    async handleDirCommand(cmd, dir) {
      if (cmd === 'rescan') {
        await this.doRescan(dir)
      } else if (cmd === 'delete') {
        await this.doDelete(dir)
      }
    },

    async doRescan(dir) {
      const idx = this.directories.findIndex(d => d.id === dir.id)
      if (idx >= 0) this.$set(this.directories[idx], 'scanStatus', 'SCANNING')
      try {
        const updated = await this.$localDocService.rescan(dir.id)
        if (idx >= 0) this.$set(this.directories, idx, updated)
        if (this.selectedDir && this.selectedDir.id === dir.id) {
          this.selectedDir = updated
          this.loadDocuments()
        } else if (this.isAllSelected) {
          this.loadDocuments({ reset: true })
        }
        if (updated.scanStatus === 'ERROR') {
          this.$message.warning(this.$t('workspace.localDocs.rescanWarning', { msg: updated.lastScanError }))
        } else {
          this.$message.success(this.$t('workspace.localDocs.rescanSuccess', { n: updated.documentCount }))
        }
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localDocs.rescanFailed'))
        if (idx >= 0) this.$set(this.directories[idx], 'scanStatus', 'ERROR')
      }
    },

    async doDelete(dir) {
      try {
        await this.$confirm(
          this.$t('workspace.localDocs.deleteConfirmMsg', { name: this.dirDisplayName(dir) }),
          this.$t('workspace.localDocs.deleteTitle'),
          { confirmButtonText: this.$t('common.delete'), cancelButtonText: this.$t('common.cancel'), type: 'warning' }
        )
      } catch {
        return
      }
      try {
        await this.$localDocService.deleteDirectory(dir.id)
        this.directories = this.directories.filter(d => d.id !== dir.id)
        if (this.selectedDir && this.selectedDir.id === dir.id) {
          this.selectAllDirectories()
        } else if (this.isAllSelected) {
          this.loadDocuments({ reset: true })
        }
        this.$message.success(this.$t('workspace.localDocs.deleteSuccess'))
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localDocs.deleteFailed'))
      }
    },

    async copyPath(doc) {
      try {
        await navigator.clipboard.writeText(doc.absolutePath)
        this.$message.success(this.$t('workspace.localDocs.copiedPath'))
      } catch {
        this.$message.error(this.$t('workspace.localDocs.copyFailed') + doc.absolutePath)
      }
    },

    openDocument(doc) {
      const uri = this.buildOpenUri(doc)
      if (!uri) {
        this.$message.warning(this.$t('workspace.localDocs.unsupportedType'))
        return
      }
      window.location.href = uri
    },

    buildOpenUri(doc) {
      const officeUri = this.buildOfficeOpenUri(doc)
      if (officeUri) return officeUri

      const apiOrigin = new URL(this.$axios.defaults.baseURL).origin
      const token = (this.$auth.strategy.token.get() || '').replace(/^Bearer\s+/i, '')
      const fileUrl = `${apiOrigin}/v1/local-docs/files/${doc.id}?token=${encodeURIComponent(token)}`
      return fileUrl
    },

    buildOfficeOpenUri(doc) {
      const officeProtocol = {
        doc: 'ms-word',
        docx: 'ms-word',
        xls: 'ms-excel',
        xlsx: 'ms-excel',
        ppt: 'ms-powerpoint',
        pptx: 'ms-powerpoint',
      }
      const proto = officeProtocol[(doc.fileType || '').toLowerCase()]
      if (!proto || !doc.absolutePath) return null
      return `${proto}:ofe|u|${this.localPathToFileUri(doc.absolutePath)}`
    },

    localPathToFileUri(path) {
      const normalized = String(path).trim().replace(/\\/g, '/')
      const encodeSegments = value => value.split('/').filter(Boolean).map(encodeURIComponent).join('/')

      if (normalized.startsWith('//')) {
        return `file://${encodeSegments(normalized.slice(2))}`
      }

      if (/^[a-zA-Z]:\//.test(normalized)) {
        const drive = normalized.slice(0, 2)
        const rest = normalized.slice(3)
        return `file:///${drive}/${encodeSegments(rest)}`
      }

      if (normalized.startsWith('/')) {
        return `file:///${encodeSegments(normalized)}`
      }

      return `file:///${encodeSegments(normalized)}`
    },

    dirDisplayName(dir) {
      if (dir.displayName) return dir.displayName
      const parts = dir.dirPath.replace(/\\/g, '/').split('/')
      return parts[parts.length - 1] || dir.dirPath
    },

    relativeDir(relativePath) {
      if (!relativePath) return ''
      const parts = relativePath.replace(/\\/g, '/').split('/')
      return parts.length > 1 ? parts.slice(0, -1).join('/') : ''
    },

    fileIcon(type) {
      const map = {
        pdf: 'el-icon-document',
        docx: 'el-icon-document',
        doc: 'el-icon-document',
        xlsx: 'el-icon-s-grid',
        xls: 'el-icon-s-grid',
        pptx: 'el-icon-picture-outline',
        ppt: 'el-icon-picture-outline',
      }
      return map[type] || 'el-icon-document'
    },

    fileColor(type) {
      const map = {
        pdf: '#e74c3c',
        docx: '#2980b9', doc: '#2980b9',
        xlsx: '#27ae60', xls: '#27ae60',
        pptx: '#e67e22', ppt: '#e67e22',
      }
      return map[type] || '#909399'
    },

    typeTagType(type) {
      const map = {
        pdf: 'danger',
        docx: 'primary', doc: 'primary',
        xlsx: 'success', xls: 'success',
        pptx: 'warning', ppt: 'warning',
      }
      return map[type] || 'info'
    },

    formatSize(bytes) {
      if (!bytes) return '-'
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleDateString(this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US', { year: 'numeric', month: '2-digit', day: '2-digit' })
    },

    formatCardDate(dateStr) {
      if (!dateStr) return this.$t('workspace.localDocs.unknownTime')
      return new Date(dateStr).toLocaleDateString(this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US', { month: '2-digit', day: '2-digit' })
    },
  },
}
</script>

<style scoped lang="scss">
.local-docs-page {
  height: 100%;
  overflow: hidden;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  height: calc(100vh - 100px);
  gap: 12px;
  transition: grid-template-columns 0.2s ease;

  &.directory-grid-layout {
    grid-template-columns: 360px minmax(0, 1fr);
  }
}

// 左侧栏
.workspace-sidebar {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.add-directory-button {
  flex: 1;
}

.directory-view-switcher {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-secondary);
}

.directory-view-button {
  width: 28px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s ease;

  &:hover { color: #667eea; }

  &.active {
    color: #667eea;
    background: var(--card-bg-color);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  }
}

.dir-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dir-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 10px;

  .dir-item {
    position: relative;
    min-height: 132px;
    padding: 16px 10px 12px;
    border: 1px solid var(--border-color);
    flex-direction: column;
    justify-content: center;
    text-align: center;
    background: var(--card-bg-color);
    transition: background 0.15s, border-color 0.15s, transform 0.15s, box-shadow 0.15s;

    &:hover {
      border-color: rgba(102, 126, 234, 0.45);
      transform: translateY(-2px);
      box-shadow: 0 5px 14px rgba(0, 0, 0, 0.08);
    }

    &.active {
      border-color: #667eea;
      box-shadow: 0 0 0 1px rgba(102, 126, 234, 0.16);
    }
  }

  .dir-item-main {
    width: 100%;
    flex-direction: column;
    justify-content: center;
    gap: 9px;
  }

  .dir-icon {
    font-size: 38px;
    color: #e6a23c;
  }

  .dir-item-all .dir-icon {
    color: #667eea;
  }

  .dir-item-info {
    width: 100%;
  }

  .dir-item-name {
    display: -webkit-box;
    min-height: 36px;
    white-space: normal;
    line-height: 18px;
    overflow-wrap: anywhere;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .dir-item-meta {
    display: flex;
    justify-content: center;
    min-height: 16px;
    margin-top: 5px;
  }

  .dir-more-btn {
    position: absolute;
    top: 7px;
    right: 7px;
    opacity: 1;
    padding: 4px 6px;
  }

  .sidebar-empty {
    grid-column: 1 / -1;
  }
}

.dir-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--bg-secondary);
    .dir-more-btn { opacity: 1; }
  }

  &.active {
    background: rgba(102, 126, 234, 0.1);
    .dir-item-name { color: #667eea; font-weight: 500; }
    .dir-icon { color: #667eea; }
  }
}

.dir-item-all {
  flex-shrink: 0;

  .dir-icon {
    color: #667eea;
  }
}

.dir-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.dir-icon {
  font-size: 18px;
  color: var(--text-muted);
  flex-shrink: 0;
  &.scanning { color: #909399; animation: spin 1s linear infinite; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.dir-item-info {
  min-width: 0;
  flex: 1;
}

.dir-item-name {
  font-size: 13px;
  color: var(--text-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dir-item-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.scan-status {
  display: flex;
  align-items: center;
  gap: 3px;
  &.scanning { color: #909399; }
  &.error { color: #f56c6c; }
}

.doc-count { color: var(--text-muted); }

.dir-more-btn {
  opacity: 0;
  transition: opacity 0.15s;
  color: var(--text-muted);
  padding: 2px 4px;
  cursor: pointer;
  border-radius: 4px;
  &:hover { background: var(--border-color); color: var(--text-color); }
}

.sidebar-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted);
  i { font-size: 36px; margin-bottom: 8px; display: block; }
  p { margin: 0; font-size: 13px; }
  &-hint { font-size: 12px; margin-top: 4px !important; }
}

// 主内容区
.workspace-main {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.main-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  gap: 8px;
}

.main-empty-icon {
  font-size: 56px;
  color: #c0c4cc;
}

.main-empty-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-secondary);
  margin: 0;
}

.main-empty-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

// 目录信息行
.dir-info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.dir-info-path {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  min-width: 0;
  i { flex-shrink: 0; color: #909399; }
}

.dir-info-path-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}

.dir-info-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.dir-info-count {
  font-weight: 500;
  color: var(--text-secondary);
}

// 工具栏
.docs-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.docs-toolbar-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  .el-input { width: 260px; }
  .el-select { width: 120px; }
}

.docs-toolbar-spacer {
  min-width: 0;
}

.view-switcher {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.view-switch-button {
  width: 34px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 16px;
  transition: all 0.15s ease;

  &:hover {
    color: #667eea;
  }

  &.active {
    color: #667eea;
    background: var(--card-bg-color);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
  }
}

// 表格
.docs-table-wrapper {
  flex: 1;
  overflow-y: auto;
}

.file-type-icon {
  font-size: 16px;
}

.rel-path {
  font-size: 12px;
  color: var(--text-muted);
}

.text-muted {
  color: var(--text-muted);
}

// month view
.month-view {
  flex: 1;
  min-height: 180px;
}

.month-section {
  padding: 4px 0 22px;
}

.month-heading {
  position: sticky;
  top: -16px;
  z-index: 2;
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 12px 0 10px;
  margin-bottom: 4px;
  background: var(--card-bg-color);
  color: var(--text-color);
  font-size: 18px;
  font-weight: 600;
}

.month-count {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 400;
}

.month-document-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  gap: 14px;
}

.document-card {
  position: relative;
  min-width: 0;
  padding: 18px 12px 13px;
  border: 1px solid transparent;
  border-radius: 10px;
  text-align: center;
  cursor: pointer;
  outline: none;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;

  &:hover,
  &:focus {
    background: var(--bg-secondary);
    border-color: var(--border-color);
    transform: translateY(-2px);

    .document-card-actions { opacity: 1; }
  }
}

.document-card-actions {
  position: absolute;
  top: 7px;
  right: 7px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.card-action-button {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: var(--card-bg-color);
  color: var(--text-muted);
  cursor: pointer;
  box-shadow: 0 1px 5px rgba(0, 0, 0, 0.12);

  &:hover { color: #667eea; }
}

.document-icon-shell {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  margin-bottom: 10px;
  border-radius: 16px;
  background: var(--file-color);

  &::before {
    content: '';
    position: absolute;
    inset: 1px;
    border-radius: 15px;
    background: var(--card-bg-color);
    opacity: 0.9;
  }

  > i {
    position: relative;
    z-index: 1;
    font-size: 38px;
  }
}

.document-extension {
  position: absolute;
  right: -4px;
  bottom: 7px;
  z-index: 1;
  padding: 2px 4px;
  border-radius: 4px;
  background: var(--file-color);
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
}

.document-card-name {
  display: -webkit-box;
  min-height: 36px;
  overflow: hidden;
  color: var(--text-color);
  font-size: 14px;
  line-height: 18px;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.document-card-directory {
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 16px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.document-card-date {
  margin-top: 5px;
  color: var(--text-muted);
  font-size: 14px;
}

.month-empty {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--text-muted);
  font-size: 13px;

  i { font-size: 38px; }
}

.month-load-state {
  padding: 14px 0 6px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}

// path input row
.path-input-row {
  display: flex;
  align-items: center;
}

:deep(.local-doc-directory-dialog) {
  width: 70vw;
  max-width: 1040px;
  min-width: 680px;

  .el-dialog__body {
    padding-bottom: 12px;
  }
}

// dir browser
.dir-browser {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
}

.browser-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.browser-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  min-width: 0;
  font-size: 12px;
}

.browser-view-switcher {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--card-bg-color);
}

.browser-view-button {
  width: 26px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 13px;

  &:hover { color: #667eea; }

  &.active {
    color: #667eea;
    background: rgba(102, 126, 234, 0.12);
  }
}

.crumb {
  cursor: pointer;
  color: #667eea;
  padding: 1px 3px;
  border-radius: 3px;
  &:hover { background: rgba(102,126,234,0.1); }
  &-last { color: var(--text-color); cursor: default; font-weight: 500; &:hover { background: none; } }
  &-root { font-size: 14px; }
  &-sep { color: var(--text-muted); user-select: none; }
}

.browser-list {
  height: 42vh;
  min-height: 300px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 0;
}

.browser-card-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  align-content: start;
  gap: 8px;
  padding: 10px;

  .browser-entry {
    min-width: 0;
    min-height: 104px;
    padding: 13px 8px 10px;
    border: 1px solid var(--border-color);
    border-radius: 8px;
    flex-direction: column;
    justify-content: center;
    gap: 7px;
    text-align: center;

    &:hover {
      border-color: rgba(102, 126, 234, 0.5);
      background: rgba(102, 126, 234, 0.06);
    }
  }

  .browser-entry-icon {
    color: #e6a23c;
    font-size: 34px;
  }

  .browser-entry-name {
    display: -webkit-box;
    flex: none;
    width: 100%;
    min-height: 32px;
    white-space: normal;
    overflow-wrap: anywhere;
    line-height: 16px;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .browser-entry-arrow {
    display: none;
  }

  .browser-empty {
    grid-column: 1 / -1;
  }
}

.browser-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-color);
  transition: background 0.12s;

  &:hover { background: var(--bg-secondary); }
}

.browser-entry-icon { color: #909399; font-size: 15px; flex-shrink: 0; }
.browser-entry-name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.browser-entry-arrow { color: #c0c4cc; font-size: 12px; flex-shrink: 0; }

.browser-empty {
  text-align: center;
  padding: 20px;
  font-size: 12px;
  color: var(--text-muted);
}

.browser-footer {
  padding: 8px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

// form hint
.form-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

// dropdown danger
:deep(.danger-item) {
  color: #f56c6c !important;
}

@media screen and (max-width: 768px) {
  :deep(.local-doc-directory-dialog) {
    width: calc(100vw - 24px);
    min-width: 0;
    margin-top: 3vh !important;
  }

  .browser-list {
    height: 46vh;
    min-height: 240px;
  }

  .browser-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-layout {
    grid-template-columns: 1fr;
    height: auto;

    &.directory-grid-layout {
      grid-template-columns: 1fr;
    }
  }
  .workspace-sidebar {
    height: auto;
    max-height: 240px;
  }
  .docs-toolbar {
    grid-template-columns: 1fr auto;
  }
  .docs-toolbar-filters {
    flex-wrap: wrap;
    .el-input { width: 100%; }
  }
  .docs-toolbar-spacer { display: none; }
  .dir-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
