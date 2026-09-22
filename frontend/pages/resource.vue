<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="resources-page">
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
          <nuxt-link to="/" class="nav-link" exact-active-class="active" exact>Home</nuxt-link>
          <nuxt-link to="/notes" class="nav-link" exact-active-class="active">{{ $t('site.nav.blog') }}</nuxt-link>
          <nuxt-link to="/projects" class="nav-link" exact-active-class="active">Projects</nuxt-link>
          <nuxt-link to="/contact" class="nav-link" exact-active-class="active">Contact</nuxt-link>
        </nav>
        <div class="header-right">
          <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? '切换到白天模式' : '切换到黑夜模式'">
            <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
          </button>
          <div v-if="$auth && $auth.loggedIn" class="user-profile">
            <img src="/default_user.png" alt="User" class="user-avatar" />
          </div>
          <button v-if="$auth && $auth.loggedIn" class="logout-btn" @click="handleLogout">Logout</button>
        </div>
      </div>
    </header>

    <!-- Hero Section -->
    <!-- <section class="hero-section">
      <div class="hero-container">
        <h1 class="hero-title">Resources</h1>
        <p class="hero-subtitle">
          Curated tools, libraries, and resources I use and recommend for modern web development.
        </p>
      </div>
    </section> -->

    <!-- Search and Filter Section -->
    <section class="search-filter-section">
      <div class="search-filter-container">
        <!-- Search Box -->
        <div class="search-box-wrapper">
          <div class="search-box">
            <i class="el-icon-search search-icon"></i>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索资源名称、描述或标签..."
              class="search-input"
            />
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="clear-search"
              title="清除搜索"
            >
              <i class="el-icon-close"></i>
            </button>
          </div>
        </div>

        <!-- Category Filter -->
        <div class="category-filter-wrapper">
          <div class="category-filter">
            <button
              v-for="category in availableCategories"
              :key="category.value"
              @click="selectedCategory = category.value"
              :class="['category-btn', { active: selectedCategory === category.value }]"
            >
              {{ category.label }}
            </button>
          </div>
        </div>

        <!-- Results Count -->
        <div class="results-info">
          <span class="results-count">显示 {{ filteredResources.length }} / {{ allResources.length }} 个资源</span>
        </div>
      </div>
    </section>

    <!-- Unified Resources Grid -->
    <section class="resources-section">
      <div class="resources-container">
        <div v-if="loading" class="loading-state">
          <i class="el-icon-loading"></i>
          <span>加载中...</span>
        </div>
        <div v-else-if="filteredResources.length === 0" class="empty-state">
          <i class="el-icon-search"></i>
          <p>没有找到匹配的资源</p>
          <button @click="clearFilters" class="clear-filters-btn">清除筛选</button>
        </div>
        <div v-else class="resources-grid">
          <a
            v-for="resource in filteredResources"
            :key="resource.id || resource.name"
            :href="resource.url"
            target="_blank"
            class="resource-card"
            :title="resource.description"
          >
            <div class="resource-icon">
              <i :class="resource.icon || 'el-icon-link'"></i>
            </div>
            <h3 class="resource-title">{{ resource.name }}</h3>
            <!-- <div class="resource-tooltip">{{ resource.description }}</div> -->
          </a>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="page-footer">
      <div class="footer-container">
        <div class="footer-left">
          <p>© 2024 Developer's Name. All rights reserved.</p>
        </div>
        <div class="footer-right">
          <a href="#" class="footer-link">
            <i class="el-icon-s-promotion"></i>
          </a>
          <a href="#" class="footer-link">
            <i class="el-icon-link"></i>
          </a>
          <a href="#" class="footer-link">
            <i class="el-icon-chat-dot-round"></i>
          </a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script>
