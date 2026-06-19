<template>
  <div class="workspace-layout" :class="{ 'topbar-collapsed': topbarCollapsed }">
    <!-- 切换按钮（始终显示） -->
    <button
      class="topbar-toggle-btn"
      :class="{ 'collapsed': topbarCollapsed }"
      @click="toggleTopbar"
      :title="topbarCollapsed ? $t('topbar.show') : $t('topbar.hide')"
    >
      <i :class="topbarCollapsed ? 'el-icon-arrow-down' : 'el-icon-arrow-up'"></i>
    </button>

    <!-- 顶部栏：logo + 用户信息 -->
    <transition name="topbar-slide">
      <div v-show="!topbarCollapsed" class="workspace-topbar">
        <div class="topbar-left">
          <div class="topbar-logo" @click="$router.push('/')">
            <i class="el-icon-notebook-2"></i>
            <span class="topbar-logo-text">MindIO</span>
          </div>
          <el-button type="text" class="topbar-home-link" @click="$router.push('/')">
            <i class="el-icon-s-home"></i> {{ $t('topbar.home') }}
          </el-button>
        </div>
        <div class="topbar-right">
          <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? $t('topbar.lightMode') : $t('topbar.darkMode')">
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <button class="theme-toggle lang-toggle" @click="toggleLang" :title="$t('lang.toggle')">
            {{ $t('lang.toggle') }}
          </button>
          <el-dropdown @command="handleUserCommand">
            <span class="topbar-user">
              <i class="el-icon-user"></i>
              {{ userName }}
              <i class="el-icon-arrow-down"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="profile">
                <i class="el-icon-user"></i> {{ $t('user.profile') }}
              </el-dropdown-item>
              <el-dropdown-item command="settings">
                <i class="el-icon-setting"></i> {{ $t('user.settings') }}
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <i class="el-icon-switch-button"></i> {{ $t('user.logout') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </div>
    </transition>

    <!-- 模块 Tab 切换 -->
    <transition name="tabs-slide">
      <div v-show="!topbarCollapsed && !isAccountPage" class="module-tabs-row">
      <div class="module-tabs">
        <div
          v-for="tab in moduleTabs"
          :key="tab.key"
          class="module-tab"
          :class="{ active: activeModule === tab.key }"
          @click="switchModule(tab.key)"
        >
          <i :class="tab.icon"></i>
          <span>{{ tab.label }}</span>
        </div>
      </div>
      <div class="module-tabs-actions">
        <el-button
          v-if="activeModule === 'notes' && !isEditPage"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newNote') }}</el-button>
        <el-button
          v-if="activeModule === 'projects'"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newProject') }}</el-button>
        <el-button
          v-if="activeModule === 'services'"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newService') }}</el-button>
        <el-button
          v-if="activeModule === 'resources'"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newResource') }}</el-button>
        <el-button
          v-if="activeModule === 'clips'"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newClip') }}</el-button>
        <el-button
          v-if="activeModule === 'tags'"
          type="primary"
          size="small"
          icon="el-icon-plus"
          @click="handleCreate"
        >{{ $t('actions.newTag') }}</el-button>
      </div>
    </div>
    </transition>

    <!-- 页面内容 -->
    <nuxt />
  </div>
</template>

<script>
export default {
  name: 'WorkspaceLayout',
  provide() {
    // 提供一个响应式的对象，包含顶部栏状态
    return {
      getTopbarCollapsed: () => this.topbarCollapsed
    }
  },
  data() {
    return {
      isDarkTheme: false,
      topbarCollapsed: false
    }
  },
  computed: {
    moduleTabs() {
      return [
        { key: 'notes', label: this.$t('nav.notes'), icon: 'el-icon-notebook-2' },
        { key: 'projects', label: this.$t('nav.projects'), icon: 'el-icon-folder-opened' },
        { key: 'services', label: this.$t('nav.services'), icon: 'el-icon-service' },
        { key: 'resources', label: this.$t('nav.resources'), icon: 'el-icon-link' },
        { key: 'tags', label: this.$t('nav.tags'), icon: 'el-icon-collection-tag' },
        { key: 'collab-requests', label: this.$t('nav.cooperation'), icon: 'el-icon-message' },
        { key: 'local-docs', label: this.$t('nav.localDocs'), icon: 'el-icon-files' },
        { key: 'local-media', label: this.$t('nav.localMedia'), icon: 'el-icon-picture' },
        { key: 'clips', label: this.$t('nav.clips'), icon: 'el-icon-document-copy' },
        { key: 'news', label: this.$t('nav.news'), icon: 'el-icon-news' },
      ]
    },
    userName() {
      return this.$auth?.user?.username || 'User'
    },
    isAdmin() {
      return this.$auth?.user?.role === 'ADMIN'
    },
    visibleModuleTabs() {
      return this.moduleTabs.filter(tab => {
        if (tab.key === 'collab-requests') {
          return this.isAdmin
        }
        return true
      })
    },
    activeModule() {
      const path = this.$route.path
      if (path.startsWith('/workspace/notes')) return 'notes'
      if (path.startsWith('/workspace/projects')) return 'projects'
      if (path.startsWith('/workspace/services')) return 'services'
      if (path.startsWith('/workspace/resources')) return 'resources'
      if (path.startsWith('/workspace/clips')) return 'clips'
      if (path.startsWith('/workspace/local-docs')) return 'local-docs'
      if (path.startsWith('/workspace/local-media')) return 'local-media'
      if (path.startsWith('/workspace/news')) return 'news'
      if (path.startsWith('/workspace/tags')) return 'tags'
      if (path.startsWith('/workspace/collaboration-requests')) return 'collab-requests'
      return 'notes'
    },
    isAccountPage() {
      const path = this.$route.path
      return path.startsWith('/workspace/profile') || path.startsWith('/workspace/settings')
    },
    isEditPage() {
      return this.$route.path.includes('/edit')
    }
  },
  watch: {
    '$route.path'() {
      // 路由变化时，确保重定向到正确的子路由
      if (this.$route.path === '/workspace') {
        this.$router.replace('/workspace/notes')
      }
    }
  },
  mounted() {
    // 初始化主题状态
    if (process.client) {
      this.isDarkTheme = document.documentElement.classList.contains('theme-dark')
      // 从 localStorage 恢复顶部栏状态
      const saved = localStorage.getItem('workspace-topbar-collapsed')
      if (saved !== null) {
        this.topbarCollapsed = saved === 'true'
      }
    }
    // 如果直接访问 /workspace，重定向到 /workspace/notes
    if (this.$route.path === '/workspace') {
      this.$router.replace('/workspace/notes')
    }
  },
  methods: {
    toggleTopbar() {
      this.topbarCollapsed = !this.topbarCollapsed
      // 保存状态到 localStorage
      if (process.client) {
        localStorage.setItem('workspace-topbar-collapsed', this.topbarCollapsed)
      }
      // 触发事件通知子页面更新
      this.$nuxt.$emit('workspace:topbar:toggle', this.topbarCollapsed)
    },
    switchModule(module) {
      if (this.activeModule === module) return
      if (module === 'collab-requests') {
        this.$router.push('/workspace/collaboration-requests')
      } else {
        this.$router.push(`/workspace/${module}`)
      }
    },
    handleCreate() {
      // 触发创建事件，由子页面监听并处理
      this.$nuxt.$emit(`workspace:create:${this.activeModule}`)
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
    toggleLang() {
      console.log('workspace toggleLang', this.$i18n.locale)  
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      console.log('workspace toggleLang next', next)
      this.$i18n.setLocale(next)
      console.log('workspace toggleLang after', this.$i18n.locale)
    },
    handleUserCommand(command) {
      if (command === 'logout') {
        this.$confirm(this.$t('confirm.logout'), this.$t('confirm.logoutTitle'), {
          confirmButtonText: this.$t('confirm.confirmBtn'),
          cancelButtonText: this.$t('confirm.cancelBtn'),
          type: 'warning'
        }).then(() => {
          this.$auth.logout()
          this.$message.success(this.$t('messages.loggedOut'))
        }).catch(() => {})
      } else if (command === 'profile') {
        this.$router.push('/workspace/profile')
      } else if (command === 'settings') {
        this.$router.push('/workspace/settings')
      }
    }
  }
}
</script>

<style scoped lang="scss">
.workspace-layout {
  height: 100vh;
  overflow: hidden;
  padding: 12px 20px;
  background: var(--bg-secondary);
  position: relative;
  transition: padding 0.3s ease;

  &.topbar-collapsed {
    padding-top: 20px; // 只保留切换按钮的少量空间
  }
}

// 切换按钮
.topbar-toggle-btn {
  position: fixed;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  width: 24px;
  height: 20px;
  border: none;
  border-radius: 0 0 8px 8px;
  background: rgba(0, 0, 0, 0.03);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  opacity: 0.6;

  i {
    font-size: 12px;
    transition: transform 0.3s ease;
  }

  &:hover {
    opacity: 1;
    background: rgba(0, 0, 0, 0.08);
    color: var(--text-secondary);
  }

  &.collapsed {
    top: 8px;
  }
}

// 顶部栏
.workspace-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 0 2px;
  overflow: hidden;
}

// 顶部栏动画
.topbar-slide-enter-active,
.topbar-slide-leave-active {
  transition: all 0.3s ease;
  max-height: 100px;
  opacity: 1;
}

.topbar-slide-enter,
.topbar-slide-leave-to {
  max-height: 0;
  opacity: 0;
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #667eea;
  cursor: pointer;

  i {
    font-size: 22px;
  }
}

.topbar-home-link {
  font-size: 13px;
  color: var(--text-muted) !important;
  padding: 4px 8px;

  &:hover {
    color: #667eea !important;
  }
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;

  .topbar-user {
    cursor: pointer;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;

    &:hover {
      color: #667eea;
    }
  }
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
    color: #667eea;
  }
}

.lang-toggle {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

// 模块 Tab 行
.module-tabs-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
  overflow: hidden;
}

// Tab 行动画
.tabs-slide-enter-active,
.tabs-slide-leave-active {
  transition: all 0.3s ease;
  max-height: 60px;
  opacity: 1;
}

.tabs-slide-enter,
.tabs-slide-leave-to {
  max-height: 0;
  opacity: 0;
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.module-tabs {
  display: flex;
  gap: 4px;
  background: var(--card-bg-color);
  border-radius: 10px;
  padding: 4px;
  border: 1px solid var(--border-color);
}

.module-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all 0.2s;

  &:hover {
    background: var(--bg-secondary);
    color: var(--text-color);
  }

  &.active {
    background: #667eea;
    color: #fff;
    font-weight: 500;
  }

  i {
    font-size: 16px;
  }
}

.module-tabs-actions {
  flex-shrink: 0;
}

@media screen and (max-width: 768px) {
  .workspace-layout {
    padding: 8px;

    &.topbar-collapsed {
      padding-top: 20px;
    }
  }

  .topbar-toggle-btn {
    top: 6px;
    width: 20px;
    height: 18px;

    i {
      font-size: 11px;
    }
  }

  .workspace-topbar {
    .topbar-logo-text {
      display: none;
    }
  }
  .module-tabs-row {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .module-tabs {
    overflow-x: auto;
    flex-wrap: nowrap;
  }
  .module-tab {
    white-space: nowrap;
    padding: 6px 10px;
    font-size: 13px;
  }
}
</style>
