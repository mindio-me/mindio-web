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
              <nuxt-link to="/workspace/notes" class="breadcrumb-link">笔记</nuxt-link>
              <i class="el-icon-arrow-right"></i>
              <span class="breadcrumb-current">{{ note.title }}</span>
            </div>
          </div>
          <div class="header-actions">
            <!-- 项目显示（只读） -->
            <el-tag
              v-if="note.projectId && note.projectName"
              size="small"
              type="info"
              effect="plain"
              style="margin-right: 8px;"
            >
              <i v-if="getProjectIcon(note.projectId)" :class="getProjectIcon(note.projectId)" style="margin-right: 4px;"></i>
              {{ note.projectName }}
            </el-tag>
            <el-button
              icon="el-icon-share"
              @click="handleShare"
              v-if="note.isPublic"
            >
              分享
            </el-button>
            <el-button
              icon="el-icon-paperclip"
              @click="goToClips"
            >
              参考素材<span v-if="clipCount > 0"> ({{ clipCount }})</span>
            </el-button>
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

          <!-- EditorJS 智能目录 -->
          <ul v-if="note.contentType === 'editorjs'" class="toc-list">
            <li
              v-for="(item, idx) in editorjsToc"
              :key="idx"
              class="toc-item"
              :class="'toc-level-' + item.displayLevel"
              @click="scrollToSection(item.index)"
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
          <!-- Editor.js 块内容 -->
          <template v-if="note.contentType === 'editorjs' && note.sectionContents && note.sectionContents.length > 0">
            <div
              v-for="(section, index) in note.sectionContents"
              :key="index"
              :id="`section-${index}`"
              class="note-section editorjs-section"
            >
              <div
                class="section-content"
                :class="'section-' + (note.sectionTypes?.[index] || 'paragraph')"
              >
                <!-- 段落 -->
                <p
                  v-if="note.sectionTypes?.[index] === 'paragraph' || !note.sectionTypes?.[index]"
                  class="ejs-paragraph"
                  v-html="parseBlockData(section, 'text')"
                ></p>
                <!-- 标题 -->
                <component
                  v-else-if="note.sectionTypes?.[index] === 'header'"
                  :is="'h' + (parseBlockData(section, 'level') || 2)"
                  class="ejs-header"
                  v-html="parseBlockData(section, 'text')"
                ></component>
                <!-- 引用块 -->
                <blockquote
                  v-else-if="note.sectionTypes?.[index] === 'quote'"
                  class="ejs-quote"
                >
                  <p v-html="parseBlockData(section, 'text')"></p>
                  <cite v-if="parseBlockData(section, 'caption')" v-html="parseBlockData(section, 'caption')"></cite>
                </blockquote>
                <!-- Mermaid 图表（EditorJS code block 自动检测） -->
                <div
                  v-else-if="note.sectionTypes?.[index] === 'code' && isMermaidCode(parseBlockData(section, 'code'))"
                  v-mermaid
                  class="mermaid-block"
                  :data-mermaid-source="encodeURIComponent(parseBlockData(section, 'code'))"
                >{{ parseBlockData(section, 'code') }}</div>
                <!-- 代码块 -->
                <pre
                  v-else-if="note.sectionTypes?.[index] === 'code'"
                  class="ejs-code"
                ><code>{{ parseBlockData(section, 'code') }}</code></pre>
                <!-- 分割线 -->
                <hr
                  v-else-if="note.sectionTypes?.[index] === 'delimiter'"
                  class="ejs-delimiter"
                />
                <!-- 表格 -->
                <div
                  v-else-if="note.sectionTypes?.[index] === 'table'"
                  class="ejs-table-wrap"
                >
                  <table class="ejs-table" v-if="parseBlockJson(section)">
                    <thead v-if="parseBlockData(section, 'withHeadings') && parseBlockJson(section).content && parseBlockJson(section).content.length > 0">
                      <tr>
                        <th v-for="(cell, ci) in parseBlockJson(section).content[0]" :key="ci" v-html="cell"></th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="(row, ri) in (parseBlockData(section, 'withHeadings') ? parseBlockJson(section).content.slice(1) : parseBlockJson(section).content)"
                        :key="ri"
                      >
                        <td v-for="(cell, ci) in row" :key="ci" v-html="cell"></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <!-- 图片 -->
                <figure
                  v-else-if="note.sectionTypes?.[index] === 'image'"
                  class="ejs-image"
                  :class="{
                    'ejs-image--stretched': parseBlockData(section, 'stretched'),
                    'ejs-image--bordered': parseBlockData(section, 'withBorder'),
                    'ejs-image--bg': parseBlockData(section, 'withBackground')
                  }"
                  :style="parseBlockData(section, 'widthPercent') ? { maxWidth: parseBlockData(section, 'widthPercent') + '%' } : {}"
                >
                  <img :src="parseBlockData(section, 'file.url') || parseBlockData(section, 'url')" :alt="parseBlockData(section, 'caption') || ''" />
                  <figcaption v-if="parseBlockData(section, 'caption')" v-html="parseBlockData(section, 'caption')"></figcaption>
                </figure>
                <!-- 列表（支持嵌套） -->
                <component
                  v-else-if="note.sectionTypes?.[index] === 'list'"
                  :is="parseBlockData(section, 'style') === 'ordered' ? 'ol' : 'ul'"
                  class="ejs-list"
                >
                  <li
                    v-for="(item, li) in parseListItems(parseBlockData(section, 'items'))"
                    :key="li"
                  >
                    <span v-html="item.content"></span>
                    <!-- 嵌套列表 -->
                    <component
                      v-if="item.items && item.items.length > 0"
                      :is="parseBlockData(section, 'style') === 'ordered' ? 'ol' : 'ul'"
                      class="ejs-list-nested"
                    >
                      <li v-for="(subItem, si) in item.items" :key="si">
                        <span v-html="subItem.content"></span>
                        <!-- 二级嵌套 -->
                        <component
                          v-if="subItem.items && subItem.items.length > 0"
                          :is="parseBlockData(section, 'style') === 'ordered' ? 'ol' : 'ul'"
                          class="ejs-list-nested"
                        >
                          <li v-for="(subSubItem, ssi) in subItem.items" :key="ssi">
                            <span v-html="subSubItem.content"></span>
                          </li>
                        </component>
                      </li>
                    </component>
                  </li>
                </component>
                <!-- 其他类型 fallback -->
                <div v-else class="plain-content" v-html="section"></div>
              </div>
            </div>
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
          <div class="footer-right">
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
import { parseListItems } from '~/utils/editorjs-list'

