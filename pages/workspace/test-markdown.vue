<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div style="padding: 20px;">
    <h1>Markdown 渲染测试</h1>
    
    <h2>原始 Markdown</h2>
    <pre style="background: #f5f5f5; padding: 10px;">{{ testMarkdown }}</pre>
    
    <h2>渲染后的 HTML</h2>
    <div style="border: 1px solid #ddd; padding: 10px; margin: 10px 0;" v-html="renderedHtml"></div>
    
    <h2>HTML 源代码</h2>
    <pre style="background: #f5f5f5; padding: 10px;">{{ renderedHtml }}</pre>
  </div>
</template>

<script>
export default {
  data() {
    return {
      testMarkdown: '![test image](http://localhost:8080/api/uploads/worknotesimage/public/feishu_image//2026/02/08/559d3fc50c6c442aa0b0fa5983dd69aayfhSp7H0Fu.png)\n\nThis is a test paragraph.'
    }
  },
  
  computed: {
    renderedHtml() {
      return this.renderMarkdown(this.testMarkdown)
    }
  },
  
  methods: {
    renderMarkdown(markdown) {
      if (!markdown) return ''

      // 简单的 Markdown 渲染（生产环境建议使用 marked 库）
      let html = markdown
        // 代码块
        .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
        // 行内代码
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        // 标题
        .replace(/^### (.*$)/gm, '<h3>$1</h3>')
        .replace(/^## (.*$)/gm, '<h2>$1</h2>')
        .replace(/^# (.*$)/gm, '<h1>$1</h1>')
        // 粗体
        .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
        // 斜体
        .replace(/\*([^*]+)\*/g, '<em>$1</em>')
        // 图片（必须在链接之前处理，因为图片语法是 ![alt](url)）
        .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" style="max-width: 100%; height: auto; border-radius: 6px;" />')
        // 链接
        .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
        // 换行
        .replace(/\n/g, '<br>')

      return html
    }
  }
}
</script>
