<template>
  <div class="home-page">
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
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.notes') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/services" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.services') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-container">
        <div class="hero-eyebrow">{{ $t('home.hero.eyebrow') }}</div>
        <h1 class="hero-title" v-html="$t('home.hero.title')"></h1>
        <p class="hero-subtitle">{{ $t('home.hero.subtitle') }}</p>
        <div class="hero-actions">
          <button class="btn-primary" @click="viewWork">{{ $t('home.hero.viewWork') }}</button>
          <button class="btn-secondary" @click="letsTalk">{{ $t('home.hero.letsTalk') }}</button>
        </div>
      </div>
    </section>

    <!-- How I Work / Vibe Coding Section -->
    <section class="vibe-section">
      <div class="vibe-container">
        <div class="vibe-label">{{ $t('home.vibe.label') }}</div>
        <h2 class="vibe-title">{{ $t('home.vibe.title') }}</h2>
        <p class="vibe-subtitle">{{ $t('home.vibe.subtitle') }}</p>

        <div class="vibe-video-wrap">
          <video
            autoplay
            muted
            loop
            playsinline
            class="vibe-video"
          >
            <source src="/vibe-coding-demo.webm" type="video/webm" />
            {{ $t('home.vibe.videoFallback') }}
          </video>
        </div>

        <div class="vibe-points">
          <div class="vibe-point">
            <span class="vibe-check">✓</span>
            <span>{{ $t('home.vibe.point1') }}</span>
          </div>
          <div class="vibe-point">
            <span class="vibe-check">✓</span>
            <span>{{ $t('home.vibe.point2') }}</span>
          </div>
          <div class="vibe-point">
            <span class="vibe-check">✓</span>
            <span>{{ $t('home.vibe.point3') }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Products Section -->
    <section class="products-section">
      <div class="section-container">
        <h2 class="section-title">{{ $t('home.products.sectionTitle') }}</h2>
        <p class="section-subtitle">{{ $t('home.products.sectionSubtitle') }}</p>

        <div class="products-grid">
          <div v-for="product in featuredProducts" :key="product.name" class="product-card">
            <div class="product-head">
              <div :class="['product-icon', `product-icon--${product.status}`]">
                <i :class="product.icon"></i>
              </div>
              <span :class="['product-badge', `product-badge--${product.status}`]">
                {{ product.statusLabel }}
              </span>
            </div>
            <h3 class="product-name">{{ product.name }}</h3>
            <p class="product-sub">{{ product.subtitle }}</p>
            <p class="product-desc">{{ product.description }}</p>
            <div class="product-tags">
              <span v-for="tag in product.tags" :key="tag" class="tag">{{ tag }}</span>
            </div>
            <nuxt-link to="/projects" class="product-link">{{ $t('home.products.viewProject') }}</nuxt-link>
          </div>
        </div>

        <div class="products-more">
          <nuxt-link to="/projects" class="more-link">{{ $t('home.products.more') }}</nuxt-link>
        </div>
      </div>
    </section>

    <!-- Services Section -->
    <section class="services-section">
      <div class="section-container">
        <h2 class="section-title">{{ $t('home.services.sectionTitle') }}</h2>
        <p class="section-subtitle">{{ $t('home.services.sectionSubtitle') }}</p>

        <div class="services-grid">
          <div v-for="svc in services" :key="svc.title" class="service-card">
            <div class="service-icon">
              <i :class="svc.icon"></i>
            </div>
            <h3 class="service-title">{{ svc.title }}</h3>
            <p class="service-desc">{{ svc.description }}</p>
            <div class="service-tags">
              <span v-for="tag in svc.tags" :key="tag" class="tag">{{ tag }}</span>
            </div>
          </div>
        </div>

        <div class="services-more">
          <nuxt-link to="/services" class="more-link">{{ $t('home.services.more') }}</nuxt-link>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-container">
        <h2 class="cta-title">{{ $t('home.cta.title') }}</h2>
        <p class="cta-description">{{ $t('home.cta.description') }}</p>
        <button class="btn-cta" @click="contactMe">{{ $t('home.cta.btn') }}</button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="home-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>{{ $t('home.footer.copyright') }}</p>
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
            <a v-if="ownerProfile && ownerProfile.twitter" :href="formatUrl(ownerProfile.twitter)" target="_blank" rel="noopener" class="footer-link">Twitter</a>
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

  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,
      ownerProfile: null
    };
  },

  computed: {
    featuredProducts() {
      return [
        {
          icon: 'el-icon-notebook-2',
          name: 'Notecast',
          subtitle: this.$t('home.products.notecast.subtitle'),
          description: this.$t('home.products.notecast.description'),
          tags: ['Nuxt.js', 'Spring Boot 3', 'EditorJS'],
          status: 'active',
          statusLabel: this.$t('home.products.notecast.statusLabel')
        },
        {
          icon: 'el-icon-box',
          name: 'IMS — Site Inventory',
          subtitle: this.$t('home.products.ims.subtitle'),
          description: this.$t('home.products.ims.description'),
          tags: ['Spring Boot 3', 'Nuxt.js 2', 'MySQL'],
          status: 'done',
          statusLabel: this.$t('home.products.ims.statusLabel')
        },
        {
          icon: 'el-icon-school',
          name: 'TrainAdmin',
          subtitle: this.$t('home.products.trainadmin.subtitle'),
          description: this.$t('home.products.trainadmin.description'),
          tags: ['SaaS', 'Multi-tenant', 'Vue.js'],
          status: 'deployed',
          statusLabel: this.$t('home.products.trainadmin.statusLabel')
        }
      ];
    },

    services() {
      return [
        {
          icon: 'el-icon-magic-stick',
          title: this.$t('home.services.productBuild.title'),
          description: this.$t('home.services.productBuild.description'),
          tags: ['Spring Boot', 'Nuxt.js', 'MVP']
        },
        {
          icon: 'el-icon-cpu',
          title: this.$t('home.services.aiIntegration.title'),
          description: this.$t('home.services.aiIntegration.description'),
          tags: ['Claude', 'OpenAI', 'Automation']
        },
        {
          icon: 'el-icon-s-tools',
          title: this.$t('home.services.businessSystems.title'),
          description: this.$t('home.services.businessSystems.description'),
          tags: ['Admin', 'Dashboard', 'Workflow']
        },
        {
          icon: 'el-icon-connection',
          title: this.$t('home.services.partnership.title'),
          description: this.$t('home.services.partnership.description'),
          tags: ['Retainer', 'Long-term', 'Flexible']
        },
        {
          icon: 'el-icon-view',
          title: this.$t('home.services.consulting.title'),
          description: this.$t('home.services.consulting.description'),
          tags: ['Architecture', 'Advice', 'One-time']
        }
      ];
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false }
  },

  async mounted() {
    if (process.client) {
      this.isDarkTheme = document.documentElement.classList.contains('theme-dark');
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
      console.log('网站 toggleLang', this.$i18n.locale)
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      console.log('网站 toggleLang next', next)
      this.$i18n.setLocale(next)
      console.log('网站 toggleLang after', this.$i18n.locale)
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
    goApp() { this.$router.push('/workspace/notes'); },
    viewWork() { this.$router.push('/projects'); },
    letsTalk() { this.$router.push('/contact'); },
    contactMe() { this.$router.push('/contact'); },
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
      title: 'Home - AI-Powered Product Development'
    };
  },

  fetch({ store }) {
    store.commit('isHeader', false);
    store.commit('isFooter', false);
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
  i { font-size: 24px; color: #667eea; }
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
}

.hero-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 auto 40px;
  max-width: 800px;
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

// ── Vibe Coding Section ───────────────────────────────────
.vibe-section {
  background: var(--bg-tertiary);
  padding: 80px 40px;
  text-align: center;
}

.vibe-container {
  max-width: 1200px;
  margin: 0 auto;
}

.vibe-label {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #667eea;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 999px;
  padding: 4px 14px;
  margin-bottom: 20px;
}

.vibe-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 16px 0;
}

.vibe-subtitle {
  font-size: 16px;
  color: var(--text-muted);
  line-height: 1.7;
  max-width: 800px;
  margin: 0 auto 48px;
}

.vibe-video-wrap {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  box-shadow: 0 8px 32px var(--shadow-color);
  margin-bottom: 48px;
}

.vibe-video {
  width: 100%;
  height: auto;
  display: block;
}

.vibe-points {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.vibe-point {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 20px 24px;
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.vibe-check {
  font-size: 16px;
  color: #10b981;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 1px;
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
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 28px var(--shadow-color);
  }
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
}

.service-tags {
  display: flex; flex-wrap: wrap; gap: 5px;
}

.services-more {
  text-align: center;
}

// ── CTA Section ───────────────────────────────────────────
.cta-section {
  background: var(--cta-gradient);
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

  .vibe-section { padding: 52px 20px; }
  .vibe-title { font-size: 26px; }
  .vibe-points { grid-template-columns: 1fr; }

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
