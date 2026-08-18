<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="clips-page">
    <div class="clips-layout">

      <!-- 左侧栏：知识图谱/时间线生成 + 标签筛选 -->
      <button type="button" class="rail-toggle rail-toggle-left" @click="leftRailOpen = !leftRailOpen">
        <i class="el-icon-price-tag"></i>
      </button>
      <aside class="clips-rail clips-left-rail" :class="{ 'is-open': leftRailOpen }">
        <div class="rail-section">
          <div class="agent-generate-group" v-for="type in ['CLUSTER', 'TIMELINE']" :key="type">
            <el-button
              size="small"
              :loading="agentJobs[type].status === 'RUNNING'"
              :disabled="generateClipCount === 0 || agentJobs[type].status === 'RUNNING' || generateInFlight[type]"
              @click="startGenerate(type)"
            >{{ agentButtonLabel(type) }}</el-button>
            <div v-if="agentJobs[type].status === 'RUNNING'" class="agent-progress">
              <el-progress
                :percentage="agentProgressPercent(type)"
                :stroke-width="4"
              />
              <span class="agent-progress-caption">{{ agentPhaseLabel(type) }} ·
                {{ $t('workspace.clips.generateStepProgress', {
                  completed: agentJobs[type].completedSteps || 0,
                  total: agentJobs[type].totalSteps || 0,
                }) }}</span>
            </div>
            <a
              v-if="agentJobs[type].status === 'DONE'"
              class="agent-last-generated-link"
              @click="$router.push('/workspace/notes/' + agentJobs[type].resultNoteId)"
            >{{ $t('workspace.clips.generateLastAt', { date: formatDate(agentJobs[type].finishedAt) }) }}</a>
          </div>
        </div>

        <el-divider />

        <div class="rail-section">
          <div class="rail-section-title">{{ $t('workspace.clips.filterByTag') }}</div>
          <div class="tag-tile-list">
            <span
              v-if="untaggedCount > 0"
              class="tag-tile tag-tile-untagged"
              :class="{ 'is-active': filterUntagged }"
              @click="toggleUntaggedFilter"
            >{{ $t('workspace.clips.untagged') }}<span class="tag-tile-count">{{ untaggedCount }}</span></span>
            <span
              v-for="tag in allClipTags"
              :key="tag.id"
              class="tag-tile"
              :class="{ 'is-active': filterTagIds.includes(tag.id) }"
              @click="toggleFilterTag(tag.id)"
            >{{ tag.name }}<span class="tag-tile-count">{{ tag.clipCount || 0 }}</span></span>
            <span v-if="allClipTags.length === 0" class="tag-tile-empty">{{ $t('workspace.clips.noTagsYet') }}</span>
          </div>
        </div>
      </aside>

      <!-- 中间：筛选栏 + 卡片列表 + 分页 -->
      <div class="clips-main">

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
                  trigger="click"
                  @click.native.stop
                  @command="cmd => handleCardCommand(cmd, clip)"
                >
                  <i class="el-icon-more card-more-icon"></i>
                  <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item command="tags">{{ $t('workspace.clips.manageTags') }}</el-dropdown-item>
                    <template v-if="clip.extractionMode === 'LINK_ONLY'">
                      <el-dropdown-item command="edit">{{ $t('workspace.clips.editTitle') }}</el-dropdown-item>
                      <el-dropdown-item command="delete" style="color:#f56c6c">{{ $t('common.delete') }}</el-dropdown-item>
                    </template>
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
              <el-tag
                v-for="t in clip.tags"
                :key="t.id"
                size="mini"
                :type="t.manuallyAdded ? 'info' : 'warning'"
                :effect="t.manuallyAdded ? 'light' : 'plain'"
              >
                <i v-if="t.aiSuggested && !t.manuallyAdded" class="el-icon-magic-stick clip-tag-ai-icon"></i>{{ t.name }}
              </el-tag>
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

      <!-- 右侧栏：最近访问的收藏 -->
      <button type="button" class="rail-toggle rail-toggle-right" @click="rightRailOpen = !rightRailOpen">
        <i class="el-icon-time"></i>
      </button>
      <aside class="clips-rail clips-right-rail" :class="{ 'is-open': rightRailOpen }">
        <div v-if="!showAiSearchChatbox">
          <div class="rail-section-title">{{ $t('workspace.clips.recentClips') }}</div>
          <ul class="recent-list">
            <li v-for="clip in recentClips" :key="clip.id" class="recent-item" @click="openDetail(clip)">
              <div class="recent-title">{{ clip.title }}</div>
              <div class="recent-meta">{{ formatRelativeTime(clip.lastAccessedAt || clip.createdAt) }}</div>
            </li>
            <li v-if="recentClips.length === 0" class="tag-tile-empty">{{ $t('workspace.clips.noRecentClips') }}</li>
          </ul>
        </div>
        <!-- AI 网络搜索 chatbox：专题研究功能重新设计前先隐藏，逻辑保留 -->
        <div v-else>
        <div class="rail-section-title">{{ $t('workspace.clips.aiSearchTitle') }}</div>
        <div class="ai-search-chat">
          <div class="ai-search-messages" ref="aiSearchMessages">
            <div
              v-for="msg in aiSearchMessages"
              :key="msg.id"
              class="ai-search-msg"
              :class="msg.role === 'USER' ? 'is-user' : 'is-assistant'"
            >
              <div class="ai-search-bubble">{{ msg.content }}</div>
              <div v-if="msg.results && msg.results.length" class="ai-search-results">
                <div v-for="(r, idx) in msg.results" :key="idx" class="ai-search-result-card">
                  <a :href="r.url" target="_blank" rel="noopener" class="ai-search-result-title">{{ r.title }}</a>
                  <div class="ai-search-result-excerpt">{{ r.excerpt }}</div>
                  <el-button
                    size="mini"
                    :type="r.sourceClipId ? 'info' : 'primary'"
                    :disabled="!!r.sourceClipId || savingResult === (msg.id + '-' + idx)"
                    :loading="savingResult === (msg.id + '-' + idx)"
                    @click="saveAiSearchResult(msg, idx)"
                  >{{ r.sourceClipId ? $t('workspace.clips.aiSearchSaved') : $t('workspace.clips.aiSearchSave') }}</el-button>
                </div>
              </div>
            </div>
            <div v-if="aiSearchLoading" class="ai-search-msg is-assistant">
              <div class="ai-search-bubble ai-search-typing">{{ $t('workspace.clips.aiSearchThinking') }}</div>
            </div>
          </div>
          <div class="ai-search-input-row">
            <el-input
              v-model="aiSearchInput"
              type="textarea"
              :rows="2"
              :placeholder="$t('workspace.clips.aiSearchPlaceholder')"
              :disabled="aiSearchLoading"
              @keydown.enter.native.exact.prevent="sendAiSearchMessage"
            />
            <el-button
              type="primary"
              size="small"
              :disabled="!aiSearchInput.trim() || aiSearchLoading"
              @click="sendAiSearchMessage"
            >{{ $t('workspace.clips.aiSearchSend') }}</el-button>
          </div>
        </div>
        </div>
      </aside>
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

    <!-- 标签管理弹窗 -->
    <el-dialog
      :visible.sync="tagManagerVisible"
      :title="$t('workspace.clips.manageTags')"
      width="420px"
      append-to-body
    >
      <div v-if="tagManagerClip" class="clip-tag-manager">
        <el-tag
          v-for="tag in allClipTags"
          :key="tag.id"
          size="small"
          effect="plain"
          :type="isClipTagActive(tag.id) ? 'primary' : 'info'"
          class="clip-tag-manager-item"
          @click="toggleClipTag(tag)"
        >{{ tag.name }}</el-tag>
        <div v-if="allClipTags.length === 0" class="clip-tag-manager-empty">
          {{ $t('workspace.clips.noTagsYet') }}
        </div>
        <div class="clip-tag-manager-create">
          <el-input
            v-model="newClipTagName"
            size="small"
            :placeholder="$t('workspace.clips.newTagPlaceholder')"
            @keyup.enter.native="createAndAddClipTag"
          />
          <el-button
            size="small"
            type="primary"
            :loading="tagManagerSaving"
            :disabled="!newClipTagName.trim()"
            @click="createAndAddClipTag"
          >{{ $t('common.confirm') }}</el-button>
        </div>
      </div>
    </el-dialog>

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
      filterTagIds: [],
      filterUntagged: false,
      untaggedCount: 0,
      leftRailOpen: false,
      rightRailOpen: false,
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
      generateClipCount: 0,
      generateInFlight: { CLUSTER: false, TIMELINE: false },
      allClipTags: [],
      recentClips: [],
      // 专题研究功能重新设计前，先隐藏 chatbox 显示"最近访问"；chatbox 逻辑保留，改回 true 即可恢复显示
      showAiSearchChatbox: false,
      aiSearchMessages: [],
      aiSearchInput: '',
      aiSearchLoading: false,
      savingResult: null,
      tagManagerVisible: false,
      tagManagerClip: null,
      newClipTagName: '',
      tagManagerSaving: false,
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
    this.loadGenerateClipCount()
    this.loadClipTags()
    this.loadUntaggedCount()
    this.loadRecentClips()
    if (this.showAiSearchChatbox) this.loadAiSearchHistory()
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
          tagIds: this.filterTagIds.length ? this.filterTagIds : undefined,
          untagged: this.filterUntagged || undefined,
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
    toggleFilterTag(tagId) {
      const idx = this.filterTagIds.indexOf(tagId)
      if (idx === -1) {
        this.filterTagIds.push(tagId)
      } else {
        this.filterTagIds.splice(idx, 1)
      }
      this.filterUntagged = false
      this.onSearch()
    },
    toggleUntaggedFilter() {
      this.filterUntagged = !this.filterUntagged
      if (this.filterUntagged) this.filterTagIds = []
      this.onSearch()
    },
    async loadUntaggedCount() {
      try {
        const res = await this.$clipService.getClips({ untagged: true, page: 0, size: 1 })
        this.untaggedCount = res.totalElements || 0
      } catch (e) {
        // 静默失败：不影响主流程，只影响"未分类"方块的计数展示
      }
    },
    async loadAiSearchHistory() {
      try {
        const res = await this.$clipSearchService.getMessages()
        this.aiSearchMessages = (res || []).map(m => ({ ...m, results: m.results || [] }))
        this.$nextTick(this.scrollAiSearchToBottom)
      } catch (e) {
        // 历史加载失败不阻塞收藏夹主流程，静默忽略
      }
    },
    async sendAiSearchMessage() {
      const content = this.aiSearchInput.trim()
      if (!content || this.aiSearchLoading) return
      this.aiSearchInput = ''
      this.aiSearchLoading = true
      try {
        const res = await this.$clipSearchService.sendMessage(content)
        this.aiSearchMessages.push(...(res || []).map(m => ({ ...m, results: m.results || [] })))
        this.$nextTick(this.scrollAiSearchToBottom)
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.aiSearchFailed'))
      } finally {
        this.aiSearchLoading = false
      }
    },
    async saveAiSearchResult(msg, idx) {
      const key = msg.id + '-' + idx
      this.savingResult = key
      try {
        const res = await this.$clipSearchService.saveResult(msg.id, idx)
        this.$set(msg.results[idx], 'sourceClipId', res.sourceClipId)
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.aiSearchSaveFailed'))
      } finally {
        this.savingResult = null
      }
    },
    scrollAiSearchToBottom() {
      const el = this.$refs.aiSearchMessages
      if (el) el.scrollTop = el.scrollHeight
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
      } else if (command === 'tags') {
        this.openTagManager(clip)
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
        // 打开详情会让后端把这条收藏标记为刚访问过，刷新"最近访问"让它排到最前面
        this.loadRecentClips()
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
        return this.$t('workspace.clips.generateInProgress')
      }
      return type === 'CLUSTER'
        ? this.$t('workspace.clips.generateCluster')
        : this.$t('workspace.clips.generateTimeline')
    },
    agentProgressPercent(type) {
      const job = this.agentJobs[type]
      const total = job.totalSteps || 0
      if (total <= 0) return 0
      const completed = job.completedSteps || 0
      return Math.min(100, Math.round((completed / total) * 100))
    },
    agentPhaseLabel(type) {
      const phase = this.agentJobs[type].phaseLabel
      if (phase === 'CLASSIFYING') return this.$t('workspace.clips.generatePhaseClassifying')
      if (phase === 'SUMMARIZING') return this.$t('workspace.clips.generatePhaseSummarizing')
      return this.$t('workspace.clips.generateInProgress')
    },
    async loadGenerateClipCount() {
      try {
        const res = await this.$clipService.getClips({ page: 0, size: 1 })
        this.generateClipCount = res.totalElements || 0
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
      } else if (job && job.status === 'DONE' && previousStatus === 'RUNNING') {
        // 生成期间 AI 可能新增/清理了标签，也可能把收藏重新归了类，刷新左侧标签栏、未分类计数和收藏列表
        this.loadClipTags()
        this.loadUntaggedCount()
        this.loadClips()
      } else if (job && job.status === 'FAILED' && previousStatus === 'RUNNING') {
        this.$message.error(this.$t('workspace.clips.generateFailed', { message: job.errorMessage || '' }))
      }
    },
    async startGenerate(type) {
      if (this.generateClipCount === 0) {
        this.$message.warning(this.$t('workspace.clips.generateNoClips'))
        return
      }
      if (this.generateInFlight[type]) return
      this.generateInFlight[type] = true
      try {
        try {
          await this.$confirm(
            this.$t('workspace.clips.generateConfirm', { count: this.generateClipCount }),
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
    async loadClipTags() {
      try {
        this.allClipTags = await this.$clipService.getTags('clip')
      } catch (e) {
        this.allClipTags = []
      }
    },
    async loadRecentClips() {
      try {
        this.recentClips = await this.$clipService.getRecentClips(5)
      } catch (e) {
        // 静默失败：不影响主流程，只影响右侧栏"最近访问"展示
      }
    },
    formatRelativeTime(time) {
      if (!time) return ''
      const normalized = typeof time === 'string' && !/Z$|[+-]\d{2}:\d{2}$/.test(time) ? time + 'Z' : time
      const date = new Date(normalized)
      const diff = Date.now() - date.getTime()
      const days = Math.floor(diff / (1000 * 60 * 60 * 24))
      if (days === 0) {
        const hours = Math.floor(diff / (1000 * 60 * 60))
        if (hours === 0) {
          const minutes = Math.floor(diff / (1000 * 60))
          return minutes <= 0 ? this.$t('workspace.clips.timeJustNow') : this.$t('workspace.clips.timeMinutesAgo', { n: minutes })
        }
        return this.$t('workspace.clips.timeHoursAgo', { n: hours })
      } else if (days < 7) {
        return this.$t('workspace.clips.timeDaysAgo', { n: days })
      }
      return date.toLocaleDateString()
    },
    openTagManager(clip) {
      this.tagManagerClip = clip
      this.tagManagerVisible = true
    },
    isClipTagActive(tagId) {
      return !!(this.tagManagerClip && this.tagManagerClip.tags
        && this.tagManagerClip.tags.some(t => t.id === tagId && t.manuallyAdded))
    },
    async toggleClipTag(tag) {
      const clip = this.tagManagerClip
      const active = this.isClipTagActive(tag.id)
      this.tagManagerSaving = true
      try {
        if (active) {
          await this.$clipService.removeClipTag(clip.id, tag.id)
        } else {
          await this.$clipService.addClipTag(clip.id, tag.id)
        }
        const fresh = await this.$clipService.getClipById(clip.id)
        clip.tags = fresh.tags || []
        const listItem = this.clips.find(c => c.id === clip.id)
        if (listItem && listItem !== clip) listItem.tags = clip.tags
        // 手动加/摘标签会改变对应标签的链接计数，以及这条收藏是否还算"未分类"
        this.loadClipTags()
        this.loadUntaggedCount()
      } catch (e) {
        this.$message.error(this.$t('workspace.clips.tagUpdateFailed'))
      } finally {
        this.tagManagerSaving = false
      }
    },
    async createAndAddClipTag() {
      const name = this.newClipTagName.trim()
      if (!name) return
      this.tagManagerSaving = true
      try {
        const { data } = await this.$axios.post('/v1/tags', { name, scope: 'clip' })
        this.allClipTags.push(data)
        this.newClipTagName = ''
        await this.toggleClipTag(data)
      } catch (e) {
        this.$message.error(e.response?.data?.message || this.$t('workspace.clips.tagUpdateFailed'))
      } finally {
        this.tagManagerSaving = false
      }
    },
  },
}
</script>

<style scoped>
.clips-page { height: calc(100vh - 100px); overflow-y: auto; }

.clips-layout { display: flex; align-items: flex-start; gap: 20px; padding: 8px 20px; }

.clips-rail {
  flex: 0 0 auto;
  padding: 4px 0;
}
.clips-left-rail { flex-basis: 220px; }
.clips-right-rail { flex-basis: 240px; }

.clips-main { flex: 1 1 auto; min-width: 0; max-width: 1320px; }

.rail-section { margin-bottom: 4px; }
.rail-section-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 10px; }
.rail-placeholder { font-size: 12px; color: #909399; line-height: 1.6; }

.recent-list { list-style: none; margin: 0; padding: 0; }
.recent-item {
  padding: 8px 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color .15s;
}
.recent-item:hover { background-color: #f5f7fa; }
.recent-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-meta { font-size: 12px; color: #909399; margin-top: 2px; }

.tag-tile-list { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-tile {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid var(--border-color, #dcdfe6);
  color: #606266;
  cursor: pointer;
  user-select: none;
  transition: all .15s;
}
.tag-tile:hover { border-color: #409eff; color: #409eff; }
.tag-tile.is-active { background: #409eff; border-color: #409eff; color: #fff; }
.tag-tile-count { margin-left: 5px; font-size: 11px; opacity: .65; }
.tag-tile-empty { font-size: 12px; color: #c0c4cc; }
.tag-tile-untagged { border-style: dashed; }
.tag-tile-untagged.is-active { background: #909399; border-color: #909399; }

.rail-toggle { display: none; }

.clips-right-rail {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  position: sticky;
  top: 8px;
}
.ai-search-chat { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.ai-search-messages { flex: 1; overflow-y: auto; padding-right: 4px; }
.ai-search-msg { margin-bottom: 12px; display: flex; flex-direction: column; }
.ai-search-msg.is-user { align-items: flex-end; }
.ai-search-msg.is-assistant { align-items: flex-start; }
.ai-search-bubble {
  max-width: 90%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-search-msg.is-user .ai-search-bubble { background: #409eff; color: #fff; }
.ai-search-msg.is-assistant .ai-search-bubble { background: var(--card-bg-color, #f4f4f5); color: #303133; }
.ai-search-typing { opacity: .6; }
.ai-search-results { margin-top: 6px; width: 100%; display: flex; flex-direction: column; gap: 8px; }
.ai-search-result-card {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 8px;
  padding: 8px 10px;
}
.ai-search-result-title { font-size: 13px; font-weight: 600; color: #409eff; text-decoration: none; display: block; margin-bottom: 4px; }
.ai-search-result-excerpt { font-size: 12px; color: #909399; margin-bottom: 6px; line-height: 1.5; }
.ai-search-input-row { display: flex; gap: 8px; align-items: flex-end; margin-top: 8px; }
.ai-search-input-row .el-textarea { flex: 1; }

.clips-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.agent-generate-group { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; margin-bottom: 10px; }
.agent-progress { width: 100%; }
.agent-progress-caption { display: block; margin-top: 4px; font-size: 12px; color: #909399; }
.agent-last-generated-link { font-size: 12px; color: #409eff; cursor: pointer; white-space: nowrap; }

@media (max-width: 1200px) {
  .clips-layout { position: relative; }

  .clips-rail {
    position: fixed;
    top: 70px;
    bottom: 20px;
    width: 260px;
    z-index: 20;
    background: var(--card-bg-color, #fff);
    border: 1px solid var(--border-color, #e4e7ed);
    border-radius: 8px;
    box-shadow: 0 4px 20px rgba(0,0,0,.15);
    padding: 16px;
    overflow-y: auto;
    transition: transform .25s ease;
    height: auto;
  }
  .clips-left-rail { left: 12px; transform: translateX(-130%); }
  .clips-left-rail.is-open { transform: translateX(0); }
  .clips-right-rail { right: 12px; transform: translateX(130%); }
  .clips-right-rail.is-open { transform: translateX(0); }

  .rail-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    position: fixed;
    top: 70px;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    border: 1px solid var(--border-color, #e4e7ed);
    background: var(--card-bg-color, #fff);
    color: #606266;
    cursor: pointer;
    z-index: 21;
    box-shadow: 0 2px 8px rgba(0,0,0,.1);
  }
  .rail-toggle-left { left: 12px; }
  .rail-toggle-right { right: 12px; }

  .clips-main { max-width: none; }
}

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
.clip-tag-ai-icon { margin-right: 2px; }
.clip-tag-manager-item { margin: 0 6px 6px 0; cursor: pointer; }
.clip-tag-manager-empty { color: #909399; font-size: 13px; margin: 4px 0 12px; }
.clip-tag-manager-create { display: flex; gap: 8px; margin-top: 12px; }
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
