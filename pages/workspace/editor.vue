<template>
  <div class="editor-page" :class="{ 'theme-dark': isDarkTheme }">
    <!-- Header -->
    <header class="editor-header">
      <div class="header-left">
        <nuxt-link to="/workspace/notes" class="back-btn">
          <i class="el-icon-arrow-left"></i>
        </nuxt-link>
        <div class="breadcrumb">
          <span class="breadcrumb-item">My Documents</span>
          <i class="el-icon-arrow-right"></i>
          <span class="breadcrumb-item current">{{ docTitle || 'Untitled' }}</span>
        </div>
      </div>
      <div class="header-center">
        <span class="save-status">
          <i :class="saveStatus.icon"></i>
          {{ saveStatus.text }}
        </span>
      </div>
      <div class="header-right">
        <!-- 项目选择器 -->
        <el-select
          v-model="projectId"
          placeholder="选择项目（可选）"
          clearable
          size="small"
          style="width: 160px; margin-right: 8px;"
          @change="onProjectChange"
        >
          <el-option
            v-for="project in projects"
            :key="project.id"
            :label="project.shortName || project.name"
            :value="project.id"
          >
            <span>
              <i v-if="project.icon" :class="project.icon" style="margin-right: 4px;"></i>
              {{ project.shortName || project.name }}
            </span>
          </el-option>
        </el-select>
        <button class="theme-toggle" @click="toggleTheme" :title="isDarkTheme ? '切换到白天模式' : '切换到黑夜模式'">
          <i :class="isDarkTheme ? 'el-icon-sunny' : 'el-icon-moon'"></i>
        </button>
        <button class="header-btn" @click="shareDoc">
          <i class="el-icon-share"></i>
          <span>Share</span>
        </button>
        <button v-if="currentNoteId" class="header-btn" @click="goToClips">
          <i class="el-icon-paperclip"></i>
          <span>素材<span v-if="clipCount > 0"> ({{ clipCount }})</span></span>
        </button>
        <div class="more-menu-wrapper" ref="moreMenuWrapper">
          <button class="header-btn icon-only" @click="toggleMoreMenu">
            <i class="el-icon-more"></i>
          </button>
          <div v-if="showMoreMenu" class="more-menu-dropdown">
            <div class="more-menu-item" @click="togglePublic">
              <i :class="isPublic ? 'el-icon-lock' : 'el-icon-unlock'"></i>
              {{ isPublic ? '设为私密' : '设为公开' }}
            </div>
            <div class="more-menu-item" @click="openWechatPublish">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="#07c160" style="margin-right:6px;vertical-align:-2px;">
                <path d="M8.7 10.6c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zm5.1 0c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zM12 2C6.48 2 2 6.03 2 11c0 2.7 1.24 5.12 3.2 6.8L4 22l4.83-1.61A10.63 10.63 0 0012 21c5.52 0 10-4.03 10-9S17.52 2 12 2z"/>
              </svg>
              发布到微信
            </div>
          </div>
        </div>
        <div class="user-avatar">
          <img src="/default_user.png" alt="User" />
        </div>
      </div>
    </header>

    <div class="editor-container">
      <!-- Left Sidebar: Document List -->
      <aside class="doc-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-header">
          <div class="sidebar-title">My Documents</div>
          <div class="sidebar-actions">
            <button class="sidebar-btn" @click="createNewDoc">
              <i class="el-icon-plus"></i>
            </button>
            <button class="sidebar-btn" @click="toggleSidebar">
              <i class="el-icon-s-fold"></i>
            </button>
          </div>
        </div>

        <div class="doc-list">
          <div
            v-for="doc in documents"
            :key="doc.id"
            class="doc-item"
            :class="{ active: currentDocId === doc.id, folder: doc.isFolder }"
            @click="selectDoc(doc)"
          >
            <i :class="doc.isFolder ? 'el-icon-folder' : 'el-icon-document'"></i>
            <span class="doc-name">{{ doc.name }}</span>
          </div>
        </div>
      </aside>

      <!-- Main Editor Area -->
      <main class="editor-main">
        <div class="editor-content">
          <!-- Document Title -->
          <div class="doc-title-area">
            <input
              type="text"
              class="doc-title-input"
              v-model="docTitle"
              placeholder="Enter title"
              @input="onTitleChange"
            />
            <div class="doc-meta">
              <span class="doc-author">
                <img src="/default_user.png" alt="Author" class="author-avatar" />
                {{ authorName }}
              </span>
              <span class="doc-date">Modified today</span>
              <span v-if="wechatPublished" class="wechat-published-badge" @click="showWechatLogs">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="#07c160">
                  <path d="M8.7 10.6c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zm5.1 0c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zM12 2C6.48 2 2 6.03 2 11c0 2.7 1.24 5.12 3.2 6.8L4 22l4.83-1.61A10.63 10.63 0 0012 21c5.52 0 10-4.03 10-9S17.52 2 12 2z"/>
                </svg>
                已发布至微信
              </span>
            </div>
          </div>

          <!-- Editor.js Container -->
          <div id="editorjs" ref="editorContainer" class="editorjs-container"></div>
        </div>
      </main>
    </div>

    <WechatPublishDialog
      ref="wechatDialog"
      :note-id="currentNoteId ? Number(currentNoteId) : null"
      :note-title="docTitle"
      :note-content="currentNoteContent"
      @published="onWechatPublished"
    />
  </div>
