<template>
  <div class="news-page">
    <div class="workspace-layout">
      <aside class="workspace-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">{{ $t('workspace.news.sourceTitle') }}</span>
          <el-button
            type="text"
            size="mini"
            icon="el-icon-refresh"
            :loading="refreshing"
            @click="doRefreshToday"
          >{{ $t('workspace.news.refreshToday') }}</el-button>
        </div>

        <div v-loading="loadingSources" class="source-list">
          <div v-if="currentSources.length" class="source-group">
            <div v-for="src in currentSources" :key="src.sourceKey" class="source-item">
              <div class="source-item-info">
                <span class="source-item-name">{{ currentCategory === 'zh' ? src.nameZh : src.nameEn }}</span>
                <span
                  class="source-status"
                  :class="statusClass(src.lastFetchStatus)"
                  :title="src.lastFetchError || ''"
                >
                  {{ statusText(src.lastFetchStatus) }}
                </span>
              </div>
              <el-switch
                :value="src.enabled"
                size="mini"
                @change="toggleSource(src)"
              />
            </div>
          </div>
        </div>
      </aside>

      <main ref="mainScroller" class="workspace-main" @scroll.passive="handleMainScroll">
        <div v-if="visibleDayGroups.length" class="news-timeline">
          <section v-for="day in visibleDayGroups" :key="day.date" class="news-day-section">
            <div class="news-day-title">
              <span>{{ formatDate(day.date) }}</span>
              <span v-if="day.date === todayStr" class="today-badge">{{ $t('workspace.news.today') }}</span>
            </div>

            <div class="news-grid">
              <div v-for="feed in day.feeds" :key="`${day.date}-${feed.source.sourceKey}`" class="news-card">
                <div class="news-card-header">
                  <span class="news-card-title">{{ currentCategory === 'zh' ? feed.source.nameZh : feed.source.nameEn }} {{ sourceEmoji(feed.source.sourceKey) }}</span>
                </div>
                <div class="news-card-body">
                  <div v-if="feed.items.length === 0" class="news-empty">
                    <span>{{ day.date === todayStr ? $t('workspace.news.emptyToday') : $t('workspace.news.emptyPast') }}</span>
                  </div>
                  <ol v-else class="news-list">
                    <li v-for="item in feed.items" :key="item.id" class="news-item">
                      <a
                        v-if="item.url"
                        :href="item.url"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="news-link"
                      >{{ item.title }}</a>
                      <span v-else class="news-text">{{ item.title }}</span>
                    </li>
                  </ol>
                </div>
                <div v-if="day.date === todayStr" class="news-card-footer">
                  <span
                    class="source-status"
                    :class="statusClass(feed.source.lastFetchStatus)"
                    :title="feed.source.lastFetchError || ''"
                  >
                    {{ feed.source.lastFetchStatus === 'SUCCESS' && feed.source.lastFetchedAt
                      ? $t('workspace.news.updatedAt') + formatTime(feed.source.lastFetchedAt)
                      : feed.source.lastFetchStatus === 'FAILED' && feed.source.lastFetchError
                        ? $t('workspace.news.failedPrefix') + feed.source.lastFetchError
                        : statusText(feed.source.lastFetchStatus) }}
                  </span>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div v-else-if="!loadingHistory" class="main-empty">
          <i class="el-icon-news main-empty-icon"></i>
          <p class="main-empty-title">{{ $t('workspace.news.emptyTitle') }}</p>
          <p class="main-empty-hint">{{ $t('workspace.news.emptyHint') }}</p>
        </div>

        <div class="history-load-state">
          <span v-if="loadingHistory">
            <i class="el-icon-loading"></i>
            {{ $t('workspace.news.loadingHistory') }}
          </span>
          <span v-else-if="!hasMoreHistory && visibleDayGroups.length">{{ $t('workspace.news.noMoreHistory') }}</span>
          <span v-else-if="hasMoreHistory && visibleDayGroups.length">{{ $t('workspace.news.scrollToLoad') }}</span>
        </div>
      </main>
    </div>
  </div>
</template>

<script>
const SOURCE_EMOJI = {
  weibo_hot: '🔥',
  baidu_hot: '🔍',
  zhihu_hot: '💡',
  bilibili_hot: '📺',
  ai_news: '🤖',
  hacker_news: '💻',
  google_news: '🌐',
  bing_news: '🔎',
  tech_news: '⚡',
}

