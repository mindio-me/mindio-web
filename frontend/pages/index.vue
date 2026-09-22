<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="home-page">
    <PublicHeader />

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-inner">
        <div class="hero-photo-col">
          <img v-if="avatarPhotoUrl" :src="avatarPhotoUrl" class="hero-photo" :alt="heroName" />
          <div v-else class="hero-photo-placeholder"><i class="el-icon-user-solid"></i></div>
        </div>
        <div class="hero-text-col">
          <div class="hero-eyebrow">{{ $t('home.hero.eyebrow') }}</div>
          <h1 class="hero-name">{{ heroName }}</h1>
          <p class="hero-title">{{ heroTitle }}</p>
          <p class="hero-bio">{{ heroSubtitle }}</p>
          <div v-if="availabilityStatus" class="availability-badge">
            <span class="availability-dot"></span>{{ availabilityStatus }}
          </div>
          <div v-if="skillsList.length" class="skill-chip-row">
            <span v-for="(skill, idx) in skillsList" :key="idx" class="skill-chip">{{ skill }}</span>
          </div>
          <p v-if="ownerLocation" class="hero-location">{{ ownerLocation }}</p>
          <div class="hero-actions">
            <button class="btn-primary" @click="contactMe">{{ $t('home.hero.ctaFulltime') }}</button>
            <button class="btn-secondary" @click="contactMe">{{ $t('home.hero.ctaProject') }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- About / Philosophy Section -->
    <section class="about-section" v-if="philosophyParagraphs.length">
      <div class="about-container">
        <h2 class="section-title">{{ $t('site.about.sectionTitle') }}</h2>
        <p v-for="(paragraph, idx) in philosophyParagraphs" :key="idx" class="about-paragraph">{{ paragraph }}</p>
      </div>
    </section>

    <!-- Featured Case Studies Section -->
    <section class="case-studies-section" v-if="contentLoading || featuredProjects.length > 0">
      <div class="section-container">
        <h2 class="section-title">{{ $t('home.caseStudies.sectionTitle') }}</h2>
        <div class="case-studies-stack">
          <template v-if="contentLoading">
            <div v-for="n in 2" :key="'case-skeleton-' + n" class="case-card case-card--skeleton" aria-hidden="true">
              <div class="skeleton-bar skeleton-bar--title"></div>
              <div class="skeleton-bar skeleton-bar--desc"></div>
              <div class="skeleton-bar skeleton-bar--sub"></div>
            </div>
          </template>
          <template v-else>
            <div v-for="project in featuredProjects" :key="project.id" class="case-card">
              <h3 class="case-title">{{ localize(project.name, project.nameZh) }}</h3>
              <p class="case-desc">{{ localize(project.description, project.descriptionZh) }}</p>
              <div v-if="localize(project.highlightMetric, project.highlightMetricZh)" class="case-highlight">{{ localize(project.highlightMetric, project.highlightMetricZh) }}</div>
              <div v-if="projectTechList(project).length" class="case-chip-row">
                <span v-for="tech in projectTechList(project)" :key="tech" class="case-chip">{{ tech }}</span>
              </div>
              <nuxt-link :to="`/projects/${project.id}`" class="product-link">{{ $t('home.caseStudies.viewCaseStudy') }}</nuxt-link>
            </div>
          </template>
        </div>
        <div class="products-more" v-if="!contentLoading">
          <nuxt-link to="/projects" class="more-link">{{ $t('home.caseStudies.more') }}</nuxt-link>
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
        <div class="cta-actions">
          <button class="btn-cta" @click="contactMe">{{ $t('home.hero.ctaFulltime') }}</button>
          <button class="btn-cta btn-cta--secondary" @click="contactMe">{{ $t('home.hero.ctaProject') }}</button>
        </div>
      </div>
    </section>

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
      latestNotes: [],
      publicProjects: [],
      contentLoading: true
    };
  },

  computed: {
    heroName() {
      return this.ownerProfile?.fullName || ''
    },
    heroTitle() {
      return this.localize(this.ownerProfile?.title, this.ownerProfile?.titleZh)
    },
    heroSubtitle() {
      return this.localize(this.ownerProfile?.bio, this.ownerProfile?.bioZh)
    },
    availabilityStatus() {
      return this.localize(this.ownerProfile?.availabilityStatus, this.ownerProfile?.availabilityStatusZh)
    },
    skillsList() {
      const text = this.localize(this.ownerProfile?.skills, this.ownerProfile?.skillsZh)
      return text.split(',').map(s => s.trim()).filter(Boolean)
    },
    ownerLocation() {
      return this.ownerProfile?.location || ''
    },
    avatarPhotoUrl() {
      return this.resolveUrl(this.ownerProfile?.avatarUrl)
    },
    featuredProjects() {
      const featured = this.publicProjects.filter(p => p.isFeatured)
      return (featured.length > 0 ? featured : this.publicProjects).slice(0, 3)
    },
    philosophyParagraphs() {
      const text = this.localize(this.ownerProfile?.philosophy, this.ownerProfile?.philosophyZh)
      return text.split(/\n{2,}/).map(p => p.trim()).filter(Boolean)
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
    contactMe() { this.$router.push('/contact'); },
    openNote(id) { this.$router.push(`/notes/${id}`); },
    resolveUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      return (this.$axios.defaults.baseURL || '') + path
    },
    // 中文访客优先显示中文字段，留空则回退到英文/默认字段
    localize(en, zh) {
      if (this.$i18n.locale === 'zh-CN' && zh) return zh
      return en || ''
    },
    projectTechList(project) {
      const text = this.localize(project?.technologies, project?.technologiesZh)
      if (!text) return []
      return text.split(',').map(t => t.trim()).filter(Boolean)
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

      return text.length > 120 ? text.slice(0, 120) + '...' : text
    },
    formatDate(dateString) {
      if (!dateString) return ''
      const date = new Date(dateString)
      if (isNaN(date.getTime())) return ''
      const intlLocale = this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US'
      return new Intl.DateTimeFormat(intlLocale, { year: 'numeric', month: 'short', day: 'numeric' }).format(date)
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

// ── Hero ──────────────────────────────────────────────────
.hero-section {
  background: var(--bg-color);
  border-bottom: 1px solid var(--border-color);
}

.hero-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: stretch;
  min-height: 460px;
}

.hero-photo-col {
  flex: 0 0 40%;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-photo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;

  i {
    font-size: 72px;
    color: var(--text-muted);
    opacity: 0.35;
  }
}

.hero-text-col {
  flex: 1;
  min-width: 0;
  padding: 64px 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero-eyebrow {
  display: inline-block;
  align-self: flex-start;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: #667eea;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 999px;
  padding: 5px 16px;
  margin-bottom: 22px;
}

.hero-name {
  font-size: 44px;
  font-weight: 800;
  color: var(--text-color);
  line-height: 1.15;
  margin: 0 0 8px 0;
  letter-spacing: -1px;
  min-height: 1.15em;
}

.hero-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-secondary);
  margin: 0 0 18px 0;
  min-height: 1.4em;
}

