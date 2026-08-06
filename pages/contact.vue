<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="contact-page">
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
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.blog') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-container">
        <h1 class="hero-title">{{ $t('contactPage.hero.title') }}</h1>
        <p class="hero-subtitle">{{ $t('contactPage.hero.subtitle') }}</p>
      </div>
    </section>

    <!-- Contact Methods Section -->
    <section class="contact-methods-section">
      <div class="contact-methods-container">

        <!-- Chinese: WeChat only -->
        <div v-if="$i18n.locale === 'zh-CN'" class="wechat-contact">
          <div class="contact-method-card wechat-card">
            <div class="method-icon wechat-icon">
              <i class="el-icon-chat-dot-round"></i>
            </div>
            <h3 class="method-title">{{ $t('contactPage.methods.wechat') }}</h3>
            <p class="method-note">{{ $t('contactPage.methods.wechatScan') }}</p>
            <div class="qr-code-box">
              <template v-if="ownerProfile && ownerProfile.wechatQrUrl">
                <img
                  :src="resolveUrl(ownerProfile.wechatQrUrl)"
                  alt="WeChat QR"
                  class="qr-image"
                  @error="$event.target.style.display='none'; $event.target.nextElementSibling.style.display='flex'"
                />
                <div class="qr-placeholder" style="display:none">
                  <i class="el-icon-picture-outline"></i>
                  <span>微信二维码</span>
                </div>
              </template>
              <div v-else class="qr-placeholder" style="display:flex">
                <i class="el-icon-picture-outline"></i>
                <span>微信二维码</span>
              </div>
            </div>
            <p class="wechat-note">{{ $t('contactPage.methods.wechatNote') }}</p>
          </div>
        </div>

        <!-- English: Email, Twitter, LinkedIn -->
        <div v-else class="contact-methods-grid">
          <!-- Email -->
          <div class="contact-method-card">
            <div class="method-icon email-icon">
              <i class="el-icon-message"></i>
            </div>
            <h3 class="method-title">{{ $t('contactPage.methods.email') }}</h3>
            <p class="method-handle">{{ $t('contactPage.methods.emailAddress') }}</p>
            <p class="method-note">{{ $t('contactPage.methods.emailNote') }}</p>
          </div>

          <!-- Twitter / X -->
          <div class="contact-method-card">
            <div class="method-icon twitter-icon">
              <i class="el-icon-share"></i>
            </div>
            <h3 class="method-title">{{ $t('contactPage.methods.twitter') }}</h3>
            <p class="method-handle">{{ $t('contactPage.methods.twitterHandle') }}</p>
            <p class="method-note">{{ $t('contactPage.methods.twitterNote') }}</p>
          </div>

          <!-- LinkedIn -->
          <div class="contact-method-card">
            <div class="method-icon linkedin-icon">
              <i class="el-icon-link"></i>
            </div>
            <h3 class="method-title">{{ $t('contactPage.methods.linkedin') }}</h3>
            <p class="method-handle">{{ $t('contactPage.methods.linkedinHandle') }}</p>
            <p class="method-note">{{ $t('contactPage.methods.linkedinNote') }}</p>
          </div>
        </div>

      </div>
    </section>

    <!-- Booking Modal: English only -->
    <div
      v-if="isBookingModalVisible && $i18n.locale !== 'zh-CN'"
      class="booking-modal-backdrop"
      @click.self="closeBookingModal"
    >
      <div class="booking-modal" ref="bookingModal">
        <button class="booking-modal-close" @click="closeBookingModal" aria-label="Close booking modal">
          ×
        </button>
        <h3 class="booking-modal-title">{{ $t('contactPage.modal.title') }}</h3>
        <p class="booking-modal-subtitle">{{ $t('contactPage.modal.subtitle') }}</p>
        <div
          id="tidycal-embed"
          class="tidycal-embed-container"
          :data-path="tidycalPath"
        ></div>
        <p class="booking-modal-footer">
          <span>{{ $t('contactPage.modal.poweredBy') }}</span>
          <a
            href="https://tidycal.com"
            target="_blank"
            rel="noopener noreferrer"
          >TidyCal</a>
        </p>
      </div>
    </div>

    <!-- Footer -->
    <footer class="page-footer">
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
  name: 'ContactPage',
  layout: 'blank',
  auth: false,
  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,
      isBookingModalVisible: false,
      ownerProfile: null,
      isTidyLoaded: false,
      tidycalPath: process.env.TIDYCAL_BOOKING_PATH || 'your-username/15-minute-meeting'
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

    if (process.client) {
      window.addEventListener('keydown', this.handleKeydown)
    }

    try {
      this.ownerProfile = await this.$profileService.getOwnerProfile()
    } catch (e) {
      // 公开页面，忽略加载失败
    }
  },
  beforeDestroy() {
    if (process.client) {
      window.removeEventListener('keydown', this.handleKeydown)
    }
  },
  methods: {
    formatUrl(url) {
      if (!url) return '#'
      return url.startsWith('http') ? url : 'https://' + url
    },
    resolveUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      return (this.$axios.defaults.baseURL || '') + path
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
    handleLogout() {
      console.log('Logout clicked')
    },
    bookCall() {
      this.isBookingModalVisible = true
      this.$nextTick(() => {
        this.loadTidycalEmbed()
      })
    },
    closeBookingModal() {
      this.isBookingModalVisible = false
    },
    handleKeydown(event) {
      if (event.key === 'Escape' && this.isBookingModalVisible) {
        this.closeBookingModal()
      }
    },
    loadTidycalEmbed() {
      if (!process.client) return

      if (this.isTidyLoaded && window.TidyCal) {
        try {
          window.TidyCal.render && window.TidyCal.render()
        } catch (e) {
          console.error('Failed to re-render TidyCal embed:', e)
        }
        return
      }

      if (document.querySelector('script[data-tidycal-embed]')) {
        this.isTidyLoaded = true
        if (window.TidyCal && window.TidyCal.render) {
          window.TidyCal.render()
        }
        return
      }

      const script = document.createElement('script')
      script.src = 'https://tidycal.com/js/embed.js'
      script.async = true
      script.setAttribute('data-tidycal-embed', 'true')
      script.onload = () => {
        this.isTidyLoaded = true
      }
      script.onerror = () => {
        console.error('Failed to load TidyCal embed script')
        this.$message && this.$message.error && this.$message.error('Failed to load booking widget, please try again later.')
      }
      document.body.appendChild(script)
    }
  },

  head() {
    return {
      title: 'Contact - Get in Touch',
    };
  },

  fetch({ store }) {
    store.commit("isHeader", false);
    store.commit("isFooter", false);
  }
}
</script>

