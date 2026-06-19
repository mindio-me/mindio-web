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
      { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' }
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
      logout: '/login',
      callback: '/login',
      home: '/workspace'
    }
  },

  // Server configuration
  server: {
    port: 10822,
    host: '0.0.0.0'
  },

  // Build Configuration
  build: {
    babel: {
      compact: false
    },
    transpile: [
      /^element-ui/,
      /^@editorjs/,
      /^mermaid/
    ],
    loaders: {
      scss: {
        sassOptions: {
          silenceDeprecations: ['legacy-js-api']
        }
      }
    },
    extend(config, { isClient, isServer }) {
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