export default {
  name: 'NewsPage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    const today = new Date()
    const pad = n => String(n).padStart(2, '0')
    const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
    return {
      todayStr,
      sources: [],
      dayGroups: [],
      historyPage: 0,
      historySize: 5,
      hasMoreHistory: true,
      loadingSources: false,
      loadingHistory: false,
      refreshing: false,
      refreshPollTimer: null,
    }
  },
  computed: {
    currentCategory() {
      return this.$i18n.locale === 'zh-CN' ? 'zh' : 'en'
    },
    currentSources() {
      return this.sources.filter(s => s.category === this.currentCategory)
    },
    visibleDayGroups() {
      const enabledKeys = new Set(this.sources.filter(s => s.enabled).map(s => s.sourceKey))
      return this.dayGroups
        .map(day => ({
          ...day,
          feeds: (day.feeds || []).filter(feed =>
            enabledKeys.has(feed.source.sourceKey) && feed.source.category === this.currentCategory
          ),
        }))
        .filter(day => day.feeds.length > 0)
    },
  },
  async created() {
    await Promise.all([this.loadSources(), this.loadHistory({ reset: true })])
  },
  beforeDestroy() {
    clearTimeout(this.refreshPollTimer)
  },
  methods: {
    async loadSources() {
      this.loadingSources = true
      try {
        this.sources = await this.$newsService.getSources()
      } catch {
        this.$message.error(this.$t('workspace.news.loadSourcesFailed'))
      } finally {
        this.loadingSources = false
      }
    },

    async loadHistory({ reset = false } = {}) {
      if (this.loadingHistory) return
      if (!reset && !this.hasMoreHistory) return

      if (reset) {
        this.historyPage = 0
        this.dayGroups = []
        this.hasMoreHistory = true
      }

      this.loadingHistory = true
      try {
        const data = await this.$newsService.getNewsHistory(this.historyPage, this.historySize)
        const incomingDays = data.days || []
        const existingDates = new Set(this.dayGroups.map(day => day.date))
        const nextDays = incomingDays.filter(day => !existingDates.has(day.date))
        this.dayGroups = reset ? incomingDays : [...this.dayGroups, ...nextDays]
        this.hasMoreHistory = !!data.hasMore
        this.historyPage = (data.page || this.historyPage) + 1
        this.syncSourceStatusFromDays(incomingDays)
      } catch {
        this.$message.error(this.$t('workspace.news.loadFailed'))
      } finally {
        this.loadingHistory = false
      }
    },

    syncSourceStatusFromDays(days) {
      if (!this.sources.length || !days.length) return
      const statusMap = {}
      days.forEach(day => {
        ;(day.feeds || []).forEach(feed => {
          statusMap[feed.source.sourceKey] = feed.source
        })
      })
      this.sources = this.sources.map(s => statusMap[s.sourceKey] ? { ...s, ...statusMap[s.sourceKey] } : s)
    },

    handleMainScroll() {
      const el = this.$refs.mainScroller
      if (!el || this.loadingHistory || !this.hasMoreHistory) return
      const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
      if (distanceToBottom < 260) {
        this.loadHistory()
      }
    },

    async toggleSource(src) {
      try {
        const updated = await this.$newsService.toggleSource(src.sourceKey)
        const idx = this.sources.findIndex(s => s.sourceKey === src.sourceKey)
        if (idx >= 0) this.$set(this.sources, idx, updated)
      } catch {
        this.$message.error(this.$t('workspace.news.toggleFailed'))
      }
    },

    async doRefreshToday() {
      const initialState = new Map(
        this.sources
          .filter(source => source.enabled)
          .map(source => [source.sourceKey, this.sourceFetchSignature(source)])
      )
      this.refreshing = true
      try {
        await this.$newsService.refreshAll()
        this.$message.success(this.$t('workspace.news.refreshStarted'))
        await this.pollRefreshResult(initialState)
      } catch {
        this.$message.error(this.$t('workspace.news.refreshFailed'))
      } finally {
        this.refreshing = false
      }
    },

    sourceFetchSignature(source) {
      return [
        source.lastFetchStatus || '',
        source.lastFetchedAt || '',
        source.lastFetchError || '',
      ].join('|')
    },

    async pollRefreshResult(initialState) {
      const completedKeys = new Set()
      const maxAttempts = 30

      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        await new Promise(resolve => {
          this.refreshPollTimer = setTimeout(resolve, 2000)
        })
        await this.loadSources()

        this.sources
          .filter(source => source.enabled)
          .forEach(source => {
            if (this.sourceFetchSignature(source) !== initialState.get(source.sourceKey)) {
              completedKeys.add(source.sourceKey)
            }
          })

        if (completedKeys.size >= initialState.size) break
      }

      await this.loadHistory({ reset: true })
    },

    sourceEmoji(key) {
      return SOURCE_EMOJI[key] || ''
    },

    statusClass(status) {
      if (!status) return 'status-none'
      if (status === 'SUCCESS') return 'status-success'
      if (status === 'FAILED') return 'status-failed'
      if (status === 'FETCHING') return 'status-fetching'
      return 'status-none'
    },

    statusText(status) {
      if (!status) return this.$t('workspace.news.statusNone')
      if (status === 'SUCCESS') return this.$t('workspace.news.statusSuccess')
      if (status === 'FAILED') return this.$t('workspace.news.statusFailed')
      if (status === 'FETCHING') return this.$t('workspace.news.statusFetching')
      return status
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      const [y, m, d] = dateStr.split('-')
      return `${y}/${m}/${d}`
    },

    formatTime(isoStr) {
      if (!isoStr) return ''
      const d = new Date(isoStr)
      const pad = n => String(n).padStart(2, '0')
      return `${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
  },
}
</script>

<style scoped lang="scss">
.news-page {
  height: 100%;
  overflow: hidden;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
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

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.sidebar-title { font-size: 13px; font-weight: 600; color: var(--text-color); }

.source-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }

.source-group { display: flex; flex-direction: column; gap: 2px; }

.source-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 7px 8px; border-radius: 8px; transition: background 0.15s;
  &:hover { background: var(--bg-secondary); }
}
.source-item-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; margin-right: 8px; }
.source-item-name { font-size: 13px; color: var(--text-color); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.source-status {
  font-size: 10px;
  &.status-success { color: #67c23a; }
  &.status-failed { color: #f56c6c; }
  &.status-fetching { color: #e6a23c; }
  &.status-none { color: var(--text-muted); }
}

.workspace-main {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 18px 20px;
  overflow-y: auto;
}

.news-timeline {
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.news-day-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.news-day-title {
  position: sticky;
  top: -18px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 10px;
  background: var(--card-bg-color);
  border-bottom: 1px solid var(--border-color);
  font-size: 17px;
  font-weight: 600;
  color: var(--text-color);
}

.today-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(102, 126, 234, 0.12);
  color: #667eea;
  font-size: 11px;
  font-weight: 600;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  align-content: flex-start;
}

.news-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.2s;
  &:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }
}

.news-card-header {
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--border-color);
  background: var(--card-bg-color);
}
.news-card-title { font-size: 13px; font-weight: 600; color: var(--text-color); }

.news-card-body { padding: 10px 14px; flex: 1; }

.news-empty {
  text-align: center; padding: 20px 0; color: var(--text-muted); font-size: 12px;
}

.news-list {
  margin: 0; padding: 0; list-style: none;
  counter-reset: news-counter;
}
.news-item {
  counter-increment: news-counter;
  display: flex; align-items: flex-start; gap: 6px;
  padding: 4px 0; font-size: 16px; line-height: 1.5;
  border-bottom: 1px solid var(--border-color);
  &:last-child { border-bottom: none; }
  &::before {
    content: counter(news-counter);
    flex-shrink: 0;
    min-width: 18px;
    font-size: 11px;
    color: var(--text-muted);
    font-weight: 600;
    padding-top: 1px;
  }
}

.news-link {
  color: var(--text-color);
  text-decoration: none;
  line-height: 1.5;
  &:hover { color: #667eea; text-decoration: underline; }
}
.news-text { color: var(--text-color); line-height: 1.5; }

.news-card-footer {
  padding: 6px 14px;
  border-top: 1px solid var(--border-color);
  background: var(--card-bg-color);
}

.main-empty {
  min-height: 420px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 80px 0; color: var(--text-muted); gap: 8px;
}
.main-empty-icon { font-size: 56px; color: #c0c4cc; }
.main-empty-title { font-size: 15px; font-weight: 500; color: var(--text-secondary); margin: 0; }
.main-empty-hint { font-size: 13px; color: var(--text-muted); margin: 0; }

.history-load-state {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 0 2px;
  color: var(--text-muted);
  font-size: 12px;
  gap: 6px;
}

@media screen and (max-width: 1024px) {
  .workspace-layout {
    grid-template-columns: 1fr;
  }
  .workspace-sidebar {
    display: none;
  }
}
</style>
