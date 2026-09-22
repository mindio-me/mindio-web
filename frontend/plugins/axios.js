/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default function ({ $axios, redirect, app }) {
  // 请求拦截器
  $axios.onRequest((config) => {
    // 添加 JWT token 到请求头
    // 注意：@nuxtjs/auth-next 可能已经设置了 Authorization，这里只做兜底且避免重复 Bearer
    if (app.$auth && app.$auth.loggedIn) {
      const token = app.$auth.strategy.token.get()
      if (token) {
        const normalized = token.startsWith('Bearer ') ? token : `Bearer ${token}`
        config.headers.common['Authorization'] = normalized
      }
    }
    return config
  })

  // 响应拦截器
  $axios.onError(async (error) => {
    // suppressErrorToast 只应该压掉"弹错误提示"这一个副作用，401 的登出/换token/跳转
    // 逻辑必须照常执行——不然一个后台静默请求过期时，整条会话恢复链路会被连带吞掉。
    const suppressToast = Boolean(error.config && error.config.suppressErrorToast)
    const code = parseInt(error.response && error.response.status)
    const message = error.response?.data?.message || '请求失败'

    if (code === 401) {
      if (app.$license && app.$license.isDesktop) {
        // 桌面版没有用户名密码登录页：许可证仍有效时静默换新 token，
        // 只有换取失败（许可证已失效）才转到邮箱激活页，绝不弹 /login
        try {
          const { data } = await $axios.post('/v1/auth/desktop-session')
          app.$auth.setUserToken(data.token)
          await app.$auth.fetchUser()
        } catch (_sessionError) {
          app.$auth.logout()
          redirect('/activate')
        }
      } else {
        // 未授权，跳转到登录页
        app.$auth.logout()
        redirect('/login')
        app.$message.error('登录已过期，请重新登录')
      }
    } else if (!suppressToast) {
      if (code === 403) {
        app.$message.error('无权限访问')
      } else if (code === 404) {
        app.$message.error('资源不存在')
      } else if (code === 500) {
        app.$message.error('服务器错误')
      } else {
        app.$message.error(message)
      }
    }

    return Promise.reject(error)
  })

  // 响应成功拦截
  $axios.onResponse((response) => {
    return response
  })
}
