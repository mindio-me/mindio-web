<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="home-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="logo">
            <MindioLogo />
            <span>MindIO</span>
          </div>
        </div>
        <nav class="header-nav">
          <nuxt-link to="/" class="nav-link" exact-active-class="active" exact>{{ $t('site.nav.home') }}</nuxt-link>
          <nuxt-link to="/notes" class="nav-link" exact-active-class="active">{{ $t('site.nav.blog') }}</nuxt-link>
          <nuxt-link to="/projects" class="nav-link" exact-active-class="active">{{ $t('site.nav.projects') }}</nuxt-link>
          <nuxt-link to="/contact" class="nav-link" exact-active-class="active">{{ $t('site.nav.contact') }}</nuxt-link>
        </nav>
        <div class="header-right">
          <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? $t('topbar.lightMode') : $t('topbar.darkMode')">
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <button class="lang-toggle" @click="toggleLang">{{ $t('lang.toggle') }}</button>
          <div v-if="$auth && $auth.loggedIn" class="avatar" @click="handleAvatarClick">
            <img src="/default_user.png" alt="User" />
          </div>
          <div v-if="(!$auth || !$auth.loggedIn) && !$device.isMobile" class="header-actions">
            <button class="login-btn" @click="goLogin">{{ $t('site.header.login') }}</button>
          </div>
          <div v-else-if="!$device.isMobile" class="header-actions">
            <button class="app-btn" @click="goApp">{{ $t('site.header.goToApp') }}</button>
            <button class="logout-btn" @click="handleLogout">{{ $t('site.header.logout') }}</button>
          </div>
          <button class="menu-toggle" @click="isMobileMenuOpen = !isMobileMenuOpen" aria-label="Menu">
            <i :class="isMobileMenuOpen ? 'el-icon-close' : 'el-icon-s-operation'"></i>
          </button>
        </div>
      </div>
    </header>

    <!-- Mobile Navigation -->
    <div v-if="isMobileMenuOpen" class="mobile-overlay" @click="isMobileMenuOpen = false"></div>
    <nav v-if="isMobileMenuOpen" class="mobile-menu">
      <nuxt-link to="/" class="mobile-nav-link" exact-active-class="active" exact @click.native="isMobileMenuOpen = false">{{ $t('site.nav.home') }}</nuxt-link>
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.blog') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-container">
        <div class="hero-eyebrow">{{ $t('home.hero.eyebrow') }}</div>
        <h1 class="hero-title">{{ heroTitle }}</h1>
        <p class="hero-subtitle">{{ heroSubtitle }}</p> 
        <div class="hero-actions">
          <button class="btn-primary" @click="goApp">{{ $t('home.hero.viewWork') }}</button>
          <button class="btn-secondary" @click="contactMe">{{ $t('home.hero.letsTalk') }}</button>
        </div>
      </div>
    </section>

    <!-- Featured Projects Section -->
    <section class="products-section" v-if="contentLoading || featuredProjects.length > 0">
      <div class="section-container">
        <h2 class="section-title">{{ $t('home.products.sectionTitle') }}</h2>
        <div class="products-grid">
          <template v-if="contentLoading">
            <div v-for="n in 3" :key="'proj-skeleton-' + n" class="product-card product-card--skeleton" aria-hidden="true">
              <div class="skeleton-bar skeleton-bar--title"></div>
              <div class="skeleton-bar skeleton-bar--sub"></div>
              <div class="skeleton-bar skeleton-bar--desc"></div>
            </div>
          </template>
          <template v-else>
            <div v-for="project in featuredProjects" :key="project.id" class="product-card">
              <h3 class="product-name">{{ project.name }}</h3>
              <p class="product-sub">{{ project.subtitle }}</p>
              <p class="product-desc">{{ project.description }}</p>
              <nuxt-link :to="`/projects/${project.id}`" class="product-link">{{ $t('home.products.viewProject') }}</nuxt-link>
            </div>
          </template>
        </div>
        <div class="products-more" v-if="!contentLoading">
          <nuxt-link to="/projects" class="more-link">{{ $t('home.products.more') }}</nuxt-link>
        </div>
      </div>
    </section>

    <!-- Latest Notes Section -->
    <section class="services-section" v-if="contentLoading || latestNotes.length > 0">
      <div class="section-container">
        <h2 class="section-title">{{ $t('home.notes.sectionTitle') }}</h2>
        <div class="services-grid">
          <template v-if="contentLoading">
            <div v-for="n in 3" :key="'note-skeleton-' + n" class="service-card service-card--skeleton" aria-hidden="true">
              <div class="skeleton-bar skeleton-bar--title"></div>
              <div class="skeleton-bar skeleton-bar--desc"></div>
              <div class="skeleton-bar skeleton-bar--sub"></div>
            </div>
          </template>
          <template v-else>
            <div v-for="note in latestNotesFormatted" :key="note.id" class="service-card" @click="openNote(note.id)" style="cursor:pointer">
              <h3 class="service-title">{{ note.title }}</h3>
              <p class="service-desc">{{ note.description }}</p>
              <div class="service-meta">{{ note.date }}</div>
            </div>
          </template>
        </div>
        <div class="services-more" v-if="!contentLoading">
          <nuxt-link to="/notes" class="more-link">{{ $t('home.notes.more') }}</nuxt-link>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-container">
        <h2 class="cta-title">{{ $t('home.cta.title') }}</h2>
        <p class="cta-description">{{ $t('home.cta.description') }}</p>
        <button class="btn-cta" @click="goApp">{{ $t('home.cta.btn') }}</button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="home-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>{{ $t('home.footer.copyright') }}</p>
        </div>
        <div class="footer-right">
          <a href="https://github.com/mindio-me/mindio-web" target="_blank" rel="noopener" class="footer-link">GitHub</a>
          <nuxt-link to="/contact" class="footer-link">{{ $t('site.nav.contact') }}</nuxt-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<script>
