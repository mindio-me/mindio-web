<template>
  <!-- Services Page -->
  <div class="services-page">
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
          <!-- <div v-if="$auth && $auth.loggedIn && !$device.isMobile" class="user-profile">
            <img src="/default_user.png" alt="User" class="user-avatar" />
          </div>
          <button v-if="$auth && $auth.loggedIn && !$device.isMobile" class="logout-btn" @click="handleLogout">{{ $t('site.header.logout') }}</button> -->
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
        <h1 class="hero-title">{{ $t('servicesPage.hero.title') }}</h1>
        <p class="hero-subtitle">{{ $t('servicesPage.hero.subtitle') }}</p>
      </div>
    </section>

    <!-- Services Grid Section -->
    <section class="services-grid-section">
      <div class="services-container">
        <div class="services-grid">

          <!-- Service 1: Product Build -->
          <div class="service-card featured">
            <div class="service-badge">{{ $t('servicesPage.cards.coreBadge') }}</div>
            <div class="service-icon">
              <i class="el-icon-magic-stick"></i>
            </div>
            <h3 class="service-title">{{ $t('servicesPage.cards.productBuild.title') }}</h3>
            <p class="service-description">{{ $t('servicesPage.cards.productBuild.desc') }}</p>
            <div class="service-tags">
              <span class="tag">Spring Boot</span>
              <span class="tag">Nuxt.js</span>
              <span class="tag">MVP</span>
            </div>
          </div>

          <!-- Service 2: AI Integration -->
          <div class="service-card">
            <div class="service-icon">
              <i class="el-icon-cpu"></i>
            </div>
            <h3 class="service-title">{{ $t('servicesPage.cards.aiIntegration.title') }}</h3>
            <p class="service-description">{{ $t('servicesPage.cards.aiIntegration.desc') }}</p>
            <div class="service-tags">
              <span class="tag">Claude</span>
              <span class="tag">OpenAI</span>
              <span class="tag">Automation</span>
            </div>
          </div>

          <!-- Service 3: Business Systems -->
          <div class="service-card">
            <div class="service-icon">
              <i class="el-icon-s-tools"></i>
            </div>
            <h3 class="service-title">{{ $t('servicesPage.cards.businessSystems.title') }}</h3>
            <p class="service-description">{{ $t('servicesPage.cards.businessSystems.desc') }}</p>
            <div class="service-tags">
              <span class="tag">Admin Panel</span>
              <span class="tag">Dashboard</span>
              <span class="tag">Workflow</span>
            </div>
          </div>

          <!-- Service 4: Technical Partnership -->
          <div class="service-card">
            <div class="service-icon">
              <i class="el-icon-connection"></i>
            </div>
            <h3 class="service-title">{{ $t('servicesPage.cards.partnership.title') }}</h3>
            <p class="service-description">{{ $t('servicesPage.cards.partnership.desc') }}</p>
            <div class="service-tags">
              <span class="tag">Retainer</span>
              <span class="tag">Long-term</span>
              <span class="tag">Flexible</span>
            </div>
          </div>

          <!-- Service 5: Consulting & Review -->
          <div class="service-card">
            <div class="service-icon">
              <i class="el-icon-view"></i>
            </div>
            <h3 class="service-title">{{ $t('servicesPage.cards.consulting.title') }}</h3>
            <p class="service-description">{{ $t('servicesPage.cards.consulting.desc') }}</p>
            <div class="service-tags">
              <span class="tag">Architecture</span>
              <span class="tag">Advice</span>
              <span class="tag">One-time</span>
            </div>
          </div>

        </div>
      </div>
    </section>

    <!-- Built & Used in Production Section -->
    <section class="proof-section">
      <div class="proof-container">
        <div class="proof-header">
          <span class="proof-label">{{ $t('servicesPage.proof.label') }}</span>
          <h2 class="proof-title">{{ $t('servicesPage.proof.title') }}</h2>
          <p class="proof-subtitle">{{ $t('servicesPage.proof.subtitle') }}</p>
        </div>

        <div class="proof-grid">

          <!-- Notecast -->
          <div class="proof-card">
            <div class="proof-card-accent proof-accent--blue"></div>
            <div class="proof-card-body">
              <div class="proof-card-head">
                <div class="proof-icon proof-icon--blue">
                  <i class="el-icon-notebook-2"></i>
                </div>
                <div class="proof-card-title-group">
                  <h3 class="proof-card-title">Notecast</h3>
                  <span class="proof-card-type">{{ $t('servicesPage.proof.notecast.type') }}</span>
                </div>
                <span class="proof-status proof-status--active">{{ $t('servicesPage.proof.notecast.status') }}</span>
              </div>
              <p class="proof-card-sub">{{ $t('servicesPage.proof.notecast.sub') }}</p>
              <p class="proof-card-desc">{{ $t('servicesPage.proof.notecast.desc') }}</p>
              <div class="proof-services-used">
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.webDev') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.backend') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.ai') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.api') }}</span>
              </div>
              <div class="proof-tech-row">
                <span class="tag">Nuxt.js</span>
                <span class="tag">Spring Boot 3</span>
                <span class="tag">MySQL</span>
                <span class="tag">EditorJS</span>
                <span class="tag">WeChat API</span>
                <span class="tag">Feishu API</span>
                <span class="tag">Claude AI</span>
              </div>
            </div>
          </div>

          <!-- IMS -->
          <div class="proof-card">
            <div class="proof-card-accent proof-accent--green"></div>
            <div class="proof-card-body">
              <div class="proof-card-head">
                <div class="proof-icon proof-icon--green">
                  <i class="el-icon-box"></i>
                </div>
                <div class="proof-card-title-group">
                  <h3 class="proof-card-title">IMS — Site Inventory</h3>
                  <span class="proof-card-type">{{ $t('servicesPage.proof.ims.type') }}</span>
                </div>
                <span class="proof-status proof-status--done">{{ $t('servicesPage.proof.ims.status') }}</span>
              </div>
              <p class="proof-card-sub">{{ $t('servicesPage.proof.ims.sub') }}</p>
              <p class="proof-card-desc">{{ $t('servicesPage.proof.ims.desc') }}</p>
              <div class="proof-services-used">
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.webDev') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.backend') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.consulting') }}</span>
                <span class="proof-service-tag">{{ $t('servicesPage.proof.tags.automation') }}</span>
              </div>
              <div class="proof-tech-row">
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

    <!-- Collaboration Process Section -->
    <section class="process-section">
      <div class="process-container">
        <h2 class="process-title">{{ $t('servicesPage.process.title') }}</h2>

        <div class="process-grid">
          <div class="process-step">
            <div class="step-number">1</div>
            <h3 class="step-title">{{ $t('servicesPage.process.step1.title') }}</h3>
            <p class="step-description">{{ $t('servicesPage.process.step1.desc') }}</p>
          </div>

          <div class="process-step">
            <div class="step-number">2</div>
            <h3 class="step-title">{{ $t('servicesPage.process.step2.title') }}</h3>
            <p class="step-description">{{ $t('servicesPage.process.step2.desc') }}</p>
          </div>

          <div class="process-step">
            <div class="step-number">3</div>
            <h3 class="step-title">{{ $t('servicesPage.process.step3.title') }}</h3>
            <p class="step-description">{{ $t('servicesPage.process.step3.desc') }}</p>
          </div>

          <div class="process-step">
            <div class="step-number">4</div>
            <h3 class="step-title">{{ $t('servicesPage.process.step4.title') }}</h3>
            <p class="step-description">{{ $t('servicesPage.process.step4.desc') }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Pricing Models Section -->
    <section class="pricing-section">
      <div class="pricing-container">
        <h2 class="pricing-title">{{ $t('servicesPage.pricing.title') }}</h2>

        <div class="pricing-cards">
          <div class="pricing-card">
            <div class="pricing-icon">
              <i class="el-icon-document"></i>
            </div>
            <h3 class="pricing-card-title">{{ $t('servicesPage.pricing.fixed.title') }}</h3>
            <p class="pricing-card-description">{{ $t('servicesPage.pricing.fixed.desc') }}</p>
          </div>

          <div class="pricing-card">
            <div class="pricing-icon">
              <i class="el-icon-time"></i>
            </div>
            <h3 class="pricing-card-title">{{ $t('servicesPage.pricing.hourly.title') }}</h3>
            <p class="pricing-card-description">{{ $t('servicesPage.pricing.hourly.desc') }}</p>
          </div>

          <div class="pricing-card">
            <div class="pricing-icon">
              <i class="el-icon-coordinate"></i>
            </div>
            <h3 class="pricing-card-title">{{ $t('servicesPage.pricing.retainer.title') }}</h3>
            <p class="pricing-card-description">{{ $t('servicesPage.pricing.retainer.desc') }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ Section -->
    <section class="faq-section">
      <div class="faq-container">
        <h2 class="faq-title">{{ $t('servicesPage.faq.title') }}</h2>
        <p class="faq-subtitle">{{ $t('servicesPage.faq.subtitle') }}</p>

        <div class="faq-list">
          <div
            v-for="(faq, index) in faqs"
            :key="index"
            class="faq-item"
            :class="{ active: activeFaq === index }"
            @click="toggleFaq(index)"
          >
            <div class="faq-question">
              <h3>{{ faq.q }}</h3>
              <i class="el-icon-arrow-down faq-icon"></i>
            </div>
            <div class="faq-answer" v-show="activeFaq === index">
              <p>{{ faq.a }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-container">
        <h2 class="cta-title">{{ $t('servicesPage.cta.title') }}</h2>
        <p class="cta-description">{{ $t('servicesPage.cta.desc') }}</p>
        <button class="btn-cta" @click="bookConsultation">{{ $t('servicesPage.cta.btn') }}</button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="page-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>{{ $t('servicesPage.footer.copyright') }}</p>
        </div>
        <div class="footer-right">
          <template v-if="$i18n.locale === 'zh-CN'">
            <a href="/contact" class="footer-link" :title="ownerProfile && ownerProfile.wechat ? ownerProfile.wechat : '微信'">
              <i class="el-icon-chat-dot-round"></i>
            </a>
          </template>
          <template v-else>
            <a v-if="ownerProfile && ownerProfile.twitter" :href="formatUrl(ownerProfile.twitter)" target="_blank" rel="noopener" class="footer-link" title="Twitter">
              <i class="el-icon-s-promotion"></i>
            </a>
            <a v-if="ownerProfile && ownerProfile.linkedin" :href="formatUrl(ownerProfile.linkedin)" target="_blank" rel="noopener" class="footer-link" title="LinkedIn">
              <i class="el-icon-link"></i>
            </a>
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
      services: [],
      loading: false,
      activeFaq: null,
      ownerProfile: null
    };
  },

  computed: {
    faqs() {
      const messages = this.$i18n.messages[this.$i18n.locale]
      return (messages && messages.servicesPage && messages.servicesPage.faq && messages.servicesPage.faq.items) || []
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false }
  },

  async mounted() {
    if (process.client) {
      const root = document.documentElement;
      this.isDarkTheme = root.classList.contains('theme-dark');
    }
    await this.loadServices();
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
    async loadServices() {
      this.loading = true;
      try {
        this.services = await this.$serviceService.getAllServices();
      } catch (error) {
        console.error('Error loading services:', error);
        this.$message.error('Failed to load services');
      } finally {
        this.loading = false;
      }
    },

    handleLogout() {
      console.log('Logout clicked')
    },

    bookConsultation() {
      this.$router.push('/workspace/contact');
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

    toggleFaq(index) {
      this.activeFaq = this.activeFaq === index ? null : index;
    }
  },

  head() {
    return {
      title: 'Services - Full-Stack Development Solutions',
    };
  },

  fetch({ store }) {
    store.commit("isHeader", false);
    store.commit("isFooter", false);
  }
};
</script>

<style scoped lang="scss">
.services-page {
  background: var(--bg-tertiary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  min-height: 100vh;
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

// Hero Section
.hero-section {
  background: var(--bg-color);
  padding: 80px 0 60px;
  text-align: center;
}

.hero-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 40px;
}

.hero-title {
  font-size: 42px;
  font-weight: 700;
  color: var(--text-color);
  line-height: 1.3;
  margin: 0 0 24px 0;
}

.hero-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0;
}

// Services Grid Section
.services-grid-section {
  padding: 60px 0 80px;
  background: var(--bg-tertiary);
}

.services-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 28px;
}

