<template>
  <div class="projects-page">
    <!-- Header -->
    <header class="page-header">
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
          <button
            class="theme-toggle"
            @click="toggleTheme"
            :title="isDarkTheme ? $t('topbar.lightMode') : $t('topbar.darkMode')"
          >
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <button class="lang-toggle" @click="toggleLang">{{ $t('lang.toggle') }}</button>
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
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.notes') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/services" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.services') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>

    <!-- Hero -->
    <section class="hero-section">
      <div class="hero-container">
        <h1 class="hero-title">{{ $t('projectsPage.hero.title') }}</h1>
        <p class="hero-subtitle">{{ $t('projectsPage.hero.subtitle') }}</p>
      </div>
    </section>

    <!-- My Products — Fixed Showcase -->
    <section class="own-products-section">
      <div class="own-products-container">
        <div class="own-products-heading">
          <span class="own-products-label">{{ $t('projectsPage.ownProducts.label') }}</span>
          <h2 class="own-products-title">{{ $t('projectsPage.ownProducts.title') }}</h2>
        </div>
        <div class="own-products-grid">

          <!-- Notecast -->
          <div class="own-product-card">
            <div class="op-left">
              <div class="op-icon op-icon--purple">
                <i class="el-icon-notebook-2"></i>
              </div>
            </div>
            <div class="op-body">
              <div class="op-meta">
                <span class="op-type">{{ $t('projectsPage.notecast.type') }}</span>
                <span class="op-status op-status--active">{{ $t('projectsPage.notecast.status') }}</span>
              </div>
              <h3 class="op-title">Notecast</h3>
              <p class="op-sub">{{ $t('projectsPage.notecast.sub') }}</p>
              <p class="op-desc">{{ $t('projectsPage.notecast.desc') }}</p>
              <div class="op-tech">
                <span class="tag">Nuxt.js</span>
                <span class="tag">Spring Boot 3</span>
                <span class="tag">MySQL</span>
                <span class="tag">EditorJS</span>
                <span class="tag">JWT</span>
                <span class="tag">WeChat API</span>
                <span class="tag">Claude AI</span>
              </div>
            </div>
          </div>

          <!-- IMS -->
          <div class="own-product-card">
            <div class="op-left">
              <div class="op-icon op-icon--green">
                <i class="el-icon-box"></i>
              </div>
            </div>
            <div class="op-body">
              <div class="op-meta">
                <span class="op-type">{{ $t('projectsPage.ims.type') }}</span>
                <span class="op-status op-status--done">{{ $t('projectsPage.ims.status') }}</span>
              </div>
              <h3 class="op-title">IMS — Site Inventory</h3>
              <p class="op-sub">{{ $t('projectsPage.ims.sub') }}</p>
              <p class="op-desc">{{ $t('projectsPage.ims.desc') }}</p>
              <div class="op-tech">
                <span class="tag">Spring Boot 3</span>
                <span class="tag">Nuxt.js 2</span>
                <span class="tag">MySQL</span>
                <span class="tag">Element UI</span>
                <span class="tag">JPA</span>
                <span class="tag">Docker</span>
              </div>
            </div>
          </div>

        </div>
      </div>
    </section>

    <!-- Filters -->
    <section class="filters-section">
      <div class="filters-container">
        <div class="search-box">
          <i class="el-icon-search search-icon"></i>
          <input
            v-model="keyword"
            type="text"
            class="search-input"
            :placeholder="$t('projectsPage.filter.placeholder')"
          />
          <button v-if="keyword" class="clear-btn" @click="keyword = ''">
            <i class="el-icon-close"></i>
          </button>
        </div>
        <div class="category-filter">
          <button
            v-for="cat in categories"
            :key="cat.value"
            :class="['category-pill', { active: activeCategory === cat.value }]"
            @click="activeCategory = cat.value"
          >
            {{ cat.label }}
          </button>
        </div>
        <div class="result-info">
          <span>{{ $t('projectsPage.filter.count', { n: filteredProjects.length }) }}</span>
        </div>
      </div>
    </section>

    <!-- Projects Grid -->
    <section class="projects-section">
      <div class="projects-container">
        <div v-if="loading" class="loading-state">
          <i class="el-icon-loading"></i>
          <p>{{ $t('projectsPage.grid.loading') }}</p>
        </div>
        <div v-else-if="filteredProjects.length === 0" class="empty-state">
          <i class="el-icon-collection"></i>
          <p>{{ $t('projectsPage.grid.empty') }}</p>
        </div>
        <div v-else class="projects-grid">
          <article
            v-for="project in filteredProjects"
            :key="project.id"
            class="project-card"
          >
            <div class="project-header">
              <div class="project-icon">
                <i :class="project.icon || getFallbackIcon(project)"></i>
              </div>
              <div class="project-meta">
                <h2 class="project-title">{{ project.name }}</h2>
                <p class="project-category">
                  {{ getCategoryLabel(project.category) }}
                </p>
              </div>
            </div>
            <p class="project-description">
              {{ project.description || $t('projectsPage.grid.noDescription') }}
            </p>
            <div v-if="project.technologies" class="project-tags">
              <span
                v-for="tech in project.technologies.split(',').map(t => t.trim()).filter(Boolean)"
                :key="tech"
                class="tag"
              >
                {{ tech }}
              </span>
            </div>
            <div class="project-footer">
              <button class="details-btn" @click="goDetail(project.id)">
                {{ $t('projectsPage.grid.viewDetails') }}
              </button>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="page-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>{{ $t('projectsPage.footer.copyright') }}</p>
        </div>
        <div class="footer-right">
          <template v-if="$i18n.locale === 'zh-CN'">
            <a href="/contact" class="footer-link" :title="ownerProfile && ownerProfile.wechat ? ownerProfile.wechat : '微信'">
              <i class="el-icon-chat-dot-round"></i> 微信
            </a>
          </template>
          <template v-else>
            <a v-if="ownerProfile && ownerProfile.linkedin" :href="formatUrl(ownerProfile.linkedin)" target="_blank" rel="noopener" class="footer-link">LinkedIn</a>
            <a href="https://github.com/mindio-me/mindio-web" target="_blank" rel="noopener" class="footer-link">GitHub</a>
          </template>
        </div>
      </div>
    </footer>
  </div>
