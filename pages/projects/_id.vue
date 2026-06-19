<template>
  <div class="project-detail-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="logo" @click="$router.push('/')">
            <i class="el-icon-notebook-2"></i>
            <span>UnibyteGlobal</span>
          </div>
        </div>
        <nav class="header-nav">
          <nuxt-link to="/" class="nav-link" exact-active-class="active" exact>Home</nuxt-link>
          <nuxt-link to="/notes" class="nav-link" exact-active-class="active">Notes</nuxt-link>
          <nuxt-link to="/projects" class="nav-link" exact-active-class="active">Projects</nuxt-link>
          <nuxt-link to="/services" class="nav-link" exact-active-class="active">Services</nuxt-link>
          <nuxt-link to="/resource" class="nav-link" exact-active-class="active">Resources</nuxt-link>
          <nuxt-link to="/contact" class="nav-link" exact-active-class="active">Contact</nuxt-link>
        </nav>
        <div class="header-right">
          <button
            class="theme-toggle"
            @click="toggleTheme"
            :title="isDarkTheme ? '切换到白天模式' : '切换到黑夜模式'"
          >
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
        </div>
      </div>
    </header>

    <section v-if="!project && !loadError" class="loading-section">
      <div class="loading-inner">
        <i class="el-icon-loading"></i>
        <p>Loading project...</p>
      </div>
    </section>

    <section v-else-if="loadError" class="error-section">
      <div class="error-inner">
        <i class="el-icon-warning-outline"></i>
        <h1>Project not found</h1>
        <p>This project may have been removed or is not publicly visible.</p>
        <button class="btn-primary" @click="$router.push('/projects')">
          Back to all projects
        </button>
      </div>
    </section>

    <section v-else class="hero-section">
      <div class="hero-container">
        <div class="hero-main">
          <p class="hero-eyebrow">{{ getCategoryLabel(project.category) }}</p>
          <h1 class="hero-title">{{ project.name }}</h1>
          <p v-if="project.subtitle" class="hero-subtitle">
            {{ project.subtitle }}
          </p>
          <p v-else-if="project.description" class="hero-subtitle">
            {{ project.description }}
          </p>
          <div class="hero-meta">
            <span v-if="project.client" class="meta-pill">
              <i class="el-icon-user"></i>
              Client: {{ project.client }}
            </span>
            <span v-if="project.status" class="meta-pill">
              <i class="el-icon-time"></i>
              {{ project.status }}
            </span>
          </div>
          <div class="hero-actions">
            <button class="btn-primary" @click="$router.push('/contact')">
              Discuss a similar project
            </button>
            <button class="btn-secondary" @click="$router.push('/projects')">
              Back to all projects
            </button>
          </div>
        </div>
        <div class="hero-side">
          <div class="hero-icon">
            <i :class="project.icon || getFallbackIcon(project)"></i>
          </div>
        </div>
      </div>
    </section>

    <section v-if="project" class="content-section">
      <div class="content-wrapper">

        <!-- 1. 摘要 -->
        <div class="card summary-card" v-if="project.subtitle || project.description">
          <h2 class="section-title">Summary</h2>
          <p class="body-text">{{ project.subtitle || project.description }}</p>
        </div>

        <!-- 2. 技术栈 -->
        <div class="card techstack-card">
          <h3 class="section-title">Tech stack</h3>
          <div v-if="project.technologies" class="tags">
            <span
              v-for="tech in project.technologies.split(',').map(t => t.trim()).filter(Boolean)"
              :key="tech"
              class="tag"
            >
              {{ tech }}
            </span>
          </div>
          <p v-else class="muted-text">
            Tech stack details will be added later.
          </p>
        </div>

        <!-- 3. 正文 + 时间线（有时间线时两栏） -->
        <div class="body-row" :class="{ 'has-timeline': hasTimeline }">
          <article class="card body-card">
            <h2 class="section-title">Overview</h2>
            <!-- 优先显示项目正文 content，其次使用简介 description -->
            <div
              v-if="project.content"
              class="project-content"
              :class="isMarkdown ? 'project-content--markdown' : 'project-content--richtext'"
              v-html="renderedContent"
            />
            <p v-else class="body-text">
              {{ project.description || 'This project description will be updated soon.' }}
            </p>
          </article>

          <aside v-if="hasTimeline" class="card timeline-card">
            <h3 class="side-title">Timeline</h3>
            <p class="muted-text">
              <span v-if="project.startDate">From {{ formatDate(project.startDate) }}</span>
              <span v-if="project.endDate">
                &nbsp;to {{ formatDate(project.endDate) }}
              </span>
            </p>
          </aside>
        </div>

      </div>
    </section>

    <!-- Footer -->
    <footer class="page-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>© 2024 MindIO. All rights reserved.</p>
        </div>
        <div class="footer-right">
          <a href="#" class="footer-link">LinkedIn</a>
          <a href="#" class="footer-link">GitHub</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script>