.service-card {
  background: var(--card-bg-color);
  border-radius: 12px;
  padding: 32px;
  border: 1px solid var(--border-color);
  transition: all 0.3s;
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 320px;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px var(--shadow-color);
    border-color: var(--border-color);
  }

  &.featured {
    // border: 2px solid #3b82f6;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
  }
}

.service-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  background: #3b82f6;
  color: white;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.service-icon {
  width: 56px;
  height: 56px;
  background: var(--icon-bg);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;

  i {
    font-size: 28px;
    color: #3b82f6;
  }
}

.service-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
  line-height: 1.3;
}

.service-description {
  font-size: 15px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 0 0 20px 0;
  flex: 1;
}

.service-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  background: var(--tag-bg);
  color: var(--tag-color);
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

// Collaboration Process Section
.process-section {
  background: var(--bg-color);
  padding: 80px 0;
}

.process-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.process-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  text-align: center;
  margin: 0 0 60px 0;
}

.process-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 40px;
}

.process-step {
  text-align: center;
}

.step-number {
  width: 60px;
  height: 60px;
  margin: 0 auto 20px;
  background: #3b82f6;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
}

.step-title {
  font-size: 19px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.step-description {
  font-size: 15px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 0;
}

// Pricing Models Section
.pricing-section {
  background: var(--bg-tertiary);
  padding: 80px 0;
}

.pricing-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.pricing-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  text-align: center;
  margin: 0 0 12px 0;
}

