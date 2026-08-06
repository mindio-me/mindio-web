/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export const NOTE_PRINT_CSS = `
* { -webkit-print-color-adjust: exact; print-color-adjust: exact; box-sizing: border-box; }

@page { margin: 20mm 18mm; size: A4; }

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  font-size: 15px;
  line-height: 1.8;
  color: #1a202c;
  background: #fff;
  margin: 0;
  padding: 0;
}

/* ── 笔记元信息头 ── */
.pdf-header-section {
  border-bottom: 2px solid #667eea;
  padding-bottom: 16px;
  margin-bottom: 28px;
}
.pdf-title {
  font-size: 26px;
  font-weight: 700;
  color: #1a202c;
  margin: 0 0 8px;
  line-height: 1.3;
}
.pdf-meta {
  font-size: 12px;
  color: #718096;
  margin-bottom: 6px;
}
.pdf-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.pdf-tag {
  display: inline-block;
  background: #f1f5f9;
  color: #475569;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

/* ── 段落 ── */
.pdf-paragraph { margin: 0.75em 0; line-height: 1.8; }

/* ── 标题 ── */
h1.pdf-header { font-size: 22px; font-weight: 700; margin: 1.4em 0 0.4em; page-break-after: avoid; }
h2.pdf-header { font-size: 18px; font-weight: 700; margin: 1.2em 0 0.4em; page-break-after: avoid; }
h3.pdf-header { font-size: 15px; font-weight: 600; margin: 1em 0 0.3em; page-break-after: avoid; }

/* ── 列表 ── */
.pdf-list { padding-left: 24px; margin: 0.75em 0; }
.pdf-list li { margin: 0.3em 0; line-height: 1.7; }
.pdf-list .pdf-list { margin: 0.2em 0; }

/* ── 代码块 ── */
.pdf-code {
  background: #f4f4f4;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 12px 16px;
  font-family: 'Fira Code', 'Monaco', Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  page-break-inside: avoid;
  color: #1a202c;
}
.pdf-code code { font-family: inherit; background: none; padding: 0; }

/* 内联代码 */
code, .inline-code {
  font-family: 'Fira Code', 'Monaco', Consolas, monospace;
  background: #f1f5f9;
  color: #e83e8c;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 0.9em;
}

/* ── 引用 ── */
.pdf-quote {
  border-left: 4px solid #667eea;
  padding: 10px 16px;
  margin: 1em 0;
  background: #f8fafc;
  border-radius: 0 4px 4px 0;
  page-break-inside: avoid;
}
.pdf-quote p { margin: 0 0 4px; color: #4a5568; font-style: italic; }
.pdf-quote cite { font-size: 0.85em; color: #718096; display: block; }

/* ── 表格 ── */
.pdf-table {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
  page-break-inside: avoid;
  font-size: 13px;
}
.pdf-table th, .pdf-table td {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}
.pdf-table th { background: #f1f5f9; font-weight: 600; }

/* ── 图片 ── */
.pdf-image {
  text-align: center;
  margin: 1em 0;
  page-break-inside: avoid;
}
.pdf-image img { max-width: 100%; height: auto; border-radius: 4px; }
.pdf-image figcaption { font-size: 12px; color: #718096; margin-top: 6px; }

/* ── 分割线 ── */
.pdf-delimiter { border: none; border-top: 2px solid #e2e8f0; margin: 1.5em 0; }

/* ── Markdown 块 ── */
.pdf-markdown h1, .pdf-markdown h2, .pdf-markdown h3, .pdf-markdown h4 {
  font-weight: 600; margin: 1em 0 0.4em; page-break-after: avoid;
}
.pdf-markdown h1 { font-size: 20px; }
.pdf-markdown h2 { font-size: 17px; }
.pdf-markdown h3 { font-size: 15px; }
.pdf-markdown p { margin: 0.75em 0; }
.pdf-markdown ul, .pdf-markdown ol { padding-left: 24px; margin: 0.75em 0; }
.pdf-markdown li { margin: 0.3em 0; }
.pdf-markdown blockquote {
  border-left: 4px solid #667eea;
  padding: 8px 14px;
  background: #f8fafc;
  margin: 0.8em 0;
}
.pdf-markdown pre.md-code-block {
  background: #f4f4f4 !important;
  color: #1a202c !important;
  border: 1px solid #ddd;
  padding: 12px 16px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  page-break-inside: avoid;
}
.pdf-markdown code.md-inline-code {
  background: #f1f5f9;
  color: #e83e8c;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 0.9em;
}
.pdf-markdown img.md-image { max-width: 100%; height: auto; border-radius: 4px; }
.pdf-markdown a { color: #409eff; text-decoration: underline; }

/* Mermaid 未渲染时的降级显示 */
.pdf-markdown .mermaid-block {
  font-style: italic;
  color: #718096;
  padding: 10px 14px;
  border: 1px dashed #cbd5e0;
  border-radius: 4px;
  margin: 0.75em 0;
  white-space: pre-wrap;
  font-family: monospace;
  font-size: 13px;
}

/* ── 富文本区域 ── */
.wangeditor-content img { max-width: 100%; height: auto; }
.wangeditor-content table { border-collapse: collapse; width: 100%; }
.wangeditor-content td, .wangeditor-content th {
  border: 1px solid #e2e8f0; padding: 6px 10px;
}

/* 链接：打印时不附加 URL */
a[href]::after { content: none !important; }
`