import { renderMarkdown as renderMd } from '~/utils/markdown'

export default {
  layout: 'blank',
  auth: false,

  async asyncData({ params, app }) {
    try {
      const project = await app.$projectService.getProjectById(params.id)
      return { project, loadError: false }
    } catch (e) {
      // 404 或其他错误时，给出友好提示
      return { project: null, loadError: true }
    }
  },

  data() {
    return {
      isDarkTheme: false,
      project: null,
      loadError: false
    }
  },

  mounted() {
    if (process.client) {
      const root = document.documentElement
      this.isDarkTheme = root.classList.contains('theme-dark')
    }
  },

  methods: {
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
        web: 'Web Application',
        saas: 'SaaS Platform',
        mobile: 'Mobile App',
        ai: 'AI-powered Solution'
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

    formatDate(value) {
      if (!value) return ''
      const d = new Date(value)
      if (Number.isNaN(d.getTime())) return value
      return d.toLocaleDateString()
    },

    renderMarkdown(text) {
      const baseURL = this.$axios?.defaults?.baseURL || ''
      return renderMd(text, { axiosBaseURL: baseURL })
    }
  },

  computed: {
    isMarkdown() {
      const type = (this.project?.contentType || '').toLowerCase()
      return type === 'markdown' || type === 'md'
    },

    hasTimeline() {
      return !!(this.project?.startDate || this.project?.endDate)
    },

    renderedContent() {
      if (!this.project || !this.project.content) return ''
      if (this.isMarkdown) {
        return this.renderMarkdown(this.project.content)
      }
      // 默认按富文本/HTML 渲染
      return this.project.content
    }
  },

  head() {
    const name = this.project?.name || 'Project'
    const desc =
      this.project?.description ||
      'Project case study built with MindIO and related technologies.'
    return {
      title: `${name} - Projects`,
      meta: [
        { hid: 'description', name: 'description', content: desc }
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
.project-detail-page {
  min-height: 100vh;
  background: var(--bg-color);
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
  cursor: pointer;

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

.theme-toggle {
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

// Loading & Error
.loading-section,
.error-section {
  padding: 80px 0;
}

.loading-inner,
.error-inner {
  max-width: 600px;
  margin: 0 auto;
  padding: 0 40px;
  text-align: center;
  color: var(--text-secondary);

  i {
    font-size: 48px;
    margin-bottom: 16px;
  }
}

.error-inner h1 {
  margin: 0 0 8px 0;
  font-size: 26px;
  color: var(--text-color);
}

.btn-primary {
  padding: 10px 24px;
  border-radius: 999px;
  border: none;
  background: #3b82f6;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #2563eb;
  }
}

// Hero
.hero-section {
  background: var(--bg-color);
  padding: 60px 0 30px;
}

.hero-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  gap: 40px;
  align-items: center;
}

.hero-main {
  flex: 1;
}

.hero-side {
  flex: 0 0 260px;
  display: flex;
  justify-content: flex-end;
}

.hero-icon {
  width: 200px;
  height: 200px;
  border-radius: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);

  i {
    font-size: 72px;
    color: rgba(255, 255, 255, 0.9);
  }
}

.hero-eyebrow {
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.hero-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.hero-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 16px 0;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.meta-pill {
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--tag-bg);
  color: var(--tag-color);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn-secondary {
  padding: 10px 24px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--card-bg-color);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--bg-secondary);
  }
}

// Content
.content-section {
  background: var(--bg-tertiary);
  padding: 20px 0 80px;
}

.content-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.body-row {
  display: block;

  &.has-timeline {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 16px;
    align-items: start;
  }
}

.card {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 20px 22px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 10px 0;
}

.body-text {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0;
}

.bullets {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.side-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 8px 0;
}

.tags {
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

.muted-text {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

// 富文本正文内容样式（适用于 WangEditor 生成的 HTML 和 Markdown 渲染结果）
.project-content {
  font-size: 15px;
  color: var(--text-color);
  line-height: 1.8;

  ::v-deep h1,
  ::v-deep h2,
  ::v-deep h3,
  ::v-deep h4 {
    margin: 1.2em 0 0.6em;
    font-weight: 600;
    color: var(--text-color);

    &:first-child {
      margin-top: 0;
    }
  }

  ::v-deep h1 {
    font-size: 1.8em;
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 0.3em;
  }

  ::v-deep h2 {
    font-size: 1.5em;
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 0.3em;
  }

  ::v-deep h3 {
    font-size: 1.25em;
  }

  ::v-deep h4 {
    font-size: 1.1em;
  }

  ::v-deep p {
    margin: 0.6em 0;
  }

  ::v-deep a {
    color: var(--primary-color, #667eea);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  ::v-deep strong {
    font-weight: 600;
  }

  ::v-deep em {
    font-style: italic;
  }

  ::v-deep ul,
  ::v-deep ol {
    margin: 0.8em 0;
    padding-left: 2em;

    li {
      margin: 0.3em 0;
    }
  }

  ::v-deep blockquote {
    margin: 1em 0;
    padding: 0.5em 1em;
    border-left: 4px solid var(--primary-color, #667eea);
    background: var(--bg-tertiary, rgba(102, 126, 234, 0.1));
    color: var(--text-secondary);
  }

  ::v-deep pre,
  ::v-deep pre.md-code-block {
    margin: 1em 0;
    padding: 1em;
    background: var(--bg-tertiary, #1e293b);
    border-radius: 6px;
    overflow-x: auto;

    code {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      color: var(--text-color);
      background: transparent;
      padding: 0;
    }
  }

  ::v-deep code,
  ::v-deep code.md-inline-code {
    padding: 0.2em 0.4em;
    background: var(--bg-tertiary, rgba(0, 0, 0, 0.1));
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 0.9em;
    color: var(--primary-color, #667eea);
  }

  ::v-deep img,
  ::v-deep img.md-image {
    max-width: 100%;
    height: auto;
    border-radius: 4px;
    margin: 1em 0;
  }

  ::v-deep hr,
  ::v-deep hr.md-hr {
    margin: 1.5em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }

  ::v-deep table {
    width: 100%;
    border-collapse: collapse;
    margin: 1em 0;

    th,
    td {
      border: 1px solid var(--border-color);
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: var(--bg-tertiary);
      font-weight: 600;
    }

    tr:hover td {
      background: var(--bg-secondary);
    }
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

// Responsive
@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
  }

  .hero-container,
  .content-wrapper {
    padding: 0 24px;
  }
}

@media screen and (max-width: 768px) {
  .header-nav {
    display: none;
  }

  .hero-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 24px;
  }

  .hero-side {
    justify-content: flex-start;
  }

  .hero-section {
    padding: 40px 0 24px;
  }

  .content-wrapper {
    padding: 0 20px;
  }

  .body-row.has-timeline {
    grid-template-columns: 1fr;
  }

  .card {
    padding: 18px 18px;
  }

  .section-title {
    font-size: 16px;
  }
}
</style>
