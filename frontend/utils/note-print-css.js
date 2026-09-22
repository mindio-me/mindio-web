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

/* ── 任务列表 ── */
.pdf-checklist { list-style: none; padding-left: 0; margin: 0.75em 0; }
.pdf-checklist-item { margin: 0.3em 0; line-height: 1.7; padding-left: 26px; position: relative; }
.pdf-checklist-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.35em;
  width: 14px;
  height: 14px;
  border: 1.5px solid #a0aec0;
  border-radius: 3px;
}
.pdf-checklist-item--checked { color: #718096; text-decoration: line-through; }
.pdf-checklist-item--checked::before {
  background: #667eea;
  border-color: #667eea;
}
.pdf-checklist-item--checked::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 0.55em;
  width: 6px;
  height: 3px;
  border-left: 1.5px solid #fff;
  border-bottom: 1.5px solid #fff;
  transform: rotate(-45deg);
}

/* ── 附件 ── */
.pdf-attach {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 8px 14px;
  margin: 1em 0;
  text-decoration: none;
  color: #1a202c;
  page-break-inside: avoid;
}
.pdf-attach__name { font-weight: 600; font-size: 13px; }
.pdf-attach__meta { font-size: 11px; color: #a0aec0; }

/* ── 链接预览 ── */
.pdf-link-tool {
  display: flex;
  align-items: stretch;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  margin: 1em 0;
  text-decoration: none;
  color: inherit;
  page-break-inside: avoid;
  overflow: hidden;
}
.pdf-link-tool__image {
  width: 100px;
  flex-shrink: 0;
  background-size: cover;
  background-position: center;
  background-color: #f1f5f9;
}
.pdf-link-tool__content { padding: 10px 14px; min-width: 0; }
.pdf-link-tool__title { font-weight: 600; font-size: 14px; color: #1a202c; margin-bottom: 4px; }
.pdf-link-tool__description {
  font-size: 12px;
  color: #4a5568;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pdf-link-tool__host { font-size: 11px; color: #a0aec0; }

/* ── 提示框 ── */
.pdf-warning {
  border-left: 4px solid #f6ad55;
  background: #fffaf0;
  padding: 10px 16px;
  margin: 1em 0;
  border-radius: 0 4px 4px 0;
  page-break-inside: avoid;
}
.pdf-warning__title { font-weight: 600; color: #9c4221; margin-bottom: 4px; }
.pdf-warning__message { color: #4a5568; }

/* ── 参考资料 ── */
.pdf-references { padding-left: 20px; margin: 1em 0; }
.pdf-references li { margin: 0.3em 0; }
.pdf-ref__note { color: #718096; font-size: 0.85em; }

/* ── 媒体画廊 ── */
.pdf-gallery { display: flex; flex-wrap: wrap; gap: 8px; margin: 1em 0; }
.pdf-gallery__card { width: 150px; }
.pdf-gallery__card img { width: 100%; border-radius: 4px; }
.pdf-gallery__video { width: 100%; height: 100px; border: none; }

/* ── 时间线 ── */
.pdf-timeline { margin: 1em 0; border-left: 2px solid #e2e8f0; padding-left: 16px; }
.pdf-timeline__item { margin-bottom: 12px; }
.pdf-timeline__date { font-size: 0.8em; color: #718096; }
.pdf-timeline__title { font-weight: 600; }
.pdf-timeline__link { display: inline; margin-left: 6px; text-decoration: none; font-size: 0.9em; vertical-align: middle; }
.pdf-timeline__desc { font-size: 0.9em; color: #4a5568; }

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
.pdf-markdown table.md-table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.75em 0;
  page-break-inside: avoid;
  font-size: 13px;
}
.pdf-markdown table.md-table th, .pdf-markdown table.md-table td {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}
.pdf-markdown table.md-table th { background: #f1f5f9; font-weight: 600; }
.pdf-markdown s { color: #718096; }
.pdf-markdown .contains-task-list { list-style: none; padding-left: 4px; }
.pdf-markdown .task-list-item { list-style: none; }
.pdf-markdown .task-list-item > label { display: flex; align-items: flex-start; gap: 6px; }
.pdf-markdown .task-list-item-checkbox { margin-top: 0.2em; }

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
