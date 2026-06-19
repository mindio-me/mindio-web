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
          <el-radio-button label="AI_CHAT">{{ $t('workspace.clips.filterAiChat') }}</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 卡片列表 -->
      <div v-loading="loading" class="clips-grid">
        <div
          v-for="clip in clips"
          :key="clip.id"
          class="clip-card"
          @click="openDetail(clip)"
        >
          <div class="clip-card-header">
            <span class="clip-source-badge" :class="sourceClass(clip.sourceType)">
              {{ sourceLabel(clip.sourceType) }}
            </span>
            <span class="clip-date">{{ formatDate(clip.createdAt) }}</span>
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

        <h2 class="drawer-title">{{ selectedClip.title }}</h2>

        <div v-if="selectedClip.sourceUrl" class="drawer-meta">
          <a :href="selectedClip.sourceUrl" target="_blank" rel="noopener">
            <i class="el-icon-link"></i> {{ selectedClip.sourceTitle || selectedClip.sourceUrl }}
          </a>
          <span v-if="selectedClip.sourceAuthor"> · {{ selectedClip.sourceAuthor }}</span>
        </div>
        <div v-if="selectedClip.aiModel" class="drawer-meta">
          <i class="el-icon-cpu"></i> {{ selectedClip.aiModel }}
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

        <!-- 完整内容 -->
        <div class="drawer-section">
          <div class="drawer-section-title">{{ $t('workspace.clips.content') }}</div>
          <div
            class="clip-content"
            :class="{ 'ai-chat-content': selectedClip.sourceType === 'AI_CHAT' }"
            v-html="fullContent"
          />
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
      fullContent: '',
      linkedNotes: [],
      showLinkNote: false,
      linkNoteId: '',
      linkUserNote: '',
      linking: false,
      searchTimer: null,
    }
  },
  computed: {
    hasLinkTo() {
      return !!this.$route.query.linkTo
    },
  },
  async created() {
    await this.loadClips()
    if (this.$route.query.linkTo) {
      this.linkNoteId = this.$route.query.linkTo
    }
  },
  mounted() {
    this.$nuxt.$on('workspace:create:clips', this.openCreate)
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:create:clips', this.openCreate)
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
    async openDetail(clip) {
      this.selectedClip = clip
      this.drawerVisible = true
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
        AI_CHAT: this.$t('workspace.clips.sourceAiChat')
      }
      return map[type] || type
    },
    sourceClass(type) {
      return { WEBPAGE: 'badge-web', WECHAT_ARTICLE: 'badge-wechat', AI_CHAT: 'badge-ai' }[type] || ''
    },
    formatDate(dateStr) {
      if (!dateStr) return ''
      return new Date(dateStr).toLocaleDateString('zh-CN')
    },
  },
}
</script>

<style scoped>
.clips-container { max-width: 1100px; margin: 0 auto; padding: 8px 0; }

.clips-toolbar { display: flex; align-items: center; margin-bottom: 20px; }

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
.clip-source-badge {
  display: inline-block; font-size: 11px; padding: 2px 7px; border-radius: 10px; font-weight: 500;
}
.badge-web     { background: #ecf5ff; color: #409eff; }
.badge-wechat  { background: #f0f9eb; color: #67c23a; }
.badge-ai      { background: #fdf6ec; color: #e6a23c; }
.clip-date { font-size: 12px; color: #c0c4cc; }
.clip-card-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; line-height: 1.4; }
.clip-card-meta { font-size: 12px; color: #909399; margin-bottom: 4px; }
.clip-card-excerpt { font-size: 12px; color: #606266; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.clip-card-tags { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 4px; }
.clips-empty { grid-column: 1/-1; text-align: center; padding: 60px 0; color: #909399; }

/* 抽屉 */
.clip-drawer { padding: 24px; height: 100%; overflow-y: auto; }
.drawer-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.drawer-actions { display: flex; gap: 8px; }
.drawer-title { font-size: 18px; font-weight: 600; margin: 0 0 8px; line-height: 1.4; }
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
</style>