</template>

<script>
import { createEditorImageResizer } from '~/utils/editorjsImageResize'

export default {
  name: 'NoteEditor',
  layout: 'blank',
  auth: false,

  components: {
    WechatPublishDialog: () => import('~/components/WechatPublishDialog.vue')
  },

  data() {
    return {
      docTitle: '',
      authorName: 'user460783',
      currentDocId: null,
      currentNoteId: null,
      projectId: null,
      projects: [],
      sidebarCollapsed: false,
      isDarkTheme: false,
      editor: null,
      imageResizer: null,
      saveStatus: {
        icon: 'el-icon-check',
        text: 'Saved to cloud'
      },
      saveTimeout: null,
      isSaving: false,
      hasUnsavedChanges: false,
      lastSavedSnapshot: null,
      showMoreMenu: false,
      isPublic: false,
      wechatPublished: false,
      clipCount: 0,
      currentNoteContent: '',
      documents: [
        { id: 1, name: 'Q&A', isFolder: false },
        { id: 2, name: 'May Development', isFolder: true },
        { id: 3, name: 'Claude Code Examples', isFolder: false },
        { id: 4, name: 'ThinkPHP 6 Database Query Design', isFolder: false },
        { id: 5, name: 'Mind Map - Personal Center', isFolder: false },
        { id: 6, name: 'Mind Map Agent RAG', isFolder: false },
        { id: 7, name: 'Update Deployment Nov 16', isFolder: false },
        { id: 8, name: 'Memo: WeChat Service Notification', isFolder: false },
        { id: 9, name: 'Video Analysis', isFolder: false },
        { id: 10, name: 'AI Dance Evaluation Solution', isFolder: false },
        { id: 11, name: 'Google Opal, Coze, n8n, Dify', isFolder: false },
        { id: 12, name: 'Personal Website - Services Page', isFolder: false },
        { id: 13, name: 'Personal Website Design (ChatGPT)', isFolder: false },
        { id: 14, name: 'Personal Website Home Page', isFolder: false },
        { id: 15, name: 'How to Explain CRMEB Pro Advantages', isFolder: false },
        { id: 16, name: 'Nuxt Multi-language Solution', isFolder: false },
        { id: 17, name: 'Multi-language Website Support', isFolder: false },
        { id: 18, name: 'Accept-Language Header Fields', isFolder: false },
        { id: 'new', name: 'Untitled', isFolder: false }
      ]
    }
  },

  async mounted() {
    // Theme detection
    if (process.client) {
      this.isDarkTheme = document.documentElement.classList.contains('theme-dark')
    }

    // Load projects list
    await this.loadProjects()

    // Check if editing existing note
    const noteId = this.$route.query.id
    if (noteId) {
      await this.loadNote(noteId)
    } else {
      this.currentDocId = 'new'
      this.currentNoteId = null
      await this.initEditor()
    }

    document.addEventListener('click', this.handleClickOutside)
    window.addEventListener('beforeunload', this.handleBeforeUnload)
  },

  beforeDestroy() {
    document.removeEventListener('click', this.handleClickOutside)
    window.removeEventListener('beforeunload', this.handleBeforeUnload)
    if (this.saveTimeout) {
      clearTimeout(this.saveTimeout)
    }
    if (this._codeBlockObserver) {
      this._codeBlockObserver.disconnect()
      this._codeBlockObserver = null
    }
    if (this.imageResizer) {
      this.imageResizer.destroy()
      this.imageResizer = null
    }
    if (this.editor) {
      this.editor.destroy()
    }
  },

  methods: {
    /**
     * Initialize Editor.js with all tools
     */
    async initEditor(data = null) {
      if (!process.client) return

      const [
        { default: EditorJS },
        { default: Header },
        { default: List },
        { default: CodeTool },
        { default: Delimiter },
        { default: Quote },
        { default: Table },
        { default: InlineCode },
        { default: ImageTool },
        { default: Marker },
        { default: MarkdownBlock },
        { default: VideoTool },
        { default: EmbedVideoTool },
        { default: AudioTool }
      ] = await Promise.all([
        import('@editorjs/editorjs'),
        import('@editorjs/header'),
        import('@editorjs/list'),
        import('@editorjs/code'),
        import('@editorjs/delimiter'),
        import('@editorjs/quote'),
        import('@editorjs/table'),
        import('@editorjs/inline-code'),
        import('@editorjs/image'),
        import('@editorjs/marker'),
        import('~/utils/editorjs-markdown-block'),
        import('~/utils/editorjsVideoTool'),
        import('~/utils/editorjsEmbedVideoTool'),
        import('~/utils/editorjsAudioTool')
      ])

      const uploadService = this.$uploadService
      const noteId = this.currentNoteId

      const editorConfig = {
        holder: 'editorjs',
        placeholder: '输入 "/" 唤出快捷菜单，或直接开始输入...',
        autofocus: true,
        tools: {
          header: {
            class: Header,
            config: {
              placeholder: '请输入标题',
              levels: [1, 2, 3],
              defaultLevel: 2
            },
            shortcut: 'CMD+SHIFT+H'
          },
          list: {
            class: List,
            inlineToolbar: true,
            config: {
              defaultStyle: 'unordered'
            }
          },
          code: {
            class: CodeTool,
            config: {
              placeholder: '输入代码...'
            }
          },
          delimiter: {
            class: Delimiter
          },
          quote: {
            class: Quote,
            config: {
              quotePlaceholder: '输入引用内容...',
              captionPlaceholder: '引用来源（可选）'
            },
            shortcut: 'CMD+SHIFT+O'
          },
          table: {
            class: Table,
            inlineToolbar: true,
            config: {
              rows: 3,
              cols: 3,
              withHeadings: true
            }
          },
          inlineCode: {
            class: InlineCode,
            shortcut: 'CMD+SHIFT+M'
          },
          image: {
            class: ImageTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return {
                      success: 1,
                      file: {
                        url: result.url || result.fileUrl || result
                      }
                    }
                  } catch (e) {
                    console.error('图片上传失败:', e)
                    return { success: 0 }
                  }
                },
                async uploadByUrl(url) {
                  try {
                    const result = await uploadService.uploadRemote(url, 'note', noteId || 0)
                    return {
                      success: 1,
                      file: {
                        url: result.url || result.fileUrl || result
                      }
                    }
                  } catch (e) {
                    console.error('图片URL上传失败:', e)
                    return { success: 0 }
                  }
                }
              }
            }
          },
          marker: {
            class: Marker,
            shortcut: 'CMD+SHIFT+M'
          },
          markdown: {
            class: MarkdownBlock,
            inlineToolbar: false,
            config: {
              axiosBaseURL: this.$axios?.defaults?.baseURL || ''
            }
          },
          embed: {
            class: EmbedVideoTool
          },
          video: {
            class: VideoTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return {
                      success: 1,
                      file: {
                        url: result.url || result.fileUrl || result
                      }
                    }
                  } catch (e) {
                    console.error('视频上传失败:', e)
                    return { success: 0 }
                  }
                }
              }
            }
          },
          audio: {
            class: AudioTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return {
                      success: 1,
                      file: {
                        url: result.url || result.fileUrl || result
                      }
                    }
                  } catch (e) {
                    console.error('音频上传失败:', e)
                    return { success: 0 }
                  }
                }
              },
              session: {
                async createSession() {
                  return await uploadService.createSession('note', noteId || 0, 'audio/*')
                },
                async getSession(sessionId, token) {
                  return await uploadService.getSession(sessionId, token)
                }
              }
            }
          }
        },
        data: data || undefined,
        onChange: () => {
          this.hasUnsavedChanges = true
          this.updateSaveStatus('saving')
          this.debouncedSave()
        },
        i18n: {
          messages: {
            ui: {
              blockTunes: {
                toggler: {
                  'Click to tune': '点击调整',
                  'or drag to move': '或拖动移动'
                }
              },
              inlineToolbar: {
                converter: {
                  'Convert to': '转换为'
                }
              },
              toolbar: {
                toolbox: {
                  Add: '添加'
                }
              }
            },
            toolNames: {
              Text: '文本',
              Heading: '标题',
              List: '列表',
              Quote: '引用',
              Code: '代码块',
              Delimiter: '分割线',
              Table: '表格',
              Image: '图片',
              InlineCode: '行内代码',
              Marker: '高亮',
              Markdown: 'Markdown',
              Embed: '嵌入视频1',
              Video: '视频1',
              Audio: '音频',
              Bold: '加粗',
              Italic: '斜体',
              Link: '链接'
            },
            tools: {
              header: {
                'Heading 1': '标题 1',
                'Heading 2': '标题 2',
                'Heading 3': '标题 3'
              },
              list: {
                Ordered: '有序列表',
                Unordered: '无序列表'
              },
              quote: {
                'Align Left': '左对齐',
                'Align Center': '居中'
              },
              table: {
                'With headings': '带表头',
                'Without headings': '无表头',
                'Add row above': '上方插入行',
                'Add row below': '下方插入行',
                'Delete row': '删除行',
                'Add column to the left': '左侧插入列',
                'Add column to the right': '右侧插入列',
                'Delete column': '删除列'
              },
              image: {
                Caption: '图片说明',
                'Select an Image': '选择图片',
                'With border': '带边框',
                'Stretch image': '拉伸图片',
                'With background': '带背景'
              }
            },
            blockTunes: {
              delete: {
                Delete: '删除',
                'Click to delete': '点击确认删除'
              },
              moveUp: {
                'Move up': '上移'
              },
              moveDown: {
                'Move down': '下移'
              }
            }
          }
        }
      }

      // 在 EditorJS 处理 data 之前提取 widthPercent（EditorJS 可能会篡改 data 对象）
      if (!this.imageResizer) {
        this.imageResizer = createEditorImageResizer({
          getEditor: () => this.editor,
          getContainer: () => this.$refs.editorContainer,
          markDirtyAndSave: () => {
            this.hasUnsavedChanges = true
            this.updateSaveStatus('saving')
            this.debouncedSave()
          }
        })
      }
      if (data) {
        console.log('[Editor] initEditor: calling setInitialData BEFORE new EditorJS()')
        this.imageResizer.setInitialData(data)
      }

      this.editor = new EditorJS(editorConfig)
      await this.editor.isReady
      console.log('[Editor] initEditor: EditorJS ready, calling setupImageResize()')
      this.imageResizer.setupImageResize()
      this.setupCodeBlockAutoResize()
    },

    // Code block textarea auto-resize
    setupCodeBlockAutoResize() {
      console.log('setupCodeBlockAutoResize', this.editor)
      const editorEl = document.getElementById('editorjs')
      if (!editorEl) return

      const autoResize = (textarea) => {
        const lines = (textarea.value || '').split('\n').length
        // font-size: 14px, line-height: 1.6 → 每行 22.4px；padding: 16px × 2 = 32px
        const lineHeight = 14 * 1.6
        const padding = 32
        const minH = 60
        const contentH = Math.ceil(lines * lineHeight + padding)
        console.log('contentH 代码块高度', contentH)
        textarea.style.height = Math.max(contentH, minH) + 'px'
        // 禁用拼写检查和语法检查，屏蔽蓝色双下划线告警
        textarea.setAttribute('spellcheck', 'false')
        textarea.setAttribute('autocomplete', 'off')
        textarea.setAttribute('autocorrect', 'off')
        textarea.setAttribute('autocapitalize', 'off')
      }

      // 延迟执行，确保 EditorJS 已渲染完内容
      setTimeout(() => {
        editorEl.querySelectorAll('.ce-code__textarea').forEach(autoResize)
      }, 200)

      // Listen for input on code block textareas (handles typing)
      editorEl.addEventListener('input', (e) => {
        if (e.target && e.target.classList.contains('ce-code__textarea')) {
          autoResize(e.target)
        }
      })

      // Observe new code blocks being added
      this._codeBlockObserver = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          for (const node of mutation.addedNodes) {
            if (node.nodeType !== 1) continue
            const textareas = node.classList && node.classList.contains('ce-code__textarea')
              ? [node]
              : (node.querySelectorAll ? node.querySelectorAll('.ce-code__textarea') : [])
            textareas.forEach((ta) => setTimeout(() => autoResize(ta), 100))
          }
        }
      })
      this._codeBlockObserver.observe(editorEl, { childList: true, subtree: true })
    },

    // Theme toggle
    toggleTheme() {
      if (this.$root.$options.app && this.$root.$options.app.themeToggle) {
        this.isDarkTheme = this.$root.$options.app.themeToggle()
      } else if (process.client) {
        const root = document.documentElement
        const isDark = root.classList.toggle('theme-dark')
        window.localStorage.setItem('worknotes-theme', isDark ? 'dark' : 'light')
        this.isDarkTheme = isDark
      }
    },

    // Sidebar methods
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },

    createNewDoc() {
      const newDoc = {
        id: Date.now(),
        name: 'Untitled',
        isFolder: false
      }
      this.documents.push(newDoc)
      this.selectDoc(newDoc)
    },

    selectDoc(doc) {
      if (!doc.isFolder) {
        this.currentDocId = doc.id
        this.docTitle = doc.name === 'Untitled' ? '' : doc.name
      }
    },

    // Save status
    onTitleChange() {
      this.updateSaveStatus('saving')
      this.debouncedSave()
    },

    updateSaveStatus(status) {
      switch (status) {
        case 'saving':
          this.saveStatus = { icon: 'el-icon-loading', text: '保存中...' }
          break
        case 'saved':
          this.saveStatus = { icon: 'el-icon-check', text: '已保存' }
          break
        case 'error':
          this.saveStatus = { icon: 'el-icon-warning', text: '保存失败' }
          break
        case 'loading':
          this.saveStatus = { icon: 'el-icon-loading', text: '加载中...' }
          break
        default:
          this.saveStatus = { icon: 'el-icon-edit', text: '未保存' }
          break
      }
    },

    debouncedSave() {
      clearTimeout(this.saveTimeout)
      this.hasUnsavedChanges = true
      this.saveTimeout = setTimeout(() => {
        this.saveToBackend()
      }, 2000)
    },

    /**
     * Save to backend - serialize Editor.js blocks to sectionContents/sectionTypes
     */
    async saveToBackend() {
      console.log('[Editor] saveToBackend called, isSaving=', this.isSaving, ', hasEditor=', !!this.editor)
      if (this.isSaving || !this.editor) return

      const title = this.docTitle.trim() || 'Untitled'

      this.isSaving = true
      this.updateSaveStatus('saving')

      try {
        const outputData = await this.editor.save()

        // If no blocks and no title, skip
        if (!outputData.blocks.length && title === 'Untitled') {
          this.updateSaveStatus('idle')
          this.isSaving = false
          return
        }

        // 比较内容快照，未变化则跳过保存
        const currentSnapshot = JSON.stringify({ title, blocks: outputData.blocks })
        if (this.lastSavedSnapshot && currentSnapshot === this.lastSavedSnapshot) {
          this.hasUnsavedChanges = false
          this.updateSaveStatus('saved')
          this.isSaving = false
          return
        }

        // 从 DOM 读取图片实际宽度百分比，注入到 image block data 中
        if (this.imageResizer) {
          this.imageResizer.syncWidthsToBlocks(outputData)
        }

        const sectionContents = outputData.blocks.map(b => JSON.stringify(b.data))
        const sectionTypes = outputData.blocks.map(b => b.type)

        const noteData = {
          title: title,
          content: JSON.stringify(outputData), // Full Editor.js JSON for reload
          contentType: 'editorjs',
          isPublic: false,
          tagIds: [],
          projectId: this.projectId || null,
          sectionContents,
          sectionTypes
        }

        let response
        if (this.currentNoteId) {
          response = await this.$noteService.updateNote(this.currentNoteId, noteData)
        } else {
          response = await this.$noteService.createNote(noteData)
          this.currentNoteId = response.id
          this.$router.replace({ query: { id: response.id } })
        }

        this.hasUnsavedChanges = false
        this.lastSavedSnapshot = currentSnapshot
        this.updateSaveStatus('saved')

        // Update sidebar doc name
        if (this.currentDocId && this.documents) {
          const doc = this.documents.find(d => d.id === this.currentDocId)
          if (doc) {
            doc.name = title
          }
        }
      } catch (error) {
        console.error('保存失败:', error)
        this.updateSaveStatus('error')
        this.$message.error('保存失败，请稍后重试')
      } finally {
        this.isSaving = false
      }
    },

    /**
     * Load note and initialize Editor.js with its data
     */
    async loadNote(noteId) {
      try {
        this.updateSaveStatus('loading')
        const note = await this.$noteService.getNoteById(noteId)

        this.currentNoteId = note.id
        this.currentDocId = note.id
        this.docTitle = note.title
        this.projectId = note.projectId || null
        this.loadClipCount(note.id)

        let editorData = null

        if (note.contentType === 'editorjs' && note.content) {
          // Load Editor.js native JSON
          try {
            editorData = JSON.parse(note.content)
          } catch (e) {
            console.error('解析 Editor.js 数据失败:', e)
          }
        } else if (note.content) {
          // Legacy: convert old richtext content to a single paragraph block
          editorData = {
            blocks: [
              {
                type: 'paragraph',
                data: { text: note.content }
              }
            ]
          }
        }

        await this.initEditor(editorData)
        this.lastSavedSnapshot = JSON.stringify({ title: note.title, blocks: editorData?.blocks || [] })
        this.hasUnsavedChanges = false
        // 清除 EditorJS 初始化过程中 onChange 触发的自动保存定时器，防止覆盖 widthPercent
        if (this.saveTimeout) {
          console.log('[Editor] loadNote: clearing saveTimeout triggered by EditorJS init')
          clearTimeout(this.saveTimeout)
          this.saveTimeout = null
        }
        this.updateSaveStatus('saved')
        console.log('[Editor] loadNote: done, hasUnsavedChanges =', this.hasUnsavedChanges)

        // 检查微信发布状态
        try {
          const logs = await this.$wechatService.getLogs(Number(noteId))
          this.wechatPublished = logs.some(l => l.status === 'SUCCESS')
        } catch (e) { /* ignore if wechat not configured */ }
      } catch (error) {
        console.error('加载笔记失败:', error)
        this.$message.error('加载笔记失败')
        this.$router.push('/workspace/notes')
      }
    },

    handleBeforeUnload(e) {
      if (this.hasUnsavedChanges) {
        e.preventDefault()
        e.returnValue = '您有未保存的更改，确定要离开吗？'
        return e.returnValue
      }
    },

    handleClickOutside(event) {
      const wrapper = this.$refs.moreMenuWrapper
      if (wrapper && !wrapper.contains(event.target)) {
        this.showMoreMenu = false
      }
    },

    shareDoc() {
      this.$message.info('分享功能即将上线')
    },

    toggleMoreMenu() {
      this.showMoreMenu = !this.showMoreMenu
    },

    goToClips() {
      if (this.currentNoteId) {
        this.$router.push(`/workspace/clips?linkTo=${this.currentNoteId}`)
      }
    },

    async loadClipCount(noteId) {
      try {
        const res = await this.$clipService.getNoteClipCount(noteId)
        this.clipCount = res?.count || 0
      } catch (e) {
        // 静默失败
      }
    },

    async togglePublic() {
      this.showMoreMenu = false
      if (!this.currentNoteId) {
        this.$message.warning('请先保存笔记')
        return
      }
      try {
        await this.$noteService.togglePublic(this.currentNoteId)
        this.isPublic = !this.isPublic
        this.$message.success(this.isPublic ? '已设为公开' : '已设为私密')
      } catch (e) {
        this.$message.error('操作失败')
      }
    },

    async openWechatPublish() {
      this.showMoreMenu = false
      if (!this.currentNoteId) {
        this.$message.warning('请先保存笔记后再发布')
        return
      }
      let content = ''
      if (this.editor) {
        try {
          const outputData = await this.editor.save()
          content = JSON.stringify(outputData)
          this.currentNoteContent = content
        } catch (e) {
          content = ''
        }
      }
      this.$refs.wechatDialog.open(content)
    },

    async onWechatPublished(result) {
      this.wechatPublished = true
      if (result.mode === 'PUBLISHED') {
        this.$message.success('群发成功！')
      } else {
        this.$message.success('草稿已推送至微信公众号后台')
      }
    },

    async showWechatLogs() {
      if (!this.currentNoteId) return
      try {
        const logs = await this.$wechatService.getLogs(this.currentNoteId)
        const latest = logs[0]
        if (latest) {
          this.$message.info(
            `最近发布：${latest.mode === 'PUBLISHED' ? '群发' : '草稿'} · ${latest.status} · ${latest.createdAt}`
          )
        }
      } catch (e) { /* ignore */ }
    },

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
     * 项目选择变更处理
     */
    onProjectChange() {
      this.hasUnsavedChanges = true
      this.updateSaveStatus('saving')
      this.debouncedSave()
    },

    async saveDocument() {
      if (this.saveTimeout) {
        clearTimeout(this.saveTimeout)
      }
      await this.saveToBackend()
      this.$message.success('文档已保存')
    }
  },

  head() {
    return {
      title: this.docTitle || 'New Document - MindIO'
    }
  },

  fetch({ store }) {
    store.commit("isHeader", false)
    store.commit("isFooter", false)
  }
}
</script>

