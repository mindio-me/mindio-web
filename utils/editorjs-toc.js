/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/**
 * EditorJS 目录生成工具
 * 支持智能层级调整：当最大级别标题只有 1 个时，将其视为文档标题并跳过
 */

/**
 * 从 EditorJS 内容解析标题列表
 * @param {string} content - EditorJS JSON 字符串
 * @returns {Array} 原始标题数组
 */
export function parseEditorJSHeaders(content) {
  if (!content) return []

  try {
    const editorData = JSON.parse(content)
    if (!editorData.blocks || !Array.isArray(editorData.blocks)) {
      return []
    }

    return editorData.blocks
      .map((block, index) => ({
        index, // 块索引，用于滚动定位
        type: block.type,
        text: block.data?.text || '',
        level: block.data?.level || 2
      }))
      .filter(item => item.type === 'header')
  } catch (e) {
    console.error('[editorjs-toc] 解析内容失败:', e)
    return []
  }
}

/**
 * 分析标题层级结构，决定是否需要调整
 * @param {Array} headers - 原始标题数组
 * @returns {Object} 调整策略
 */
export function analyzeLevelStructure(headers) {
  if (headers.length === 0) {
    return {
      needAdjust: false,
      levelOffset: 0,
      skipTopLevel: false,
      topLevelHeader: null
    }
  }

  // 找出最小的标题级别（数字越小表示标题越大）
  const minLevel = Math.min(...headers.map(h => h.level))

  // 统计最小级别标题的数量
  const minLevelHeaders = headers.filter(h => h.level === minLevel)

  // 关键判断：如果最大级别标题只有1个
  if (minLevelHeaders.length === 1) {
    // 获取其他级别的标题
    const otherHeaders = headers.filter(h => h.level !== minLevel)

    if (otherHeaders.length > 0) {
      // 找出第二小的级别作为新的顶级
      const secondMinLevel = Math.min(...otherHeaders.map(h => h.level))

      return {
        needAdjust: true,
        levelOffset: secondMinLevel - 1, // 调整偏移量，使第二级标题显示为 level-1
        skipTopLevel: true, // 跳过孤独的顶级标题
        topLevelHeader: minLevelHeaders[0] // 被跳过的标题信息
      }
    }
  }

  // 正常情况：计算基准偏移量使显示从 level 1 开始
  return {
    needAdjust: false,
    levelOffset: minLevel - 1,
    skipTopLevel: false,
    topLevelHeader: null
  }
}

/**
 * 生成带编号和调整后层级的目录数据
 * @param {Array} headers - 原始标题数组
 * @param {Object} options - 配置选项
 * @param {boolean} options.autoAdjust - 是否启用自动层级调整（默认 true）
 * @returns {Array} 处理后的目录数组
 */
export function generateTocData(headers, options = {}) {
  const { autoAdjust = true } = options

  if (headers.length === 0) return []

  // 分析层级结构
  const analysis = autoAdjust
    ? analyzeLevelStructure(headers)
    : { needAdjust: false, levelOffset: 0, skipTopLevel: false }

  // 过滤掉需要跳过的顶级标题
  let processedHeaders = headers
  if (analysis.skipTopLevel && analysis.topLevelHeader) {
    processedHeaders = headers.filter(h => h.index !== analysis.topLevelHeader.index)
  }

  // 计算显示层级和编号
  const counters = [0, 0, 0, 0] // 支持 4 级标题

  return processedHeaders.map(item => {
    // 计算调整后的显示层级
    const displayLevel = Math.max(1, item.level - analysis.levelOffset)

    // 更新计数器
    counters[displayLevel - 1]++

    // 重置更深层级的计数器
    for (let i = displayLevel; i < 4; i++) {
      counters[i] = 0
    }

    // 生成编号
    const number = counters.slice(0, displayLevel).join('.')

    return {
      ...item,
      displayLevel, // 用于 CSS 样式
      number, // 显示的编号
      originalLevel: item.level // 保留原始层级（可选，用于调试）
    }
  })
}

/**
 * 便捷函数：从 EditorJS 内容直接生成目录
 * @param {string} content - EditorJS JSON 字符串
 * @param {Object} options - 配置选项
 * @returns {Array} 目录数组
 */
export function createEditorJSToc(content, options = {}) {
  const headers = parseEditorJSHeaders(content)
  return generateTocData(headers, options)
}