.pricing-subtitle {
  font-size: 16px;
  color: var(--text-muted);
  text-align: center;
  margin: 0 0 50px 0;
}

.pricing-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
  margin-bottom: 32px;
}

.pricing-card {
  background: var(--card-bg-color);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  padding: 32px;
  transition: all 0.3s ease;

  &:hover {
    border-color: #3b82f6;
    transform: translateY(-4px);
    box-shadow: 0 12px 24px rgba(59, 130, 246, 0.15);
  }
}

.pricing-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;

  i {
    font-size: 28px;
    color: white;
  }
}

.pricing-card-title {
  font-size: 19px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
  line-height: 1.3;
}

.pricing-card-description {
  font-size: 15px;
  color: var(--text-muted);
  line-height: 1.6;
  margin: 0;
}

.pricing-note {
  font-size: 15px;
  color: var(--text-muted);
  text-align: center;
  margin: 0;
  font-style: italic;
}

// FAQ Section
.faq-section {
  background: var(--bg-color);
  padding: 80px 0;
}

.faq-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 40px;
}

.faq-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  text-align: center;
  margin: 0 0 12px 0;
}

.faq-subtitle {
  font-size: 16px;
  color: var(--text-muted);
  text-align: center;
  margin: 0 0 50px 0;
}

.faq-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.faq-item {
  background: var(--card-bg-color);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--border-color);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }

  &.active {
    border-color: #3b82f6;

    .faq-icon {
      transform: rotate(180deg);
      color: #3b82f6;
    }
  }
}