<style scoped lang="scss">
.more-menu-wrapper {
  position: relative;
}
.more-menu-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  min-width: 160px;
  z-index: 1000;
  overflow: hidden;
}
.more-menu-item {
  padding: 10px 16px;
  font-size: 14px;
  color: #303133;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s;
  &:hover {
    background: #f5f7fa;
  }
}
.wechat-published-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #07c160;
  cursor: pointer;
  padding: 2px 8px;
  border: 1px solid #07c160;
  border-radius: 10px;
  margin-left: 8px;
  &:hover {
    background: #f0fff4;
  }
}

.editor-page {
  min-height: 100vh;
  background: var(--bg-color, #fff);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

// Header
.editor-header {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-color, #e8e8e8);
  background: var(--header-bg, #fff);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--text-muted, #666);
  text-decoration: none;
  transition: all 0.2s;

  &:hover {
    background: var(--bg-secondary, #f5f5f5);
    color: var(--text-color, #333);
  }
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-muted, #999);

  .breadcrumb-item {
    &.current {
      color: var(--text-color, #333);
    }
  }

  i {
    font-size: 12px;
  }
}

.header-center {
  .save-status {
    font-size: 13px;
    color: var(--text-muted, #999);
    display: flex;
    align-items: center;
    gap: 6px;

    i {
      font-size: 14px;
    }
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-toggle {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--bg-secondary, #f5f5f5);
  border-radius: 50%;
  color: var(--text-secondary, #666);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 16px;

  &:hover {
    background: var(--border-color, #e8e8e8);
  }
}

.header-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-secondary, #f5f5f5);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-color, #333);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--border-color, #e8e8e8);
  }

  &.icon-only {
    padding: 6px 8px;
  }

  &:first-child {
    background: #ff6a00;
    color: white;

    &:hover {
      background: #e65c00;
    }
  }
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

// Editor Container
.editor-container {
  display: flex;
  height: calc(100vh - 52px);
}

// Sidebar
.doc-sidebar {
  width: 240px;
  border-right: 1px solid var(--border-color, #e8e8e8);
  background: var(--bg-secondary, #fafafa);
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  overflow: hidden;

  &.collapsed {
    width: 0;
    border-right: none;
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #e8e8e8);
}

.sidebar-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted, #666);
}

.sidebar-actions {
  display: flex;
  gap: 4px;
}

.sidebar-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  color: var(--text-muted, #666);
  cursor: pointer;

  &:hover {
    background: var(--border-color, #e8e8e8);
  }
}

.doc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text-color, #333);
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: var(--border-color, #e8e8e8);
  }

  &.active {
    background: #d6e4ff;
    color: #1890ff;
  }

  &.folder {
    i {
      color: #faad14;
    }
  }

  i {
    font-size: 16px;
    color: var(--text-muted, #999);
  }

  .doc-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// Main Editor
.editor-main {
  flex: 1;
  display: flex;
  justify-content: center;
  overflow-y: auto;
  padding: 40px 20px;
  background: var(--bg-color, #fff);
}

.editor-content {
  width: 100%;
  max-width: 800px;
}

.doc-title-area {
  margin-bottom: 24px;
}

.doc-title-input {
  width: 100%;
  font-size: 36px;
  font-weight: 700;
  color: var(--text-color, #333);
  border: none;
  outline: none;
  background: transparent;
  padding: 0;
  margin-bottom: 12px;

  &::placeholder {
    color: var(--text-placeholder, #ccc);
  }
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--text-muted, #999);
}

.doc-author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
}

// Editor.js Container
.editorjs-container {
  min-height: 500px;
}

// Responsive
@media screen and (max-width: 768px) {
  .doc-sidebar {
    position: fixed;
    left: 0;
    top: 52px;
    height: calc(100vh - 52px);
    z-index: 50;

    &.collapsed {
      left: -240px;
    }
  }

  .editor-main {
    padding: 24px 16px;
  }

  .doc-title-input {
    font-size: 28px;
  }

  .header-btn span {
    display: none;
  }

  .header-btn.icon-only {
    display: flex;
  }
}
</style>

<style lang="scss">
/* Editor.js global styles (unscoped) - needed to style Editor.js internal elements */

.editorjs-container {
  /* Base Editor.js styling */
  .codex-editor {
    font-size: 16px;
    line-height: 1.8;
  }

  .ce-block__content {
    max-width: 100%;
  }

  .ce-toolbar__content {
    max-width: 100%;
  }

  /* Paragraph */
  .ce-paragraph {
    color: var(--text-color, #333);
    line-height: 1.8;
  }

  /* Header */
  .ce-header {
    color: var(--text-color, #333);
    font-weight: 600;
    padding: 0.6em 0 0.3em;
  }

  /* Quote */
  .cdx-quote {
    border-left: 4px solid #667eea;
    padding: 12px 20px;
    background: var(--bg-secondary, #f8f9fa);
    border-radius: 4px;

    .cdx-quote__text {
      color: var(--text-secondary, #555);
      font-style: italic;
      min-height: 1.5em;
    }

    .cdx-quote__caption {
      color: var(--text-muted, #999);
      font-size: 0.85em;
    }
  }

  /* Code block */
  .cdx-block.ce-code {
    border-radius: 8px;
  }

  .ce-code__textarea {
    background: #1e1e1e !important;
    color: #d4d4d4 !important;
    font-family: 'Fira Code', 'Monaco', 'Consolas', monospace !important;
    font-size: 14px !important;
    line-height: 1.6 !important;
    border-radius: 8px !important;
    border: none !important;
    padding: 16px 20px !important;
    min-height: 60px;
    /* 屏蔽语法检查的蓝色双下划线 */
    text-decoration: none !important;
    text-decoration-line: none !important;
    text-decoration-skip-ink: none !important;
    /* 禁用浏览器自动拼写检查样式 */
    -webkit-text-decoration-skip: none !important;
    -moz-text-decoration-skip: none !important;
  }

  /* Delimiter */
  .ce-delimiter {
    line-height: 1;
    text-align: center;
    color: var(--border-color, #e8e8e8);

    &::before {
      content: '';
      display: block;
      border-top: 2px solid var(--border-color, #e8e8e8);
      margin: 24px 0;
    }
  }

  /* Table */
  .tc-table {
    border-collapse: collapse;
    width: 100%;

    .tc-row {
      border-bottom: 1px solid var(--border-color, #e8e8e8);
    }

    .tc-cell {
      border: 1px solid var(--border-color, #e8e8e8);
      padding: 8px 12px;
      color: var(--text-color, #333);
    }

    .tc-row--heading .tc-cell {
      background: var(--bg-secondary, #f5f5f5);
      font-weight: 600;
    }
  }

  /* Image */
  .image-tool {
    position: relative;

    .image-tool__image {
      border-radius: 8px;
      margin: 0 auto 10px;
      position: relative;

      img {
        max-width: 100%;
        width: 100%;
        display: block;
      }

      /* 图片缩放手柄 - 在 .image-tool__image 内部，自然跟随图片右边缘 */
      .image-resize-handle {
        position: absolute;
        right: 0;
        top: 0;
        bottom: 0;
        width: 8px;
        cursor: col-resize;
        z-index: 10;

        &::after {
          content: '';
          position: absolute;
          right: 0;
          top: 50%;
          transform: translateY(-50%);
          width: 4px;
          height: 36px;
          border-radius: 2px;
          background: rgba(102, 126, 234, 0.6);
          opacity: 0;
          transition: opacity 0.2s;
        }
      }

      &:hover .image-resize-handle::after,
      .image-resize-handle.resizing::after {
        opacity: 1;
      }
    }

    .image-tool__caption {
      color: var(--text-muted, #999);
      font-size: 0.85em;
      text-align: center;
    }

    &--withBorder .image-tool__image {
      border: 2px solid var(--border-color, #e8e8e8);
    }

    &--withBackground .image-tool__image {
      background: var(--bg-secondary, #f5f5f5);
      padding: 16px;
    }

    /* 拖拽时宽度提示 - 挂在 .image-tool wrapper 上，避免被 overflow:hidden 裁剪 */
    .image-resize-tooltip {
      position: absolute;
      top: -30px;
      right: 0;
      background: rgba(0, 0, 0, 0.75);
      color: #fff;
      padding: 3px 10px;
      border-radius: 4px;
      font-size: 12px;
      white-space: nowrap;
      pointer-events: none;
    }
  }

  /* Video */
  .video-tool {
    position: relative;

    .video-tool__video {
      border-radius: 8px;
      margin: 0 auto 10px;
      position: relative;
      overflow: hidden;

      video {
        display: block;
        width: 100%;
        border-radius: 8px;
      }

      .image-resize-handle {
        position: absolute;
        right: 0;
        top: 0;
        bottom: 0;
        width: 8px;
        cursor: col-resize;
        z-index: 10;

        &::after {
          content: '';
          position: absolute;
          right: 0;
          top: 50%;
          transform: translateY(-50%);
          width: 4px;
          height: 36px;
          border-radius: 2px;
          background: rgba(102, 126, 234, 0.6);
          opacity: 0;
          transition: opacity 0.2s;
        }
      }

      &:hover .image-resize-handle::after,
      .image-resize-handle.resizing::after {
        opacity: 1;
      }
    }

    .video-tool__caption {
      color: var(--text-muted, #999);
      font-size: 0.85em;
      text-align: center;
      margin-top: 4px;
    }

    .image-resize-tooltip {
      position: absolute;
      top: -30px;
      right: 0;
      background: rgba(0, 0, 0, 0.75);
      color: #fff;
      padding: 3px 10px;
      border-radius: 4px;
      font-size: 12px;
      white-space: nowrap;
      pointer-events: none;
    }

    .video-tool__upload-area {
      border: 2px dashed var(--border-color, #e8e8e8);
      border-radius: 8px;
      padding: 32px 16px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      transition: border-color 0.2s, background 0.2s;

      &:hover,
      &--dragover {
        border-color: #667eea;
        background: rgba(102, 126, 234, 0.05);
      }

      &--loading {
        opacity: 0.6;
        pointer-events: none;
      }

      .video-tool__upload-icon {
        color: var(--text-muted, #bbb);
      }

      .video-tool__upload-text {
        color: var(--text-muted, #999);
        font-size: 0.9em;
      }
    }
  }

  /* Embed video tool (custom) */
  .embed-video-tool {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;

    .embed-video-tool__frame {
      border-radius: 8px;
      margin-bottom: 10px;
      position: relative;
      // 右侧给 handle 留位
      padding-right: 10px;
      box-sizing: border-box;

      iframe {
        display: block;
        width: 100%;
        border-radius: 8px;
      }

      .image-resize-handle {
        position: absolute;
        right: 0;
        top: 0;
        bottom: 0;
        width: 10px;
        cursor: col-resize;
        z-index: 10;

        &::after {
          content: '';
          position: absolute;
          right: 1px;
          top: 50%;
          transform: translateY(-50%);
          width: 4px;
          height: 36px;
          border-radius: 2px;
          background: rgba(102, 126, 234, 0.6);
          opacity: 0;
          transition: opacity 0.2s;
        }
      }
    }

    // hover 检测放在外层容器，避免 iframe 吞掉鼠标事件
    &:hover .embed-video-tool__frame .image-resize-handle::after,
    .embed-video-tool__frame .image-resize-handle.resizing::after {
      opacity: 1;
    }

    .image-resize-tooltip {
      position: absolute;
      top: -30px;
      right: 0;
      background: rgba(0, 0, 0, 0.75);
      color: #fff;
      padding: 3px 10px;
      border-radius: 4px;
      font-size: 12px;
      white-space: nowrap;
      pointer-events: none;
    }

    .embed-video-tool__caption {
      color: var(--text-muted, #999);
      font-size: 0.85em;
      text-align: center;
      margin-top: 6px;
    }

    .embed-video-tool__input-row {
      display: flex;
      gap: 8px;
    }

    .embed-video-tool__input {
      flex: 1;
      padding: 8px 12px;
      border: 1px solid var(--border-color, #e8e8e8);
      border-radius: 6px;
      font-size: 14px;
      background: var(--bg-color, #fff);
      color: var(--text-color, #333);
      outline: none;

      &:focus {
        border-color: #667eea;
      }
    }

    .embed-video-tool__btn {
      padding: 8px 16px;
      background: #667eea;
      color: #fff;
      border: none;
      border-radius: 6px;
      font-size: 14px;
      cursor: pointer;
      white-space: nowrap;

      &:hover {
        background: #5a6fd6;
      }
    }

    .embed-video-tool__error {
      color: #f56c6c;
      font-size: 13px;
      margin-top: 6px;
      min-height: 18px;
    }
  }

  /* List */
  .cdx-list {
    color: var(--text-color, #333);
    padding-left: 24px;

    .cdx-list__item {
      padding: 4px 0;
      line-height: 1.6;
    }
  }

  /* Inline code */
  .inline-code {
    background: var(--bg-secondary, #f5f5f5);
    color: #e83e8c;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Fira Code', 'Monaco', 'Consolas', monospace;
    font-size: 0.9em;
  }

  /* Marker / highlight */
  .cdx-marker {
    background: rgba(245, 235, 111, 0.4);
    padding: 2px 0;
  }

  /* Toolbar */
  .ce-toolbar__plus,
  .ce-toolbar__settings-btn {
    color: var(--text-muted, #999);
    background: var(--bg-color, #fff);
    border: 1px solid var(--border-color, #e8e8e8);

    &:hover {
      background: var(--bg-secondary, #f5f5f5);
    }
  }

  .ce-toolbox {
    background: var(--card-bg-color, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    box-shadow: 0 4px 16px var(--shadow-color, rgba(0, 0, 0, 0.12));
    border-radius: 8px;

    .ce-toolbox__button {
      color: var(--text-color, #333);

      &:hover {
        background: var(--bg-secondary, #f5f5f5);
      }
    }
  }

  /* Inline toolbar */
  .ce-inline-toolbar {
    background: var(--card-bg-color, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    box-shadow: 0 4px 16px var(--shadow-color, rgba(0, 0, 0, 0.12));

    .ce-inline-tool {
      color: var(--text-color, #333);

      &:hover {
        background: var(--bg-secondary, #f5f5f5);
      }
    }

    .ce-inline-toolbar__dropdown {
      border-right: 1px solid var(--border-color, #e8e8e8);
    }
  }

  /* Settings popup */
  .ce-settings {
    background: var(--card-bg-color, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    box-shadow: 0 4px 16px var(--shadow-color, rgba(0, 0, 0, 0.12));

    .cdx-settings-button {
      color: var(--text-color, #333);

      &:hover {
        background: var(--bg-secondary, #f5f5f5);
      }
    }
  }

  /* Conversion toolbar */
  .ce-conversion-toolbar {
    background: var(--card-bg-color, #fff);
    border: 1px solid var(--border-color, #e8e8e8);

    .ce-conversion-tool {
      color: var(--text-color, #333);

      &:hover {
        background: var(--bg-secondary, #f5f5f5);
      }
    }
  }
}

/* Dark mode specific overrides */
.theme-dark .editorjs-container {
  .cdx-marker {
    background: rgba(245, 235, 111, 0.25);
  }

  .inline-code {
    color: #f472b6;
    background: var(--bg-tertiary, #1a2332);
  }

  .doc-item.active {
    background: #1e3a5f;
    color: #60a5fa;
  }
}
</style>