<style lang="scss" scoped>
.contact-page {
  min-height: 100vh;
  background: var(--bg-color);
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

// Header
.page-header {
  background: var(--header-bg);
  border-bottom: 1px solid var(--border-color);
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

.user-profile {
  display: flex;
  align-items: center;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
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
  font-size: 48px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 20px 0;
}

.hero-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

// Contact Methods Section
.contact-methods-section {
  background: var(--bg-color);
  padding: 40px 0 80px;
  flex: 1;
}

.contact-methods-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.contact-methods-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.wechat-contact {
  display: flex;
  justify-content: center;
}

.wechat-card {
  max-width: 380px;
  width: 100%;
  text-align: center;
}

.qr-code-box {
  width: 180px;
  height: 180px;
  margin: 20px auto 12px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.qr-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.qr-placeholder {
  width: 100%;
  height: 100%;
  background: var(--bg-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-muted);

  i {
    font-size: 36px;
  }

  span {
    font-size: 13px;
  }
}

.contact-method-card {
  background: var(--card-bg-color);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  padding: 32px;
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--border-color);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
}

.method-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;

  i {
    font-size: 24px;
    color: white;
  }

  &.email-icon {
    background: #3b82f6;
  }

  &.twitter-icon {
    background: #1da1f2;
  }

  &.wechat-icon {
    background: #07c160;
  }

  &.linkedin-icon {
    background: #0a66c2;
  }
}

.method-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 8px 0;
}