.faq-question {
  padding: 24px 28px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;

  h3 {
    font-size: 17px;
    font-weight: 600;
    color: var(--text-color);
    margin: 0;
    line-height: 1.4;
  }
}

.faq-icon {
  font-size: 18px;
  color: var(--text-muted);
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.faq-answer {
  padding: 0 28px 24px 28px;
  animation: fadeIn 0.3s ease;

  p {
    font-size: 15px;
    color: var(--text-muted);
    line-height: 1.7;
    margin: 0;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// CTA Section
.cta-section {
  background: var(--bg-color);
  padding: 80px 0;
  text-align: center;
}

.cta-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 40px;
}

.cta-title {
  font-size: 36px;
  font-weight: 700;
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
  font-size: 16px;
  font-weight: 600;
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
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  text-decoration: none;
  transition: color 0.2s;

  i {
    font-size: 20px;
  }

  &:hover {
    color: white;
  }
}

// Proof Section (Built & Used in Production)
.proof-section {
  background: var(--bg-tertiary);
  padding: 80px 0;
}

.proof-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.proof-header {
  text-align: center;
  margin-bottom: 56px;
}

.proof-label {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #667eea;
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 999px;
  padding: 4px 14px;
  margin-bottom: 16px;
  text-transform: uppercase;
}

.proof-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.proof-subtitle {
  font-size: 16px;
  color: var(--text-muted);
  line-height: 1.7;
  max-width: 680px;
  margin: 0 auto;
}

.proof-grid {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.proof-card {
  background: var(--card-bg-color);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  overflow: hidden;
  display: flex;
  transition: all 0.25s;

  &:hover {
    box-shadow: 0 12px 32px var(--shadow-color);
    border-color: rgba(102, 126, 234, 0.2);
    transform: translateY(-2px);
  }
}

.proof-card-accent {
  width: 4px;
  flex-shrink: 0;

  &.proof-accent--blue {
    background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  }

  &.proof-accent--green {
    background: linear-gradient(180deg, #34d399 0%, #059669 100%);
  }
}

.proof-card-body {
  flex: 1;
  padding: 32px;
}

.proof-card-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.proof-icon {
  width: 52px;
  height: 52px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  i {
    font-size: 26px;
    color: white;
  }

  &.proof-icon--blue {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  }

  &.proof-icon--green {
    background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  }
}

.proof-card-title-group {
  flex: 1;
  min-width: 0;
}

.proof-card-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 3px 0;
}

.proof-card-type {
  font-size: 13px;
  color: var(--text-muted);
}

.proof-status {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 999px;
  flex-shrink: 0;

  &.proof-status--active {
    background: rgba(59, 130, 246, 0.1);
    color: #3b82f6;
    border: 1px solid rgba(59, 130, 246, 0.2);
  }

  &.proof-status--done {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.2);
  }
}

.proof-card-sub {
  font-size: 14px;
  color: var(--text-muted);
  font-style: italic;
  margin: 0 0 14px 0;
}

.proof-card-desc {
  font-size: 15px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 20px 0;
}

.proof-services-used {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.proof-service-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 6px;
  background: rgba(102, 126, 234, 0.08);
  color: #667eea;
  border: 1px solid rgba(102, 126, 234, 0.2);
}

.proof-tech-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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

// Responsive Design
@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
  }

  .services-grid {
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 24px;
  }

  .hero-title {
    font-size: 36px;
  }

  .cta-title {
    font-size: 30px;
  }

  .process-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 32px;
  }

  .pricing-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
  }

  .faq-container {
    padding: 0 32px;
  }

  .faq-question {
    padding: 20px 24px;

    h3 {
      font-size: 16px;
    }
  }

  .faq-answer {
    padding: 0 24px 20px 24px;
  }
}

