<template>
  <!-- MindIO public notes page -->
  <div class="mindio-app">
    <!-- Header -->
    <header class="worknotes-header">
      <div class="header-content">
        <div class="header-left">
          <div class="logo">
            <i class="el-icon-notebook-2"></i>
            <span>UnibyteGlobal</span>
          </div>
        </div>
        <nav class="header-nav">
          <nuxt-link to="/" class="nav-link" exact-active-class="active" exact>{{ $t('site.nav.home') }}</nuxt-link>
          <nuxt-link to="/notes" class="nav-link" exact-active-class="active">{{ $t('site.nav.notes') }}</nuxt-link>
          <nuxt-link to="/projects" class="nav-link" exact-active-class="active">{{ $t('site.nav.projects') }}</nuxt-link>
          <nuxt-link to="/services" class="nav-link" exact-active-class="active">{{ $t('site.nav.services') }}</nuxt-link>
          <nuxt-link to="/contact" class="nav-link" exact-active-class="active">{{ $t('site.nav.contact') }}</nuxt-link>
        </nav>
        <div class="header-right">
          <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? $t('topbar.lightMode') : $t('topbar.darkMode')">
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <button class="lang-toggle" @click="toggleLang">{{ $t('lang.toggle') }}</button>
          <button class="menu-toggle" @click="isMobileMenuOpen = !isMobileMenuOpen" aria-label="Menu">
            <i :class="isMobileMenuOpen ? 'el-icon-close' : 'el-icon-s-operation'"></i>
          </button>
          <!-- <div v-if="$auth && $auth.loggedIn && !$device.isMobile" class="user-profile">
            <img src="/default_user.png" alt="User" class="user-avatar" />
          </div>
          <button v-if="$auth && $auth.loggedIn && !$device.isMobile" class="logout-btn" @click="handleLogout">{{ $t('site.header.logout') }}</button> -->
        </div>
      </div>
    </header>

    <!-- 移动端导航遮罩 -->
    <div v-if="isMobileMenuOpen" class="nav-mobile-overlay" @click="isMobileMenuOpen = false"></div>
    <!-- 移动端导航菜单 -->
    <nav v-if="isMobileMenuOpen" class="nav-mobile-menu">
      <nuxt-link to="/" class="mobile-nav-link" exact-active-class="active" exact @click.native="isMobileMenuOpen = false">{{ $t('site.nav.home') }}</nuxt-link>
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.notes') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/services" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.services') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>

    <!-- Main Content -->
    <div class="worknotes-main">
      <!-- Left Sidebar: Calendar -->
      <aside class="calendar-sidebar">
        <div class="calendar-container">
          <NotesCalendar
            :notes-by-date="notesByDate"
            :selected-date="selectedDateStr"
            :selected-month="selectedMonthStr"
            :current-date="currentDateStr"
            storage-key="notes-calendar-scroll"
            @date-selected="handleDateSelected"
            @month-header-click="handleMonthSelected"
          />
        </div>
      </aside>

      <!-- Right Content: Notes -->
      <main class="notes-content">
        <div class="notes-content-inner">
          <!-- Search Bar -->
          <div class="search-section">
            <div class="search-bar">
              <i class="el-icon-search"></i>
              <input
                type="text"
                :placeholder="$t('notesPage.search.placeholder')"
                v-model="searchKeyword"
                @input="handleSearch"
              />
            </div>
          </div>

          <!-- Filter Tags -->
          <div class="filter-tags">
            <button
              class="tag-btn"
              :class="{ active: !selectedTag }"
              @click="selectTag('all')"
            >
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

          <!-- Loading State -->
          <div v-if="loading" class="loading-state">
            <div class="loading-spinner"></div>
            <p>{{ $t('common.loading') }}</p>
          </div>

          <!-- Error State -->
          <div v-else-if="error" class="error-state">
            <i class="el-icon-warning" style="font-size: 48px; color: #e53e3e; margin-bottom: 16px;"></i>
            <p class="error-message">
              {{ error.response?.status === 401 || error.response?.status === 403
                ? $t('notesPage.error.loginRequired')
                : error.response?.status === 404
                ? $t('notesPage.error.notFound')
                : $t('notesPage.error.loadFailed') }}
            </p>
            <button class="retry-btn" @click="loadNotes">
              <i class="el-icon-refresh"></i>
              <span>{{ $t('notesPage.error.retry') }}</span>
            </button>
          </div>

          <!-- Notes Grid -->
          <div v-else-if="filteredNotes.length > 0" class="notes-grid">
            <div
              v-for="note in filteredNotes"
              :key="note.id"
              class="note-card"
              @click="openNote(note)"
            >
              <h3 class="note-title">{{ note.title }}</h3>
              <p class="note-description">{{ note.description }}</p>
              <div class="note-footer">
                <span class="note-date">{{ note.date }}</span>
                <span
                  v-if="note.tag"
                  class="note-tag"
                  :style="{ backgroundColor: note.tagColor }"
                >
                  {{ note.tag }}
                </span>
              </div>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="empty-state">
            <i class="el-icon-document" style="font-size: 64px; color: #cbd5e0; margin-bottom: 16px;"></i>
            <p class="empty-message">
              {{ searchKeyword && searchKeyword.trim()
                ? $t('notesPage.empty.noSearch', { keyword: searchKeyword.trim() })
                : selectedDateStr
                ? $t('notesPage.empty.noDate', { date: selectedDateStr })
                : selectedMonthStr
                ? $t('notesPage.empty.noMonth', { month: selectedMonthStr })
                : selectedTag
                ? $t('notesPage.empty.noTag')
                : $t('notesPage.empty.noNotes') }}
            </p>
            <div v-if="selectedDateStr || selectedMonthStr" class="empty-actions" style="margin-bottom: 16px;">
              <button class="clear-filter-btn" @click="clearTimeFilter" style="display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px; background: var(--card-bg-color); border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-secondary); font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.2s;">
                <i class="el-icon-close"></i>
                <span>{{ $t('notesPage.empty.clearTime') }}</span>
              </button>
            </div>
            <button v-if="!selectedDateStr && !selectedMonthStr" class="new-note-btn" @click="createNewNote">
              <i class="el-icon-plus"></i>
              <span>{{ $t('notesPage.empty.createNote') }}</span>
            </button>
          </div>

          <!-- Pagination -->
          <div v-if="!loading && !error && totalElements > 0 && !selectedDateStr && !selectedMonthStr" class="pagination">
            <button
              class="page-btn"
              :disabled="currentPage === 0 || loading"
              @click="previousPage"
            >
              <i class="el-icon-arrow-left"></i>
              <span>{{ $t('notesPage.pagination.prev') }}</span>
            </button>
            <span class="page-info">
              {{ $t('notesPage.pagination.info', { page: currentPage + 1, total: totalPages }) }}
              <span class="page-total">{{ $t('notesPage.pagination.total', { count: totalElements }) }}</span>
            </span>
            <button
              class="page-btn"
              :disabled="currentPage >= totalPages - 1 || loading"
              @click="nextPage"
            >
              <span>{{ $t('notesPage.pagination.next') }}</span>
              <i class="el-icon-arrow-right"></i>
            </button>
          </div>
        </div>
      </main>
    </div>

    <!-- 移动端：底部上滑提示条 -->
    <div
      class="mobile-bottom-handle"
      v-show="!isSheetOpen"
      @click="openSheet"
      @touchstart.passive="onHandleTouchStart"
      @touchend="onHandleTouchEnd"
    >
      <div class="mobile-handle-bar"></div>
      <span class="mobile-handle-text">上滑查看月历</span>
    </div>

    <!-- 移动端：半透明遮罩 -->
    <div
      class="mobile-sheet-overlay"
      v-show="isSheetOpen"
      @click="closeSheet"
    ></div>

    <!-- 移动端：底部抽屉月历 -->
    <div
      class="mobile-bottom-sheet"
      :class="{ 'is-open': isSheetOpen }"
      @touchstart.passive="onSheetTouchStart"
      @touchend="onSheetTouchEnd"
    >
      <div class="mobile-sheet-drag-bar" @click="closeSheet">
        <div class="mobile-handle-bar"></div>
      </div>
      <div class="mobile-sheet-title">选择日期</div>
      <div class="mobile-sheet-calendar">
        <NotesCalendar
          :notes-by-date="notesByDate"
          :selected-date="selectedDateStr"
          :selected-month="selectedMonthStr"
          :current-date="currentDateStr"
          storage-key="notes-calendar-mobile"
          @date-selected="handleDateSelected"
          @month-header-click="handleMonthSelected"
        />
      </div>
      <div v-if="selectedDateStr || selectedMonthStr" class="mobile-sheet-result">
        📝 {{ selectedDateStr || selectedMonthStr }} · 共 {{ filteredNotes.length }} 篇笔记
      </div>
    </div>

    <!-- 移动端：悬浮按钮 -->
    <button
      class="mobile-fab"
      @click="selectedDateStr || selectedMonthStr ? clearTimeFilter() : openSheet()"
    >
      <template v-if="selectedDateStr || selectedMonthStr">
        <span class="mobile-fab-x">✕</span>
        <span class="mobile-fab-date">{{ selectedDateStr || selectedMonthStr }}</span>
      </template>
      <template v-else>📅</template>
    </button>
  </div>
