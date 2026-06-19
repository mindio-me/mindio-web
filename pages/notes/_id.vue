<template>
  <div class="note-detail-page">
    <!-- 加载状态 -->
    <el-card v-loading="loading" class="note-card">
      <div v-if="note" class="note-container">
        <!-- 头部操作栏 -->
        <div class="note-header">
          <div class="header-left">
            <el-button 
              icon="el-icon-back" 
              circle 
              @click="goBack"
              title="返回"
            ></el-button>
            <div class="breadcrumb">
              <nuxt-link to="/notes" class="breadcrumb-link">笔记</nuxt-link>
              <i class="el-icon-arrow-right"></i>
              <span class="breadcrumb-current">{{ note.title }}</span>
            </div>
          </div>
          <div class="header-actions">
            <el-button
              icon="el-icon-share"
              @click="handleShare"
              v-if="note.isPublic"
            >
              分享
            </el-button>
            <template v-if="isAuthenticated">
              <el-button
                type="primary"
                icon="el-icon-edit"
                @click="editNote"
              >
                编辑
              </el-button>
              <el-dropdown @command="handleCommand">
                <el-button icon="el-icon-more" circle></el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item command="delete" divided>
                    <i class="el-icon-delete"></i> 删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </template>
          </div>
        </div>

        <!-- 笔记标题 -->
        <h1 class="note-title">{{ note.title }}</h1>

        <!-- 笔记元信息 -->
        <div class="note-meta">
          <div class="meta-left">
            <el-avatar 
              :size="32" 
              :src="avatarUrl"
              class="author-avatar"
            >
              {{ note.ownerUsername ? note.ownerUsername.charAt(0).toUpperCase() : 'U' }}
            </el-avatar>
            <div class="meta-info">
              <span class="author-name">{{ note.ownerUsername }}</span>
              <div class="meta-dates">
                <span class="meta-date">
                  <i class="el-icon-time"></i>
                  创建于 {{ formatDate(note.createdAt) }}
                </span>
                <span class="meta-date" v-if="note.modifiedAt !== note.createdAt">
                  <i class="el-icon-edit"></i>
                  更新于 {{ formatDate(note.modifiedAt) }}
                </span>
              </div>
            </div>
          </div>
          <div class="meta-right">
            <el-tag 
              v-if="note.isPublic" 
              type="success" 
              size="small"
              effect="dark"
            >
              <i class="el-icon-view"></i> 公开
            </el-tag>
            <el-tag 
              type="info" 
              size="small"
              effect="plain"
            >
              <i class="el-icon-view"></i> {{ note.viewCount || 0 }} 次阅读
            </el-tag>
          </div>
        </div>

        <!-- 标签 -->
        <div v-if="note.tags && note.tags.length > 0" class="note-tags">
          <el-tag
            v-for="tag in note.tags"
            :key="tag.id"
            type="info"
            effect="plain"
            size="small"
            class="tag-item"
            @click="filterByTag(tag.id)"
          >
            <i class="el-icon-collection-tag"></i>
            {{ tag.name }}
          </el-tag>
        </div>

        <!-- 摘要 -->
        <div v-if="note.summary" class="note-summary">
          <div class="summary-header">
            <i class="el-icon-document"></i>
            <span>摘要</span>
          </div>
          <p class="summary-content">{{ note.summary }}</p>
        </div>

        <el-divider></el-divider>

        <!-- 目录导航 -->
        <div
          v-if="(note.contentType === 'editorjs' && editorjsToc.length > 0) || (note.sectionContents && note.sectionContents.length > 1)"
          class="note-toc"
        >
          <div class="toc-header">
            <i class="el-icon-menu"></i>
            <span>目录</span>
          </div>

          <!-- EditorJS 目录 -->
          <ul v-if="note.contentType === 'editorjs'" class="toc-list">
            <li
              v-for="(item, idx) in editorjsToc"
              :key="idx"
              class="toc-item"
              :class="'toc-level-' + item.displayLevel"
              @click="scrollToEditorBlock(item.index)"
            >
              <span class="toc-number">{{ item.number }}</span>
              <span class="toc-title" v-html="item.text"></span>
            </li>
          </ul>

          <!-- 旧格式目录 -->
          <ul v-else class="toc-list">
            <li
              v-for="(section, index) in note.sectionContents"
              :key="index"
              class="toc-item"
              @click="scrollToSection(index)"
            >
              <span class="toc-number">{{ index + 1 }}</span>
              <span class="toc-title">区块 {{ index + 1 }}</span>
            </li>
          </ul>
        </div>

        <!-- 笔记内容 -->
        <div class="note-body">
          <!-- Editor.js 只读渲染 -->
          <template v-if="note.contentType === 'editorjs'">
            <div ref="editorjsContainer" class="editorjs-readonly-container"></div>
          </template>

          <!-- 旧格式多区块内容 -->
          <template v-else-if="note.contentType !== 'editorjs' && note.sectionContents && note.sectionContents.length > 0">
            <div
              v-for="(section, index) in note.sectionContents"
              :key="index"
              :id="`section-${index}`"
              class="note-section"
            >
              <div class="section-header" v-if="note.sectionContents.length > 1">
                <span class="section-number">区块 {{ index + 1 }}</span>
                <el-tag size="mini" type="info" effect="plain">
                  {{ getSectionTypeLabel(note.sectionTypes?.[index]) }}
                </el-tag>
              </div>
              <div class="section-content" :class="getSectionClass(note.sectionTypes?.[index])">
                <div
                  v-if="note.sectionTypes?.[index] === 'richtext' || !note.sectionTypes?.[index]"
                  class="rich-content" v-html="section"
                ></div>
                <div v-else-if="note.sectionTypes?.[index] === 'markdown'" class="markdown-content" v-mermaid v-html="renderMarkdown(section)"></div>
                <div v-else-if="note.sectionTypes?.[index] === 'quote'" class="quote-content" v-html="section"></div>
                <pre v-else-if="note.sectionTypes?.[index] === 'code'" class="code-block"><code v-html="section"></code></pre>
                <code v-else-if="note.sectionTypes?.[index] === 'inline-code'" class="inline-code-block" v-html="section"></code>
                <div v-else-if="note.sectionTypes?.[index] === 'divider'" class="divider-block"></div>
                <div v-else-if="note.sectionTypes?.[index] === 'table'" class="table-content">
                  <table v-if="parseTable(section)">
                    <tbody>
                      <tr v-for="(row, rIndex) in parseTable(section).rows" :key="rIndex">
                        <td v-for="(cell, cIndex) in row" :key="cIndex" v-html="cell"></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else-if="note.sectionTypes?.[index] === 'ai-chat'" class="ai-chat-content" v-html="section"></div>
                <div v-else class="plain-content" v-html="section"></div>
              </div>
            </div>
          </template>

          <!-- 单内容（向后兼容） -->
          <div v-else class="note-content-main">
            <div
              v-if="note.contentType === 'richtext'"
              class="rich-content"
              v-html="note.content"
            ></div>
            <div
              v-else-if="note.contentType === 'markdown'"
              class="markdown-content"
              v-mermaid
              v-html="renderMarkdown(note.content)"
            ></div>
            <div
              v-else
              class="plain-content"
              v-html="note.content"
            ></div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="note-footer">
          <div class="footer-left">
            <el-button 
              icon="el-icon-arrow-up" 
              size="small"
              @click="scrollToTop"
            >
              返回顶部
            </el-button>
          </div>
          <div class="footer-right" v-if="isAuthenticated">
            <el-button
              type="primary"
              icon="el-icon-edit"
              @click="editNote"
            >
              编辑笔记
            </el-button>
          </div>
        </div>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state">
        <el-empty description="笔记不存在或无权访问">
          <el-button type="primary" @click="goBack">返回笔记列表</el-button>
        </el-empty>
      </div>
    </el-card>
  </div>
