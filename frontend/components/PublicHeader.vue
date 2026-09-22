<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="public-header-wrap">
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <nuxt-link to="/" class="logo">
            <img v-if="logoUrl" :src="resolveUrl(logoUrl)" class="logo-img" alt="" />
            <MindioLogo v-else />
            <span>{{ siteName }}</span>
          </nuxt-link>
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

    <div v-if="isMobileMenuOpen" class="mobile-overlay" @click="isMobileMenuOpen = false"></div>
    <nav v-if="isMobileMenuOpen" class="mobile-menu">
      <nuxt-link to="/" class="mobile-nav-link" exact-active-class="active" exact @click.native="isMobileMenuOpen = false">{{ $t('site.nav.home') }}</nuxt-link>
      <nuxt-link to="/notes" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.blog') }}</nuxt-link>
      <nuxt-link to="/projects" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.projects') }}</nuxt-link>
      <nuxt-link to="/contact" class="mobile-nav-link" exact-active-class="active" @click.native="isMobileMenuOpen = false">{{ $t('site.nav.contact') }}</nuxt-link>
    </nav>
  </div>
</template>

<script>
export default {
  name: 'PublicHeader',

  data() {
    return {
      isDarkTheme: false,
      isMobileMenuOpen: false,
      siteName: '',
      logoUrl: ''
    }
  },

  watch: {
    $route() { this.isMobileMenuOpen = false }
  },

  async mounted() {
    if (process.client) {
      this.isDarkTheme = document.documentElement.classList.contains('theme-dark')
    }
    try {
      const settings = await this.$axios.$get('/v1/settings/site')
      if (settings && settings.siteName) this.siteName = settings.siteName
      if (settings && settings.logoUrl) this.logoUrl = settings.logoUrl
    } catch (e) {}
  },

  methods: {
    toggleLang() {
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      this.$i18n.setLocale(next)
    },
    toggleTheme() {
      if (this.$root.$options.app && this.$root.$options.app.themeToggle) {
        this.isDarkTheme = this.$root.$options.app.themeToggle()
      } else if (process.client) {
        const isDark = document.documentElement.classList.toggle('theme-dark')
        window.localStorage.setItem('worknotes-theme', isDark ? 'dark' : 'light')
        this.isDarkTheme = isDark
      }
    },
    handleAvatarClick() {
      if (this.$auth && this.$auth.loggedIn) {
        this.goApp()
      } else {
        this.goLogin()
      }
    },
    resolveUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      return (this.$axios.defaults.baseURL || '') + path
    },
    goLogin() { this.$router.push('/login') },
    goApp() { this.$router.push(this.$auth && this.$auth.loggedIn ? '/workspace/notes' : '/login') },
    async handleLogout() {
      if (this.$auth && this.$auth.loggedIn) {
        await this.$auth.logout()
        this.$message.success(this.$t('messages.loggedOut'))
      } else {
        this.$router.push('/login')
      }
    }
  }
}
</script>

<style scoped lang="scss">
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
  text-decoration: none;
}

.logo-img {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  object-fit: contain;
  border-radius: 8px;
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

@media screen and (max-width: 1024px) {
  .header-left {
    width: auto;
    border-right: none;
  }
}

@media screen and (max-width: 768px) {
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
}
</style>
