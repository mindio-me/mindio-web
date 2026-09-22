/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default {
  // Global page headers
  head: {
    title: 'MindIO',
    htmlAttrs: {
      lang: 'zh-CN'
    },
    meta: [
      { charset: 'utf-8' },
      { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      { hid: 'description', name: 'description', content: 'MindIO is a personal workspace for turning input into output.' }
    ],
    link: [
      { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' }
    ],
    script: [
      {
        innerHTML: `(function(){try{var t=localStorage.getItem('worknotes-theme');var h=new Date().getHours();var isDark=t?t==='dark':(h>=18||h<6);if(isDark)document.documentElement.classList.add('theme-dark')}catch(e){}})()`,
        type: 'text/javascript'
      }
    ],
    __dangerouslyDisableSanitizers: ['script']
  },

  // Global CSS
  css: [
    'element-ui/lib/theme-chalk/index.css',
    '~/assets/styles/main.scss'
  ],

  // Plugins to run before rendering page
  plugins: [
    '@/plugins/element-ui',
    '@/plugins/axios',
    '@/plugins/services',
    '@/plugins/theme',
    { src: '@/plugins/mermaid-directive', mode: 'client' },
    '@/plugins/device'
  ],

  // Auto import components
  components: true,

  // Modules for dev and build
  buildModules: [],

  // Modules
  modules: [
    '@nuxtjs/axios',
    '@nuxtjs/auth-next',
    '@nuxtjs/i18n'
  ],

  // i18n configuration
  i18n: {
    locales: [
      { code: 'zh-CN', name: '中文', file: 'zh-CN.json' },
      { code: 'en', name: 'English', file: 'en.json' }
    ],
    defaultLocale: 'zh-CN',
    strategy: 'no_prefix',
    langDir: 'locales/',
    lazy: true,
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'worknotes-locale',
      alwaysRedirect: false,
      fallbackLocale: 'zh-CN'
    },
    vueI18n: {
      fallbackLocale: 'zh-CN'
    }
  },

  // Axios module configuration
  axios: {
    baseURL: process.env.API_BASE_URL || 'http://localhost:8080/api',
    proxy: false
  },

  // Auth module configuration
  auth: {
    strategies: {
      local: {
        token: {
          property: 'token',
          global: true,
          type: 'Bearer'
        },
        user: {
          property: false,
          autoFetch: true
        },
        endpoints: {
          login: { url: '/v1/auth/login', method: 'post' },
          logout: { url: '/v1/auth/logout', method: 'post' },
          user: { url: '/v1/auth/me', method: 'get' }
        }
      }
    },
    redirect: {
      login: '/login',
      logout: '/',
      callback: '/login',
      home: '/workspace'
    }
  },

  // Server configuration
  server: {
    port: 10822,
    host: '0.0.0.0'
  },

  // SSR 渲染配置
  render: {
    bundleRenderer: {
      // Nuxt 开发模式默认给每次 SSR 渲染都建一个全新的 vm 沙箱（vue-server-renderer 的
      // runInNewContext:true），这个沙箱只手动塞了 Buffer/console/process/setTimeout 等
      // 寥寥几个全局对象，没有 atob 之类 Node 16+ 才补齐的全局。markdown-it 的 HTML 实体
      // 解码表在模块加载时就会立刻调用 atob() 解码，一旦有任何走 SSR 的组件 import 了
      // utils/markdown.js（比如 ChatPanel.vue），开发模式下每次渲染都会 ReferenceError:
      // atob is not defined。关掉沙箱后走真实 Node 全局（本来生产模式默认就是这样），
      // 开发/生产行为保持一致。
      runInNewContext: false
    }
  },

  // Build Configuration
  build: {
    babel: {
      compact: false
    },
    transpile: [
      /^element-ui/,
      /^@editorjs/,
      /^editorjs-undo/,
      /^mermaid/,
      /markdown-it/ // 不能加 ^ 锚点：markdown-it 的嵌套依赖 entities 用了 webpack 4 解析不了的数字分隔符语法，
                     // 这条要同时匹配 node_modules/markdown-it/... 和 node_modules/markdown-it/node_modules/entities/...，
                     // 而 Nuxt 判断是否转译时用的路径永远带开头分隔符，^ 锚定的正则在这里永远匹配不上
    ],
    loaders: {
      scss: {
        sassOptions: {
          silenceDeprecations: ['legacy-js-api']
        }
      }
    },
    extend(config, { isClient, isServer }) {
      // Fix mermaid's uuid import issue with Webpack 4
      config.resolve = config.resolve || {}
      config.resolve.alias = config.resolve.alias || {}
      config.resolve.alias.uuid = require.resolve('uuid')
      // Handle .mjs files from @editorjs and mermaid packages with babel-loader
      config.module.rules.push({
        test: /\.mjs$/,
        include: /node_modules[\\/](@editorjs|mermaid)/,
        type: 'javascript/auto',
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              ['@nuxt/babel-preset-app', {
                corejs: { version: 3 }
              }]
            ]
          }
        }
      })

      // Handle .js files from mermaid (uses modern JS syntax like ??)
      config.module.rules.push({
        test: /\.js$/,
        include: /node_modules[\\/]mermaid/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              ['@nuxt/babel-preset-app', {
                corejs: { version: 3 }
              }]
            ]
          }
        }
      })

      // Also handle .mjs files in general node_modules
      config.module.rules.push({
        test: /\.mjs$/,
        include: /node_modules/,
        exclude: /node_modules[\\/](@editorjs|mermaid)/,
        type: 'javascript/auto'
      })

      // Ensure @editorjs packages are processed by babel-loader
      if (config.module && config.module.rules) {
        // Find the main JS babel rule
        const jsRuleIndex = config.module.rules.findIndex(rule => {
          if (rule.test && rule.test.toString().includes('js') && !rule.test.toString().includes('mjs')) {
            return rule.use && rule.use.some(use => {
              const loader = typeof use === 'string' ? use : (use.loader || '')
              return loader.includes('babel')
            })
          }
          return false
        })

        if (jsRuleIndex >= 0) {
          const jsRule = config.module.rules[jsRuleIndex]
          // Modify exclude to include @editorjs packages
          const originalExclude = jsRule.exclude
          jsRule.exclude = (filePath) => {
            // Don't exclude @editorjs and mermaid packages (they need babel processing)
            if (/node_modules[\\/](@editorjs|mermaid)/.test(filePath)) {
              return false
            }
            // Apply original exclude logic
            if (typeof originalExclude === 'function') {
              return originalExclude(filePath)
            } else if (originalExclude instanceof RegExp) {
              return originalExclude.test(filePath)
            } else if (originalExclude) {
              return originalExclude
            }
            // Default: exclude node_modules except @editorjs and mermaid
            return /node_modules/.test(filePath) && !/node_modules[\\/](@editorjs|mermaid)/.test(filePath)
          }
        }
      }
    }
  },

  // Router configuration
  router: {
    middleware: ['auth']
  },

  // 禁用页面过渡动画，防止深色模式下闪白
  pageTransition: {
    name: '',
    mode: ''
  },
  layoutTransition: {
    name: '',
    mode: ''
  }
}