</template>

<script>
export default {
  layout: 'blank',
  auth: false,

  async asyncData({ app }) {
    try {
      const projects = await app.$projectService.getPublicProjects()
      return { projects }
    } catch (e) {
      return { projects: [] }
    }
  },

  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,
      loading: false,
      projects: [],
      keyword: '',
      activeCategory: 'all',
      ownerProfile: null
    }
  },

  computed: {
    categories() {
      const set = new Set()
      this.projects.forEach(p => {
        if (p.category) set.add(p.category)
      })
      const base = [{ value: 'all', label: this.$t('projectsPage.filter.all') }]
      const map = {
        web: this.$t('projectsPage.filter.catWeb'),
        saas: this.$t('projectsPage.filter.catSaas'),
        mobile: this.$t('projectsPage.filter.catMobile'),
        ai: this.$t('projectsPage.filter.catAi')
      }
      Array.from(set)
        .sort()
        .forEach(cat => {
          base.push({ value: cat, label: map[cat] || cat })
        })
      return base
    },

    filteredProjects() {
      let list = this.projects || []

      if (this.activeCategory !== 'all') {
        list = list.filter(p => p.category === this.activeCategory)
      }

      if (this.keyword.trim()) {
        const kw = this.keyword.toLowerCase()
        list = list.filter(p => {
          const name = (p.name || '').toLowerCase()
          const desc = (p.description || '').toLowerCase()
          const tech = (p.technologies || '').toLowerCase()
          return (
            name.includes(kw) ||
            desc.includes(kw) ||
            tech.includes(kw)
          )
        })
      }

      return list
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false }
  },

  async mounted() {
    if (process.client) {
      const root = document.documentElement
      this.isDarkTheme = root.classList.contains('theme-dark')
    }
    try {
      this.ownerProfile = await this.$profileService.getOwnerProfile()
    } catch (e) {}
  },

  methods: {
    formatUrl(url) {
      if (!url) return '#'
      return url.startsWith('http') ? url : 'https://' + url
    },
    toggleLang() {
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      this.$i18n.setLocale(next)
    },
    toggleTheme() {
      if (this.$root.$options.app && this.$root.$options.app.themeToggle) {
        this.isDarkTheme = this.$root.$options.app.themeToggle()
      } else if (process.client) {
        const root = document.documentElement
        const isDark = root.classList.toggle('theme-dark')
        window.localStorage.setItem('worknotes-theme', isDark ? 'dark' : 'light')
        this.isDarkTheme = isDark
      }
    },

    getCategoryLabel(category) {
      const map = {
        web: this.$t('projectsPage.filter.catWeb'),
        saas: this.$t('projectsPage.filter.catSaas'),
        mobile: this.$t('projectsPage.filter.catMobile'),
        ai: this.$t('projectsPage.filter.catAi')
      }
      return map[category] || (category || 'Project')
    },

    getFallbackIcon(project) {
      const name = (project.name || '').toLowerCase()
      if (name.includes('train')) return 'el-icon-school'
      if (name.includes('lims')) return 'el-icon-data-analysis'
      if (name.includes('crm')) return 'el-icon-s-custom'
      return 'el-icon-briefcase'
    },

    goDetail(id) {
      this.$router.push(`/projects/${id}`)
    }
  },

  head() {
    return {
      title: 'Projects - Recent Work',
      meta: [
        {
          hid: 'description',
          name: 'description',
          content: 'Case studies and selected projects managed with MindIO and related technologies.'
        }
      ]
    }
  },

  fetch({ store }) {
    store.commit('isHeader', false)
    store.commit('isFooter', false)
  }
}
</script>

