<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="blog-page">
    <PublicHeader />

    <main class="blog-main">
      <section class="blog-hero">
        <div>
          <p class="eyebrow">{{ $t('notesPage.hero.eyebrow') }}</p>
          <h1>{{ $t('notesPage.hero.title') }}</h1>
          <p class="hero-subtitle">{{ $t('notesPage.hero.subtitle') }}</p>
        </div>
        <div class="hero-stat">
          <span class="stat-number">{{ totalElements || filteredNotes.length }}</span>
          <span class="stat-label">{{ $t('notesPage.hero.countLabel') }}</span>
        </div>
      </section>

      <section class="blog-toolbar">
        <label class="search-bar">
          <i class="el-icon-search"></i>
          <input
            type="text"
            :placeholder="$t('notesPage.search.placeholder')"
            v-model="searchKeyword"
            @input="handleSearch"
          />
        </label>

        <div class="filter-tags" v-if="availableTags.length">
          <button class="tag-btn" :class="{ active: !selectedTag }" @click="selectTag('all')">
            {{ $t('notesPage.filter.all') }}
          </button>
          <button
            v-for="tag in availableTags"
            :key="tag.id"
            class="tag-btn"
            :class="{ active: selectedTag === tag.id }"
            @click="selectTag(tag.id)"
          >
            {{ tag.name }}
          </button>
        </div>
      </section>

      <section v-if="loading" class="state-block">
        <div class="loading-spinner"></div>
        <p>{{ $t('common.loading') }}</p>
      </section>

      <section v-else-if="error" class="state-block">
        <i class="el-icon-warning"></i>
        <p>
          {{ error.response?.status === 401 || error.response?.status === 403
            ? $t('notesPage.error.loginRequired')
            : error.response?.status === 404
            ? $t('notesPage.error.notFound')
            : $t('notesPage.error.loadFailed') }}
        </p>
        <button class="text-btn" @click="loadNotes">
          <i class="el-icon-refresh"></i>
          <span>{{ $t('notesPage.error.retry') }}</span>
        </button>
      </section>

      <section v-else-if="filteredNotes.length" class="posts-section">
        <article v-if="featuredNote" class="featured-post" @click="openNote(featuredNote)">
          <div class="featured-meta">
            <span>{{ $t('notesPage.featured') }}</span>
            <span>{{ featuredNote.date }}</span>
          </div>
          <h2>{{ featuredNote.title }}</h2>
          <p>{{ featuredNote.description }}</p>
          <div class="post-footer">
            <span>{{ featuredNote.readingTime }}</span>
            <span v-if="featuredNote.tag" class="note-tag">{{ featuredNote.tag }}</span>
          </div>
        </article>

        <div class="post-list">
          <article
            v-for="note in secondaryNotes"
            :key="note.id"
            class="post-row"
            @click="openNote(note)"
          >
            <div class="post-date">{{ note.date }}</div>
            <div class="post-content">
              <h3>{{ note.title }}</h3>
              <p>{{ note.description }}</p>
              <div class="post-footer">
                <span>{{ note.readingTime }}</span>
                <span v-if="note.tag" class="note-tag">{{ note.tag }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-else class="state-block">
        <i class="el-icon-document"></i>
        <p>
          {{ searchKeyword && searchKeyword.trim()
            ? $t('notesPage.empty.noSearch', { keyword: searchKeyword.trim() })
            : selectedTag
            ? $t('notesPage.empty.noTag')
            : $t('notesPage.empty.noNotes') }}
        </p>
        <button v-if="searchKeyword || selectedTag" class="text-btn" @click="clearFilters">
          <i class="el-icon-close"></i>
          <span>{{ $t('notesPage.empty.clearFilters') }}</span>
        </button>
      </section>

      <div v-if="!loading && !error && totalElements > pageSize" class="pagination">
        <button class="page-btn" :disabled="currentPage === 0 || loading" @click="previousPage">
          <i class="el-icon-arrow-left"></i>
          <span>{{ $t('notesPage.pagination.prev') }}</span>
        </button>
        <span class="page-info">
          {{ $t('notesPage.pagination.info', { page: currentPage + 1, total: totalPages }) }}
          <span class="page-total">{{ $t('notesPage.pagination.total', { count: totalElements }) }}</span>
        </span>
        <button class="page-btn" :disabled="currentPage >= totalPages - 1 || loading" @click="nextPage">
          <span>{{ $t('notesPage.pagination.next') }}</span>
          <i class="el-icon-arrow-right"></i>
        </button>
      </div>
    </main>

    <PublicFooter :owner-profile="ownerProfile" />
  </div>
</template>

<script>
export default {
  layout: 'blank',
  auth: false,

  data() {
    return {
      ownerProfile: null,
      isRestoringListState: false,
      listStateStorageKey: 'mindio:blog:list-state',
      listScrollSaveTimer: null,
      loading: true,
      error: null,
      searchKeyword: '',
      searchDebounceTimer: null,
      selectedTag: null,
      availableTags: [],
      notes: [],
      totalElements: 0,
      totalPages: 0,
      currentPage: 0,
      pageSize: 10
    }
  },

  computed: {
    filteredNotes() {
      let filtered = this.notes

      if (this.searchKeyword && this.searchKeyword.trim()) {
        const keyword = this.searchKeyword.trim().toLowerCase()
        filtered = filtered.filter(note => {
          const title = (note.title || '').toLowerCase()
          const summary = (note.summary || '').toLowerCase()
          const content = (note.content || '').toLowerCase()
          return title.includes(keyword) || summary.includes(keyword) || content.includes(keyword)
        })
      }

      if (this.selectedTag) {
        filtered = filtered.filter(note => note.tags && note.tags.some(tag => tag.id === this.selectedTag))
      }

      return filtered.map(note => {
        const description = note.summary || this.extractDescription(note.content)
        return {
          ...note,
          description,
          date: this.formatDate(note.modifiedAt || note.createdAt),
          tag: note.tags && note.tags.length > 0 ? note.tags[0].name : '',
          tagId: note.tags && note.tags.length > 0 ? note.tags[0].id : null,
          readingTime: this.formatReadingTime(description, note.content)
        }
      })
    },
    featuredNote() {
      return this.filteredNotes[0] || null
    },
    secondaryNotes() {
      return this.filteredNotes.slice(1)
    }
  },

  async mounted() {
    if (process.client) {
      this.restoreListState()
    }
    await this.loadNotes()
    this.$nextTick(() => {
      this.setupListScrollPersistence()
      this.restoreListScrollPosition()
    })
    try {
      this.ownerProfile = await this.$profileService.getOwnerProfile()
    } catch (e) {}
  },

  beforeDestroy() {
    this.saveListState()
    this.removeListScrollPersistence()
    if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer)
    if (this.listScrollSaveTimer) clearTimeout(this.listScrollSaveTimer)
  },

  watch: {
    selectedTag() {
      if (this.isRestoringListState) return
      this.currentPage = 0
      this.saveListState()
      this.loadNotes()
    },
    searchKeyword(newVal) {
      if (this.isRestoringListState) return
      if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer)
      if (!newVal || !newVal.trim()) {
        this.currentPage = 0
        this.saveListState()
        this.loadNotes()
        return
      }
      this.searchDebounceTimer = setTimeout(() => {
        this.currentPage = 0
        this.saveListState()
        this.loadNotes()
      }, 500)
    }
  },

  methods: {
    getNotesContentElement() {
      if (!process.client) return null
      return document.querySelector('.blog-main')
    },
    getListStatePayload() {
      const notesContent = this.getNotesContentElement()
      return {
        searchKeyword: this.searchKeyword,
        selectedTag: this.selectedTag,
        currentPage: this.currentPage,
        scrollTop: notesContent ? notesContent.scrollTop : window.scrollY || 0,
        savedAt: Date.now()
      }
    },
    saveListState() {
      if (!process.client) return
      try {
        sessionStorage.setItem(this.listStateStorageKey, JSON.stringify(this.getListStatePayload()))
      } catch (e) {
        console.warn('Failed to save blog list state', e)
      }
    },
    restoreListState() {
      if (!process.client) return
      try {
        const raw = sessionStorage.getItem(this.listStateStorageKey)
        if (!raw) return
        const state = JSON.parse(raw)
        this.isRestoringListState = true
        this.searchKeyword = state.searchKeyword || ''
        this.selectedTag = state.selectedTag ?? null
        this.currentPage = Number.isInteger(state.currentPage) && state.currentPage >= 0 ? state.currentPage : 0
        this._pendingListScrollTop = Number.isFinite(state.scrollTop) ? state.scrollTop : 0
      } catch (e) {
        console.warn('Failed to restore blog list state', e)
      } finally {
        this.$nextTick(() => {
          this.isRestoringListState = false
        })
      }
    },
    restoreListScrollPosition() {
      if (!process.client || this._pendingListScrollTop == null) return
      window.scrollTo({ top: this._pendingListScrollTop })
      this._pendingListScrollTop = null
    },
    setupListScrollPersistence() {
      if (!process.client || this._notesListScrollHandler) return
      this._notesListScrollHandler = () => {
        if (this.listScrollSaveTimer) clearTimeout(this.listScrollSaveTimer)
        this.listScrollSaveTimer = setTimeout(() => this.saveListState(), 150)
      }
      window.addEventListener('scroll', this._notesListScrollHandler)
    },
    removeListScrollPersistence() {
      if (process.client && this._notesListScrollHandler) {
        window.removeEventListener('scroll', this._notesListScrollHandler)
      }
      this._notesListScrollHandler = null
    },
    async loadNotes() {
      this.loading = true
      this.error = null
      try {
        const response = await this.$noteService.getPublicNotes(this.currentPage, this.pageSize)
        const notesData = response && response.data ? response.data : response

        if (notesData && notesData.content) {
          this.notes = notesData.content
          this.totalElements = notesData.totalElements || 0
          this.totalPages = notesData.totalPages || 0
        } else if (Array.isArray(notesData)) {
          this.notes = notesData
          this.totalElements = notesData.length
          this.totalPages = 1
        } else {
          this.notes = []
          this.totalElements = 0
          this.totalPages = 0
        }

        this.extractAvailableTags()
      } catch (error) {
        console.error('Failed to load public notes:', error)
        this.error = error
        this.notes = []
      } finally {
        this.loading = false
      }
    },
    extractAvailableTags() {
      const tagMap = new Map()
      this.notes.forEach(note => {
        if (!note.tags || !Array.isArray(note.tags)) return
        note.tags.forEach(tag => {
          if (tag && tag.id && !tagMap.has(tag.id)) {
            tagMap.set(tag.id, tag)
          }
        })
      })
      this.availableTags = Array.from(tagMap.values())
    },
    extractDescription(content) {
      if (!content) return ''
      let text = content

      if (typeof content === 'string' && content.trim().startsWith('{') && (content.includes('"blocks"') || content.includes('"time"'))) {
        try {
          const editorData = JSON.parse(content)
          if (editorData.blocks && Array.isArray(editorData.blocks)) {
            text = editorData.blocks.map(block => {
              if (block.type === 'paragraph' || block.type === 'header') return block.data?.text || ''
              if (block.type === 'list') {
                const items = block.data?.items || []
                return items.map(item => typeof item === 'string' ? item : item?.content || '').join(' ')
              }
              if (block.type === 'quote') return block.data?.text || ''
              return ''
            }).join(' ')
          }
        } catch (e) {
          text = content
        }
      }

      text = String(text)
        .replace(/<[^>]*>/g, '')
        .replace(/[#*_`~\[\]()]/g, '')
        .replace(/\s+/g, ' ')
        .trim()

      return text.length > 180 ? text.slice(0, 180) + '...' : text
    },
    formatDate(dateString) {
      if (!dateString) return ''
      const date = new Date(dateString)
      if (isNaN(date.getTime())) return ''
      const intlLocale = this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US'
      return new Intl.DateTimeFormat(intlLocale, { year: 'numeric', month: 'short', day: 'numeric' }).format(date)
    },
    formatReadingTime(description, content) {
      const source = `${description || ''} ${content || ''}`.replace(/<[^>]*>/g, ' ')
      const wordLikeCount = source.trim() ? source.trim().split(/\s+/).length : 0
      const cjkCount = (source.match(/[\u4e00-\u9fa5]/g) || []).length
      const minutes = Math.max(1, Math.ceil(Math.max(wordLikeCount / 220, cjkCount / 500)))
      return this.$t('notesPage.readingTime', { n: minutes })
    },
    selectTag(tagId) {
      this.selectedTag = tagId === 'all' || !tagId ? null : tagId
    },
    clearFilters() {
      this.searchKeyword = ''
      this.selectedTag = null
    },
    handleSearch() {
      // Search is handled by the watcher.
    },
    openNote(note) {
      if (note && note.id) {
        this.saveListState()
        this.$router.push(`/notes/${note.id}`)
      } else {
        this.$message.warning(this.$t('notesPage.error.invalidId'))
      }
    },
    previousPage() {
      if (this.currentPage > 0) {
        this.currentPage--
        this.saveListState()
        this.loadNotes()
      }
    },
    nextPage() {
      if (this.currentPage < this.totalPages - 1) {
        this.currentPage++
        this.saveListState()
        this.loadNotes()
      }
    }
  },

  head() {
    return {
      title: 'Blog - MindIO'
    }
  },

  fetch({ store }) {
    store.commit('isHeader', false)
    store.commit('isFooter', false)
  }
}
</script>

<style scoped lang="scss">
.blog-page {
  min-height: 100vh;
  background: var(--bg-secondary);
  color: var(--text-color);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.blog-main {
  width: min(1120px, calc(100% - 40px));
  margin: 0 auto;
  padding: 64px 0 72px;
}

.blog-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  align-items: end;
  gap: 32px;
  padding-bottom: 36px;
  border-bottom: 1px solid var(--border-color);

  h1 {
    max-width: 760px;
    margin: 0;
    font-size: clamp(42px, 7vw, 88px);
    line-height: 0.95;
    letter-spacing: 0;
    color: var(--text-color);
  }
}

.eyebrow {
  margin: 0 0 18px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.hero-subtitle {
  max-width: 700px;
  margin: 22px 0 0;
  color: var(--text-secondary);
  font-size: 18px;
  line-height: 1.7;
}

.hero-stat {
  border-left: 1px solid var(--border-color);
  padding-left: 24px;
}

.stat-number {
  display: block;
  font-size: 42px;
  font-weight: 800;
  // color: #111827;
  color: var(--text-color);
}

.stat-label {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.blog-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 28px 0 34px;
}

.search-bar {
  width: min(420px, 100%);
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;

  i {
    color: var(--text-placeholder);
  }

  input {
    width: 100%;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--text-color);
    font-size: 14px;
  }
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.tag-btn {
  height: 34px;
  padding: 0 13px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;

  &.active,
  &:hover {
    border-color: #2563eb;
    color: #2563eb;
    background: rgba(37, 99, 235, 0.08);
  }
}

.posts-section {
  display: grid;
  grid-template-columns: minmax(340px, 0.95fr) minmax(0, 1.35fr);
  gap: 28px;
  align-items: start;
}

.featured-post,
.post-row {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(37, 99, 235, 0.45);
    box-shadow: 0 16px 34px rgba(15, 23, 42, 0.08);
  }
}

.featured-post {
  position: sticky;
  top: 92px;
  min-height: 420px;
  padding: 30px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background:
    linear-gradient(180deg, rgba(37, 99, 235, 0.08), transparent 42%),
    var(--card-bg-color);

  h2 {
    margin: 0 0 18px;
    color: var(--text-color);
    font-size: 34px;
    line-height: 1.12;
    letter-spacing: 0;
    overflow-wrap: anywhere;
  }

  p {
    margin: 0 0 22px;
    color: var(--text-secondary);
    font-size: 16px;
    line-height: 1.75;
  }
}

.featured-meta,
.post-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.featured-meta {
  margin-bottom: auto;
  justify-content: space-between;
}

.post-list {
  display: grid;
  gap: 12px;
}

.post-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 24px;
  padding: 22px;
}