export default {
  name: 'ResourcesPage',
  layout: 'blank',
  auth: false,
  async fetch({ store }) {
    store.commit("isHeader", false);
    store.commit("isFooter", false);
  },
  data() {
    return {
      isDarkTheme: false,
      resources: [],
      loading: false,
      searchQuery: '',
      selectedCategory: 'all',
      // 本地 Mock 数据（AI Tools）
      aiTools: [
        { name: 'Figma AI', url: 'https://www.figma.com/ai/', icon: 'el-icon-picture-outline', description: 'AI-powered design features in Figma for auto-layout, content generation, and smart suggestions.', category: 'ai-tools' },
        { name: 'Visily', url: 'https://visily.ai/', icon: 'el-icon-brush', description: 'AI-powered wireframe and prototype tool. Transform screenshots into editable designs instantly.', category: 'ai-tools' },
        { name: 'Gemini', url: 'https://gemini.google.com/', icon: 'el-icon-star-off', description: 'Google\'s multimodal AI assistant for text, image, and code generation with advanced reasoning.', category: 'ai-tools' },
        { name: 'Cursor', url: 'https://www.cursor.com/', icon: 'el-icon-edit-outline', description: 'AI-first code editor built for pair programming with AI. Write, edit, and debug code faster.', category: 'ai-tools' },
        { name: 'Claude', url: 'https://claude.ai/', icon: 'el-icon-chat-dot-round', description: 'Anthropic\'s AI assistant excelling at analysis, writing, coding, and thoughtful conversations.', category: 'ai-tools' },
        { name: 'GitHub Copilot', url: 'https://github.com/features/copilot', icon: 'el-icon-coin', description: 'AI pair programmer that suggests code completions and entire functions in your IDE.', category: 'ai-tools' },
        { name: 'ChatGPT', url: 'https://chat.openai.com/', icon: 'el-icon-chat-line-round', description: 'OpenAI\'s conversational AI for writing, analysis, coding, and creative tasks.', category: 'ai-tools' },
        { name: 'Midjourney', url: 'https://www.midjourney.com/', icon: 'el-icon-picture', description: 'AI art generator creating stunning images from text prompts. Best for artistic visuals.', category: 'ai-tools' },
        { name: 'Stable Diffusion', url: 'https://stability.ai/', icon: 'el-icon-camera', description: 'Open-source AI image generation model. Run locally or via API for creative control.', category: 'ai-tools' },
        { name: 'Adobe Firefly', url: 'https://www.adobe.com/products/firefly.html', icon: 'el-icon-magic-stick', description: 'Adobe\'s generative AI for creative workflows. Safe for commercial use with content credentials.', category: 'ai-tools' },
        { name: 'Runway', url: 'https://www.runway.ml/', icon: 'el-icon-video-camera', description: 'AI video generation and editing platform. Create videos from text, images, or other videos.', category: 'ai-tools' },
        { name: 'Perplexity', url: 'https://www.perplexity.ai/', icon: 'el-icon-search', description: 'AI-powered search engine providing answers with citations from real-time web sources.', category: 'ai-tools' },
        { name: 'v0 by Vercel', url: 'https://v0.dev/', icon: 'el-icon-cpu', description: 'AI UI generator that creates React components from text descriptions using shadcn/ui.', category: 'ai-tools' },
        { name: 'Framer AI', url: 'https://www.framer.com/ai', icon: 'el-icon-files', description: 'Generate and publish websites from text prompts. No-code website builder with AI.', category: 'ai-tools' },
        { name: 'Notion AI', url: 'https://www.notion.so/product/ai', icon: 'el-icon-notebook-2', description: 'AI writing assistant integrated into Notion for drafting, summarizing, and brainstorming.', category: 'ai-tools' },
        { name: 'Jasper', url: 'https://www.jasper.ai/', icon: 'el-icon-document', description: 'AI content platform for marketing teams. Generate blog posts, ads, and social media content.', category: 'ai-tools' },
        { name: 'Copy.ai', url: 'https://www.copy.ai/', icon: 'el-icon-edit', description: 'AI copywriting tool for marketing content, product descriptions, and sales emails.', category: 'ai-tools' },
        { name: 'Grammarly', url: 'https://grammarly.com/', icon: 'el-icon-check', description: 'AI writing assistant for grammar, spelling, tone, and clarity across all platforms.', category: 'ai-tools' },
        { name: 'Synthesia', url: 'https://www.synthesia.io/', icon: 'el-icon-video-play', description: 'Create AI videos with virtual avatars. Generate training videos without cameras or actors.', category: 'ai-tools' },
        { name: 'ElevenLabs', url: 'https://elevenlabs.io/', icon: 'el-icon-microphone', description: 'AI voice generation and cloning. Create realistic voiceovers in multiple languages.', category: 'ai-tools' },
        { name: 'Descript', url: 'https://www.descript.com/', icon: 'el-icon-headset', description: 'AI-powered audio/video editor. Edit media by editing text transcripts.', category: 'ai-tools' },
        { name: 'Remove.bg', url: 'https://www.remove.bg/', icon: 'el-icon-scissors', description: 'AI background removal tool. Remove image backgrounds instantly with one click.', category: 'ai-tools' },
        { name: 'Canva AI', url: 'https://www.canva.com/ai-image-generator/', icon: 'el-icon-picture-outline-round', description: 'AI features in Canva including Magic Write, image generation, and design suggestions.', category: 'ai-tools' },
        { name: 'Beautiful.ai', url: 'https://beautiful.ai/', icon: 'el-icon-data-line', description: 'AI presentation maker that automatically applies design rules to your slides.', category: 'ai-tools' },
        { name: 'Hugging Face', url: 'https://www.huggingface.co/', icon: 'el-icon-present', description: 'Open-source AI community and platform. Host models, datasets, and ML applications.', category: 'ai-tools' },
        { name: 'Replicate', url: 'https://replicate.com/', icon: 'el-icon-refresh', description: 'Run open-source AI models via API. Deploy custom models without infrastructure.', category: 'ai-tools' },
        { name: 'Anthropic', url: 'https://www.anthropic.com/', icon: 'el-icon-coordinate', description: 'AI safety company building Claude. Access APIs for enterprise AI applications.', category: 'ai-tools' },
        { name: 'Cohere', url: 'https://cohere.com/', icon: 'el-icon-connection', description: 'Enterprise AI platform for NLP. Build search, generation, and classification features.', category: 'ai-tools' },
        { name: 'LangChain', url: 'https://www.langchain.com/', icon: 'el-icon-link', description: 'Framework for building LLM applications. Chain prompts, tools, and memory together.', category: 'ai-tools' },
        { name: 'Databricks', url: 'https://www.databricks.com/', icon: 'el-icon-s-data', description: 'Data and AI platform for building, training, and deploying ML models at scale.', category: 'ai-tools' }
      ],
      // 静态资源数据（其他分类）
      staticResources: [
        { name: 'VS Code', url: 'https://code.visualstudio.com/', icon: 'el-icon-monitor', description: 'My primary code editor with extensions for Vue.js, ESLint, Prettier, and GitLens.', category: 'dev-tools' },
        { name: 'Docker', url: 'https://www.docker.com/', icon: 'el-icon-takeaway-box', description: 'Containerization platform for consistent development and deployment environments.', category: 'dev-tools' },
        { name: 'Postman', url: 'https://www.postman.com/', icon: 'el-icon-connection', description: 'API development and testing tool for building and documenting RESTful APIs.', category: 'dev-tools' },
        { name: 'Git', url: 'https://git-scm.com/', icon: 'el-icon-setting', description: 'Version control system for tracking changes and collaborating with teams.', category: 'dev-tools' },
        { name: 'Vue.js', url: 'https://vuejs.org/', icon: 'el-icon-cpu', description: 'Progressive JavaScript framework for building user interfaces and single-page applications.', category: 'frameworks' },
        { name: 'Node.js', url: 'https://nodejs.org/', icon: 'el-icon-coin', description: 'JavaScript runtime built on Chrome\'s V8 engine for building scalable backend services.', category: 'frameworks' },
        { name: 'Nuxt.js', url: 'https://nuxtjs.org/', icon: 'el-icon-files', description: 'Vue.js framework for server-side rendering, static site generation, and more.', category: 'frameworks' },
        { name: 'Tailwind CSS', url: 'https://tailwindcss.com/', icon: 'el-icon-brush', description: 'Utility-first CSS framework for rapidly building custom user interfaces.', category: 'frameworks' },
        { name: 'MDN Web Docs', url: 'https://developer.mozilla.org/', icon: 'el-icon-reading', description: 'Comprehensive documentation for web technologies including HTML, CSS, and JavaScript.', category: 'learning' },
        { name: 'Vue Mastery', url: 'https://www.vuemastery.com/', icon: 'el-icon-video-camera', description: 'Premium video courses and tutorials for mastering Vue.js and the Vue ecosystem.', category: 'learning' },
        { name: 'freeCodeCamp', url: 'https://www.freecodecamp.org/', icon: 'el-icon-notebook-1', description: 'Free coding bootcamp with thousands of hours of content on web development.', category: 'learning' },
        { name: 'Awesome Vue', url: 'https://github.com/vuejs/awesome-vue', icon: 'el-icon-collection', description: 'Curated list of awesome things related to Vue.js - libraries, tools, and resources.', category: 'learning' },
        { name: 'Figma', url: 'https://www.figma.com/', icon: 'el-icon-picture', description: 'Collaborative interface design tool for creating UI/UX designs and prototypes.', category: 'design' },
        { name: 'Unsplash', url: 'https://unsplash.com/', icon: 'el-icon-picture-outline', description: 'Free high-resolution photos for commercial and personal use.', category: 'design' },
        { name: 'Dribbble', url: 'https://dribbble.com/', icon: 'el-icon-medal', description: 'Design inspiration platform showcasing creative work from designers worldwide.', category: 'design' },
        { name: 'Coolors', url: 'https://coolors.co/', icon: 'el-icon-magic-stick', description: 'Fast color scheme generator for creating beautiful color palettes.', category: 'design' }
      ]
    }
  },

  computed: {
    // 合并所有资源（API + 本地数据）
    allResources() {
      const apiResources = this.resources.map(r => ({
        id: r.id,
        name: r.name,
        url: r.url,
        icon: r.icon,
        description: r.description,
        category: r.category || 'other',
        tags: r.tags || ''
      }));

      // const localResources = [
      //   ...this.aiTools.map(t => ({ ...t, id: `local-ai-${t.name}` })),
      //   ...this.staticResources.map(r => ({ ...r, id: `local-${r.category}-${r.name}` }))
      // ];

      return [...apiResources];
    },

    // 提取所有可用分类
    availableCategories() {
      const categories = new Set();
      this.allResources.forEach(r => {
        if (r.category) {
          categories.add(r.category);
        }
      });

      const categoryLabels = {
        'all': '全部',
        'ai-tools': 'AI 工具',
        'dev-tools': '开发工具',
        'frameworks': '框架库',
        'learning': '学习资源',
        'design': '设计工具',
        'other': '其他'
      };

      const categoryList = [
        { value: 'all', label: categoryLabels['all'] || '全部' }
      ];

      Array.from(categories).sort().forEach(cat => {
        categoryList.push({
          value: cat,
          label: categoryLabels[cat] || cat
        });
      });

      return categoryList;
    },

    // 过滤后的资源
    filteredResources() {
      let filtered = this.allResources;

      // 分类筛选
      if (this.selectedCategory !== 'all') {
        filtered = filtered.filter(r => r.category === this.selectedCategory);
      }

      // 搜索筛选
      if (this.searchQuery.trim()) {
        const query = this.searchQuery.toLowerCase().trim();
        filtered = filtered.filter(r => {
          const nameMatch = r.name.toLowerCase().includes(query);
          const descMatch = (r.description || '').toLowerCase().includes(query);
          const tagsMatch = (r.tags || '').toLowerCase().includes(query);
          return nameMatch || descMatch || tagsMatch;
        });
      }

      // 按显示顺序排序（如果有）
      return filtered.sort((a, b) => {
        if (a.displayOrder !== undefined && b.displayOrder !== undefined) {
          return a.displayOrder - b.displayOrder;
        }
        return a.name.localeCompare(b.name);
      });
    }
  },

  async mounted() {
    if (process.client) {
      const root = document.documentElement;
      this.isDarkTheme = root.classList.contains('theme-dark');
    }
    await this.loadResources();
  },

  methods: {
    async loadResources() {
      this.loading = true;
      try {
        this.resources = await this.$resourceService.getAllResources();
        console.log('Resources loaded:', this.resources);
      } catch (error) {
        console.error('Error loading resources:', error);
        // 如果 API 失败，继续使用本地数据
        this.resources = [];
      } finally {
        this.loading = false;
      }
    },

    clearFilters() {
      this.searchQuery = '';
      this.selectedCategory = 'all';
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
    }
  }
}
</script>