export default {
  layout: 'blank',
  auth: false,

  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,
      ownerProfile: null,
      latestNotes: [],
      publicProjects: [],
      contentLoading: true
    };
  },

  computed: {
    heroTitle() {
      return this.ownerProfile?.title || ''
    },
    heroSubtitle() {
      return this.ownerProfile?.bio || ''
    },
    featuredProjects() {
      const featured = this.publicProjects.filter(p => p.isFeatured)
      return (featured.length > 0 ? featured : this.publicProjects).slice(0, 3)
    },
    latestNotesFormatted() {
      return this.latestNotes.map(note => ({
        id: note.id,
        title: note.title,
        description: note.summary || this.extractDescription(note.content),
        date: this.formatDate(note.modifiedAt || note.createdAt)
      }))
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false }
  },

  // fetch() 在 SSR 阶段就会执行完，首屏 HTML 直接带上最终文案，
  // 避免 hero 标题/副标题先渲染成空再被数据填充导致的跳变。
  async fetch() {
    this.$store.commit('isHeader', false);
    this.$store.commit('isFooter', false);
    try {
      this.ownerProfile = await this.$profileService.getOwnerProfile()
      if (this.ownerProfile && !this.ownerProfile.title) this.ownerProfile.title = this.$t('home.hero.fallbackTitle');
      if (this.ownerProfile && !this.ownerProfile.bio) this.ownerProfile.bio = this.$t('home.hero.fallbackSubtitle');
    } catch (e) {}
  },

  async mounted() {
    if (process.client) {
      this.isDarkTheme = document.documentElement.classList.contains('theme-dark');
    }
    try {
      this.publicProjects = await this.$projectService.getPublicProjects()
    } catch (e) {
      this.publicProjects = []
    }
    try {
      const notesPage = await this.$noteService.getPublicNotes(0, 3)
      this.latestNotes = (notesPage && notesPage.content) || []
    } catch (e) {
      this.latestNotes = []
    }
    this.contentLoading = false
  },

  methods: {
    toggleLang() {
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      this.$i18n.setLocale(next)
    },
    toggleTheme() {
      if (this.$root.$options.app && this.$root.$options.app.themeToggle) {
        this.isDarkTheme = this.$root.$options.app.themeToggle();
      } else if (process.client) {
        const isDark = document.documentElement.classList.toggle('theme-dark');
        window.localStorage.setItem('worknotes-theme', isDark ? 'dark' : 'light');
        this.isDarkTheme = isDark;
      }
    },
    handleAvatarClick() {
      if (this.$auth && this.$auth.loggedIn) {
        this.goApp();
      } else {
        this.goLogin();
      }
    },
    goLogin() { this.$router.push('/login'); },
    goApp() { this.$router.push(this.$auth && this.$auth.loggedIn ? '/workspace/notes' : '/login'); },
    viewWork() { this.$router.push('/projects'); },
    contactMe() { this.$router.push('/contact'); },
    openNote(id) { this.$router.push(`/notes/${id}`); },
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

      return text.length > 120 ? text.slice(0, 120) + '...' : text
    },
    formatDate(dateString) {
      if (!dateString) return ''
      const date = new Date(dateString)
      if (isNaN(date.getTime())) return ''
      const intlLocale = this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US'
      return new Intl.DateTimeFormat(intlLocale, { year: 'numeric', month: 'short', day: 'numeric' }).format(date)
    },
    async handleLogout() {
      if (this.$auth && this.$auth.loggedIn) {
        await this.$auth.logout();
        this.$message.success(this.$t('messages.loggedOut'));
      } else {
        this.$router.push('/login');
      }
    }
  },

  head() {
    return {
      title: 'MindIO'
    };
  }
};
</script>

