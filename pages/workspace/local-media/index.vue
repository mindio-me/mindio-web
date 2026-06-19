<template>
  <div class="local-media-page">
    <div class="workspace-layout">
      <!-- ========== 左侧栏 ========== -->
      <aside class="workspace-sidebar">
        <div class="sidebar-header">
          <el-button
            type="primary"
            size="small"
            icon="el-icon-folder-add"
            style="width:100%"
            @click="openAddDialog"
          >{{ $t('workspace.localMedia.addDirectory') }}</el-button>
        </div>

        <div v-loading="loadingDirs" class="dir-list">
          <div
            class="dir-item dir-item-all"
            :class="{ active: isAllSelected }"
            :title="$t('workspace.localMedia.emptyTitle')"
            @click="selectAllDirectories"
          >
            <div class="dir-item-main">
              <i class="el-icon-files dir-icon"></i>
              <div class="dir-item-info">
                <div class="dir-item-name">{{ $t('workspace.localMedia.allDir') }}</div>
                <div class="dir-item-meta">
                  <span class="doc-count">{{ $t('workspace.localMedia.fileCount', { n: totalFileCount }) }}</span>
                </div>
              </div>
            </div>
          </div>

          <div
            v-for="dir in directories"
            :key="dir.id"
            class="dir-item"
            :class="{ active: selectedDir && selectedDir.id === dir.id }"
            @click="selectDir(dir)"
          >
            <div class="dir-item-main">
              <i class="el-icon-folder dir-icon" :class="{ scanning: dir.scanStatus === 'SCANNING' }"></i>
              <div class="dir-item-info">
                <div class="dir-item-name">{{ dirDisplayName(dir) }}</div>
                <div class="dir-item-meta">
                  <span v-if="dir.scanStatus === 'SCANNING'" class="scan-status scanning">
                    <i class="el-icon-loading"></i> {{ $t('workspace.localMedia.scanning') }}
                  </span>
                  <span v-else-if="dir.scanStatus === 'ERROR'" class="scan-status error">
                    <i class="el-icon-warning-outline"></i> {{ $t('workspace.localMedia.scanError') }}
                  </span>
                  <span v-else class="doc-count">{{ $t('workspace.localMedia.fileCount', { n: dir.fileCount }) }}</span>
                </div>
              </div>
            </div>
            <el-dropdown trigger="click" @command="cmd => handleDirCommand(cmd, dir)" @click.native.stop>
              <span class="dir-more-btn" @click.stop><i class="el-icon-more"></i></span>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="rescan" icon="el-icon-refresh">{{ $t('workspace.localMedia.rescan') }}</el-dropdown-item>
                <el-dropdown-item command="delete" icon="el-icon-delete" class="danger-item">{{ $t('workspace.localMedia.deleteDir') }}</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </div>

          <div v-if="!loadingDirs && directories.length === 0" class="sidebar-empty">
            <i class="el-icon-picture-outline"></i>
            <p>{{ $t('workspace.localMedia.noDirs') }}</p>
            <p class="sidebar-empty-hint">{{ $t('workspace.localMedia.noDirsHint') }}</p>
          </div>
        </div>
      </aside>

      <!-- ========== 主内容区 ========== -->
      <main class="workspace-main">
        <div v-if="!selectedDir" class="main-empty">
          <i class="el-icon-picture-outline main-empty-icon"></i>
          <p class="main-empty-title">{{ $t('workspace.localMedia.emptyTitle') }}</p>
          <p class="main-empty-hint">{{ $t('workspace.localMedia.emptyHint') }}</p>
        </div>

        <template v-else>
          <!-- 目录信息行 -->
          <div class="dir-info-bar">
            <div class="dir-info-path">
              <i :class="isAllSelected ? 'el-icon-files' : 'el-icon-folder-opened'"></i>
              <span v-if="isAllSelected" class="dir-info-path-text">{{ $t('workspace.localMedia.allDirsLabel') }}</span>
              <span v-else class="dir-info-path-text" :title="selectedDir.dirPath">{{ selectedDir.dirPath }}</span>
            </div>
            <div class="dir-info-meta">
              <span v-if="isAllSelected">{{ $t('workspace.localMedia.dirCount', { n: directories.length }) }}</span>
              <span v-else-if="selectedDir.lastScanAt">{{ $t('workspace.localMedia.lastScan') }}{{ formatDate(selectedDir.lastScanAt) }}</span>
              <span v-else class="text-muted">{{ $t('workspace.localMedia.notScanned') }}</span>
              <span class="dir-info-count">{{ $t('workspace.localMedia.totalFiles', { n: isAllSelected ? totalFileCount : selectedDir.fileCount }) }}</span>
            </div>
          </div>

          <el-alert
            v-if="!isAllSelected && selectedDir.scanStatus === 'ERROR' && selectedDir.lastScanError"
            :title="$t('workspace.localMedia.scanErrorPrefix') + selectedDir.lastScanError"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
          />

          <!-- 工具栏 -->
          <div class="media-toolbar">
            <el-input
              v-model="keyword"
              :placeholder="$t('workspace.localMedia.searchPlaceholder')"
              prefix-icon="el-icon-search"
              size="small"
              clearable
              style="width:260px"
              @input="onSearch"
            />
            <div class="type-tabs">
              <span
                v-for="tab in typeTabs"
                :key="tab.value"
                class="type-tab"
                :class="{ active: filterType === tab.value }"
                @click="setFilterType(tab.value)"
              >{{ tab.label }}</span>
            </div>
          </div>

          <!-- 日期分段瀑布流 + 右侧面板 -->
          <div class="media-content-area">
            <div
              ref="mediaScroller"
              v-loading="loadingFiles && files.length === 0"
              class="media-timeline"
              @scroll.passive="handleMediaScroll"
            >
              <section v-for="group in dateGroups" :key="group.key" class="date-section">
                <div class="date-heading">
                  <span>{{ group.label }}</span>
                  <span class="date-count">{{ group.files.length }}{{ $t('workspace.localMedia.dateCountSuffix') }}</span>
                </div>

                <div class="media-waterfall" :class="{ 'panel-open': !!selectedFile }">
                  <article
                    v-for="file in group.files"
                    :key="file.id"
                    class="media-card"
                    :class="{ active: selectedFile && selectedFile.id === file.id }"
                    tabindex="0"
                    @click="selectFile(file)"
                    @keydown.enter="selectFile(file)"
                  >
                    <div
                      class="media-card-thumb"
                      :class="mediaTypeClass(file.mediaType)"
                      :style="thumbnailStyle(file)"
                    >
                      <img
                        v-if="file.mediaType === 'IMAGE'"
                        :src="thumbnailUrl(file)"
                        :alt="file.fileName"
                        loading="lazy"
                        @error="$event.target.style.display='none'"
                      >
                      <i v-else-if="file.mediaType === 'VIDEO'" class="el-icon-video-camera media-type-icon"></i>
                      <i v-else class="el-icon-headset media-type-icon"></i>
                    </div>
                    <div class="media-card-name">{{ file.fileName }}</div>
                    <div class="media-card-meta">
                      <span>{{ (file.fileExtension || '').toUpperCase() }}</span>
                      <span>{{ formatSize(file.fileSize) }}</span>
                    </div>
                    <div class="path-overlay">{{ file.absolutePath }}</div>
                  </article>
                </div>
              </section>

              <div v-if="!loadingFiles && dateGroups.length === 0" class="grid-empty">
                <p>{{ !isAllSelected && selectedDir.scanStatus === 'SCANNING' ? $t('workspace.localMedia.scanningWait') : $t('workspace.localMedia.noMatchFiles') }}</p>
              </div>

              <div v-if="dateGroups.length" class="media-load-state">
                <span v-if="loadingFiles"><i class="el-icon-loading"></i> {{ $t('workspace.localMedia.loadingMore') }}</span>
                <span v-else-if="hasMoreFiles">{{ $t('workspace.localMedia.scrollToLoad') }}</span>
                <span v-else>{{ $t('workspace.localMedia.allLoaded') }}</span>
              </div>
            </div>

            <!-- 右侧详情面板 -->
            <transition name="panel-slide">
              <div v-if="selectedFile" class="detail-panel">
                <div class="panel-header">
                  <span class="panel-title">{{ $t('workspace.localMedia.detailTitle') }}</span>
                  <span class="panel-close" @click="selectedFile = null">✕</span>
                </div>

                <!-- 图片预览 -->
                <div v-if="selectedFile.mediaType === 'IMAGE'" class="panel-preview panel-preview--image">
                  <img :src="fileUrl(selectedFile)" :alt="selectedFile.fileName">
                </div>

                <!-- 视频播放器 -->
                <div v-else-if="selectedFile.mediaType === 'VIDEO'" class="panel-preview panel-preview--video">
                  <video :key="selectedFile.id" controls :src="fileUrl(selectedFile)"></video>
                </div>

                <!-- 音频播放器 -->
                <div v-else class="panel-preview panel-preview--audio">
                  <i class="el-icon-headset audio-icon"></i>
                  <audio :key="selectedFile.id" controls :src="fileUrl(selectedFile)"></audio>
                </div>

                <!-- 文件信息 -->
                <div class="panel-meta">
                  <div class="panel-filename">{{ selectedFile.fileName }}</div>
                  <div class="meta-row"><span>{{ $t('workspace.localMedia.metaFormat') }}</span><span>{{ (selectedFile.fileExtension || '').toUpperCase() }}</span></div>
                  <div class="meta-row"><span>{{ $t('workspace.localMedia.metaSize') }}</span><span>{{ formatSize(selectedFile.fileSize) }}</span></div>
                  <div v-if="selectedFile.imageWidth" class="meta-row">
                    <span>{{ $t('workspace.localMedia.metaDimensions') }}</span><span>{{ selectedFile.imageWidth }} × {{ selectedFile.imageHeight }}</span>
                  </div>
                  <div class="meta-row"><span>{{ $t('workspace.localMedia.metaModified') }}</span><span>{{ formatDate(selectedFile.fileLastModified) }}</span></div>
                  <div class="meta-row">
                    <span>{{ $t('workspace.localMedia.metaPath') }}</span>
                    <span class="meta-path" :title="selectedFile.absolutePath">{{ selectedFile.absolutePath }}</span>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="panel-actions">
                  <el-button size="small" type="primary" icon="el-icon-folder-opened" style="width:100%" @click="openFile(selectedFile)">
                    {{ $t('workspace.localMedia.openFile') }}
                  </el-button>
                  <el-button size="small" icon="el-icon-document-copy" style="width:100%;margin-left:0;margin-top:8px" @click="copyPath(selectedFile)">
                    {{ $t('workspace.localMedia.copyPath') }}
                  </el-button>
                </div>
              </div>
            </transition>
          </div>

        </template>
      </main>
    </div>

    <!-- 添加目录 Dialog -->
    <el-dialog :title="$t('workspace.localMedia.dialogTitle')" :visible.sync="addDialogVisible" width="560px" @close="resetForm">
      <el-form ref="addForm" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item :label="$t('workspace.localMedia.dirPath')" prop="dirPath">
          <div class="path-input-row">
            <el-input v-model="addForm.dirPath" :placeholder="$t('workspace.localMedia.dirPathPlaceholder')" clearable style="flex:1" @change="closeBrowser" />
            <el-button icon="el-icon-folder-opened" style="margin-left:8px;flex-shrink:0" @click="toggleBrowser">{{ $t('workspace.localMedia.browse') }}</el-button>
          </div>
        </el-form-item>

        <div v-if="browserVisible" class="dir-browser">
          <div class="browser-breadcrumb">
            <span class="crumb crumb-root" @click="navigateTo('')"><i class="el-icon-s-home"></i></span>
            <template v-for="(crumb, i) in breadcrumbs">
              <span :key="'sep-' + i" class="crumb-sep">/</span>
              <span :key="'crumb-' + i" class="crumb" :class="{ 'crumb-last': i === breadcrumbs.length - 1 }" @click="navigateTo(crumb.path)">{{ crumb.name }}</span>
            </template>
          </div>
          <div v-loading="browserLoading" class="browser-list">
            <div v-for="entry in browserEntries" :key="entry.path" class="browser-entry" @click="navigateTo(entry.path)">
              <i class="el-icon-folder browser-entry-icon"></i>
              <span class="browser-entry-name">{{ entry.name }}</span>
              <i class="el-icon-arrow-right browser-entry-arrow"></i>
            </div>
            <div v-if="!browserLoading && browserEntries.length === 0" class="browser-empty">{{ $t('workspace.localMedia.noSubDirs') }}</div>
          </div>
          <div v-if="browserCurrentPath" class="browser-footer">
            <el-button type="primary" size="small" icon="el-icon-check" @click="selectBrowserPath">{{ $t('workspace.localMedia.selectCurrent', { name: browserCurrentName }) }}</el-button>
          </div>
        </div>

        <el-form-item :label="$t('workspace.localMedia.displayName')">
          <el-input v-model="addForm.displayName" :placeholder="$t('workspace.localMedia.displayNamePlaceholder')" clearable />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="addDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="adding" @click="submitAdd">{{ $t('workspace.localMedia.submitAdd') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
