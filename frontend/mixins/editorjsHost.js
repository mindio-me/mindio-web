/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { createEditorImageResizer } from '~/utils/editorjsImageResize'
import { clipboardMayContainImage, getClipboardImagePayload, uploadClipboardImage } from '~/utils/clipboardImage'

export default {
  data() {
    return {
      editor: null,
      editorUndo: null,
      imageResizer: null
    }
  },
  methods: {
    /**
     * options:
     *   container: HTMLElement — 挂载点（会被设置一个新的 holder id）
     *   model: 'note' | 'project' — 透传给 uploadService 的模块名
     *   entityId: Number — 笔记/项目 id，作为上传的 pid
     *   data: Object|null — EditorJS 初始文档
     *   locale: string — this.$i18n.locale
     *   axiosBaseURL: string
     *   authHeader: () => string
     *   placeholders: {
     *     editor, header, code, quote, quoteCaption, warningTitle, warningMessage,
     *     attachButton, attachError,
     *     clipboardImageReadFailed: () => string,
     *     uploadingImage: (sizeText) => string,
     *     imageTooLarge: (sizeText) => string,
     *     imagePasteUploadFailed: (sizeText) => string
     *   }
     *   onChange: () => void — 每次内容变化时调用（含粘贴图片插入后）
     *   includeRecordTool: boolean — 是否注册实时录音块（笔记页 true，项目页不传/false）
     *   getRecordEntityId: () => Number|null — includeRecordTool 为 true 时必填
     */
    async initEditorJsHost(options) {
      if (!process.client) return
      const container = options.container
      if (!container) return
      const holderId = 'editorjs-host-' + Date.now()
      container.id = holderId
      this._editorJsHostContainer = container

      const [
        { default: EditorJS }, { default: Header }, { default: List },
        { default: CodeTool }, { default: Delimiter }, { default: Quote },
        { default: Table }, { default: InlineCode }, { default: ImageTool },
        { default: Marker }, { default: Checklist }, { default: Warning }, { default: LinkTool }, { default: AttachesTool }, { default: MarkdownBlock },
        { default: VideoTool }, { default: EmbedVideoTool }, { default: AudioTool },
        { default: RecordTool },
        { default: CodeWrapTune }, { default: Undo },
        { default: ReferencesTool }, { default: GalleryTool }, { default: TimelineTool }
      ] = await Promise.all([
        import('@editorjs/editorjs'), import('@editorjs/header'), import('@editorjs/list'),
        import('@editorjs/code'), import('@editorjs/delimiter'), import('@editorjs/quote'),
        import('@editorjs/table'), import('@editorjs/inline-code'), import('@editorjs/image'),
        import('@editorjs/marker'), import('@editorjs/checklist'), import('@editorjs/warning'),
        import('@editorjs/link'),
        import('@editorjs/attaches'),
        import('~/utils/editorjs-markdown-block'),
        import('~/utils/editorjsVideoTool'), import('~/utils/editorjsEmbedVideoTool'),
        import('~/utils/editorjsAudioTool'),
        import('~/utils/editorjsRecordTool'),
        import('~/utils/editorjsCodeWrapTune'),
        import('editorjs-undo'),
        import('~/utils/editorjsReferencesTool'), import('~/utils/editorjsGalleryTool'), import('~/utils/editorjsTimelineTool')
      ])

      const uploadService = this.$uploadService
      const model = options.model
      const entityId = options.entityId || 0
      const ph = options.placeholders

      const tools = {
        header: { class: Header, config: { placeholder: ph.header, levels: [1, 2, 3], defaultLevel: 2 }, shortcut: 'CMD+SHIFT+H' },
        list: { class: List, inlineToolbar: true, config: { defaultStyle: 'unordered' } },
        code: { class: CodeTool, config: { placeholder: ph.code }, tunes: ['codeWrap'] },
        codeWrap: { class: CodeWrapTune },
        delimiter: { class: Delimiter },
        quote: { class: Quote, config: { quotePlaceholder: ph.quote, captionPlaceholder: ph.quoteCaption }, shortcut: 'CMD+SHIFT+O' },
        table: { class: Table, inlineToolbar: true, config: { rows: 3, cols: 3, withHeadings: true } },
        inlineCode: { class: InlineCode, shortcut: 'CMD+SHIFT+M' },
        image: {
          class: ImageTool,
          config: {
            features: { caption: 'optional' },
            uploader: {
              async uploadByFile(file) {
                try {
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) {
                  console.error('图片上传失败:', e)
                  return { success: 0 }
                }
              },
              async uploadByUrl(url) {
                try {
                  const result = await uploadService.uploadRemote(url, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) { return { success: 0 } }
              }
            }
          }
        },
        marker: { class: Marker },
        checklist: { class: Checklist, inlineToolbar: true },
        warning: {
          class: Warning,
          inlineToolbar: true,
          config: { titlePlaceholder: ph.warningTitle, messagePlaceholder: ph.warningMessage }
        },
        linkTool: {
          class: LinkTool,
          config: {
            endpoint: `${options.axiosBaseURL || ''}/v1/link-preview`,
            headers: { Authorization: options.authHeader ? options.authHeader() : '' }
          }
        },
        attaches: {
          class: AttachesTool,
          config: {
            buttonText: ph.attachButton,
            errorMessage: ph.attachError,
            uploader: {
              async uploadByFile(file) {
                try {
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return {
                    success: 1,
                    file: {
                      url: result.url || result.fileUrl || result,
                      name: result.fileName || file.name,
                      size: result.fileSize,
                      extension: result.extName
                    }
                  }
                } catch (e) {
                  console.error('文件上传失败:', e)
                  return { success: 0 }
                }
              }
            }
          }
        },
        references: {
          class: ReferencesTool,
          config: {
            axiosBaseURL: options.axiosBaseURL || '',
            getAuthHeader: () => ({ Authorization: options.authHeader ? options.authHeader() : '' }),
            uploader: {
              async uploadByFile(file) {
                try {
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) {
                  console.error('参考文档上传失败:', e)
                  return { success: 0 }
                }
              }
            }
          }
        },
        mediaGallery: {
          class: GalleryTool,
          config: {
            uploader: {
              async uploadByFile(file) {
                try {
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) {
                  console.error('画廊素材上传失败:', e)
                  return { success: 0 }
                }
              },
              async uploadByUrl(url) {
                try {
                  const result = await uploadService.uploadRemote(url, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) {
                  console.error('画廊图片链接抓取失败:', e)
                  return { success: 0 }
                }
              }
            }
          }
        },
        timeline: { class: TimelineTool },
        markdown: { class: MarkdownBlock, inlineToolbar: false, config: { axiosBaseURL: options.axiosBaseURL || '' } },
        embed: { class: EmbedVideoTool },
        video: {
          class: VideoTool,
          config: {
            uploader: {
              async uploadByFile(file) {
                try {
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
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
                  const result = await uploadService.uploadLocal(file, model, entityId)
                  return { success: 1, file: { url: result.url || result.fileUrl || result } }
                } catch (e) {
                  console.error('音频上传失败:', e)
                  return { success: 0 }
                }
              }
            },
            session: {
              async createSession() {
                return await uploadService.createSession(model, entityId, 'audio/*')
              },
              async getSession(sessionId, token) {
                return await uploadService.getSession(sessionId, token)
              }
            }
          }
        }
      }

      if (options.includeRecordTool) {
        tools.audioRecord = {
          class: RecordTool,
          config: { getNoteId: options.getRecordEntityId }
        }
      }

      this.editor = new EditorJS({
        holder: holderId,
        placeholder: ph.editor,
        autofocus: false,
        tools,
        data: options.data || undefined,
        onChange: () => {
          if (options.onChange) options.onChange()
        },
        // EditorJS 自身有一套独立于 vue-i18n 的内部 i18n 机制（块工具/菜单文案），
        // 且这套字典是模块级全局单例（I18n.currentDictionary），只有传入非空 messages 时
        // 才会调用 setDictionary() 覆盖它——英文分支必须显式传空字典触发重置，
        // 传 undefined 只会导致沿用上一次（通常是中文）构造过的编辑器留下的全局字典，
        // 表现上就像英文模式下菜单文案"写死"成中文了一样
        i18n: options.locale === 'zh-CN' ? {
          messages: {
            ui: {
              blockTunes: { toggler: { 'Click to tune': '点击调整', 'or drag to move': '或拖动移动' } },
              inlineToolbar: { converter: { 'Convert to': '转换为' } },
              toolbar: { toolbox: { Add: '添加' } }
            },
            toolNames: {
              Text: '文本', Heading: '标题', List: '列表', Quote: '引用',
              Code: '代码块', Delimiter: '分割线', Table: '表格', Image: '图片',
              InlineCode: '行内代码', Marker: '高亮', Checklist: '任务列表', Warning: '提示框', Attachment: '附件', Markdown: 'Markdown', Embed: '嵌入视频2', Video: '视频2', Audio: '音频', AudioRecord: '录音', Bold: '加粗', Italic: '斜体', Link: '链接'
            },
            tools: {
              header: { 'Heading 1': '标题 1', 'Heading 2': '标题 2', 'Heading 3': '标题 3' },
              list: { Ordered: '有序列表', Unordered: '无序列表' },
              quote: { 'Align Left': '左对齐', 'Align Center': '居中' },
              table: { 'With headings': '带表头', 'Without headings': '无表头', 'Add row above': '上方插入行', 'Add row below': '下方插入行', 'Delete row': '删除行', 'Add column to the left': '左侧插入列', 'Add column to the right': '右侧插入列', 'Delete column': '删除列' },
              image: { Caption: '图片说明', 'Select an Image': '选择图片', 'With border': '带边框', 'Stretch image': '拉伸图片', 'With background': '带背景', 'With caption': '图片说明' }
            },
            blockTunes: {
              delete: { Delete: '删除', 'Click to delete': '点击确认删除' },
              moveUp: { 'Move up': '上移' },
              moveDown: { 'Move down': '下移' }
            }
          }
        } : { messages: {} }
      })

      await this.editor.isReady
      if (!this.imageResizer) {
        this.imageResizer = createEditorImageResizer({
          getEditor: () => this.editor,
          getContainer: () => container,
          markDirtyAndSave: () => {
            if (options.onChange) options.onChange()
          }
        })
      }
      this.imageResizer.setupImageResize()
      this.setupCodeBlockAutoResizeHost(container)
      this.setupImagePasteHost({ container, model, entityId, placeholders: ph, onPasted: options.onChange })
      this.setupHeaderToggleShortcutHost(container)
      this.setupListCopyFixHost()
      this.editorUndo = new Undo({ editor: this.editor })
      if (options.data) this.editorUndo.initialize(options.data)
    },

    setupListCopyFixHost() {
      const fixClipboardText = (e) => {
        if (!e.clipboardData || !this.editor) return
        const count = this.editor.blocks.getBlocksCount()
        const selected = []
        for (let i = 0; i < count; i++) {
          const block = this.editor.blocks.getBlockByIndex(i)
          if (block && block.selected) selected.push(block)
        }
        if (selected.length === 0) return

        const text = selected.map(block => {
          const clone = block.holder.cloneNode(true)
          clone.querySelectorAll('.cdx-list__item').forEach(item => {
            item.insertAdjacentText('afterend', '\n')
          })
          return clone.textContent
        }).join('\n\n')

        e.clipboardData.setData('text/plain', text)
      }

      document.addEventListener('copy', fixClipboardText)
      document.addEventListener('cut', fixClipboardText)
      this._listCopyFixHandler = () => {
        document.removeEventListener('copy', fixClipboardText)
        document.removeEventListener('cut', fixClipboardText)
      }
    },

    setupHeaderToggleShortcutHost(container) {
      if (!container) return
      const handler = async (e) => {
        const isCmd = e.ctrlKey || e.metaKey
        if (!isCmd || !e.shiftKey || e.key.toUpperCase() !== 'H') return

        const index = this.editor.blocks.getCurrentBlockIndex()
        const block = this.editor.blocks.getBlockByIndex(index)
        if (!block || block.name !== 'header') return

        e.stopPropagation()
        e.preventDefault()
        const newBlock = await this.editor.blocks.convert(block.id, 'paragraph')
        this.editor.caret.setToBlock(newBlock, 'end')
      }

      container.addEventListener('keydown', handler, true)
      this._headerToggleHandler = () => container.removeEventListener('keydown', handler, true)
    },

    setupImagePasteHost({ container, model, entityId, placeholders, onPasted }) {
      if (!container) return
      const uploadService = this.$uploadService

      const handler = async (e) => {
        const clipboardData = e.clipboardData
        if (!clipboardMayContainImage(clipboardData)) return

        e.stopPropagation()
        e.preventDefault()

        const payload = await getClipboardImagePayload(clipboardData)
        if (!payload) {
          this.$message.warning(placeholders.clipboardImageReadFailed())
          return
        }

        const sizeText = payload.file ? ` (${(payload.file.size / 1024 / 1024).toFixed(1)}MB)` : ''
        const loadingMsg = this.$message({ message: placeholders.uploadingImage(sizeText), duration: 0 })
        try {
          const result = await uploadClipboardImage(uploadService, payload, model, entityId || 0)
          loadingMsg.close()
          const url = result.url || result.fileUrl || result
          await this.editor.blocks.insert('image', {
            file: { url },
            caption: '',
            withBorder: false,
            withBackground: false,
            stretched: false
          })
          if (onPasted) onPasted()
        } catch (err) {
          loadingMsg.close()
          console.error('粘贴图片上传失败:', err)
          const status = err?.response?.status
          if (status === 413) {
            this.$message.error(placeholders.imageTooLarge(sizeText))
          } else {
            this.$message.error(placeholders.imagePasteUploadFailed(sizeText))
          }
        }
      }

      container.addEventListener('paste', handler, true)
      this._imagePasteHandler = () => container.removeEventListener('paste', handler, true)
    },

    setupCodeBlockAutoResizeHost(container) {
      if (!container) return

      const autoResize = (textarea) => {
        const minH = 60
        textarea.style.height = 'auto'
        textarea.style.height = Math.max(textarea.scrollHeight, minH) + 'px'
        textarea.setAttribute('spellcheck', 'false')
        textarea.setAttribute('autocomplete', 'off')
        textarea.setAttribute('autocorrect', 'off')
        textarea.setAttribute('autocapitalize', 'off')
      }

      setTimeout(() => {
        container.querySelectorAll('.ce-code__textarea').forEach(autoResize)
      }, 200)

      container.addEventListener('input', (e) => {
        if (e.target && e.target.classList.contains('ce-code__textarea')) {
          autoResize(e.target)
        }
      })

      if (this._codeBlockObserver) {
        this._codeBlockObserver.disconnect()
      }
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
      this._codeBlockObserver.observe(container, { childList: true, subtree: true })
    },

    destroyEditorJsHost() {
      if (this._codeBlockObserver) {
        this._codeBlockObserver.disconnect()
        this._codeBlockObserver = null
      }
      if (this.imageResizer) {
        this.imageResizer.destroy()
        this.imageResizer = null
      }
      if (this._imagePasteHandler) {
        this._imagePasteHandler()
        this._imagePasteHandler = null
      }
      if (this._headerToggleHandler) {
        this._headerToggleHandler()
        this._headerToggleHandler = null
      }
      if (this._listCopyFixHandler) {
        this._listCopyFixHandler()
        this._listCopyFixHandler = null
      }
      if (this.editorUndo) {
        // editorjs-undo 把 keydown 监听器挂在容器节点上，靠监听容器的自定义"destroy"事件来
        // 移除自己——但 EditorJS 自身的 destroy() 从不派发这个事件。容器节点是复用的
        // （切换笔记/项目只改 id，不重建DOM），不手动补发这个事件，每切换一次就会在同一个节点上
        // 再叠一份 keydown 监听器，切换几次后按一次 Ctrl+Z 会同时触发多个僵尸实例的处理逻辑。
        try { this._editorJsHostContainer?.dispatchEvent(new Event('destroy')) } catch (e) { /* ignore */ }
        this.editorUndo = null
      }
      if (this.editor) {
        try { this.editor.destroy() } catch (e) { /* ignore */ }
        this.editor = null
      }
    }
  }
}
