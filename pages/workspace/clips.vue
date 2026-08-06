<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="clips-page">
    <div class="clips-container">

      <!-- 筛选栏 -->
      <div class="clips-toolbar">
        <el-input
          v-model="searchKeyword"
          :placeholder="$t('workspace.clips.searchPlaceholder')"
          prefix-icon="el-icon-search"
          size="small"
          clearable
          style="width:260px"
          @input="onSearch"
        />
        <el-radio-group v-model="filterType" size="small" style="margin-left:16px" @change="onSearch">
          <el-radio-button label="">{{ $t('workspace.clips.filterAll') }}</el-radio-button>
          <el-radio-button label="WEBPAGE">{{ $t('workspace.clips.filterWebpage') }}</el-radio-button>
          <el-radio-button label="WECHAT_ARTICLE">{{ $t('workspace.clips.filterWechat') }}</el-radio-button>
        </el-radio-group>
        <el-button
          size="small"
          icon="el-icon-upload2"
          style="margin-left:16px"
          @click="$router.push('/workspace/bookmark-import')"
        >{{ $t('workspace.clips.importBookmarks') }}</el-button>

        <div class="agent-generate-group" v-for="type in ['CLUSTER', 'TIMELINE']" :key="type">
          <el-button
            size="small"
            :disabled="webpageClipCount === 0 || agentJobs[type].status === 'RUNNING' || generateInFlight[type]"
            @click="startGenerate(type)"
          >{{ agentButtonLabel(type) }}</el-button>
          <a
            v-if="agentJobs[type].status === 'DONE'"
            class="agent-last-generated-link"
            @click="$router.push('/workspace/notes/' + agentJobs[type].resultNoteId)"
          >{{ $t('workspace.clips.generateLastAt', { date: formatDate(agentJobs[type].finishedAt) }) }}</a>
        </div>
      </div>

      <!-- 卡片列表 -->
      <div v-loading="loading" class="clips-grid">
        <div
          v-for="clip in clips"
          :key="clip.id"
          class="clip-card"
          @click="onCardClick(clip)"
        >
          <div class="clip-card-header">
            <span class="clip-source-badge" :class="sourceClass(clip.sourceType)">
              {{ sourceLabel(clip.sourceType) }}
            </span>
            <span class="clip-card-header-right">
              <span class="clip-date">{{ formatDate(clip.createdAt) }}</span>
              <el-dropdown
                v-if="clip.extractionMode === 'LINK_ONLY'"
                trigger="click"
                @click.native.stop
                @command="cmd => handleCardCommand(cmd, clip)"
              >
                <i class="el-icon-more card-more-icon"></i>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item command="edit">{{ $t('workspace.clips.editTitle') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" style="color:#f56c6c">{{ $t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </span>
          </div>
          <div class="clip-card-title">{{ clip.title }}</div>
          <div v-if="clip.sourceAuthor || clip.sourceTitle" class="clip-card-meta">
            {{ clip.sourceAuthor }}{{ clip.sourceAuthor && clip.sourceTitle ? ' · ' : '' }}{{ clip.sourceTitle }}
          </div>
          <div class="clip-card-excerpt">{{ clip.excerpt }}</div>
          <div v-if="clip.tags && clip.tags.length" class="clip-card-tags">
            <el-tag v-for="t in clip.tags" :key="t.id" size="mini" type="info">{{ t.name }}</el-tag>
          </div>
        </div>

        <div v-if="!loading && clips.length === 0" class="clips-empty">
          <i class="el-icon-document-copy" style="font-size:48px;color:#c0c4cc"></i>
          <p>{{ $t('workspace.clips.empty') }}</p>
        </div>
      </div>

      <!-- 分页 -->
      <el-pagination
        v-if="total > pageSize"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page.sync="currentPage"
        style="text-align:center;margin-top:24px"
        @current-change="loadClips"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      :visible.sync="drawerVisible"
      direction="rtl"
      size="820px"
      :with-header="false"
    >
      <div v-if="selectedClip" class="clip-drawer">
        <div class="drawer-header">
          <span class="clip-source-badge" :class="sourceClass(selectedClip.sourceType)">
            {{ sourceLabel(selectedClip.sourceType) }}
          </span>
          <div class="drawer-actions">
            <el-button
              size="mini"
              type="danger"
              plain
              icon="el-icon-delete"
              @click="deleteClip(selectedClip)"
            >{{ $t('common.delete') }}</el-button>
            <el-button size="mini" icon="el-icon-close" @click="drawerVisible = false" />
          </div>
        </div>

        <div class="drawer-title-row">
          <template v-if="editingTitle">
            <el-input
              ref="titleInput"
              v-model="titleDraft"
              size="small"
              class="drawer-title-input"
              @keyup.enter.native="saveTitle"
              @keyup.esc.native="cancelEditTitle"
            />
            <el-button size="mini" type="primary" :loading="savingTitle" @click="saveTitle">{{ $t('common.confirm') }}</el-button>
            <el-button size="mini" @click="cancelEditTitle">{{ $t('common.cancel') }}</el-button>
          </template>
          <template v-else>
            <h2 class="drawer-title">{{ selectedClip.title }}</h2>
            <i class="el-icon-edit drawer-title-edit-icon" @click="startEditTitle"></i>
          </template>
        </div>

        <div v-if="selectedClip.sourceUrl" class="drawer-original-link">
          <a :href="selectedClip.sourceUrl" target="_blank" rel="noopener">
            <el-button size="small" icon="el-icon-link">{{ $t('workspace.clips.openOriginal') }}</el-button>
          </a>
          <span v-if="selectedClip.sourceAuthor" class="drawer-author"> · {{ selectedClip.sourceAuthor }}</span>
        </div>

        <el-divider />

        <!-- 已关联的笔记 -->
        <div class="drawer-section">
          <div class="drawer-section-title">
            {{ $t('workspace.clips.linkedNotes') }}
            <el-button
              type="text"
              size="mini"
              icon="el-icon-plus"
              @click="showLinkNote = true"
            >{{ $t('workspace.clips.addLink') }}</el-button>
          </div>
          <div v-if="linkedNotes.length === 0" class="drawer-empty">{{ $t('workspace.clips.noLinkedNotes') }}</div>
          <div v-for="ref in linkedNotes" :key="ref.refId" class="linked-note-item">
            <span class="linked-note-title" @click="goToNote(ref.noteId)">
              <i class="el-icon-document"></i> Note #{{ ref.noteId }}
            </span>
            <span v-if="ref.userNote" class="linked-note-annotation">{{ ref.userNote }}</span>
            <el-button
              type="text"
              size="mini"
              style="color:#f56c6c"
              @click="unlinkNote(ref)"
            >{{ $t('workspace.clips.unlink') }}</el-button>
          </div>

          <!-- 关联到笔记输入 -->
          <div v-if="showLinkNote" class="link-note-form">
            <el-input
              v-model="linkNoteId"
              size="small"
              :placeholder="$t('workspace.clips.noteIdPlaceholder')"
              style="width:120px"
            />
            <el-input
              v-model="linkUserNote"
              size="small"
              :placeholder="$t('workspace.clips.annotationPlaceholder')"
              style="width:180px;margin-left:8px"
            />
            <el-button
              size="small"
              type="primary"
              style="margin-left:8px"
              :loading="linking"
              @click="linkNote"
            >{{ $t('common.confirm') }}</el-button>
            <el-button size="small" @click="showLinkNote = false">{{ $t('common.cancel') }}</el-button>
          </div>
        </div>

        <el-divider />

        <div v-if="isLinkOnlyOrFailed" class="drawer-section link-only-panel">
          <p class="link-only-message">
            {{ selectedClip.extractionStatus === 'FAILED'
              ? $t('workspace.clips.extractionFailedNotice')
              : $t('workspace.clips.linkOnlyNotice') }}
          </p>
          <a :href="selectedClip.sourceUrl" target="_blank" rel="noopener">
            <el-button type="primary" icon="el-icon-link">{{ $t('workspace.clips.openOriginalButton') }}</el-button>
          </a>
        </div>

        <div v-else class="drawer-section">
          <div class="drawer-section-title">{{ $t('workspace.clips.content') }}</div>
          <div class="clip-content" v-html="fullContent" />
        </div>
      </div>
    </el-drawer>

    <!-- 创建对话框 -->
    <source-clip-create-dialog ref="createDialog" @created="onClipCreated" />
  </div>
</template>

<script>
import SourceClipCreateDialog from '~/components/SourceClipCreateDialog.vue'

export default {
  name: 'ClipsPage',
  layout: 'workspace',
  components: { SourceClipCreateDialog },
  middleware: 'auth',
  data() {
    return {
      loading: false,
      clips: [],
      total: 0,
      currentPage: 1,
      pageSize: 20,
      searchKeyword: '',
      filterType: '',
      drawerVisible: false,
      selectedClip: null,
      editingTitle: false,
      titleDraft: '',
      savingTitle: false,
      fullContent: '',
      linkedNotes: [],
      showLinkNote: false,
      linkNoteId: '',
      linkUserNote: '',
      linking: false,
      searchTimer: null,
      agentJobs: {
        CLUSTER: { status: null },
        TIMELINE: { status: null },
      },
      agentPollTimers: { CLUSTER: null, TIMELINE: null },
      webpageClipCount: 0,
      generateInFlight: { CLUSTER: false, TIMELINE: false },
    }
  },
  computed: {
    hasLinkTo() {
      return !!this.$route.query.linkTo
    },
    isLinkOnlyOrFailed() {
      if (!this.selectedClip) return false
      return this.selectedClip.extractionMode === 'LINK_ONLY'
        || this.selectedClip.extractionStatus === 'FAILED'
    },
  },
  async created() {
    await this.loadClips()
    if (this.$route.query.linkTo) {
      this.linkNoteId = this.$route.query.linkTo
    }
    this.refreshAgentJob('CLUSTER')
    this.refreshAgentJob('TIMELINE')
    this.loadWebpageClipCount()
  },
  mounted() {
    this.$nuxt.$on('workspace:create:clips', this.openCreate)
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:create:clips', this.openCreate)
    clearTimeout(this.agentPollTimers.CLUSTER)
    clearTimeout(this.agentPollTimers.TIMELINE)
  },
  methods: {
    async loadClips() {
      this.loading = true
      try {
        const res = await this.$clipService.getClips({
          keyword: this.searchKeyword || undefined,
          sourceType: this.filterType || undefined,
          page: this.currentPage - 1,
          size: this.pageSize,
        })
        this.clips = res.content || []
        this.total = res.totalElements || 0
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.loadFailed'))
      } finally {
        this.loading = false
      }
    },
    onSearch() {
      clearTimeout(this.searchTimer)
      this.currentPage = 1
      this.searchTimer = setTimeout(() => this.loadClips(), 300)
    },
    openCreate() {
      this.$refs.createDialog.open()
    },
    onClipCreated() {
      this.currentPage = 1
      this.loadClips()
    },
    onCardClick(clip) {
      if (clip.extractionMode === 'LINK_ONLY') {
        window.open(clip.sourceUrl, '_blank', 'noopener')
      } else {
        this.openDetail(clip)
      }
    },
    handleCardCommand(command, clip) {
      if (command === 'edit') {
        this.promptEditTitle(clip)
      } else if (command === 'delete') {
        this.deleteClip(clip)
      }
    },
    async promptEditTitle(clip) {
      try {
        const { value } = await this.$prompt(
          this.$t('workspace.clips.editTitlePrompt'),
          this.$t('workspace.clips.editTitle'),
          {
            confirmButtonText: this.$t('common.confirm'),
            cancelButtonText: this.$t('common.cancel'),
            inputValue: clip.title,
            inputValidator: (val) => !!(val && val.trim()),
            inputErrorMessage: this.$t('workspace.clips.titleRequired'),
          }
        )
        const updated = await this.$clipService.updateClipTitle(clip.id, value.trim())
        clip.title = updated.title
        if (this.selectedClip && this.selectedClip.id === clip.id) {
          this.selectedClip.title = updated.title
        }
      } catch (e) {
        if (e !== 'cancel' && e !== 'close') {
          this.$message.error('保存失败')
        }
      }
    },
    async openDetail(clip) {
      this.selectedClip = clip
      this.drawerVisible = true
      this.editingTitle = false
      this.linkedNotes = []
      this.fullContent = ''
      this.showLinkNote = false
      // 加载完整内容和关联笔记
      try {
        const [full, refs] = await Promise.all([
          this.$clipService.getClipById(clip.id),
          this.$clipService.getClipLinkedNotes(clip.id).catch(() => []),
        ])
        this.fullContent = full.content || ''
        this.linkedNotes = refs
      } catch (e) {
        // ignore
      }
    },
    startEditTitle() {
      this.titleDraft = this.selectedClip.title
      this.editingTitle = true
      this.$nextTick(() => this.$refs.titleInput && this.$refs.titleInput.focus())
    },
    cancelEditTitle() {
      this.editingTitle = false
    },
    async saveTitle() {
      const title = this.titleDraft.trim()
      if (!title) {
        this.$message.warning('请填写标题')
        return
      }
      this.savingTitle = true
      try {
        const updated = await this.$clipService.updateClipTitle(this.selectedClip.id, title)
        this.selectedClip.title = updated.title
        const listItem = this.clips.find(c => c.id === this.selectedClip.id)
        if (listItem) listItem.title = updated.title
        this.editingTitle = false
      } catch (e) {
        this.$message.error('保存失败')
      } finally {
        this.savingTitle = false
      }
    },
    async deleteClip(clip) {
      await this.$confirm(this.$t('workspace.clips.deleteConfirm'), this.$t('workspace.clips.deleteConfirmTitle'), {
        confirmButtonText: this.$t('common.delete'),
        cancelButtonText: this.$t('common.cancel'),
        type: 'warning',
      })
      try {
        await this.$clipService.deleteClip(clip.id)
        this.$message.success(this.$t('workspace.clips.deleteSuccess'))
        this.drawerVisible = false
        this.loadClips()
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.deleteFailed'))
      }
    },
    async linkNote() {
      if (!this.linkNoteId) return
      this.linking = true
      try {
        const ref = await this.$clipService.linkClipToNote(
          Number(this.linkNoteId),
          this.selectedClip.id,
          { userNote: this.linkUserNote || undefined }
        )
        this.linkedNotes.push(ref)
        this.linkNoteId = ''
        this.linkUserNote = ''
        this.showLinkNote = false
        this.$message.success(this.$t('workspace.clips.linkSuccess'))
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('workspace.clips.linkFailed'))
      } finally {
        this.linking = false
      }
    },
    async unlinkNote(ref) {
      try {
        await this.$clipService.unlinkClipFromNote(ref.noteId, this.selectedClip.id)
        this.linkedNotes = this.linkedNotes.filter(r => r.refId !== ref.refId)
        this.$message.success(this.$t('workspace.clips.unlinkSuccess'))
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.unlinkFailed'))
      }
    },
    goToNote(noteId) {
      this.$router.push(`/workspace/notes/${noteId}`)
    },
    sourceLabel(type) {
      const map = {
        WEBPAGE: this.$t('workspace.clips.sourceWebpage'),
        WECHAT_ARTICLE: this.$t('workspace.clips.sourceWechat'),
      }
      return map[type] || type
    },
    sourceClass(type) {
      return { WEBPAGE: 'badge-web', WECHAT_ARTICLE: 'badge-wechat' }[type] || ''
    },
    formatDate(dateStr) {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleDateString('zh-CN')
    },
    agentButtonLabel(type) {
      const job = this.agentJobs[type]
      if (job.status === 'RUNNING') {
        return this.$t('workspace.clips.generateInProgress', {
          completed: job.completedSteps || 0,
          total: job.totalSteps || 0,
        })
      }
      return type === 'CLUSTER'
        ? this.$t('workspace.clips.generateCluster')
        : this.$t('workspace.clips.generateTimeline')
    },
    async loadWebpageClipCount() {
      try {
        const res = await this.$clipService.getClips({ sourceType: 'WEBPAGE', page: 0, size: 1 })
        this.webpageClipCount = res.totalElements || 0
      } catch (e) {
        // 静默失败：不影响主列表，只影响生成按钮的计数展示
      }
    },
    async refreshAgentJob(type) {
      const previousStatus = this.agentJobs[type].status
      let job
      try {
        job = await this.$bookmarkAgentService.getCurrentJob(type)
      } catch (e) {
        // 轮询临时失败（网络抖动/5xx）：不改变当前展示状态，稍后重试，避免卡在"生成中"再也不刷新
        this.agentPollTimers[type] = setTimeout(() => this.refreshAgentJob(type), 2000)
        return
      }
      this.agentJobs[type] = job || { status: null }
      if (job && job.status === 'RUNNING') {
        this.agentPollTimers[type] = setTimeout(() => this.refreshAgentJob(type), 2000)
      } else if (job && job.status === 'FAILED' && previousStatus === 'RUNNING') {
        this.$message.error(this.$t('workspace.clips.generateFailed', { message: job.errorMessage || '' }))
      }
    },
    async startGenerate(type) {
      if (this.webpageClipCount === 0) {
        this.$message.warning(this.$t('workspace.clips.generateNoClips'))
        return
      }
      if (this.generateInFlight[type]) return
      this.generateInFlight[type] = true
      try {
        try {
          await this.$confirm(
            this.$t('workspace.clips.generateConfirm', { count: this.webpageClipCount }),
            '',
            { confirmButtonText: this.$t('common.confirm'), cancelButtonText: this.$t('common.cancel') }
          )
        } catch (e) {
          return // 用户取消
        }
        const job = await this.$bookmarkAgentService.generate(type)
        this.agentJobs[type] = job
        this.agentPollTimers[type] = setTimeout(() => this.refreshAgentJob(type), 2000)
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.generateFailed', {
          message: e?.response?.data?.message || e?.message || '',
        }))
      } finally {
        this.generateInFlight[type] = false
      }
    },
  },
}
</script>

