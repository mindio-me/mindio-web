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
  $axios.onError((error) => {
    const code = parseInt(error.response && error.response.status)
    const message = error.response?.data?.message || '请求失败'

    if (code === 401) {
      // 未授权，跳转到登录页
      app.$auth.logout()
      redirect('/login')
      app.$message.error('登录已过期，请重新登录')
    } else if (code === 403) {
      app.$message.error('无权限访问')
    } else if (code === 404) {
      app.$message.error('资源不存在')
    } else if (code === 500) {
      app.$message.error('服务器错误')
    } else {
      app.$message.error(message)
    }

    return Promise.reject(error)
  })

  // 响应成功拦截
  $axios.onResponse((response) => {
    return response
  })
}