const ALL_DIRECTORIES_ID = 'all'

export default {
  name: 'LocalMediaPage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    return {
      directories: [],
      loadingDirs: false,
      selectedDir: null,

      files: [],
      total: 0,
      currentPage: 0,
      pageSize: 60,
      hasMoreFiles: true,
      loadingFiles: false,
      reloadFilesPending: false,
      keyword: '',
      filterType: '',
      searchTimer: null,

      selectedFile: null,


      addDialogVisible: false,
      adding: false,
      addForm: { dirPath: '', displayName: '' },

      browserVisible: false,
      browserLoading: false,
      browserCurrentPath: '',
      browserEntries: [],
    }
  },
  computed: {
    typeTabs() {
      return [
        { label: this.$t('workspace.localMedia.typeAll'), value: '' },
        { label: this.$t('workspace.localMedia.typeImage'), value: 'IMAGE' },
        { label: this.$t('workspace.localMedia.typeVideo'), value: 'VIDEO' },
        { label: this.$t('workspace.localMedia.typeAudio'), value: 'AUDIO' },
      ]
    },
    addRules() {
      return {
        dirPath: [{ required: true, message: this.$t('workspace.localMedia.dirPathRequired'), trigger: 'blur' }],
      }
    },
    isAllSelected() {
      return this.selectedDir && this.selectedDir.id === ALL_DIRECTORIES_ID
    },
    totalFileCount() {
      return this.directories.reduce((total, dir) => total + (Number(dir.fileCount) || 0), 0)
    },
    dateGroups() {
      const groups = []
      const groupMap = new Map()
      this.files.forEach(file => {
        const date = file.fileLastModified ? new Date(file.fileLastModified) : null
        const validDate = date && !Number.isNaN(date.getTime())
        const key = validDate
          ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
          : 'unknown'
        if (!groupMap.has(key)) {
          const group = {
            key,
            label: validDate
              ? date.toLocaleDateString(this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' })
              : this.$t('workspace.localMedia.unknownTime'),
            files: [],
          }
          groupMap.set(key, group)
          groups.push(group)
        }
        groupMap.get(key).files.push(file)
      })
      return groups
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
  },
  async created() {
    await this.loadDirectories()
    this.selectAllDirectories()
  },
  beforeDestroy() {
    clearTimeout(this.searchTimer)
  },
  methods: {
    async loadDirectories() {
      this.loadingDirs = true
      try {
        this.directories = await this.$localMediaService.getDirectories()
      } catch {
        this.$message.error(this.$t('workspace.localMedia.loadDirsFailed'))
      } finally {
        this.loadingDirs = false
      }
    },

    selectDir(dir) {
      this.selectedDir = dir
      this.keyword = ''
      this.filterType = ''
      this.currentPage = 0
      this.files = []
      this.total = 0
      this.hasMoreFiles = true
      this.selectedFile = null
      this.resetMediaScroll()
      this.loadFiles({ reset: true })
    },

    selectAllDirectories() {
      this.selectDir({
        id: ALL_DIRECTORIES_ID,
        displayName: this.$t('workspace.localMedia.allDir'),
        fileCount: this.totalFileCount,
      })
    },

    getFilesPage(params) {
      if (this.isAllSelected) {
        return this.$localMediaService.getAllFiles(params)
      }
      return this.$localMediaService.getFiles(this.selectedDir.id, params)
    },

    async loadFiles({ reset = true } = {}) {
      if (!this.selectedDir) return
      if (this.loadingFiles) {
        if (reset) this.reloadFilesPending = true
        return
      }
      if (!reset && !this.hasMoreFiles) return
      if (reset) {
        this.currentPage = 0
        this.files = []
        this.total = 0
        this.hasMoreFiles = true
        this.selectedFile = null
        this.resetMediaScroll()
      }
      this.loadingFiles = true
      try {
        const res = await this.getFilesPage({
          keyword: this.keyword || undefined,
          mediaType: this.filterType || undefined,
          page: this.currentPage,
          size: this.pageSize,
          sortBy: 'fileLastModified',
          direction: 'DESC',
        })
        const incoming = res.content || []
        this.files = reset ? incoming : [...this.files, ...incoming]
        this.total = res.totalElements || 0
        this.hasMoreFiles = !res.last && this.files.length < this.total
        if (this.hasMoreFiles) this.currentPage += 1
      } catch {
        this.$message.error(this.$t('workspace.localMedia.loadFilesFailed'))
      } finally {
        this.loadingFiles = false
        if (this.reloadFilesPending) {
          this.reloadFilesPending = false
          this.loadFiles({ reset: true })
        }
      }
    },

    onSearch() {
      clearTimeout(this.searchTimer)
      this.searchTimer = setTimeout(() => this.loadFiles({ reset: true }), 300)
    },

    setFilterType(val) {
      this.filterType = val
      this.loadFiles({ reset: true })
    },

    handleMediaScroll() {
      if (this.loadingFiles || !this.hasMoreFiles) return
      const el = this.$refs.mediaScroller
      if (!el) return
      const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
      if (distanceToBottom < 360) {
        this.loadFiles({ reset: false })
      }
    },

    resetMediaScroll() {
      this.$nextTick(() => {
        if (this.$refs.mediaScroller) this.$refs.mediaScroller.scrollTop = 0
      })
    },

    selectFile(file) {
      this.selectedFile = this.selectedFile && this.selectedFile.id === file.id ? null : file
    },

    thumbnailUrl(file) {
      const token = (this.$auth.strategy.token.get() || '').replace(/^Bearer\s+/i, '')
      return this.$localMediaService.thumbnailUrl(file.id, token)
    },

    fileUrl(file) {
      const token = (this.$auth.strategy.token.get() || '').replace(/^Bearer\s+/i, '')
      return this.$localMediaService.fileUrl(file.id, token)
    },

    openFile(file) {
      window.open(this.fileUrl(file), '_blank')
    },

    async copyPath(file) {
      try {
        await navigator.clipboard.writeText(file.absolutePath)
        this.$message.success(this.$t('workspace.localMedia.copiedPath'))
      } catch {
        this.$message.error(this.$t('workspace.localMedia.copyFailed') + file.absolutePath)
      }
    },

    openAddDialog() { this.addDialogVisible = true },

    resetForm() {
      this.addForm = { dirPath: '', displayName: '' }
      this.$refs.addForm && this.$refs.addForm.resetFields()
      this.browserVisible = false
      this.browserCurrentPath = ''
      this.browserEntries = []
    },

    async toggleBrowser() {
      if (this.browserVisible) { this.browserVisible = false; return }
      this.browserVisible = true
      await this.navigateTo(this.addForm.dirPath.trim() || '')
    },

    closeBrowser() { this.browserVisible = false },

    async navigateTo(path) {
      this.browserLoading = true
      try {
        const res = await this.$localMediaService.listDirectory(path)
        this.browserCurrentPath = res.currentPath || ''
        this.browserEntries = res.entries || []
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localMedia.cannotReadDir'))
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
      try { await this.$refs.addForm.validate() } catch { return }
      this.adding = true
      try {
        const newDir = await this.$localMediaService.addDirectory({
          dirPath: this.addForm.dirPath.trim(),
          displayName: this.addForm.displayName.trim() || undefined,
        })
        this.directories.unshift(newDir)
        this.addDialogVisible = false
        this.selectDir(newDir)
        if (newDir.scanStatus === 'ERROR') {
          this.$message.warning(this.$t('workspace.localMedia.addWarning', { msg: newDir.lastScanError }))
        } else {
          this.$message.success(this.$t('workspace.localMedia.addSuccess', { n: newDir.fileCount }))
        }
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localMedia.addFailed'))
      } finally {
        this.adding = false
      }
    },

    async handleDirCommand(cmd, dir) {
      if (cmd === 'rescan') await this.doRescan(dir)
      else if (cmd === 'delete') await this.doDelete(dir)
    },

    async doRescan(dir) {
      const idx = this.directories.findIndex(d => d.id === dir.id)
      if (idx >= 0) this.$set(this.directories[idx], 'scanStatus', 'SCANNING')
      try {
        const updated = await this.$localMediaService.rescan(dir.id)
        if (idx >= 0) this.$set(this.directories, idx, updated)
        if (this.selectedDir && this.selectedDir.id === dir.id) {
          this.selectedDir = updated
          this.loadFiles()
        } else if (this.isAllSelected) {
          this.loadFiles()
        }
        if (updated.scanStatus === 'ERROR') {
          this.$message.warning(this.$t('workspace.localMedia.rescanWarning', { msg: updated.lastScanError }))
        } else {
          this.$message.success(this.$t('workspace.localMedia.rescanSuccess', { n: updated.fileCount }))
        }
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localMedia.rescanFailed'))
        if (idx >= 0) this.$set(this.directories[idx], 'scanStatus', 'ERROR')
      }
    },

    async doDelete(dir) {
      try {
        await this.$confirm(
          this.$t('workspace.localMedia.deleteConfirmMsg', { name: this.dirDisplayName(dir) }),
          this.$t('workspace.localMedia.deleteTitle'),
          { confirmButtonText: this.$t('common.delete'), cancelButtonText: this.$t('common.cancel'), type: 'warning' }
        )
      } catch { return }
      try {
        await this.$localMediaService.deleteDirectory(dir.id)
        this.directories = this.directories.filter(d => d.id !== dir.id)
        this.selectedFile = null
        if (this.selectedDir && this.selectedDir.id === dir.id) {
          this.selectAllDirectories()
        } else if (this.isAllSelected) {
          this.loadFiles()
        }
        this.$message.success(this.$t('workspace.localMedia.deleteSuccess'))
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.localMedia.deleteFailed'))
      }
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

    mediaTypeClass(mediaType) {
      return {
        'thumb--image': mediaType === 'IMAGE',
        'thumb--video': mediaType === 'VIDEO',
        'thumb--audio': mediaType === 'AUDIO',
      }
    },

    thumbnailStyle(file) {
      if (file.mediaType !== 'IMAGE' || !file.imageWidth || !file.imageHeight) return {}
      const ratio = Math.min(2, Math.max(0.65, file.imageWidth / file.imageHeight))
      return { aspectRatio: String(ratio) }
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
  },
}
</script>

<style scoped lang="scss">
.local-media-page {
  height: 100%;
  overflow: hidden;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  height: calc(100vh - 100px);
  gap: 12px;
}

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

.sidebar-header { flex-shrink: 0; }

.dir-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dir-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover { background: var(--bg-secondary); .dir-more-btn { opacity: 1; } }
  &.active {
    background: rgba(102, 126, 234, 0.1);
    .dir-item-name { color: #667eea; font-weight: 500; }
    .dir-icon { color: #667eea; }
  }
}

.dir-item-all {
  flex-shrink: 0;

  .dir-icon { color: #667eea; }
}

.dir-item-main { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
.dir-icon { font-size: 18px; color: var(--text-muted); flex-shrink: 0; &.scanning { animation: spin 1s linear infinite; } }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.dir-item-info { min-width: 0; flex: 1; }
.dir-item-name { font-size: 13px; color: var(--text-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dir-item-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.scan-status { display: flex; align-items: center; gap: 3px; &.scanning { color: #909399; } &.error { color: #f56c6c; } }
.doc-count { color: var(--text-muted); }
.dir-more-btn { opacity: 0; transition: opacity 0.15s; color: var(--text-muted); padding: 2px 4px; cursor: pointer; border-radius: 4px; &:hover { background: var(--border-color); color: var(--text-color); } }

.sidebar-empty {
  text-align: center; padding: 40px 0; color: var(--text-muted);
  i { font-size: 36px; margin-bottom: 8px; display: block; }
  p { margin: 0; font-size: 13px; }
  &-hint { font-size: 12px; margin-top: 4px !important; }
}

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
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: var(--text-muted); gap: 8px;
}
.main-empty-icon { font-size: 56px; color: #c0c4cc; }
.main-empty-title { font-size: 15px; font-weight: 500; color: var(--text-secondary); margin: 0; }
.main-empty-hint { font-size: 13px; color: var(--text-muted); margin: 0; }

.dir-info-bar {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 8px; padding-bottom: 12px; border-bottom: 1px solid var(--border-color);
}
.dir-info-path {
  display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); min-width: 0;
  i { flex-shrink: 0; color: #909399; }
}
.dir-info-path-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 400px; }
.dir-info-meta { display: flex; align-items: center; gap: 16px; font-size: 12px; color: var(--text-muted); flex-shrink: 0; }
.dir-info-count { font-weight: 500; color: var(--text-secondary); }

.media-toolbar {
  display: flex; align-items: center; gap: 12px; flex-shrink: 0;
}

.type-tabs { display: flex; gap: 4px; }
.type-tab {
  padding: 4px 12px; border-radius: 14px; font-size: 12px; cursor: pointer;
  color: var(--text-muted); background: var(--bg-secondary); transition: all 0.15s;
  &:hover { color: var(--text-color); }
  &.active { background: rgba(102,126,234,0.15); color: #667eea; font-weight: 500; }
}

.media-content-area {
  flex: 1; min-height: 0; overflow: hidden; position: relative; display: flex; gap: 12px;
}

.media-timeline {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 0 4px 4px 2px;
}

.date-section {
  padding-bottom: 20px;
}

.date-heading {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 10px 0 9px;
  margin-bottom: 4px;
  background: var(--card-bg-color);
  color: var(--text-color);
  font-size: 17px;
  font-weight: 600;
}

.date-count {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 400;
}

.media-waterfall {
  column-width: 180px;
  column-gap: 12px;

  &.panel-open {
    column-width: 160px;
  }
}

.media-card {
  position: relative;
  display: inline-block;
  width: 100%;
  margin: 0 0 12px;
  break-inside: avoid;
  cursor: pointer;
  border-radius: 9px;
  overflow: hidden;
  border: 2px solid transparent;
  outline: none;
  transition: border-color 0.15s, transform 0.1s, box-shadow 0.15s;
  background: var(--bg-secondary);
  &:hover,
  &:focus {
    transform: translateY(-2px);
    border-color: var(--border-color);
    box-shadow: 0 5px 14px rgba(0, 0, 0, 0.1);
  }
  &:hover .path-overlay { opacity: 1; }
  &.active { border-color: #667eea; }
}

.path-overlay {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  font-size: 10px;
  line-height: 1.4;
  padding: 5px 7px;
  word-break: break-all;
  opacity: 0;
  transition: opacity 0.18s;
  pointer-events: none;
}

.media-card-thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--card-bg-color);
  img { width: 100%; height: 100%; object-fit: cover; display: block; }
  &.thumb--video { background: rgba(52, 211, 153, 0.15); }
  &.thumb--audio { background: rgba(139, 92, 246, 0.15); }
}

.media-type-icon { font-size: 36px; &.el-icon-video-camera { color: #34d399; } &.el-icon-headset { color: #8b5cf6; } }

.media-card-name {
  padding: 7px 8px 2px; font-size: 12px; color: var(--text-secondary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.media-card-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 8px 7px;
  color: var(--text-muted);
  font-size: 10px;
}

.grid-empty { width: 100%; text-align: center; padding: 60px 0; color: var(--text-muted); font-size: 14px; }

.media-load-state {
  padding: 8px 0 4px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}

.detail-panel {
  width: 300px; flex-shrink: 0; background: var(--card-bg-color);
  border: 1px solid var(--border-color); border-radius: 10px;
  display: flex; flex-direction: column; overflow: hidden;
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; border-bottom: 1px solid var(--border-color); flex-shrink: 0;
}
.panel-title { font-size: 13px; color: var(--text-secondary); }
.panel-close { cursor: pointer; color: var(--text-muted); font-size: 14px; &:hover { color: var(--text-color); } }

.panel-preview {
  flex-shrink: 0;
  &--image {
    background: var(--bg-secondary);
    img { width: 100%; max-height: 220px; object-fit: contain; display: block; }
  }
  &--video {
    video { width: 100%; max-height: 200px; display: block; background: #000; }
  }
  &--audio {
    padding: 24px 16px; display: flex; flex-direction: column; align-items: center; gap: 12px;
    background: var(--bg-secondary);
    audio { width: 100%; }
  }
}

.audio-icon { font-size: 40px; color: #8b5cf6; }

.panel-meta {
  padding: 12px 14px; overflow-y: auto; flex: 1;
}
.panel-filename { font-size: 13px; color: var(--text-color); word-break: break-all; margin-bottom: 12px; font-weight: 500; }
.meta-row {
  display: flex; justify-content: space-between; gap: 8px; margin-bottom: 6px; font-size: 12px;
  span:first-child { color: var(--text-muted); flex-shrink: 0; }
  span:last-child { color: var(--text-secondary); text-align: right; }
}
.meta-path { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.panel-actions { padding: 12px 14px; border-top: 1px solid var(--border-color); flex-shrink: 0; }

.panel-slide-enter-active, .panel-slide-leave-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.panel-slide-enter, .panel-slide-leave-to { transform: translateX(20px); opacity: 0; }

.text-muted { color: var(--text-muted); }

.path-input-row { display: flex; align-items: center; }
.dir-browser { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; margin-bottom: 16px; }
.browser-breadcrumb {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2px; padding: 8px 12px;
  background: var(--bg-secondary); border-bottom: 1px solid var(--border-color); font-size: 12px;
}
.crumb {
  cursor: pointer; color: #667eea; padding: 1px 3px; border-radius: 3px;
  &:hover { background: rgba(102,126,234,0.1); }
  &-last { color: var(--text-color); cursor: default; font-weight: 500; &:hover { background: none; } }
  &-root { font-size: 14px; }
  &-sep { color: var(--text-muted); user-select: none; }
}
.browser-list { max-height: 220px; overflow-y: auto; padding: 4px 0; }
.browser-entry {
  display: flex; align-items: center; gap: 8px; padding: 7px 14px;
  cursor: pointer; font-size: 13px; color: var(--text-color); transition: background 0.12s;
  &:hover { background: var(--bg-secondary); }
}
.browser-entry-icon { color: #909399; font-size: 15px; flex-shrink: 0; }
.browser-entry-name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.browser-entry-arrow { color: #c0c4cc; font-size: 12px; flex-shrink: 0; }
.browser-empty { text-align: center; padding: 20px; font-size: 12px; color: var(--text-muted); }
.browser-footer { padding: 8px 12px; border-top: 1px solid var(--border-color); background: var(--bg-secondary); }

:deep(.danger-item) { color: #f56c6c !important; }
</style>