</template>

<script>
export default {
  layout: 'blank',
  auth: false, // 页面不需要认证，但 API 调用需要 token

  components: {
    NotesCalendar: () => import('~/components/NotesCalendar.vue')
  },

  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,

      // Current date
      currentDate: new Date(),
      selectedDateStr: null, // 选中的日期 'YYYY-MM-DD'
      selectedMonthStr: null, // 选中的月份 'YYYY-MM'
      isSheetOpen: false,
      touchStartY: 0,
      isRestoringListState: false,
      listStateStorageKey: 'worknotes:notes:list-state',
      listScrollSaveTimer: null,

      // Loading and error states
      loading: false,
      error: null,

      // Search
      searchKeyword: '',
      searchDebounceTimer: null,

      // Tags
      selectedTag: null, // null means 'all'
      tags: [],
      availableTags: [], // All available tags from API

      // Notes
      notes: [],
      allNotesForCalendar: [], // 用于月历的所有笔记
      totalElements: 0,
      totalPages: 0,
      currentPage: 0,
      pageSize: 12,

      // Calendar note dates (for highlighting)
      notesByDate: {} // { 'YYYY-MM-DD': [note1, note2, ...] }
    };
  },

  computed: {
    currentDateStr() {
      return new Date().toISOString().split('T')[0]
    },

    filteredNotes() {
      let filtered = (this.selectedDateStr || this.selectedMonthStr)
        ? this.allNotesForCalendar
        : this.notes

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
        filtered = filtered.filter(note => {
          return note.tags && note.tags.some(tag => tag.id === this.selectedTag)
        })
      }

      // 按日期筛选
      if (this.selectedDateStr) {
        filtered = filtered.filter(note => {
          const noteDate = new Date(note.modifiedAt || note.createdAt)
          const noteDateStr = noteDate.toISOString().split('T')[0]
          return noteDateStr === this.selectedDateStr
        })
      } else if (this.selectedMonthStr) {
        filtered = filtered.filter(note => {
          const noteDate = new Date(note.modifiedAt || note.createdAt)
          const noteMonthStr = noteDate.toISOString().slice(0, 7)
          return noteMonthStr === this.selectedMonthStr
        })
      }

      // 映射并返回
      return filtered.map(note => ({
        ...note,
        description: note.summary || this.extractDescription(note.content),
        date: this.formatDate(note.modifiedAt || note.createdAt),
        tag: note.tags && note.tags.length > 0 ? note.tags[0].name : '',
        tagColor: this.getTagColor(note.tags && note.tags.length > 0 ? note.tags[0].name : ''),
        tagId: note.tags && note.tags.length > 0 ? note.tags[0].id : null,
        noteDate: new Date(note.modifiedAt || note.createdAt)
      }))
    }
  },

  async mounted() {
    if (process.client) {
      const root = document.documentElement;
      this.isDarkTheme = root.classList.contains('theme-dark');
      this.restoreListState()
    }
    await this.loadTags()
    await this.loadNotesDates() // 先加载所有笔记的日期数据
    await this.loadNotes()
    this.$nextTick(() => {
      this.setupListScrollPersistence()
      this.restoreListScrollPosition()
    })
  },

  beforeDestroy() {
    this.saveListState()
    this.removeListScrollPersistence()
    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer)
    }
    if (this.listScrollSaveTimer) {
      clearTimeout(this.listScrollSaveTimer)
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false },
    selectedTag() {
      if (this.isRestoringListState) return
      // 标签改变时重置到第一页并重新加载
      this.currentPage = 0
      this.saveListState()
      this.loadNotes()
    },
    searchKeyword(newVal, oldVal) {
      if (this.isRestoringListState) return
      // 搜索关键词改变时，使用 500ms 防抖
      if (this.searchDebounceTimer) {
        clearTimeout(this.searchDebounceTimer)
      }
      
      // 如果搜索框被清空，立即加载（不需要防抖）
      if (!newVal || !newVal.trim()) {
        this.currentPage = 0
        this.saveListState()
        this.loadNotes()
        return
      }
      
      // 否则使用防抖
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
      return document.querySelector('.notes-content')
    },
    getListStatePayload() {
      const notesContent = this.getNotesContentElement()
      return {
        searchKeyword: this.searchKeyword,
        selectedTag: this.selectedTag,
        selectedDateStr: this.selectedDateStr,
        selectedMonthStr: this.selectedMonthStr,
        currentPage: this.currentPage,
        scrollTop: notesContent ? notesContent.scrollTop : 0,
        savedAt: Date.now()
      }
    },
    saveListState() {
      if (!process.client) return
      try {
        sessionStorage.setItem(this.listStateStorageKey, JSON.stringify(this.getListStatePayload()))
      } catch (e) {
        console.warn('保存笔记列表状态失败:', e)
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
        this.selectedDateStr = state.selectedDateStr || null
        this.selectedMonthStr = state.selectedMonthStr || null
        this.currentPage = Number.isInteger(state.currentPage) && state.currentPage >= 0 ? state.currentPage : 0
        this._pendingListScrollTop = Number.isFinite(state.scrollTop) ? state.scrollTop : 0
      } catch (e) {
        console.warn('恢复笔记列表状态失败:', e)
      } finally {
        this.$nextTick(() => {
          this.isRestoringListState = false
        })
      }
    },
    restoreListScrollPosition() {
      if (!process.client || this._pendingListScrollTop == null) return
      const notesContent = this.getNotesContentElement()
      if (!notesContent) return
      notesContent.scrollTop = this._pendingListScrollTop
      this._pendingListScrollTop = null
    },
    setupListScrollPersistence() {
      const notesContent = this.getNotesContentElement()
      if (!notesContent || this._notesListScrollHandler) return
      this._notesListScrollHandler = () => {
        if (this.listScrollSaveTimer) clearTimeout(this.listScrollSaveTimer)
        this.listScrollSaveTimer = setTimeout(() => {
          this.saveListState()
        }, 150)
      }
      notesContent.addEventListener('scroll', this._notesListScrollHandler)
    },
    removeListScrollPersistence() {
      const notesContent = this.getNotesContentElement()
      if (notesContent && this._notesListScrollHandler) {
        notesContent.removeEventListener('scroll', this._notesListScrollHandler)
      }
      this._notesListScrollHandler = null
    },
    toggleLang() {
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      this.$i18n.setLocale(next)
    },
    toggleTheme() {
      if (this.$root.$options.app && this.$root.$options.app.themeToggle) {
        this.isDarkTheme = this.$root.$options.app.themeToggle();
      } else if (process.client) {
        const root = document.documentElement;
        const isDark = root.classList.toggle('theme-dark');
        window.localStorage.setItem('worknotes-theme', isDark ? 'dark' : 'light');
        this.isDarkTheme = isDark;
      }
    },

    /**
     * 从公开笔记中提取标签（因为公开接口可能不支持标签列表接口）
     */
    async loadTags() {
      try {
        // 对于公开笔记页面，我们从已加载的笔记中提取标签
        // 或者尝试调用公开标签接口（如果后端有的话）
        // 暂时先设置为空数组，等笔记加载后再提取
        this.availableTags = []
      } catch (error) {
        console.error('加载标签失败:', error)
        this.availableTags = []
      }
    },

    /**
     * 加载笔记列表
     */
    async loadNotes() {
      this.loading = true
      this.error = null

      try {
        // 使用公开笔记接口（无需认证）
        const response = await this.$noteService.getPublicNotes(
          this.currentPage,
          this.pageSize
        )
        
        // 处理分页响应（Spring Data Page 格式）
        let notesData = response
        if (response && response.data) {
          notesData = response.data
        }

        // 处理分页响应（Spring Data Page 格式）
        if (notesData && notesData.content) {
          let notes = notesData.content
          
          // 前端过滤：如果有搜索关键词，在前端进行简单筛选
          if (this.searchKeyword && this.searchKeyword.trim()) {
            const keyword = this.searchKeyword.trim().toLowerCase()
            notes = notes.filter(note => {
              const title = (note.title || '').toLowerCase()
              const summary = (note.summary || '').toLowerCase()
              const content = (note.content || '').toLowerCase()
              return title.includes(keyword) || summary.includes(keyword) || content.includes(keyword)
            })
          }
          
          // 前端过滤：如果有选中的标签，在前端进行筛选
          if (this.selectedTag) {
            notes = notes.filter(note => {
              return note.tags && note.tags.some(tag => tag.id === this.selectedTag)
            })
          }
          
          this.notes = notes
          this.totalElements = notesData.totalElements || notes.length
          this.totalPages = notesData.totalPages || Math.max(1, Math.ceil(this.totalElements / this.pageSize))
        } else {
          // 如果不是分页响应，直接使用数组
          this.notes = Array.isArray(notesData) ? notesData : []
          this.totalElements = this.notes.length
          this.totalPages = Math.max(1, Math.ceil(this.totalElements / this.pageSize))
          this.extractTagsFromNotes(this.notes)
        }
        
        // 从笔记中提取标签（用于标签筛选器）
        if (notesData && notesData.content) {
          this.extractTagsFromNotes(notesData.content)
        } else {
          this.extractTagsFromNotes(this.notes)
        }

          // 从笔记中提取标签（用于标签筛选器）
          this.extractTagsFromNotes(notesData.content)
      } catch (error) {
        console.error('加载笔记失败:', error)
        this.error = error
        this.notes = []
        this.totalElements = 0
        this.totalPages = 0
        
        if (error.response?.status === 401 || error.response?.status === 403) {
          this.$message.error(this.$t('notesPage.error.loginRequired'))
        } else if (error.response?.status === 404) {
          this.$message.warning(this.$t('notesPage.error.notFound'))
        } else {
          this.$message.error(this.$t('notesPage.error.loadFailed'))
        }
      } finally {
        this.loading = false
      }
    },

    /**
     * 从笔记中提取标签（用于公开笔记页面的标签筛选）
     */
    extractTagsFromNotes(notes) {
      const tagMap = new Map()
      notes.forEach(note => {
        if (note.tags && Array.isArray(note.tags)) {
          note.tags.forEach(tag => {
            if (!tagMap.has(tag.id)) {
              tagMap.set(tag.id, tag)
            }
          })
        }
      })
      this.availableTags = Array.from(tagMap.values())
    },

    /**
     * 加载所有公开笔记的日期数据（用于月历显示）
     */
    async loadNotesDates() {
      try {
        // 获取所有公开笔记（分页获取全部）
        let allNotes = []
        let page = 0
        const size = 100 // 每页获取更多数据
        let hasMore = true

        while (hasMore) {
          const response = await this.$noteService.getPublicNotes(page, size)
          let notesData = response
          if (response && response.data) {
            notesData = response.data
          }

          const notes = notesData.content || []
          allNotes = allNotes.concat(notes)
          
          if (notes.length < size || allNotes.length >= (notesData.totalElements || 0)) {
            hasMore = false
          } else {
            page++
          }
        }

        this.allNotesForCalendar = allNotes
        // 构建日期到笔记的映射
        const notesByDate = {}
        allNotes.forEach(note => {
          if (note.createdAt || note.modifiedAt) {
            const date = new Date(note.modifiedAt || note.createdAt)
            if (!isNaN(date.getTime())) {
              const dateKey = date.toISOString().split('T')[0]
              if (!notesByDate[dateKey]) {
                notesByDate[dateKey] = []
              }
              notesByDate[dateKey].push(note)
            }
          }
        })
        this.notesByDate = notesByDate
      } catch (error) {
        console.error('加载笔记日期数据失败:', error)
        // 静默失败，不影响主列表
        this.notesByDate = {}
      }
    },

    /**
     * 从内容中提取描述/摘要
     * 支持 EditorJS、HTML 和 Markdown 格式
     */
    extractDescription(content) {
      if (!content) return ''

      let text = content

      // 检测是否为 EditorJS JSON 格式
      if (content.trim().startsWith('{') && (content.includes('"blocks"') || content.includes('"time"'))) {
        try {
          const editorData = JSON.parse(content)
          if (editorData.blocks && Array.isArray(editorData.blocks)) {
            // 提取所有文本块的内容
            text = editorData.blocks
              .map(block => {
                // 提取不同类型块的文本
                if (block.type === 'paragraph' || block.type === 'header') {
                  return block.data?.text || ''
                } else if (block.type === 'list') {
                  const items = block.data?.items || []
                  return items.map(item => {
                    // 处理嵌套列表项（对象格式）
                    if (typeof item === 'string') {
                      return item
                    } else if (item && item.content) {
                      return item.content
                    }
                    return ''
                  }).join(' ')
                } else if (block.type === 'quote') {
                  return block.data?.text || ''
                } else if (block.type === 'code') {
                  return '' // 跳过代码块
                }
                return ''
              })
              .filter(Boolean)
              .join(' ')
          }
        } catch (e) {
          // JSON 解析失败，继续按原方式处理
          console.warn('解析 EditorJS 内容失败:', e)
        }
      }

      // 移除 HTML 标签
      text = text.replace(/<[^>]*>/g, '')

      // 移除 Markdown 语法
      text = text
        .replace(/^#{1,6}\s+/gm, '') // 标题
        .replace(/\*\*([^*]+)\*\*/g, '$1') // 粗体
        .replace(/\*([^*]+)\*/g, '$1') // 斜体
        .replace(/`([^`]+)`/g, '$1') // 行内代码
        .replace(/```[\s\S]*?```/g, '') // 代码块
        .replace(/\[([^\]]+)\]\([^\)]+\)/g, '$1') // 链接
        .replace(/!\[([^\]]*)\]\([^\)]+\)/g, '') // 图片
        .replace(/^\s*[-*+]\s+/gm, '') // 列表项
        .replace(/^\s*\d+\.\s+/gm, '') // 有序列表
        .replace(/^>\s+/gm, '') // 引用
        .replace(/\n{3,}/g, '\n\n') // 多个换行符
        .trim()

      // 移除多余的空白字符
      text = text.replace(/\s+/g, ' ')

      // 返回前 150 个字符
      if (text.length > 150) {
        // 尝试在单词边界截断
        const truncated = text.substring(0, 150)
        const lastSpace = truncated.lastIndexOf(' ')
        return lastSpace > 100 ? truncated.substring(0, lastSpace) + '...' : truncated + '...'
      }

      return text
    },

    /**
     * 格式化日期
     * 支持相对时间显示（如"2天前"）和绝对时间显示
     */
    formatDate(dateString) {
      if (!dateString) return ''
      
      const date = new Date(dateString)
      if (isNaN(date.getTime())) return ''
      
      const now = new Date()
      const diffMs = now.getTime() - date.getTime()
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
      const diffMinutes = Math.floor(diffMs / (1000 * 60))
      
      if (diffMinutes < 1) return this.$t('notesPage.time.justNow')
      if (diffMinutes < 60) return this.$t('notesPage.time.minutesAgo', { n: diffMinutes })
      if (diffHours < 24) return this.$t('notesPage.time.hoursAgo', { n: diffHours })
      if (diffDays === 0) return this.$t('notesPage.time.today')
      if (diffDays === 1) return this.$t('notesPage.time.yesterday')
      if (diffDays === 2) return this.$t('notesPage.time.twoDaysAgo')
      if (diffDays < 7) return this.$t('notesPage.time.daysAgo', { n: diffDays })
      if (diffDays < 30) return this.$t('notesPage.time.weeksAgo', { n: Math.floor(diffDays / 7) })
      if (diffDays < 365) return this.$t('notesPage.time.monthsAgo', { n: Math.floor(diffDays / 30) })
      const intlLocale = this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US'
      return new Intl.DateTimeFormat(intlLocale, { year: 'numeric', month: 'short', day: 'numeric' }).format(date)
    },

    /**
     * 获取标签颜色
     */
    getTagColor(tagName) {
      const colorMap = {
        'Vue.js': '#E6F7ED',
        'Backend': '#FFF4E6',
        'JavaScript': '#FFF9E6',
        'DevOps': '#E6F2FF',
        'Tutorial': '#F3E5F5'
      }
      return colorMap[tagName] || '#F5F5F5'
    },

    /**
     * 处理日期选择（来自 NotesCalendar 组件）
     */
    handleDateSelected(dateStr) {
      this.selectedDateStr = dateStr
      this.selectedMonthStr = null
      // 重置到第一页
      this.currentPage = 0
      this.saveListState()
      // 重新加载笔记（会应用日期筛选）
      this.loadNotes()
      // 滚动到笔记列表顶部
      this.$nextTick(() => {
        const notesContent = document.querySelector('.notes-content')
        if (notesContent) {
          notesContent.scrollTo({ top: 0, behavior: 'smooth' })
        }
      })
    },

    /**
     * 处理月份选择（来自 NotesCalendar 组件）
     */
    handleMonthSelected(monthStr) {
      this.selectedMonthStr = monthStr
      this.selectedDateStr = null
      this.currentPage = 0
      this.saveListState()
      this.loadNotes()
      this.$nextTick(() => {
        const notesContent = document.querySelector('.notes-content')
        if (notesContent) {
          notesContent.scrollTo({ top: 0, behavior: 'smooth' })
        }
      })
    },

    /**
     * 清除日期筛选
     */
    clearDateFilter() {
      this.selectedDateStr = null
      this.selectedMonthStr = null
      this.currentPage = 0
      this.saveListState()
      this.loadNotes()
    },

    clearTimeFilter() {
      this.clearDateFilter()
    },

    openSheet() {
      this.isSheetOpen = true
    },
    closeSheet() {
      this.isSheetOpen = false
    },
    onHandleTouchStart(e) {
      this.touchStartY = e.touches[0].clientY
    },
    onHandleTouchEnd(e) {
      const dy = this.touchStartY - e.changedTouches[0].clientY
      if (dy > 40) this.openSheet()
    },
    onSheetTouchStart(e) {
      this.touchStartY = e.touches[0].clientY
    },
    onSheetTouchEnd(e) {
      const dy = e.changedTouches[0].clientY - this.touchStartY
      if (dy > 60) this.closeSheet()
    },

    selectTag(tagId) {
      this.selectedTag = tagId === 'all' || !tagId ? null : tagId
    },

    handleSearch() {
      // Search is handled by watch
    },

    openNote(note) {
      console.log('openNote', note)
      // Navigate to note detail page
      if (note && note.id) {
        this.saveListState()
        console.log('openNote router', this.$router)
        this.$router.push(`/notes/${note.id}`)
      } else {
        this.$message.warning(this.$t('notesPage.error.invalidId'))
      }
    },

    handleLogout() {
      this.$auth.logout()
      this.$message.success(this.$t('messages.loggedOut'))
    },

    createNewNote() {
      this.$router.push('/workspace/notes/new')
    },

    /**
     * 加载上一页
     */
    previousPage() {
      if (this.currentPage > 0) {
        this.currentPage--
        this.saveListState()
        this.loadNotes()
      }
    },

    /**
     * 加载下一页
     */
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
      title: 'MindIO - Personal Workspace',
    };
  },

  fetch({ store }) {
    store.commit("isHeader", false); // Hide default header
    store.commit("isFooter", false); // Hide default footer
  }
};
</script>

<style scoped lang="scss">
.mindio-app {
  min-height: 100vh;
  background: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

// Header
.worknotes-header {
  background: var(--header-bg);
  box-shadow: 0 1px 3px var(--shadow-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
}

.header-left {
  display: flex;
  align-items: center;
  width: 280px;
  flex-shrink: 0;
  padding: 0 24px;
  background: var(--header-bg);
  border-right: 1px solid var(--border-color);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-color);

  i {
    font-size: 24px;
    color: #667eea;
  }
}

.header-nav {
  display: flex;
  gap: 32px;
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.nav-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 15px;
  font-weight: 500;
  transition: color 0.2s;

  &:hover {
    color: #667eea;
  }

  &.active {
    color: #667eea;
    position: relative;

    &::after {
      content: '';
      position: absolute;
      bottom: -20px;
      left: 0;
      right: 0;
      height: 2px;
      background: #667eea;
    }
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
}

.user-profile {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.logout-btn {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--bg-secondary);
    border-color: var(--border-color);
  }
}

.theme-toggle,
.lang-toggle {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;

  i {
    font-size: 16px;
  }

  &:hover {
    background: rgba(148, 163, 184, 0.08);
  }
}

.lang-toggle {
  font-size: 12px;
  font-weight: 600;
}

// Main Content
.worknotes-main {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: start;
  gap: 24px;
  width: min(1240px, calc(100% - 48px));
  margin: 0 auto;
  padding: 32px 0 48px;
  min-height: calc(100vh - 64px);
}

// Calendar Sidebar
.calendar-sidebar {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: calc(100vh - 128px);
  min-height: 520px;
  position: sticky;
  top: 96px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.calendar-container {
  background: var(--card-bg-color);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// Notes Content
.notes-content {
  min-width: 0;
}

.notes-content-inner {
  width: 100%;
}

.search-section {
  margin-bottom: 24px;
  display: flex;
  gap: 16px;
  align-items: center;
}

.search-bar {
  flex: 1;
  background: var(--card-bg-color);
  border-radius: 8px;
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);

  i {
    font-size: 20px;
    color: var(--text-placeholder);
  }

  input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 15px;
    color: var(--text-color);
    background: transparent;

    &::placeholder {
      color: var(--text-placeholder);
    }
  }
}

.new-note-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  color: white;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

  i {
    font-size: 16px;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  }

  &:active {
    transform: translateY(0);
  }
}

// Filter Tags
.filter-tags {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}

.tag-btn {
  padding: 8px 20px;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);

  &:hover {
    background: var(--bg-secondary);
    border-color: var(--border-color);
    transform: translateY(-1px);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  }

  &.active {
    background: #667eea;
    color: white;
    border-color: #667eea;
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
  }
}

// Notes Grid
.notes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.note-card {
  background: var(--card-bg-color);
  border-radius: 8px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    border-color: var(--border-color);
  }
}

.note-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
  line-height: 1.4;
  // 处理连续字符串越界问题
  word-break: break-word;
  overflow-wrap: break-word;
  overflow: hidden;
  // 限制最多显示2行，超出部分用省略号
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-description {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 0 0 20px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.note-date {
  font-size: 13px;
  color: var(--text-placeholder);
}

.note-tag {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  border: solid 1px var(--border-color);
  background-color: transparent !important;
  color: var(--text-secondary);
}

// 移动端专用元素 — 桌面默认隐藏
.mobile-bottom-handle,
.mobile-sheet-overlay,
.mobile-bottom-sheet,
.mobile-fab {
  display: none;
}

.menu-toggle {
  display: none;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: transparent;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  flex-shrink: 0;

  i { font-size: 18px; }
  &:hover { background: var(--bg-secondary); }
}

.nav-mobile-overlay {
  display: none;
}

.nav-mobile-menu {
  display: none;
}

// Responsive Design
@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
    padding: 0 16px;
  }

  .header-nav {
    margin-left: 24px;
    gap: 24px;
  }

  .worknotes-main {
    grid-template-columns: 1fr;
    gap: 20px;
    width: min(100% - 32px, 760px);
    padding: 24px 0 40px;
    min-height: calc(100vh - 64px);
  }

  .calendar-sidebar {
    width: 100%;
    padding: 16px;
    height: auto;
    min-height: 0;
    max-height: 400px;
    position: static;
  }

  .notes-content {
    min-height: 0;
  }

  .calendar-container {
    max-width: 100%;
  }

  .notes-content-inner {
    padding: 0;
  }

  .notes-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;
  }

  .search-section {
    flex-direction: column;
    gap: 12px;
  }

  .search-bar {
    width: 100%;
  }

  .new-note-btn {
    width: 100%;
    justify-content: center;
  }

  .pagination {
    flex-wrap: wrap;
    gap: 12px;
    padding: 16px;
  }

  .page-info {
    width: 100%;
    text-align: center;
    order: 3;
  }
}

@media screen and (max-width: 768px) {
  .header-nav {
    display: none;
  }

  .header-left {
    padding: 0 20px;
  }

  .header-right {
    padding: 0 20px;
    gap: 8px;
  }

  .logo span {
    display: none;
  }

  .menu-toggle {
    display: flex;
  }

  .nav-mobile-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 150;
  }

  .nav-mobile-menu {
    display: flex;
    flex-direction: column;
    position: fixed;
    top: 64px;
    left: 0;
    right: 0;
    background: var(--header-bg);
    border-bottom: 1px solid var(--border-color);
    box-shadow: 0 8px 24px var(--shadow-color);
    z-index: 200;
    padding: 8px 0 16px;

    .mobile-nav-link {
      display: block;
      padding: 14px 24px;
      color: var(--text-secondary);
      text-decoration: none;
      font-size: 16px;
      font-weight: 500;
      border-left: 3px solid transparent;
      transition: all 0.15s;

      &:hover {
        color: #667eea;
        background: var(--bg-secondary);
      }

      &.active {
        color: #667eea;
        border-left-color: #667eea;
        background: rgba(102, 126, 234, 0.06);
      }
    }
  }

  .logo {
    font-size: 18px;

    i {
      font-size: 20px;
    }
  }

  .logout-btn {
    padding: 6px 12px;
    font-size: 13px;
  }

  .user-profile {
    width: 32px;
    height: 32px;
  }

  .calendar-sidebar {
    display: none;
  }

  // 底部提示条
  .mobile-bottom-handle {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: var(--card-bg-color);
    border-top: 1px solid var(--border-color);
    padding: 8px 0 12px;
    z-index: 98;
    cursor: pointer;
  }

  .mobile-handle-bar {
    width: 32px;
    height: 4px;
    background: var(--border-color);
    border-radius: 2px;
  }

  .mobile-handle-text {
    font-size: 11px;
    color: var(--text-muted);
  }

  // 遮罩
  .mobile-sheet-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.5);
    z-index: 99;
  }

  // 底部抽屉
  .mobile-bottom-sheet {
    display: flex;
    flex-direction: column;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 78vh;
    background: var(--card-bg-color);
    border-radius: 16px 16px 0 0;
    z-index: 100;
    transform: translateY(100%);
    transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);

    &.is-open {
      transform: translateY(0);
    }
  }

  .mobile-sheet-drag-bar {
    display: flex;
    justify-content: center;
    padding: 10px 0 4px;
    cursor: pointer;
    flex-shrink: 0;
  }

  .mobile-sheet-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-color);
    text-align: center;
    padding: 0 0 8px;
    flex-shrink: 0;
  }

  .mobile-sheet-calendar {
    flex: 1;
    overflow-y: auto;
    padding: 0 8px;
    min-height: 0;
  }

  .mobile-sheet-result {
    flex-shrink: 0;
    background: var(--bg-secondary);
    border-top: 1px solid var(--border-color);
    padding: 10px 16px;
    font-size: 13px;
    color: #667eea;
    font-weight: 500;
    text-align: center;
  }

  // 悬浮按钮
  .mobile-fab {
    display: flex;
    align-items: center;
    gap: 4px;
    position: fixed;
    right: 16px;
    bottom: 52px;
    min-width: 44px;
    height: 44px;
    padding: 0 14px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;
    border-radius: 22px;
    color: white;
    font-size: 18px;
    cursor: pointer;
    z-index: 100;
    box-shadow: 0 4px 16px rgba(102, 126, 234, 0.45);
    transition: all 0.2s;

    &:active {
      transform: scale(0.94);
    }
  }

  .mobile-fab-x {
    font-size: 13px;
    font-weight: 700;
  }

  .mobile-fab-date {
    font-size: 12px;
    font-weight: 600;
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // 笔记内容底部留出 handle 条空间
  .notes-content {
    padding-bottom: 52px;
  }

  .notes-content-inner {
    padding: 0;
  }

  .notes-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .note-card {
    padding: 20px;
  }

  .note-title {
    font-size: 16px;
    margin-bottom: 10px;
  }

  .note-description {
    font-size: 13px;
    margin-bottom: 16px;
  }

  .note-footer {
    flex-wrap: wrap;
    gap: 8px;
  }

  .note-date {
    font-size: 12px;
  }

  .note-tag {
    font-size: 11px;
    padding: 3px 10px;
  }

  .filter-tags {
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 8px;
    margin-bottom: 24px;
    -webkit-overflow-scrolling: touch;

    &::-webkit-scrollbar {
      height: 4px;
    }

    &::-webkit-scrollbar-track {
      background: rgba(0, 0, 0, 0.05);
      border-radius: 2px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 2px;

      &:hover {
        background: rgba(0, 0, 0, 0.3);
      }
    }
  }

  .tag-btn {
    padding: 6px 16px;
    font-size: 13px;
    white-space: nowrap;
  }

  .search-section {
    margin-bottom: 20px;
  }

  .search-bar {
    padding: 10px 16px;

    i {
      font-size: 18px;
    }

    input {
      font-size: 14px;
    }
  }

  .new-note-btn {
    padding: 10px 20px;
    font-size: 14px;
  }

  .pagination {
    gap: 8px;
    padding: 12px 16px;
    margin-top: 32px;
  }

  .page-btn {
    padding: 8px 12px;
    flex-shrink: 0;
  }

  .page-info {
    font-size: 12px;
    text-align: center;
    flex: 1;
  }

  .loading-state,
  .error-state,
  .empty-state {
    padding: 40px 20px;
  }
}

// 超小屏幕优化
@media screen and (max-width: 480px) {
  .calendar-sidebar {
    display: none;
  }

  .notes-content-inner {
    padding: 0;
  }

  .note-card {
    padding: 16px;
  }

  .pagination {
    padding: 12px;
  }

  .page-info {
    font-size: 11px;
  }
}

// Loading State
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;

  .loading-spinner {
    width: 40px;
    height: 40px;
    border: 4px solid var(--border-color);
    border-top: 4px solid #667eea;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: 16px;
  }

  p {
    color: var(--text-muted);
    font-size: 14px;
  }
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

// Error State
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;

  .error-message {
    color: #e53e3e;
    font-size: 16px;
    margin-bottom: 24px;
    font-weight: 500;
  }

  .retry-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 24px;
    background: #667eea;
    color: white;
    border: none;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;

    i {
      font-size: 16px;
    }

    &:hover {
      background: #5568d3;
      transform: translateY(-1px);
      box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

// Empty State
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;

  .empty-message {
    color: #718096;
    font-size: 16px;
    margin-bottom: 24px;
    font-weight: 500;
  }
}

// Pagination
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-top: 40px;
  padding: 24px;
  background: var(--card-bg-color);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

  .page-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 20px;
    background: var(--card-bg-color);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    color: var(--text-secondary);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;

    i {
      font-size: 14px;
    }

    &:hover:not(:disabled) {
      background: var(--bg-secondary);
      border-color: var(--border-color);
      transform: translateY(-1px);
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
    }

    &:active:not(:disabled) {
      transform: translateY(0);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .page-info {
    color: var(--text-secondary);
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 4px;

    strong {
      color: #667eea;
      font-weight: 600;
    }

    .page-total {
      color: var(--text-muted);
      font-size: 13px;
      margin-left: 4px;
    }
  }
}
</style>