.method-handle {
  font-size: 14px;
  color: #3b82f6;
  margin: 0 0 6px 0;
  font-weight: 500;
}

.method-note {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

.wechat-note {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

// Form Section
.form-section {
  background: var(--bg-tertiary);
  padding: 80px 0;
}

.form-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
}

.form-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-color);
  text-align: center;
  margin: 0 0 40px 0;
}

.collaboration-form {
  background: var(--card-bg-color);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  padding: 40px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
  margin-bottom: 8px;

  .optional {
    color: var(--text-muted);
    font-weight: 400;
  }
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--input-border);
  border-radius: 8px;
  font-size: 15px;
  color: var(--text-color);
  background: var(--input-bg);
  transition: all 0.2s;
  box-sizing: border-box;

  &:focus {
    outline: none;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

.form-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--input-border);
  border-radius: 8px;
  font-size: 15px;
  color: var(--text-color);
  background: var(--input-bg);
  font-family: inherit;
  resize: vertical;
  transition: all 0.2s;
  box-sizing: border-box;

  &:focus {
    outline: none;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

.upload-area {
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
    background: var(--bg-secondary);
  }
}

.file-input {
  display: none;
}

.upload-content {
  i {
    font-size: 48px;
    color: var(--text-placeholder);
    margin-bottom: 12px;
  }
}

.upload-text {
  font-size: 15px;
  color: var(--text-muted);
  margin: 0 0 8px 0;
}

.upload-link {
  color: #3b82f6;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.upload-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

.file-name {
  font-size: 14px;
  color: #3b82f6;
  margin-top: 12px;
  font-weight: 500;
}

.form-actions {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.btn-submit {
  padding: 12px 48px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #2563eb;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  }
}

.form-note {
  font-size: 14px;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.6;
  margin: 0;
}

// Call Option
.call-option {
  text-align: center;
  margin-top: 40px;
}

.call-text {
  font-size: 15px;
  color: var(--text-muted);
  margin: 0 0 16px 0;
}

.btn-call {
  padding: 10px 32px;
  background: var(--card-bg-color);
  color: var(--text-color);
  border: 2px solid var(--border-color);
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--border-color);
    background: var(--bg-secondary);
  }
}

// Booking Modal
.booking-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 16px;
}

.booking-modal {
  position: relative;
  width: 100%;
  max-width: 720px;
  max-height: 90vh;
  background: var(--card-bg-color);
  border-radius: 16px;
  padding: 24px 24px 16px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.45);
  display: flex;
  flex-direction: column;
}

.booking-modal-close {
  position: absolute;
  top: 12px;
  right: 16px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 22px;
  cursor: pointer;
  line-height: 1;
}

.booking-modal-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 4px;
}

.booking-modal-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0 0 16px;
}

.tidycal-embed-container {
  flex: 1;
  min-height: 420px;
  overflow: auto;
}

.booking-modal-footer {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: right;

  a {
    color: #3b82f6;
    text-decoration: none;
    margin-left: 4px;

    &:hover {
      text-decoration: underline;
    }
  }
}

// Footer
.page-footer {
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
  margin: 0;
  font-size: 14px;
}

.footer-right {
  display: flex;
  gap: 24px;
}

.footer-link {
  color: #a0aec0;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;

  &:hover {
    color: white;
  }
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

  .header-nav {
    margin-left: 24px;
  }

  .hero-title {
    font-size: 40px;
  }

  .contact-methods-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 20px;
  }

  .wechat-card {
    max-width: 100%;
  }
}

@media screen and (max-width: 768px) {
  .contact-page { overflow-x: hidden; }

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
  .contact-methods-container,
  .form-container {
    padding: 0 20px;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .contact-methods-section {
    padding: 40px 0 60px;
  }

  .contact-methods-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .contact-method-card {
    padding: 24px;
  }

  .form-section {
    padding: 60px 0;
  }

  .form-title {
    font-size: 28px;
  }

  .collaboration-form {
    padding: 24px;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
    margin-bottom: 0;
  }

  .footer-container {
    flex-direction: column;
    gap: 16px;
    text-align: center;
    padding: 0 20px;
  }
}
</style>
