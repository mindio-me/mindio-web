/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default function ({ app }) {
  if (process.server) return;

  const STORAGE_KEY = 'worknotes-theme';
  const root = document.documentElement;

  // 根据当前时间判断是否为夜间（18:00-6:00）
  const isNightTime = () => {
    const hour = new Date().getHours();
    return hour >= 18 || hour < 6;
  };

  // 读取本地存储中的主题
  const saved = window.localStorage.getItem(STORAGE_KEY);

  // 如果用户未设置过主题，根据时间自动判断
  let theme;
  if (saved) {
    theme = saved === 'dark' ? 'dark' : 'light';
  } else {
    theme = isNightTime() ? 'dark' : 'light';
  }

  if (theme === 'dark') {
    root.classList.add('theme-dark');
  } else {
    root.classList.remove('theme-dark');
  }

  // 暴露一个简单的切换方法到 app 上，页面可以通过 this.$themeToggle() 调用
  app.themeToggle = () => {
    const isDark = root.classList.toggle('theme-dark');
    window.localStorage.setItem(STORAGE_KEY, isDark ? 'dark' : 'light');
    return isDark;
  };
}