</template>

<script>
import { renderMarkdown as renderMd, isMermaidCode } from '~/utils/markdown'
import { createEditorJSToc } from '~/utils/editorjs-toc'

export default {
  name: 'NoteDetailPage',
  layout: 'blank',
  auth: false,
  head() {
    return {
      title: this.note ? `${this.note.title} - MindIO` : 'Note Details - MindIO'
    }
  },
  data() {
    return {
      loading: true,
      note: null,
      error: false,
      avatarUrl: null,
      editorInstance: null
    }
  },
  computed: {
    isAuthenticated() {
      return this.$auth && this.$auth.loggedIn
    },
    editorjsToc() {
      if (!this.note || this.note.contentType !== 'editorjs' || !this.note.content) {
        return []
      }
      return createEditorJSToc(this.note.content)
    }
  },
  async mounted() {
    await this.loadNote()
    // 如果是 EditorJS 笔记，初始化编辑器
    if (this.note && this.note.contentType === 'editorjs') {
      await this.$nextTick()
      await this.initEditorJS()
    }
  },
  beforeDestroy() {
    // 销毁 EditorJS 实例
    if (this.editorInstance && typeof this.editorInstance.destroy === 'function') {
      this.editorInstance.destroy()
      this.editorInstance = null
    }
  },
  methods: {
    /**
     * 加载笔记详情
     */
    async loadNote() {
      this.loading = true
      this.error = false

      try {
        const noteId = this.$route.params.id
        console.log('[NoteDetail] 开始加载笔记, id:', noteId)
        const note = await this.$noteService.getPublicNoteById(noteId)
        console.log('[NoteDetail] 笔记加载完成:', {
          id: note?.id,
          title: note?.title,
          contentType: note?.contentType
        })
        
        // 确保 tags 是数组格式（后端返回的 Set 在 JSON 序列化后应该是数组）
        if (note.tags && !Array.isArray(note.tags)) {
          note.tags = Array.from(note.tags || [])
        }
        
        // 确保 sectionContents 和 sectionTypes 是数组
        if (note.sectionContents && !Array.isArray(note.sectionContents)) {
          note.sectionContents = Array.from(note.sectionContents || [])
        }
        if (note.sectionTypes && !Array.isArray(note.sectionTypes)) {
          note.sectionTypes = Array.from(note.sectionTypes || [])
        }
        
        this.note = note
        
        // 设置页面标题
        if (process.client) {
          document.title = `${note.title} - MindIO`
        }
      } catch (error) {
        console.error('加载笔记失败:', error)
        this.error = true
        if (error.response?.status === 404) {
          this.$message.error('笔记不存在')
        } else if (error.response?.status === 401 || error.response?.status === 403) {
          this.$message.error('无权访问此笔记')
        } else {
          this.$message.error('加载笔记失败')
        }
      } finally {
        this.loading = false
      }
    },

    /**
     * 编辑笔记
     */
    editNote() {
      if (this.note.contentType === 'editorjs') {
        this.$router.push(`/workspace/editor?id=${this.note.id}`)
      } else {
        this.$router.push(`/workspace/notes/${this.note.id}/edit`)
      }
    },

    /**
     * 删除笔记
     */
    async handleCommand(command) {
      if (command === 'delete') {
        this.$confirm(`确定要删除笔记 "${this.note.title}" 吗？`, '删除确认', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: false
        }).then(async () => {
          try {
            await this.$noteService.deleteNote(this.note.id)
            this.$message.success('删除成功')
            this.$router.push('/notes')
          } catch (error) {
            this.$message.error('删除失败')
          }
        }).catch(() => {})
      }
    },

    /**
     * 分享笔记
     */
    handleShare() {
      if (!this.note.isPublic) {
        this.$message.warning('只有公开笔记才能分享')
        return
      }
      
      const shareUrl = `${window.location.origin}/workspace/public/${this.note.id}`
      
      // 复制到剪贴板
      if (navigator.clipboard) {
        navigator.clipboard.writeText(shareUrl).then(() => {
          this.$message.success('分享链接已复制到剪贴板')
        }).catch(() => {
          this.showShareDialog(shareUrl)
        })
      } else {
        this.showShareDialog(shareUrl)
      }
    },

    /**
     * 显示分享对话框
     */
    showShareDialog(url) {
      this.$prompt('分享链接', '复制以下链接分享给他人', {
        inputValue: url,
        inputType: 'textarea',
        confirmButtonText: '复制',
        cancelButtonText: '关闭'
      }).then(() => {
        this.$message.success('链接已复制')
      }).catch(() => {})
    },

    /**
     * 按标签筛选
     */
    filterByTag(tagId) {
      this.$router.push({
        path: '/notes',
        query: { tagId }
      })
    },

    /**
     * 滚动到指定区块
     */
    scrollToSection(index) {
      const element = document.getElementById(`section-${index}`)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },

    /**
     * 滚动到 EditorJS 块
     */
    scrollToEditorBlock(blockIndex) {
      const blocks = this.$refs.editorjsContainer?.querySelectorAll('.ce-block')
      if (blocks && blocks[blockIndex]) {
        blocks[blockIndex].scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },

    /**
     * 返回顶部
     */
    scrollToTop() {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },

    /**
     * 返回
     */
    goBack() {
      this.$router.push('/notes')
    },

    /**
     * 格式化日期
     */
    formatDate(date) {
      if (!date) return ''
      const d = new Date(date)
      return d.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    },

    /**
     * 获取区块类型标签
     */
    getSectionTypeLabel(type) {
      const labels = {
        'richtext': '富文本',
        'markdown': 'Markdown',
        'paragraph': '段落',
        'header': '标题',
        'quote': '引用',
        'code': '代码块',
        'inline-code': '行内代码',
        'delimiter': '分割线',
        'divider': '分割线',
        'table': '表格',
        'image': '图片',
        'list': '列表',
        'ai-chat': 'AI 对话',
        'iframe': '嵌入页面',
        'gallery': '图片库'
      }
      return labels[type] || '文本'
    },

    /**
     * 获取区块样式类
     */
    getSectionClass(type) {
      return {
        'section-richtext': type === 'richtext' || !type,
        'section-markdown': type === 'markdown',
        'section-quote': type === 'quote',
        'section-code': type === 'code',
        'section-inline-code': type === 'inline-code',
        'section-divider': type === 'divider',
        'section-table': type === 'table',
        'section-ai-chat': type === 'ai-chat',
        'section-iframe': type === 'iframe',
        'section-gallery': type === 'gallery'
      }
    },

    /**
     * 解析 Editor.js 块数据（JSON 字符串）并提取指定字段
     */
    parseBlockData(section, field) {
      try {
        const data = typeof section === 'string' ? JSON.parse(section) : section
        // Support nested fields like 'file.url'
        return field.split('.').reduce((obj, key) => obj?.[key], data)
      } catch (e) {
        return null
      }
    },

    /**
     * 解析 Editor.js 块 JSON
     */
    parseBlockJson(section) {
      try {
        return typeof section === 'string' ? JSON.parse(section) : section
      } catch (e) {
        return null
      }
    },

    /**
     * 解析表格数据（存储为 JSON 字符串）- 旧格式兼容
     */
    parseTable(section) {
      if (!section) return null
      try {
        const data = typeof section === 'string' ? JSON.parse(section) : section
        if (Array.isArray(data.rows)) {
          return { rows: data.rows }
        }
        return null
      } catch (e) {
        console.error('解析表格区块失败:', e)
        return null
      }
    },

    renderMarkdown(markdown) {
      return renderMd(markdown, { axiosBaseURL: this.$axios?.defaults?.baseURL || '' })
    },
    isMermaidCode(code) {
      return isMermaidCode(code)
    },

    /**
     * 初始化 EditorJS 只读模式
     */
    async initEditorJS() {
      if (!this.note || this.note.contentType !== 'editorjs') return
      if (!this.$refs.editorjsContainer) return

      try {
        // 动态导入 EditorJS 和工具
        const [
          { default: EditorJS },
          { default: Header },
          { default: List },
          { default: CodeTool },
          { default: Delimiter },
          { default: Quote },
          { default: Table },
          { default: ImageTool },
          { default: MarkdownBlock }
        ] = await Promise.all([
          import('@editorjs/editorjs'),
          import('@editorjs/header'),
          import('@editorjs/list'),
          import('@editorjs/code'),
          import('@editorjs/delimiter'),
          import('@editorjs/quote'),
          import('@editorjs/table'),
          import('@editorjs/image'),
          import('~/utils/editorjs-markdown-block')
        ])

        // 解析笔记内容
        let editorData = { blocks: [] }
        if (this.note.content) {
          try {
            editorData = JSON.parse(this.note.content)
            console.log('[EditorJS] 解析内容成功, blocks 数量:', editorData.blocks?.length)
            // 打印图片块详情
            editorData.blocks?.forEach((block, index) => {
              if (block.type === 'image') {
                console.log(`[EditorJS] 图片块 #${index}:`, {
                  url: block.data?.file?.url,
                  caption: block.data?.caption,
                  widthPercent: block.data?.widthPercent
                })
              }
            })
          } catch (e) {
            console.error('解析 EditorJS 内容失败:', e)
          }
        }

        // 初始化 EditorJS（只读模式）
        this.editorInstance = new EditorJS({
          holder: this.$refs.editorjsContainer,
          readOnly: true,
          data: editorData,
          tools: {
            header: { class: Header, inlineToolbar: false },
            list: { class: List, inlineToolbar: false },
            code: CodeTool,
            delimiter: Delimiter,
            quote: { class: Quote, inlineToolbar: false },
            table: { class: Table, inlineToolbar: false },
            image: {
              class: ImageTool,
              config: {
                uploader: {
                  uploadByFile: () => Promise.resolve({ success: 0 })
                }
              }
            },
            markdown: {
              class: MarkdownBlock,
              config: {
                axiosBaseURL: this.$axios?.defaults?.baseURL || ''
              }
            }
          },
          minHeight: 0
        })

        await this.editorInstance.isReady
        console.log('[EditorJS] 编辑器初始化完成')

        // 检查渲染后的图片 DOM 元素
        this.$nextTick(() => {
          const imageElements = this.$refs.editorjsContainer?.querySelectorAll('.image-tool img')
          console.log('[EditorJS] DOM 中图片元素数量:', imageElements?.length)
          imageElements?.forEach((img, i) => {
            console.log(`[EditorJS] 图片 #${i} src:`, img.src)
            img.addEventListener('load', () => console.log(`[EditorJS] 图片 #${i} 加载成功`))
            img.addEventListener('error', (e) => console.error(`[EditorJS] 图片 #${i} 加载失败:`, img.src, e))
          })
        })

        // 恢复图片的缩放比例
        this.applyImageWidths(editorData)
      } catch (error) {
        console.error('初始化 EditorJS 失败:', error)
      }
    },

    /**
     * 根据 EditorJS 数据中的 widthPercent 设置图片宽度
     */
    applyImageWidths(editorData) {
      console.log('[EditorJS] applyImageWidths 开始')
      if (!editorData || !editorData.blocks) {
        console.log('[EditorJS] applyImageWidths: 无 editorData 或 blocks')
        return
      }
      if (!this.$refs.editorjsContainer) {
        console.log('[EditorJS] applyImageWidths: 无 editorjsContainer ref')
        return
      }

      // 收集所有 image block 的 widthPercent
      const imageWidths = []
      editorData.blocks.forEach(block => {
        if (block.type === 'image') {
          imageWidths.push(block.data?.widthPercent || null)
        }
      })
      console.log('[EditorJS] applyImageWidths: 图片宽度数组:', imageWidths)

      if (imageWidths.length === 0) {
        console.log('[EditorJS] applyImageWidths: 无图片块')
        return
      }

      // 延迟执行，确保 DOM 已渲染
      this.$nextTick(() => {
        const imageContainers = this.$refs.editorjsContainer.querySelectorAll('.image-tool .image-tool__image')
        console.log('[EditorJS] applyImageWidths: 找到', imageContainers.length, '个图片容器')
        imageContainers.forEach((el, index) => {
          const widthPercent = imageWidths[index]
          console.log(`[EditorJS] applyImageWidths: 图片 #${index} widthPercent=${widthPercent}`)
          if (widthPercent && widthPercent < 100) {
            el.style.width = widthPercent + '%'
          }
        })
      })
    }
  }
}
</script>