export default {
  name: 'NoteDetailPage',
  layout: 'workspace',
  middleware: ['auth'],
  head() {
    return {
      title: this.note ? `${this.note.title} - MindIO` : 'Note Details - MindIO'
    }
  },
  computed: {
    editorjsToc() {
      if (!this.note || this.note.contentType !== 'editorjs' || !this.note.content) {
        return []
      }
      return createEditorJSToc(this.note.content)
    }
  },
  data() {
    return {
      loading: true,
      note: null,
      error: false,
      avatarUrl: null,
      projects: [],
      clipCount: 0,
    }
  },
  async mounted() {
    await Promise.all([
      this.loadNote(),
      this.loadProjects()
    ])
    this.loadClipCount()
  },
  methods: {
    /**
     * 解析列表项（支持嵌套）
     */
    parseListItems,
    /**
     * 加载项目列表
     */
    async loadProjects() {
      try {
        this.projects = await this.$projectService.getMyProjects()
      } catch (error) {
        console.error('加载项目列表失败:', error)
      }
    },
    /**
     * 获取项目图标
     */
    getProjectIcon(projectId) {
      if (!projectId) return null
      const project = this.projects.find(p => p.id === projectId)
      return project ? project.icon : null
    },
    /**
     * 加载笔记详情
     */
    async loadNote() {
      this.loading = true
      this.error = false
      
      try {
        const noteId = this.$route.params.id
        const note = await this.$noteService.getNoteById(noteId)
        
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

    goToClips() {
      this.$router.push(`/workspace/clips?linkTo=${this.note.id}`)
    },
    async loadClipCount() {
      try {
        const res = await this.$clipService.getNoteClipCount(this.note?.id || this.$route.params.id)
        this.clipCount = res || 0
      } catch (e) {
        // 静默失败
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
            this.$router.push('/workspace/notes')
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
      
      const shareUrl = `${window.location.origin}/notes/public/${this.note.id}`
      
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
        path: '/workspace/notes',
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
     * 返回顶部
     */
    scrollToTop() {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },

    /**
     * 返回
     */
    goBack() {
      this.$router.back()
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
    }
  }
}
</script>

<style scoped lang="scss">
.note-detail-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;

  .note-card {
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  }

  .note-container {
    .note-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 30px;
      padding-bottom: 20px;
      border-bottom: 1px solid #ebeef5;

      .header-left {
        display: flex;
        align-items: center;
        gap: 15px;

        .breadcrumb {
          display: flex;
          align-items: center;
          gap: 8px;
          color: #909399;
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
            color: #303133;
            font-weight: 500;
          }

          i {
            font-size: 12px;
          }
        }
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }

    .note-title {
      font-size: 36px;
      font-weight: 700;
      color: #303133;
      margin: 0 0 20px 0;
      line-height: 1.4;
    }

    .note-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding: 15px;
      background: #f5f7fa;
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
            color: #303133;
            margin-bottom: 4px;
          }

          .meta-dates {
            display: flex;
            gap: 15px;
            font-size: 13px;
            color: #909399;

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
          background-color: #ecf5ff;
          border-color: #b3d8ff;
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
      background: #f8f9fa;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 30px;
      border-left: 4px solid #409eff;

      .toc-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
        color: #303133;
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
            background: #e4e7ed;
          }

          .toc-number {
            display: flex;
            align-items: center;
            justify-content: center;
            min-width: 24px;
            height: 24px;
            padding: 0 6px;
            background: #409eff;
            color: white;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
          }

          .toc-title {
            color: #606266;
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
          border-bottom: 2px solid #ebeef5;

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
        line-height: 1.8;
        color: #303133;

        ::v-deep {
          h1, h2, h3, h4, h5, h6 {
            margin-top: 24px;
            margin-bottom: 16px;
            font-weight: 600;
            line-height: 1.25;
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
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
          }

          pre {
            background: #f5f7fa;
            padding: 16px;
            border-radius: 6px;
            overflow-x: auto;
            border-left: 4px solid #409eff;
            margin: 20px 0;

            code {
              background: none;
              padding: 0;
            }
          }

          code {
            background: #f5f7fa;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
            font-size: 0.9em;
          }

          blockquote {
            border-left: 4px solid #409eff;
            padding: 12px 20px;
            margin: 20px 0;
            background: #f5f7fa;
            border-radius: 4px;
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
              border: 1px solid #ebeef5;
              padding: 12px;
              text-align: left;
            }

            th {
              background: #f5f7fa;
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
        background: #f5f7fa;
        border-radius: 4px;
        font-size: 14px;
        color: #606266;
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
        background: #f5f7fa;
        font-family: 'Courier New', monospace;
        font-size: 13px;
        color: #303133;
      }

      .divider-block {
        height: 1px;
        margin: 16px 0;
        background: #ebeef5;
      }

      .table-content {
        overflow-x: auto;

        table {
          width: 100%;
          border-collapse: collapse;

          td {
            border: 1px solid #ebeef5;
            padding: 8px 12px;
            font-size: 14px;
            color: #303133;
          }

          tr:nth-child(even) {
            background: #f9fafb;
          }
        }

        .table-fallback {
          font-size: 13px;
          color: #909399;
        }
      }

      // AI 对话样式
      .ai-chat-content {
        @extend .rich-content;
        background: #f8f9fa;
        padding: 20px;
        border-radius: 8px;
      }

      // iframe 样式
      .iframe-content {
        margin: 20px 0;

        iframe {
          width: 100%;
          min-height: 500px;
          border: 1px solid #ebeef5;
          border-radius: 6px;
        }
      }

      // Editor.js 渲染样式
      .ejs-paragraph {
        font-size: 16px;
        line-height: 1.8;
        color: #303133;
        margin: 8px 0;
      }

      .ejs-header {
        font-weight: 600;
        color: #303133;
        margin: 1.2em 0 0.5em;
        line-height: 1.4;
      }

      .ejs-quote {
        border-left: 4px solid #667eea;
        padding: 12px 20px;
        margin: 16px 0;
        background: #f8f9fa;
        border-radius: 4px;

        p {
          color: #555;
          font-style: italic;
          margin: 0;
          line-height: 1.8;
        }

        cite {
          display: block;
          margin-top: 8px;
          font-size: 0.85em;
          color: #909399;
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
        border-top: 2px solid #ebeef5;
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
          border: 1px solid #ebeef5;
          padding: 10px 14px;
          text-align: left;
          color: #303133;
        }

        th {
          background: #f5f7fa;
          font-weight: 600;
        }

        tr:hover td {
          background: #fafafa;
        }
      }

      .ejs-image {
        margin: 20px 0;
        text-align: center;

        img {
          max-width: 100%;
          height: auto;
          border-radius: 8px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }

        figcaption {
          margin-top: 8px;
          font-size: 0.85em;
          color: #909399;
        }

        &--bordered img {
          border: 2px solid #ebeef5;
        }

        &--bg {
          background: #f5f7fa;
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
        color: #303133;
        list-style-position: outside;

        li {
          margin: 6px 0;
          line-height: 1.6;
          font-size: 16px;
        }

        // 嵌套列表样式
        .ejs-list-nested {
          padding-left: 20px;
          margin: 4px 0;
          list-style-position: outside;

          li {
            margin: 4px 0;
          }
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
        line-height: 1.8;
        color: #303133;
        white-space: pre-wrap;
      }
    }

    .note-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 20px;
      border-top: 1px solid #ebeef5;
      margin-top: 40px;
    }
  }

  .error-state {
    padding: 60px 20px;
    text-align: center;
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