<style scoped lang="scss">
.home-page {
  background: var(--bg-color);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

// ── Header ────────────────────────────────────────────────
.page-header {
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
  &:hover { color: #667eea; }
  &.active {
    color: #667eea;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      bottom: -20px;
      left: 0; right: 0;
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

.theme-toggle,
.lang-toggle {
  width: 32px; height: 32px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: transparent;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s;
  i { font-size: 16px; }
  &:hover { background: rgba(148, 163, 184, 0.08); }
}

.lang-toggle {
  font-size: 12px;
  font-weight: 600;
}

.avatar {
  width: 36px; height: 36px;
  border-radius: 50%; overflow: hidden;
  background: #e2e8f0;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  img { width: 100%; height: 100%; object-fit: cover; }
}

.header-actions {
  display: flex; align-items: center; gap: 12px;
}

.login-btn, .app-btn {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: var(--card-bg-color);
  color: var(--text-secondary);
  font-size: 14px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
  &:hover { background: var(--bg-secondary); }
}

.logout-btn {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 14px; font-weight: 500;
  cursor: pointer; transition: all 0.2s;
  &:hover { background: var(--bg-secondary); }
}

// ── Hero ──────────────────────────────────────────────────
.hero-section {
  background: var(--bg-color);
  padding: 100px 40px 80px;
  text-align: center;
}

.hero-container {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: #667eea;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 999px;
  padding: 5px 16px;
  margin-bottom: 28px;
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  color: var(--text-color);
  line-height: 1.15;
  margin: 0 0 24px 0;
  letter-spacing: -1px;
  /* 兜底占位：数据到达前(如慢网络、纯客户端渲染)按最多 2 行预留高度，避免页面拉伸 */
  min-height: calc(1.15em * 2);
}

.hero-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 auto 40px;
  max-width: 800px;
  /* 兜底占位：按最多 2 行预留高度 */
  min-height: calc(1.7em * 2);
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.btn-primary {
  padding: 14px 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  border: none;
  background: #3b82f6;
  color: white;
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    background: #2563eb;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  }
}

.btn-secondary {
  padding: 14px 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--card-bg-color);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  &:hover { background: var(--bg-secondary); }
}

// ── Common Section Styles ─────────────────────────────────
.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 80px 40px;
}

.section-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  text-align: center;
  margin: 0 0 12px 0;
}

.section-subtitle {
  font-size: 16px;
  color: var(--text-muted);
  text-align: center;
  margin: 0 0 56px 0;
}

.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--tag-bg);
  color: var(--tag-color);
}

// ── Products Section ──────────────────────────────────────
.products-section {
  background: var(--bg-color);
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 40px;
}

.product-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 28px var(--shadow-color);
  }
}

.product-card--skeleton,
.service-card--skeleton {
  cursor: default;
  &:hover { transform: none; box-shadow: none; }
}

.skeleton-bar {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--border-color) 25%, var(--bg-secondary) 50%, var(--border-color) 75%);
  background-size: 200% 100%;
  animation: skeleton-pulse 1.4s ease-in-out infinite;

  &--title { width: 60%; height: 18px; }
  &--sub { width: 40%; }
  &--desc { width: 100%; }
}

@keyframes skeleton-pulse {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.product-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.product-icon {
  width: 44px; height: 44px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  i { font-size: 22px; color: white; }

  &.product-icon--active {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }
  &.product-icon--done {
    background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  }
  &.product-icon--deployed {
    background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%);
  }
}

.product-badge {
  font-size: 11px; font-weight: 700;
  padding: 3px 9px;
  border-radius: 999px;

  &.product-badge--active {
    background: rgba(59, 130, 246, 0.1);
    color: #3b82f6;
    border: 1px solid rgba(59, 130, 246, 0.2);
  }
  &.product-badge--done {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.2);
  }
  &.product-badge--deployed {
    background: rgba(168, 85, 247, 0.1);
    color: #a855f7;
    border: 1px solid rgba(168, 85, 247, 0.2);
  }
}