<style scoped lang="scss">
.note-detail-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;

  .note-card {
    border-radius: 8px;
    border: solid 1px var(--border-color);
    box-shadow: 0 2px 12px var(--shadow-color);
    background: var(--card-bg-color);

    ::v-deep .el-card__body {
      background: var(--card-bg-color);
    }
  }

  .note-container {
    .note-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 30px;
      padding-bottom: 20px;
      border-bottom: 1px solid var(--border-color);

      .header-left {
        display: flex;
        align-items: center;
        gap: 15px;

        .breadcrumb {
          display: flex;
          align-items: center;
          gap: 8px;
          color: var(--text-muted);
          font-size: 14px;

          .breadcrumb-link {
            color: #409eff;
            text-decoration: none;
            transition: color 0.3s;

            &:hover {
              color: #66b1ff;
            }
          }

          .breadcrumb-current {
            color: var(--text-color);
            font-weight: 500;
          }

          i {
            font-size: 12px;
          }
        }
      }

      .header-actions {
        display: flex;
        gap: 10px;
      }
    }

    .note-title {
      font-size: 36px;
      font-weight: 700;
      color: var(--text-color);
      margin: 0 0 20px 0;
      line-height: 1.4;
    }

    .note-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding: 15px;
      background: var(--bg-secondary);
      border-radius: 6px;

      .meta-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .author-avatar {
          flex-shrink: 0;
        }

        .meta-info {
          .author-name {
            display: block;
            font-weight: 500;
            color: var(--text-color);
            margin-bottom: 4px;
          }

          .meta-dates {
            display: flex;
            gap: 15px;
            font-size: 13px;
            color: var(--text-muted);

            .meta-date {
              display: flex;
              align-items: center;
              gap: 4px;

              i {
                font-size: 14px;
              }
            }
          }
        }
      }

      .meta-right {
        display: flex;
        gap: 8px;
      }
    }

    .note-tags {
      margin-bottom: 20px;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .tag-item {
        cursor: pointer;
        transition: all 0.3s;

        &:hover {
          opacity: 0.8;
        }
      }
    }

    .note-summary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;

      .summary-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
        margin-bottom: 10px;
        font-size: 16px;
      }

      .summary-content {
        margin: 0;
        line-height: 1.8;
        opacity: 0.95;
      }
    }

    .note-toc {
      background: var(--bg-tertiary);
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;
      border-left: 4px solid #409eff;

      .toc-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
        color: var(--text-color);
        margin-bottom: 15px;
        font-size: 16px;
      }

      .toc-list {
        list-style: none;
        padding: 0;
        margin: 0;

        .toc-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 8px 12px;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.3s;

          &:hover {
            background: var(--bg-secondary);
          }

          .toc-number {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 24px;
            height: 24px;
            background: #409eff;
            color: white;
            border-radius: 50%;
            font-size: 12px;
            font-weight: 600;
          }

          .toc-title {
            color: var(--text-secondary);
          }

          // EditorJS 标题层级缩进
          &.toc-level-1 {
            padding-left: 12px;
            font-weight: 600;
          }

          &.toc-level-2 {
            padding-left: 24px;
          }

          &.toc-level-3 {
            padding-left: 36px;
            font-size: 13px;
          }

          &.toc-level-4 {
            padding-left: 48px;
            font-size: 12px;
          }
        }
      }
    }

      .note-body {
      margin-bottom: 40px;

        .note-section {
        margin-bottom: 40px;

        .section-header {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 15px;
          padding-bottom: 10px;
          border-bottom: 2px solid var(--border-color);

          .section-number {
            font-weight: 600;
            color: #409eff;
            font-size: 16px;
          }
        }

        .section-content {
          min-height: 60px;
        }
      }

      .note-content-main {
        min-height: 200px;
      }

      // 富文本样式
      .rich-content {
        font-size: 16px;
        color: var(--text-color);

        ::v-deep {
          h1, h2, h3, h4, h5, h6 {
            margin-top: 24px;
            margin-bottom: 16px;
            font-weight: 600;
            line-height: 1.25;
            color: var(--text-color);
          }

          h1 { font-size: 2em; }
          h2 { font-size: 1.5em; }
          h3 { font-size: 1.25em; }

          p {
            margin: 16px 0;
          }

          img {
            max-width: 100%;
            height: auto;
            border-radius: 6px;
            margin: 20px 0;
            box-shadow: 0 2px 8px var(--shadow-color);
          }

          pre {
            background: var(--bg-secondary);
            padding: 16px;
            border-radius: 6px;
            overflow-x: auto;
            border-left: 4px solid #409eff;
            margin: 20px 0;

            code {
              background: none;
              padding: 0;
              color: var(--text-color);
            }
          }

          code {
            background: var(--bg-secondary);
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
            font-size: 0.9em;
            color: var(--text-color);
          }

          blockquote {
            border-left: 4px solid #409eff;
            padding: 12px 20px;
            margin: 20px 0;
            background: var(--bg-secondary);
            border-radius: 4px;
            color: var(--text-secondary);
          }

          ul, ol {
            padding-left: 30px;
            margin: 16px 0;
          }

          li {
            margin: 8px 0;
          }

          table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;

            th, td {
              border: 1px solid var(--border-color);
              padding: 12px;
              text-align: left;
              color: var(--text-color);
            }

            th {
              background: var(--bg-secondary);
              font-weight: 600;
            }
          }

          a {
            color: #409eff;
            text-decoration: none;

            &:hover {
              text-decoration: underline;
            }
          }
        }
      }

      // Markdown 样式
      .markdown-content {
        @extend .rich-content;
      }

      // 引用块样式
      .quote-content {
        border-left: 4px solid #409eff;
        padding: 12px 16px;
        background: var(--bg-secondary);
        border-radius: 4px;
        font-size: 14px;
        color: var(--text-secondary);
      }

      // 代码块样式
      .code-block {
        background: #1e293b;
        color: #e5e7eb;
        padding: 12px 16px;
        border-radius: 6px;
        overflow-x: auto;
        font-family: 'Courier New', monospace;
        font-size: 13px;
        line-height: 1.6;
      }

      .inline-code-block {
        display: inline-block;
        padding: 2px 6px;
        border-radius: 4px;
        background: var(--bg-secondary);
        font-family: 'Courier New', monospace;
        font-size: 13px;
        color: var(--text-color);
      }

      .divider-block {
        height: 1px;
        margin: 16px 0;
        background: var(--border-color);
      }

      .table-content {
        overflow-x: auto;

        table {
          width: 100%;
          border-collapse: collapse;

          td {
            border: 1px solid var(--border-color);
            padding: 8px 12px;
            font-size: 14px;
            color: var(--text-color);
          }

          tr:nth-child(even) {
            background: var(--bg-tertiary);
          }
        }

        .table-fallback {
          font-size: 13px;
          color: var(--text-muted);
        }
      }

      // AI 对话样式
      .ai-chat-content {
        @extend .rich-content;
        background: var(--bg-tertiary);
        padding: 20px;
        border-radius: 8px;
      }

      // iframe 样式
      .iframe-content {
        margin: 20px 0;

        iframe {
          width: 100%;
          min-height: 500px;
          border: 1px solid var(--border-color);
          border-radius: 6px;
        }
      }

      // Editor.js 渲染样式
      .ejs-paragraph {
        font-size: 16px;
        color: var(--text-color);
        margin: 8px 0;
      }

      .ejs-header {
        font-weight: 600;
        color: var(--text-color);
        margin: 1.2em 0 0.5em;
        line-height: 1.4;
      }

      .ejs-quote {
        border-left: 4px solid #667eea;
        padding: 12px 20px;
        margin: 16px 0;
        background: var(--bg-tertiary);
        border-radius: 4px;

        p {
          color: var(--text-secondary);
          font-style: italic;
          margin: 0;
        }

        cite {
          display: block;
          margin-top: 8px;
          font-size: 0.85em;
          color: var(--text-muted);
          font-style: normal;
        }
      }

      .ejs-code {
        background: #1e1e1e;
        color: #d4d4d4;
        padding: 16px 20px;
        border-radius: 8px;
        overflow-x: auto;
        font-family: 'Fira Code', 'Monaco', 'Consolas', monospace;
        font-size: 14px;
        line-height: 1.6;
        margin: 16px 0;

        code {
          background: none;
          padding: 0;
          color: inherit;
          font-size: inherit;
        }
      }

      .ejs-delimiter {
        border: none;
        border-top: 2px solid var(--border-color);
        margin: 24px 0;
      }

      .ejs-table-wrap {
        overflow-x: auto;
        margin: 16px 0;
      }

      .ejs-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;

        th, td {
          border: 1px solid var(--border-color);
          padding: 10px 14px;
          text-align: left;
          color: var(--text-color);
        }

        th {
          background: var(--bg-secondary);
          font-weight: 600;
        }

        tr:hover td {
          background: var(--bg-tertiary);
        }
      }

      .ejs-image {
        margin: 20px 0;
        text-align: center;

        img {
          max-width: 100%;
          height: auto;
          border-radius: 8px;
          box-shadow: 0 2px 8px var(--shadow-color);
        }

        figcaption {
          margin-top: 8px;
          font-size: 0.85em;
          color: var(--text-muted);
        }

        &--bordered img {
          border: 2px solid var(--border-color);
        }

        &--bg {
          background: var(--bg-secondary);
          padding: 16px;
          border-radius: 8px;
        }

        &--stretched img {
          width: 100%;
        }
      }

      .ejs-list {
        padding-left: 24px;
        margin: 12px 0;
        color: var(--text-color);

        li {
          margin: 6px 0;
          line-height: 1.6;
          font-size: 16px;
        }
      }

      .editorjs-section {
        .section-content {
          min-height: auto;
        }
      }

      // 纯文本样式
      .plain-content {
        font-size: 16px;
        color: var(--text-color);
        white-space: pre-wrap;
      }
    }

    .note-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 20px;
      border-top: 1px solid var(--border-color);
      margin-top: 40px;
    }
  }

  .error-state {
    padding: 60px 20px;
    text-align: center;
  }

  // EditorJS 只读容器样式
  .editorjs-readonly-container {
    min-height: 200px;
    font-size: 16px;
    color: var(--text-color);

    // 继承 EditorJS 默认样式，与 workspace 保持一致
    ::v-deep .ce-block__content {
      max-width: 100%;
    }

    ::v-deep .ce-paragraph {
      line-height: 1.6em;
    }

    ::v-deep .ce-header {
      padding: 0.4em 0;
    }

    ::v-deep .cdx-list {
      padding-left: 40px;
    }

    ::v-deep .cdx-nested-list__item {
      padding-left: 20px;
    }

    ::v-deep .ce-code__textarea {
      min-height: 100px;
      background: #1e1e1e;
      color: #d4d4d4;
    }

    ::v-deep .cdx-quote {
      border-left: 4px solid #667eea;
      padding-left: 1em;
    }

    ::v-deep .tc-table {
      border-collapse: collapse;

      td {
        border: 1px solid var(--border-color);
        padding: 8px 12px;
      }
    }

    // 图片居中显示（与编辑器保持一致）
    ::v-deep .image-tool .image-tool__image {
      margin: 0 auto 10px;
    }
  }
}

// 响应式设计
@media screen and (max-width: 768px) {
  .note-detail-page {
    padding: 10px;

    .note-container {
      .note-header {
        flex-direction: column;
        align-items: stretch;
        gap: 15px;

        .header-left {
          .breadcrumb {
            font-size: 12px;
          }
        }

        .header-actions {
          justify-content: flex-end;
        }
      }

      .note-title {
        font-size: 24px;
      }

      .note-meta {
        flex-direction: column;
        align-items: flex-start;
        gap: 15px;

        .meta-right {
          width: 100%;
          justify-content: flex-start;
        }
      }

      .note-footer {
        flex-direction: column;
        gap: 15px;
      }
    }
  }
}
</style>

