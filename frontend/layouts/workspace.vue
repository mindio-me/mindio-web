<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
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

    <!-- 顶部栏：logo + 模块导航 + 用户信息 -->
    <transition name="topbar-slide">
      <div v-show="!topbarCollapsed" class="workspace-topbar">
        <div class="topbar-left">
          <div class="topbar-logo" @click="$router.push('/')">
            <MindioLogo />
            <!-- <span class="topbar-logo-text">MindIO</span> -->
          </div>
          <template v-if="isAccountPage">
            <div class="module-tabs-back" @click="$router.push('/workspace/notes')">
              <i class="el-icon-back"></i>
              <span>{{ $t('topbar.backToWorkspace') }}</span>
            </div>
          </template>
          <template v-else>
            <div class="module-tabs">
              <div
                v-for="tab in visibleModuleTabs"
                :key="tab.key"
                class="module-tab"
                :class="{ active: activeModule === tab.key }"
                @click="switchModule(tab.key)"
              >
                <i :class="tab.icon"></i>
                <span>{{ tab.label }}</span>
              </div>
            </div>
            <el-button
              v-if="showCreateButton"
              type="primary"
              size="small"
              icon="el-icon-plus"
              circle
              class="module-tabs-create-btn"
              :disabled="desktopReadOnly"
              :title="createButtonTooltip"
              @click="handleCreate"
            ></el-button>
          </template>
        </div>
        <div class="topbar-right">
          <button class="theme-toggle" @click="$router.push('/')" :title="$t('topbar.home')">
            <i class="el-icon-s-home"></i>
          </button>
          <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? $t('topbar.lightMode') : $t('topbar.darkMode')">
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <button class="theme-toggle lang-toggle" @click="toggleLang" :title="$t('lang.toggle')">
            {{ $t('lang.toggle') }}
          </button>
          <button class="theme-toggle" @click="$nuxt.$emit('workspace:chat:toggle')" :title="$t('topbar.chatOpen')">
            <i class="el-icon-chat-dot-round"></i>
          </button>
          <button
            class="theme-toggle recording-mic-btn"
            :class="{ 'is-recording': recordingStatus !== 'idle' }"
            @click="onMicButtonClick"
            :title="recordingStatus !== 'idle' ? '录音进行中' : '开始录音'"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
              <path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3z"/>
              <path d="M19 11a7 7 0 0 1-14 0"/>
              <line x1="12" y1="19" x2="12" y2="22"/>
            </svg>
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

    <el-alert
      v-if="desktopReadOnly"
      class="desktop-readonly-alert"
      type="warning"
      :closable="false"
      show-icon
    >
      <template slot="title">
        桌面版当前为只读模式：可以读取和导出本地数据，但不能新增或编辑。请到"设置"里刷新或完成授权。
      </template>
    </el-alert>

    <!-- 页面内容：占满顶部栏之外的剩余空间，具体高度由页面自身用 height:100% 适配 -->
    <div class="workspace-content">
      <nuxt />
    </div>

    <GlobalChatDrawer />
    <RecordingCapsule />
  </div>
</template>

<script>
import recordingController from '~/utils/recordingController'

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
    recordingStatus() {
      return recordingController.state.status
    },
    moduleTabs() {
      return [
        { key: 'notes', label: this.$t('nav.notes'), icon: 'el-icon-notebook-2' },
        { key: 'clips', label: this.$t('nav.clips'), icon: 'el-icon-star-off' },
        { key: 'projects', label: this.$t('nav.projects'), icon: 'el-icon-folder-opened' },
        { key: 'achievements', label: this.$t('nav.achievements'), icon: 'el-icon-trophy' },
        { key: 'local-docs', label: this.$t('nav.localDocs'), icon: 'el-icon-files' },
        { key: 'local-media', label: this.$t('nav.localMedia'), icon: 'el-icon-picture' },
      ]
    },
    userName() {
      return this.$auth?.user?.username || 'User'
    },
    visibleModuleTabs() {
      return this.moduleTabs
    },
    activeModule() {
      const path = this.$route.path
      if (path.startsWith('/workspace/notes')) return 'notes'
      if (path.startsWith('/workspace/projects')) return 'projects'
      if (path.startsWith('/workspace/achievements')) return 'achievements'
      if (path.startsWith('/workspace/clips')) return 'clips'
      if (path.startsWith('/workspace/bookmark-import')) return 'clips'
      if (path.startsWith('/workspace/local-docs')) return 'local-docs'
      if (path.startsWith('/workspace/local-media')) return 'local-media'
      if (path.startsWith('/workspace/tags')) return 'tags'
      return 'notes'
    },
    isAccountPage() {
      const path = this.$route.path
      return path.startsWith('/workspace/profile') || path.startsWith('/workspace/settings')
    },
    isEditPage() {
      return this.$route.path.includes('/edit')
    },
    showCreateButton() {
      if (this.activeModule === 'notes' && this.isEditPage) return false
      return ['notes', 'projects', 'services', 'achievements', 'resources', 'clips', 'tags'].includes(this.activeModule)
    },
    createButtonTooltip() {
      const key = {
        notes: 'actions.newNote',
        projects: 'actions.newProject',
        services: 'actions.newService',
        achievements: 'actions.newAchievement',
        resources: 'actions.newResource',
        clips: 'actions.newClip',
        tags: 'actions.newTag'
      }[this.activeModule]
      return key ? this.$t(key) : ''
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
      this.$router.push(`/workspace/${module}`)
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
    },
    onMicButtonClick() {
      if (recordingController.state.status === 'idle') {
        // 失败提示统一由 RecordingCapsule 订阅 controller 的 'error' 事件弹出
        // （本布局没有自己的 toast），这里只负责不让 rejection 逃逸成未处理异常。
        recordingController.start().catch(() => {})
      }
    }
  }
}
</script>

<style scoped lang="scss">
.workspace-layout {
  height: 100vh;
  overflow: hidden;
  padding: 8px 12px;
  background: var(--bg-secondary);
  position: relative;
  transition: padding 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: 0;

  &.topbar-collapsed {
    padding-top: 14px; // 只保留切换按钮的少量空间
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
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  cursor: pointer;
  flex-shrink: 0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;

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

.recording-mic-btn.is-recording {
  color: #ef4444;
  &:hover {
    color: #ef4444;
    background: rgba(239, 68, 68, 0.08);
  }
}

// 模块 Tab 列表
.module-tabs {
  display: flex;
  gap: 4px;
  background: var(--card-bg-color);
  // border-radius: 10px;
  padding: 4px;
  // border: 1px solid var(--border-color);
  overflow-x: auto;
  flex-shrink: 1;
  min-width: 0;
  -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 20px), transparent 100%);
  mask-image: linear-gradient(to right, #000 calc(100% - 20px), transparent 100%);
}

.module-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
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

.module-tabs-create-btn {
  flex-shrink: 0;
}

.module-tabs-back {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: all 0.2s;
  flex-shrink: 0;

  &:hover {
    background: var(--bg-secondary);
    color: var(--text-color);
  }
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
  .topbar-left {
    gap: 8px;
  }
  .topbar-right {
    gap: 6px;
  }
  .lang-toggle {
    display: none;
  }
  .module-tabs {
    flex-wrap: nowrap;
  }
  .module-tab {
    white-space: nowrap;
    padding: 6px 10px;
    font-size: 13px;
  }
}
</style>