.product-name {
  font-size: 18px; font-weight: 700;
  color: var(--text-color);
  margin: 0;
}

.product-sub {
  font-size: 13px;
  color: var(--text-muted);
  font-style: italic;
  margin: 0;
}

.product-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
  flex: 1;
}

.product-tags {
  display: flex; flex-wrap: wrap; gap: 5px;
}

.product-link {
  font-size: 13px; font-weight: 600;
  color: #667eea;
  text-decoration: none;
  margin-top: 4px;
  transition: color 0.2s;
  &:hover { color: #764ba2; }
}

.products-more {
  text-align: center;
}

.more-link {
  font-size: 15px; font-weight: 600;
  color: #667eea;
  text-decoration: none;
  transition: color 0.2s;
  &:hover { color: #764ba2; }
}

// ── Services Section ──────────────────────────────────────
.services-section {
  background: var(--bg-tertiary);
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}

.service-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  min-height: 160px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 20px var(--shadow-color);
  }
}

.service-icon {
  width: 44px; height: 44px;
  background: var(--icon-bg);
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  i { font-size: 22px; color: #3b82f6; }
}

.service-title {
  font-size: 16px; font-weight: 700;
  color: var(--text-color);
  margin: 0;
}

.service-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.service-meta {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-muted);
}

.service-tags {
  display: flex; flex-wrap: wrap; gap: 5px;
}

.services-more {
  text-align: center;
}

// ── CTA Section ───────────────────────────────────────────
.cta-section {
  // background: var(--cta-gradient);
  // background: var(--bg-tertiary);
  background: var(--bg-color);
  padding: 80px 0;
}

.cta-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 40px;
  text-align: center;
}

.cta-title {
  font-size: 32px; font-weight: 700;
  color: var(--text-color);
  margin: 0 0 16px 0;
}

.cta-description {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 32px 0;
}

.btn-cta {
  padding: 16px 40px;
  font-size: 18px; font-weight: 600;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    background: #2563eb;
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(59, 130, 246, 0.3);
  }
}

// ── Footer ────────────────────────────────────────────────
.home-footer {
  background: #1a202c;
  padding: 40px 0;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.footer-left p {
  color: #a0aec0;
  margin: 0; font-size: 14px;
}

.footer-right {
  display: flex; gap: 24px;
}

.footer-link {
  color: #a0aec0;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
  &:hover { color: white; }
}

// ── Hamburger & Mobile Menu ───────────────────────────────
.menu-toggle {
  display: none;
  width: 32px; height: 32px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: transparent;
  align-items: center; justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  flex-shrink: 0;
  i { font-size: 18px; }
  &:hover { background: var(--bg-secondary); }
}

.mobile-overlay {
  display: none;
}

.mobile-menu {
  display: none;
}

// ── Responsive ────────────────────────────────────────────
@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
  }

  .products-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .services-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 768px) {
  .home-page { overflow-x: hidden; }

  .header-nav { display: none; }
  .header-left { padding: 0 20px; }
  .header-right { padding: 0 20px; gap: 8px; }
  .logo span { display: none; }
  .app-btn, .login-btn { padding: 6px 12px; font-size: 13px; }
  .logout-btn { display: none; }
  .menu-toggle { display: flex; }

  .mobile-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 97;
  }

  .mobile-menu {
    display: flex;
    flex-direction: column;
    position: fixed;
    top: 64px;
    left: 0; right: 0;
    background: var(--header-bg);
    border-bottom: 1px solid var(--border-color);
    box-shadow: 0 8px 24px var(--shadow-color);
    z-index: 98;
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

      &:hover { color: #667eea; background: var(--bg-secondary); }
      &.active {
        color: #667eea;
        border-left-color: #667eea;
        background: rgba(102, 126, 234, 0.06);
      }
    }
  }

  .hero-section { padding: 56px 20px 52px; }
  .hero-title { font-size: 34px; letter-spacing: -0.5px; }
  .hero-subtitle { font-size: 16px; }
  .hero-actions { flex-direction: column; align-items: center; }
  .btn-primary, .btn-secondary { width: 100%; max-width: 300px; }

  .section-container { padding: 52px 20px; }
  .section-title { font-size: 26px; }

  .products-grid { grid-template-columns: 1fr; }
  .services-grid { grid-template-columns: 1fr; }

  .cta-section { padding: 52px 0; }
  .cta-title { font-size: 24px; }
  .cta-container { padding: 0 20px; }

  .footer-container { flex-direction: column; gap: 16px; text-align: center; padding: 0 20px; }
}
</style>