<style lang="scss" scoped>
.resources-page {
  min-height: 100vh;
  background: var(--bg-color);
  display: flex;
  flex-direction: column;
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

// Hero Section
.hero-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 60px 0 40px;
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
  color: white;
  margin: 0 0 16px 0;
}

.hero-subtitle {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
  margin: 0;
}

// Search and Filter Section
.search-filter-section {
  background: var(--bg-color);
  padding: 32px 0 20px;
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 64px;
  z-index: 90;
  backdrop-filter: blur(10px);
}

.search-filter-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 40px;
}

.search-box-wrapper {
  margin-bottom: 20px;
}

.search-box {
  position: relative;
  max-width: 600px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  background: var(--card-bg-color);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  padding: 0 16px;
  transition: all 0.2s;

  &:focus-within {
    border-color: #667eea;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  }
}

.search-icon {
  font-size: 20px;
  color: var(--text-muted);
  margin-right: 12px;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: var(--text-color);
  padding: 12px 0;
  width: 100%;

  &::placeholder {
    color: var(--text-muted);
  }
}

.clear-search {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: color 0.2s;
  margin-left: 8px;

  &:hover {
    color: var(--text-color);
  }

  i {
    font-size: 16px;
  }
}

.category-filter-wrapper {
  margin-bottom: 16px;
}

.category-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.category-btn {
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  background: var(--card-bg-color);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    border-color: #667eea;
    color: #667eea;
    background: rgba(102, 126, 234, 0.05);
  }

  &.active {
    background: #667eea;
    border-color: #667eea;
    color: white;
  }
}