.hero-bio {
  font-size: 16px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 20px 0;
  max-width: 520px;
  min-height: calc(1.7em * 2);
}

.availability-badge {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  color: #10b981;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);
  border-radius: 999px;
  padding: 6px 14px;
  margin-bottom: 20px;
}

.availability-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
}

.skill-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.skill-chip {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 5px 12px;
}

.hero-location {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 28px 0;
}

.hero-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
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

// ── About / Philosophy ───────────────────────────────────
.about-section {
  background: var(--bg-tertiary);
  padding: 72px 40px;
}

.about-container {
  max-width: 760px;
  margin: 0 auto;
  text-align: center;
}

.about-paragraph {
  font-size: 17px;
  color: var(--text-secondary);
  line-height: 1.9;
  margin: 0 0 20px;
  text-align: left;

  &:last-child { margin-bottom: 0; }
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

// ── Featured Case Studies ──────────────────────────────────
.case-studies-section {
  background: var(--bg-color);
}

.case-studies-stack {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 860px;
  margin: 0 auto 40px;
}

.case-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 36px;
  transition: all 0.2s;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 28px var(--shadow-color);
  }
}

.case-card--skeleton {
  cursor: default;
  &:hover { transform: none; box-shadow: none; }
}

.case-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-color);
  margin: 0 0 12px;
}

.case-desc {
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 16px;
}

.case-highlight {
  border-left: 3px solid #10b981;
  background: rgba(16, 185, 129, 0.06);
  border-radius: 0 6px 6px 0;
  padding: 10px 16px;
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 18px;
}

.case-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.case-chip {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 4px 10px;
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

.services-more {
  text-align: center;
}

// ── CTA Section ───────────────────────────────────────────
.cta-section {
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

.cta-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
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

.btn-cta--secondary {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  &:hover {
    background: var(--bg-secondary);
    box-shadow: none;
    color: var(--text-secondary);
  }
}

// ── Responsive ────────────────────────────────────────────
@media screen and (max-width: 1024px) {
  .services-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 900px) {
  .hero-inner {
    flex-direction: column;
    min-height: 0;
  }

  .hero-photo-col {
    flex: none;
    height: 240px;
  }

  .hero-text-col {
    padding: 40px 32px;
  }
}

@media screen and (max-width: 768px) {
  .home-page { overflow-x: hidden; }

  .hero-photo-col { height: 200px; }
  .hero-text-col { padding: 32px 20px; }
  .hero-name { font-size: 32px; letter-spacing: -0.5px; }
  .hero-title { font-size: 15px; }
  .hero-bio { font-size: 15px; }
  .hero-actions { flex-direction: column; align-items: stretch; }
  .btn-primary, .btn-secondary { width: 100%; text-align: center; }

  .section-container { padding: 52px 20px; }
  .section-title { font-size: 26px; }

  .about-section { padding: 48px 20px; }
  .about-paragraph { font-size: 15px; }

  .case-studies-stack { gap: 16px; }
  .case-card { padding: 24px; }
  .services-grid { grid-template-columns: 1fr; }

  .cta-section { padding: 52px 0; }
  .cta-title { font-size: 24px; }
  .cta-container { padding: 0 20px; }
  .cta-actions { flex-direction: column; align-items: stretch; }
  .btn-cta { width: 100%; }

  .footer-container { flex-direction: column; gap: 16px; text-align: center; padding: 0 20px; }
}
</style>
