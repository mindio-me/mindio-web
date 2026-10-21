/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { createEditorImageResizer } from '~/utils/editorjsImageResize'
import { clipboardMayContainImage, getClipboardImagePayload, uploadClipboardImage } from '~/utils/clipboardImage'
import { parseImageCopyMarker } from '~/utils/editorjsImageCopy'
import { confirmDiscardEdits } from '~/utils/audioTranscriptionStore'
import { tweetSyndicationToken } from '~/utils/videoEmbedResolver'
import { editorjsI18nConfig } from '~/utils/editorjsI18n'

export default {
  data() {
    return {
      editor: null,
      editorUndo: null,
      imageResizer: null
    }
  },
  async beforeRouteLeave(to, from, next) {
    next(await confirmDiscardEdits())
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
        { default: CodeWrapTune }, { default: ImageCopyTune }, { default: Undo },
        { default: ReferencesTool }, { default: GalleryTool }, { default: TimelineTool }, { default: MathTool }, { default: StrikethroughTool }, { default: SuperscriptTool }
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
        import('~/utils/editorjsCodeWrapTune'), import('~/utils/editorjsImageCopy'),
        import('editorjs-undo'),
        import('~/utils/editorjsReferencesTool'), import('~/utils/editorjsGalleryTool'), import('~/utils/editorjsTimelineTool'), import('~/utils/editorjsMathTool'), import('~/utils/editorjsStrikethroughTool'), import('~/utils/editorjsSuperscriptTool')
      ])

      // @editorjs/warning 自带 sanitize 是 message: {}，保存时只放行行内工具的标签、会清掉 <br>，
      // 多行提示框（含 Markdown callout 转换来的）保存后塌成一行；这里额外放行 <br>
      class WarningWithBreaks extends Warning {
        static get sanitize() {
          return { title: {}, message: { br: true } }
        }
      }

      const uploadService = this.$uploadService
      const model = options.model
      const entityId = options.entityId || 0
      // 不能把 $audioTranscriptionService 这个类实例直接放进工具 config：Editor.js 初始化时
      // ut({}, internalTools, config.tools) 会把配置深合并进新对象，类实例被当成普通对象拆开，
      // 只剩自身属性 $axios，原型上的 list/trigger 全丢（面板报 service.list is not a function）。
      // 函数在深合并时按引用原样保留，所以只传两个包装函数。
      const audioTranscriptions = this.$audioTranscriptionService
      const transcriptionService = audioTranscriptions
        ? {
            list: (noteId) => audioTranscriptions.list(noteId),
            trigger: (...args) => audioTranscriptions.trigger(...args),
            saveUtterances: (...args) => audioTranscriptions.saveUtterances(...args),
            update: (...args) => audioTranscriptions.update(...args)
          }
        : null
      const ph = options.placeholders

      const tools = {
        header: { class: Header, inlineToolbar: true, config: { placeholder: ph.header, levels: [1, 2, 3, 4, 5, 6], defaultLevel: 2 }, shortcut: 'CMD+SHIFT+H' },
        list: { class: List, inlineToolbar: true, config: { defaultStyle: 'unordered' } },
        code: { class: CodeTool, config: { placeholder: ph.code }, tunes: ['codeWrap'] },
        codeWrap: { class: CodeWrapTune },
        delimiter: { class: Delimiter },
        quote: { class: Quote, inlineToolbar: true, config: { quotePlaceholder: ph.quote, captionPlaceholder: ph.quoteCaption }, shortcut: 'CMD+SHIFT+O' },
        table: { class: Table, inlineToolbar: true, config: { rows: 3, cols: 3, withHeadings: true } },
        inlineCode: { class: InlineCode, shortcut: 'CMD+SHIFT+M' },
        imageCopy: { class: ImageCopyTune, config: { notify: (type, message) => this.$message[type](message) } },
        image: {
          class: ImageTool,
          tunes: ['imageCopy'],
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
        strikethrough: { class: StrikethroughTool, shortcut: 'CMD+SHIFT+X' },
        superscript: { class: SuperscriptTool },
        math: { class: MathTool },
        checklist: { class: Checklist, inlineToolbar: true },
        warning: {
          class: WarningWithBreaks,
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
                      // attaches 工具的标题取自 file.title，缺失时会退回占位符 "File title"
                      title: result.fileName || file.name,
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
            },
            // X 推文封面浏览器跨域拿不到，经后端转一手；失败返回 null，画廊退回通用占位图
            fetchTweetPoster: async (tweetId) => {
              try {
                const res = await this.$axios.get('/v1/embed/tweet-poster', {
                  params: { id: tweetId, token: tweetSyndicationToken(tweetId) }
                })
                return res.data?.posterUrl || null
              } catch (e) {
                return null
              }
            },
            // 弹窗里用原生播放器播推文视频：点开时现取 mp4 直链（不存进笔记，避免过期）；
            // 失败返回 null，弹窗退回推文卡片
            fetchTweetVideo: async (tweetId) => {
              try {
                const res = await this.$axios.get('/v1/embed/tweet-video', {
                  params: { id: tweetId, token: tweetSyndicationToken(tweetId) }
                })
                return res.data?.videoUrl ? res.data : null
              } catch (e) {
                return null
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
            getNoteId: options.getRecordEntityId || null,
            transcriptionService,
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
          config: { getNoteId: options.getRecordEntityId, transcriptionService }
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
        // 中文字典和"英文必须传空字典"的原因见 editorjsI18n
        i18n: editorjsI18nConfig(options.locale)
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
        const copiedImage = clipboardData && parseImageCopyMarker(clipboardData.getData('text/html'))
        if (copiedImage) {
          e.stopPropagation()
          e.preventDefault()
          await this.insertCopiedImageBlock(copiedImage)
          if (onPasted) onPasted()
          return
        }
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

    // 站内「复制图片」粘贴回来：直接引用原文件，说明/边框/背景/拉伸/宽度一并带过来
    async insertCopiedImageBlock(data) {
      const { widthPercent, ...imageData } = data
      const block = await this.editor.blocks.insert('image', imageData)
      if (!widthPercent || !block) return
      // 宽度不在 ImageTool 的数据里，由 imageResizer 按 DOM 宽度在保存时回写；它给新图片按序号套旧宽度
      // 的 MutationObserver 回调先于下一帧执行，这里等一帧再写，保证以复制来的宽度为准
      requestAnimationFrame(() => {
        const el = block.holder && block.holder.querySelector('.image-tool__image')
        if (el) el.style.width = widthPercent + '%'
      })
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