.results-info {
  text-align: center;
  margin-top: 12px;
}

.results-count {
  font-size: 13px;
  color: var(--text-muted);
}

// Resources Section
.resources-section {
  background: var(--bg-color);
  padding: 20px 0 80px;
  flex: 1;
}

.resources-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 40px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-muted);

  i {
    font-size: 48px;
    margin-bottom: 16px;
    display: block;
    color: var(--text-muted);
  }

  span,
  p {
    font-size: 16px;
    margin-bottom: 16px;
  }
}

.clear-filters-btn {
  padding: 10px 20px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #5568d3;
  }
}

// Compact Grid Layout
.resources-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 12px;
}

.resource-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px 12px;
  text-align: center;
  text-decoration: none;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
  cursor: pointer;

  &:hover {
    border-color: #667eea;
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);

    .resource-icon {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      transform: scale(1.05);

      i {
        color: white;
      }
    }

    .resource-tooltip {
      opacity: 1;
      visibility: visible;
      transform: translateX(-50%) translateY(0);
    }
  }
}

.resource-icon {
  width: 40px;
  height: 40px;
  background: var(--tag-bg);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;

  i {
    font-size: 20px;
    color: #667eea;
    transition: all 0.2s ease;
  }
}

.resource-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
  line-height: 1.3;
  text-align: center;
  word-break: break-word;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.resource-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  background: #1a202c;
  color: white;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
  width: 200px;
  text-align: left;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  pointer-events: none;

  &::after {
    content: '';
    position: absolute;
    top: 100%;
    left: 50%;
    transform: translateX(-50%);
    border: 5px solid transparent;
    border-top-color: #1a202c;
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

// Responsive Design
@media screen and (max-width: 1400px) {
  .resources-grid {
    grid-template-columns: repeat(8, 1fr);
  }
}

@media screen and (max-width: 1200px) {
  .resources-grid {
    grid-template-columns: repeat(6, 1fr);
  }
}

@media screen and (max-width: 1024px) {
  .header-nav {
    gap: 24px;
  }

  .hero-title {
    font-size: 36px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .search-filter-container {
    padding: 0 24px;
  }

  .resources-container {
    padding: 0 24px;
  }

  .resources-grid {
    grid-template-columns: repeat(5, 1fr);
    gap: 10px;
  }

  .resource-card {
    padding: 14px 10px;
  }

  .resource-icon {
    width: 36px;
    height: 36px;

    i {
      font-size: 18px;
    }
  }

  .resource-title {
    font-size: 12px;
  }
}

@media screen and (max-width: 768px) {
  .header-nav {
    display: none;
  }

  .hero-section {
    padding: 40px 0 30px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-subtitle {
    font-size: 14px;
  }

  .search-filter-section {
    padding: 24px 0;
    top: 64px;
  }

  .search-filter-container {
    padding: 0 20px;
  }

  .search-box {
    padding: 0 12px;
  }

  .search-input {
    font-size: 14px;
    padding: 10px 0;
  }

  .category-filter {
    gap: 6px;
  }

  .category-btn {
    padding: 6px 12px;
    font-size: 13px;
  }

  .resources-section {
    padding: 30px 0 60px;
  }

  .resources-container {
    padding: 0 20px;
  }

  .resources-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }

  .resource-card {
    padding: 12px 8px;
    gap: 6px;
  }

  .resource-icon {
    width: 32px;
    height: 32px;

    i {
      font-size: 16px;
    }
  }

  .resource-title {
    font-size: 11px;
    -webkit-line-clamp: 2;
    line-clamp: 2;
  }

  .resource-tooltip {
    width: 160px;
    font-size: 11px;
    padding: 6px 10px;
  }

  .footer-container {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }
}

@media screen and (max-width: 480px) {
  .resources-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
  }

  .resource-card {
    padding: 10px 6px;
  }

  .resource-icon {
    width: 28px;
    height: 28px;

    i {
      font-size: 14px;
    }
  }

  .resource-title {
    font-size: 10px;
  }
}
</style>
