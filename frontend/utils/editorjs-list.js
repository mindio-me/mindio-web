/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * EditorJS 列表渲染工具
 * 支持 @editorjs/list 2.0 的嵌套结构
 */

/**
 * 解析 EditorJS 列表项
 * 兼容旧格式（字符串数组）和新格式（对象数组）
 * @param {Array} items - 列表项数组
 * @returns {Array} 标准化的列表项数组
 */
export function parseListItems(items) {
  if (!items || !Array.isArray(items)) return []

  return items.map(item => {
    // 兼容旧格式：字符串
    if (typeof item === 'string') {
      return { content: item, items: [], meta: {} }
    }
    // 新格式：对象
    return {
      content: item.content || '',
      items: parseListItems(item.items), // 递归解析嵌套项
      meta: item.meta || {}
    }
  })
}

/**
 * 检查列表是否有嵌套项
 * @param {Array} items - 列表项数组
 * @returns {boolean}
 */
export function hasNestedItems(items) {
  if (!items || !Array.isArray(items)) return false
  return items.some(item => item.items && item.items.length > 0)
}