.post-date {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.post-content {
  min-width: 0;

  h3 {
    margin: 0 0 10px;
    color: var(--text-color);
    font-size: 20px;
    line-height: 1.3;
    overflow-wrap: anywhere;
  }

  p {
    margin: 0 0 14px;
    color: var(--text-secondary);
    line-height: 1.65;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.note-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  color: #0f766e;
  background: rgba(15, 118, 110, 0.08);
}

.state-block {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--text-muted);
  text-align: center;

  i {
    font-size: 42px;
    color: #94a3b8;
  }
}

.loading-spinner {
  width: 34px;
  height: 34px;
  border: 3px solid var(--border-color);
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.text-btn,
.page-btn {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--card-bg-color);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;

  &:hover:not(:disabled) {
    border-color: #2563eb;
    color: #2563eb;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
}

.pagination {
  margin-top: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.page-info {
  color: var(--text-secondary);
  font-size: 14px;
}

.page-total {
  color: var(--text-muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media screen and (max-width: 900px) {
  .blog-main {
    padding: 42px 0 56px;
  }

  .blog-hero,
  .posts-section {
    grid-template-columns: 1fr;
  }

  .hero-stat {
    border-left: 0;
    border-top: 1px solid var(--border-color);
    padding: 18px 0 0;
  }

  .blog-toolbar {
    flex-direction: column;
  }

  .filter-tags {
    justify-content: flex-start;
  }

  .featured-post {
    position: static;
    min-height: 320px;
  }

  .state-block {
    min-height: 320px;
  }
}

@media screen and (max-width: 768px) {
  .blog-page {
    overflow-x: hidden;
  }
}

@media screen and (max-width: 640px) {
  .blog-main {
    width: min(100% - 28px, 1120px);
  }

  .blog-hero h1 {
    font-size: 44px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .post-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .pagination {
    flex-wrap: wrap;
  }

  .page-info {
    width: 100%;
    order: 3;
    text-align: center;
  }
}
</style>