@media screen and (max-width: 768px) {
  .services-page { overflow-x: hidden; }

  .header-left {
    padding: 0 20px;
  }

  .header-right {
    padding: 0 20px;
    gap: 8px;
  }

  .header-nav {
    display: none;
  }

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
    padding: 56px 0 36px;
  }

  .hero-container,
  .services-container,
  .cta-container {
    padding: 0 20px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .services-grid-section {
    padding: 40px 0 60px;
  }

  .services-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .service-card {
    padding: 24px;
  }

  .proof-section {
    padding: 60px 0;
  }

  .proof-container {
    padding: 0 20px;
  }

  .proof-title {
    font-size: 28px;
  }

  .proof-card {
    flex-direction: column;
  }

  .proof-card-accent {
    width: 100%;
    height: 4px;
  }

  .proof-card-body {
    padding: 24px;
  }

  .proof-card-title {
    font-size: 18px;
  }

  .process-section {
    padding: 60px 0;
  }

  .process-container {
    padding: 0 20px;
  }

  .process-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .pricing-section {
    padding: 60px 0;
  }

  .pricing-container {
    padding: 0 20px;
  }

  .pricing-cards {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .pricing-card {
    padding: 24px;
  }

  .faq-section {
    padding: 60px 0;
  }

  .faq-container {
    padding: 0 20px;
  }

  .faq-title {
    font-size: 28px;
  }

  .faq-question {
    padding: 18px 20px;

    h3 {
      font-size: 15px;
    }
  }

  .faq-icon {
    font-size: 16px;
  }

  .faq-answer {
    padding: 0 20px 18px 20px;

    p {
      font-size: 14px;
    }
  }

  .cta-section {
    padding: 60px 0;
  }

  .cta-title {
    font-size: 26px;
  }

  .footer-container {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }
}
</style>
