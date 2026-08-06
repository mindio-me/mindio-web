/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Vue from 'vue'
import Element from 'element-ui'
import zhCN from 'element-ui/lib/locale/lang/zh-CN'
import en from 'element-ui/lib/locale/lang/en'

const elLocaleMap = { 'zh-CN': zhCN, 'en': en }
const validLocales = ['zh-CN', 'en']

export default async ({ app }) => {
  // 迁移：如果 Cookie 尚未设置，从 localStorage 读取历史偏好并触发一次 setLocale
  // setLocale 会同时写入 Cookie（@nuxtjs/i18n detectBrowserLanguage.useCookie 负责）
  if (process.client) {
    const cookieLocale = document.cookie.split(';')
      .find(c => c.trim().startsWith('worknotes-locale='))
      ?.split('=')[1]?.trim()
    if (!cookieLocale) {
      const stored = localStorage.getItem('worknotes-locale')
      if (stored && validLocales.includes(stored) && stored !== app.i18n.locale) {
        await app.i18n.setLocale(stored)
      }
    }
  }

  Vue.use(Element, { locale: elLocaleMap[app.i18n.locale] || zhCN })

  app.i18n.onLanguageSwitched = (_old, newLocale) => {
    Element.locale(elLocaleMap[newLocale] || zhCN)
  }
}