<style scoped lang="scss">
.projects-page {
  min-height: 100vh;
  background: var(--bg-tertiary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

// Header
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

// Hero
.hero-section {
  background: var(--bg-color);
  padding: 80px 0 40px;
  text-align: center;
}

.hero-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 40px;
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 16px 0;
}

.hero-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

// Filters
.filters-section {
  background: var(--bg-color);
  padding: 24px 0;
  border-bottom: 1px solid var(--border-color);
}

.filters-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.search-box {
  max-width: 520px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  background: var(--card-bg-color);
  border-radius: 999px;
  border: 1px solid var(--border-color);
  padding: 4px 14px;
}

.search-icon {
  font-size: 18px;
  color: var(--text-muted);
  margin-right: 8px;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-color);
  padding: 6px 0;

  &::placeholder {
    color: var(--text-muted);
  }
}

.clear-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-bottom: 8px;
}

.category-pill {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--card-bg-color);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;

  &.active {
    background: #667eea;
    border-color: #667eea;
    color: #fff;
  }
}

.result-info {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

// Projects
.projects-section {
  background: var(--bg-tertiary);
  padding: 40px 0 80px;
}

.projects-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);

  i {
    font-size: 40px;
    margin-bottom: 12px;
  }
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 24px;
}

.project-card {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 20px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px var(--shadow-color);
  }
}

.project-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.project-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--icon-bg);
  display: flex;
  align-items: center;
  justify-content: center;

  i {
    font-size: 20px;
    color: #667eea;
  }
}

.project-meta {
  flex: 1;
}

.project-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 2px 0;
}

.project-category {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

.project-description {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.project-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--tag-bg);
  color: var(--tag-color);
}

.project-footer {
  margin-top: 4px;
  display: flex;
  justify-content: flex-end;
}

.details-btn {
  padding: 6px 14px;
  font-size: 13px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-color);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #667eea;
    color: #667eea;
  }
}

// Footer
.page-footer {
  background: #1a202c;
  padding: 32px 0;
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
  color: #9ca3af;
  margin: 0;
  font-size: 14px;
}

.footer-right {
  display: flex;
  gap: 16px;
}

.footer-link {
  color: #9ca3af;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;

  &:hover {
    color: #fff;
  }
}

// Own Products Section
.own-products-section {
  background: var(--bg-color);
  border-bottom: 2px solid var(--border-color);
  padding: 48px 0 40px;
}

.own-products-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.own-products-heading {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.own-products-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #667eea;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 999px;
  padding: 3px 12px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.own-products-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0;
}

.own-products-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.own-product-card {
  display: flex;
  gap: 20px;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 24px;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 24px;
    right: 24px;
    height: 2px;
    background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
    border-radius: 0 0 2px 2px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 28px var(--shadow-color);
    border-color: rgba(102, 126, 234, 0.25);

    &::after {
      opacity: 1;
    }
  }
}

.op-left {
  flex-shrink: 0;
}

.op-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;

  i {
    font-size: 24px;
    color: white;
  }

  &.op-icon--purple {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  &.op-icon--green {
    background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  }
}

.op-body {
  flex: 1;
  min-width: 0;
}

.op-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.op-type {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.op-status {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;

  &.op-status--active {
    background: rgba(59, 130, 246, 0.1);
    color: #3b82f6;
    border: 1px solid rgba(59, 130, 246, 0.2);
  }

  &.op-status--done {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.2);
  }
}

.op-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 3px 0;
}

.op-sub {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 10px 0;
  font-style: italic;
}

.op-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 12px 0;
}

.op-tech {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
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

.mobile-overlay { display: none; }
.mobile-menu { display: none; }

// Responsive
@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
  }

  .projects-container,
  .filters-container,
  .hero-container,
  .own-products-container {
    padding: 0 24px;
  }

  .own-products-grid {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .projects-page { overflow-x: hidden; }

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

  .hero-section {
    padding: 56px 0 32px;
  }

  .hero-title {
    font-size: 30px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .projects-section {
    padding: 32px 0 52px;
  }

  .projects-container,
  .filters-container,
  .hero-container {
    padding: 0 20px;
  }

  .own-products-section {
    padding: 36px 0 28px;
  }

  .own-products-container {
    padding: 0 20px;
  }

  .own-product-card {
    flex-direction: column;
    gap: 16px;
  }

  .footer-container {
    flex-direction: column;
    gap: 16px;
    text-align: center;
    padding: 0 20px;
  }
}
</style>