<style scoped>
.clips-page { height: calc(100vh - 100px); overflow-y: auto; }

.clips-container { max-width: 1100px; margin: 0 auto; padding: 8px 0; }

.clips-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.agent-generate-group { display: flex; align-items: center; gap: 6px; }
.agent-last-generated-link { font-size: 12px; color: #409eff; cursor: pointer; white-space: nowrap; }

.clips-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}
.clip-card {
  background: var(--card-bg-color, #fff);
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: box-shadow .2s;
}
.clip-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.clip-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.clip-card-header-right { display: flex; align-items: center; gap: 8px; }
.card-more-icon { cursor: pointer; color: #909399; font-size: 14px; padding: 2px; }
.card-more-icon:hover { color: #409eff; }
.clip-source-badge {
  display: inline-block; font-size: 11px; padding: 2px 7px; border-radius: 10px; font-weight: 500;
}
.badge-web     { background: #ecf5ff; color: #409eff; }
.badge-wechat  { background: #f0f9eb; color: #67c23a; }
.clip-date { font-size: 12px; color: #c0c4cc; }
.clip-card-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; line-height: 1.4; overflow-wrap: anywhere; word-break: break-word; }
.clip-card-meta { font-size: 12px; color: #909399; margin-bottom: 4px; overflow-wrap: anywhere; word-break: break-word; }
.clip-card-excerpt { font-size: 12px; color: #606266; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; word-break: break-word; }
.clip-card-tags { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 4px; }
.clips-empty { grid-column: 1/-1; text-align: center; padding: 60px 0; color: #909399; }

/* 抽屉 */
.clip-drawer { padding: 24px; height: 100%; overflow-y: auto; }
.drawer-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.drawer-actions { display: flex; gap: 8px; }
.drawer-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.drawer-title { font-size: 18px; font-weight: 600; margin: 0; line-height: 1.4; }
.drawer-title-edit-icon { cursor: pointer; color: #909399; font-size: 14px; }
.drawer-title-edit-icon:hover { color: #409eff; }
.drawer-title-input { flex: 1; }
.drawer-meta { font-size: 12px; color: #909399; margin-bottom: 4px; }
.drawer-meta a { color: #409eff; text-decoration: none; }
.drawer-section { margin-bottom: 16px; }
.drawer-section-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.drawer-empty { font-size: 12px; color: #c0c4cc; }
.linked-note-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--border-color, #f0f0f0); }
.linked-note-title { cursor: pointer; color: #409eff; flex: 1; }
.linked-note-annotation { font-size: 12px; color: #909399; }
.link-note-form { display: flex; align-items: center; padding: 10px 0; flex-wrap: wrap; gap: 4px; }

.clip-content { font-size: 13px; line-height: 1.7; }

.drawer-original-link { margin-bottom: 12px; }
.drawer-author { font-size: 12px; color: #909399; }
.link-only-panel { text-align: center; padding: 40px 0; }
.link-only-message { color: #909399; font-size: 13px; margin-bottom: 16px; }
</style>
