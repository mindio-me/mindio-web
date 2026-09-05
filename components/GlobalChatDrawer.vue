<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-drawer
    :visible.sync="open"
    direction="rtl"
    size="640px"
    :with-header="false"
    :append-to-body="true"
    class="global-chat-drawer"
  >
    <div class="chat-panel">
      <div class="chat-header">
        <span class="chat-header-title">
          <i class="el-icon-chat-dot-round"></i>
          {{ $t('workspace.chat.title') }}
        </span>
        <button class="chat-header-close" @click="open = false">
          <i class="el-icon-close"></i>
        </button>
      </div>

      <div v-if="currentNoteId" class="chat-note-hint">
        <i class="el-icon-paperclip"></i>
        {{ $t('workspace.chat.currentNoteHint') }}
      </div>

      <div class="chat-messages" ref="messages">
        <div v-if="loadingHistory" class="chat-history-loading">
          <i class="el-icon-loading"></i>
        </div>
        <div v-else-if="historyError && messages.length === 0" class="chat-history-error">
          <span>{{ $t('workspace.chat.historyLoadFailed') }}</span>
          <el-button size="mini" @click="loadHistory">{{ $t('workspace.chat.retry') }}</el-button>
        </div>
        <div v-else-if="historyLoaded && messages.length === 0 && !sending" class="chat-empty-state">
          {{ $t('workspace.chat.emptyState') }}
        </div>
        <template v-else>
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="chat-turn"
            :class="msg.role === 'USER' ? 'is-user' : 'is-assistant'"
          >
            <div v-if="msg.role === 'USER'" class="chat-user-bubble">
              <div v-if="msg.attachments && msg.attachments.length" class="chat-attachment-preview chat-attachment-preview-sent">
                <div v-for="(att, aidx) in msg.attachments" :key="aidx" class="chat-attachment-chip">
                  <img v-if="att.type === 'image'" :src="att.url" class="chat-attachment-thumb" />
                  <i v-else class="el-icon-document"></i>
                  <span class="chat-attachment-name">{{ att.fileName }}</span>
                </div>
              </div><span class="chat-user-text">{{ msg.content }}</span>
            </div>
            <div v-else class="chat-assistant-block">
              <div class="chat-assistant-icon"><i class="el-icon-chat-dot-round"></i></div>
              <div class="chat-assistant-body">
                <div class="chat-assistant-text" v-html="renderContent(msg.content)"></div>
                <div v-if="msg.citations && msg.citations.length" class="chat-citations">
                  <div v-for="(c, idx) in msg.citations" :key="idx" class="chat-citation-item">
                    <span class="chat-citation-chip" @click="onCitationClick(c)">{{ c.title }}</span>
                    <button
                      v-if="currentNoteId && (c.sourceType === 'WEB' || c.sourceType === 'CLIP')"
                      type="button"
                      class="chat-citation-link-btn"
                      :disabled="linkingCitationKey === (msg.id + '-' + idx)"
                      @click.stop="onLinkCitationToNote(c, msg.id + '-' + idx)"
                    >{{ $t('workspace.chat.linkToNote') }}</button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="sending" class="chat-turn is-assistant">
            <div class="chat-assistant-block">
              <div class="chat-assistant-icon"><i class="el-icon-chat-dot-round"></i></div>
              <div class="chat-assistant-body">
                <div v-if="searchingQuery" class="chat-tool-indicator">
                  <i class="el-icon-loading"></i>
                  {{ $t('workspace.chat.searching', { query: searchingQuery }) }}
                </div>
                <div v-if="liveAssistantText" class="chat-assistant-text" v-html="renderContent(liveAssistantText)"></div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-composer">
        <div v-if="pendingAttachments.length" class="chat-attachment-preview">
          <div v-for="(att, idx) in pendingAttachments" :key="idx" class="chat-attachment-chip">
            <img v-if="att.type === 'image'" :src="att.previewUrl" class="chat-attachment-thumb" />
            <i v-else class="el-icon-document"></i>
            <span class="chat-attachment-name">{{ att.fileName }}</span>
            <i
              class="el-icon-close chat-attachment-remove"
              :title="$t('workspace.chat.removeAttachment')"
              @click="removeAttachment(idx)"
            ></i>
          </div>
        </div>

        <el-input
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          :placeholder="$t('workspace.chat.placeholder')"
          :disabled="sending"
          class="chat-composer-textarea"
          @keydown.enter.native.exact.prevent="sendMessage"
          @paste.native="onPaste"
        />

        <div class="chat-composer-toolbar">
          <div class="chat-composer-toolbar-left">
            <el-dropdown trigger="click" @command="onAttachCommand">
              <button class="chat-icon-btn" type="button">
                <i class="el-icon-plus"></i>
              </button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="image">{{ $t('workspace.chat.attachImage') }}</el-dropdown-item>
                <el-dropdown-item command="document">{{ $t('workspace.chat.attachDocument') }}</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
            <input ref="fileInput" type="file" class="chat-file-input" @change="onFileSelected" />
          </div>
          <div class="chat-composer-toolbar-right">
            <button
              v-if="speechSupported"
              type="button"
              class="chat-icon-btn"
              :class="{ 'is-recording': recognizing }"
              :title="$t('workspace.chat.voiceInput')"
              @click="toggleVoiceInput"
            >
              <i class="el-icon-microphone"></i>
            </button>
            <button
              type="button"
              class="chat-send-btn"
              :disabled="(!input.trim() && pendingAttachments.length === 0) || sending"
              @click="sendMessage"
            >
              <i class="el-icon-position"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { renderMarkdown } from '~/utils/markdown'

export default {
  name: 'GlobalChatDrawer',
  data() {
    return {
      open: false,
      historyLoaded: false,
      loadingHistory: false,
      historyError: false,
      messages: [],
      input: '',
      sending: false,
      searchingQuery: null,
      liveAssistantText: '',
      pendingAttachments: [],
      speechSupported: false,
      recognizing: false,
      recognition: null,
      // /workspace/notes 是"列表+右侧预览/编辑"单页模式，切换笔记不改URL，路由识别不到，
      // 靠 notes/index.vue 广播出来的当前笔记id兜底（见下面的事件监听）。
      broadcastNoteId: null,
      linkingCitationKey: null
    }
  },
  computed: {
    currentNoteId() {
      // 笔记有三种独立路由页面：富文本/Markdown的编辑页(/workspace/notes/{id}/edit)、
      // EditorJS笔记的编辑页(/workspace/editor?id={id}，id是查询参数不是路径段)、
      // 以及只读查看页(/workspace/notes/{id})——三种都算"当前笔记"，路由能直接识别。
      const editRouteMatch = this.$route.path.match(/^\/workspace\/notes\/(\d+)\/edit$/)
      if (editRouteMatch) return Number(editRouteMatch[1])

      if (this.$route.path === '/workspace/editor' && this.$route.query.id) {
        const editorJsId = Number(this.$route.query.id)
        if (!Number.isNaN(editorJsId)) return editorJsId
      }

      const viewRouteMatch = this.$route.path.match(/^\/workspace\/notes\/(\d+)$/)
      if (viewRouteMatch) return Number(viewRouteMatch[1])

      // 上面都没匹配到，说明是笔记列表主界面这种不换路由的场景，用广播值兜底
      return this.broadcastNoteId
    }
  },
  mounted() {
    this.$nuxt.$on('workspace:chat:toggle', this.toggleOpen)
    this.$nuxt.$on('workspace:current-note-id', this.onBroadcastNoteId)
    this.speechSupported = process.client && !!(window.SpeechRecognition || window.webkitSpeechRecognition)
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:chat:toggle', this.toggleOpen)
    this.$nuxt.$off('workspace:current-note-id', this.onBroadcastNoteId)
    if (this.recognition) this.recognition.stop()
  },
  methods: {
    toggleOpen() {
      this.open = !this.open
      if (this.open && !this.historyLoaded) {
        this.loadHistory()
      }
    },
    onBroadcastNoteId(noteId) {
      this.broadcastNoteId = noteId || null
    },
    async loadHistory() {
      this.loadingHistory = true
      this.historyError = false
      try {
        const res = await this.$globalChatService.getMessages(50)
        this.messages = res || []
        this.historyLoaded = true
        this.$nextTick(this.scrollToBottom)
      } catch (e) {
        this.historyError = true
      } finally {
        this.loadingHistory = false
      }
    },
    async sendMessage() {
      const content = this.input.trim()
      if ((!content && this.pendingAttachments.length === 0) || this.sending) return

      const attachmentsToSend = this.pendingAttachments.map(a => ({
        type: a.type, mimeType: a.mimeType, base64Data: a.base64Data, url: a.url, fileName: a.fileName
      }))
      const attachmentsToRestore = this.pendingAttachments

      const optimisticUser = {
        id: `pending-${Date.now()}`,
        role: 'USER',
        content,
        citations: [],
        attachments: attachmentsToSend.map(a => ({ type: a.type, url: a.url, fileName: a.fileName })),
        createdAt: new Date().toISOString()
      }
      this.messages.push(optimisticUser)
      this.input = ''
      this.pendingAttachments = []
      this.sending = true
      this.searchingQuery = null
      this.liveAssistantText = ''
      this.$nextTick(this.scrollToBottom)

      let userMessageConfirmed = false
      try {
        await this.$globalChatService.sendMessageStream(content, this.currentNoteId, attachmentsToSend, (event) => {
          if (event.type === 'user_message') {
            userMessageConfirmed = true
            optimisticUser.id = event.id
            optimisticUser.createdAt = event.createdAt
          } else if (event.type === 'text_delta') {
            this.liveAssistantText += event.text
            this.searchingQuery = null
          } else if (event.type === 'tool_call') {
            this.searchingQuery = event.query
          } else if (event.type === 'done') {
            this.messages.push({
              id: event.id, role: 'ASSISTANT', content: event.content,
              citations: event.citations || [], createdAt: event.createdAt
            })
            this.liveAssistantText = ''
            this.searchingQuery = null
          } else if (event.type === 'error') {
            this.messages.push({
              id: `error-${Date.now()}`, role: 'ASSISTANT', content: event.content,
              citations: [], createdAt: new Date().toISOString()
            })
            this.liveAssistantText = ''
            this.searchingQuery = null
          }
          this.$nextTick(this.scrollToBottom)
        })

        if (this.liveAssistantText) {
          this.messages.push({
            id: `error-${Date.now()}`, role: 'ASSISTANT', content: this.liveAssistantText,
            citations: [], createdAt: new Date().toISOString()
          })
          this.liveAssistantText = ''
        }
      } catch (e) {
        if (userMessageConfirmed) {
          // 服务端已经收到并保存了这条消息（甚至可能已经生成了部分回复），只是连接中途断开——
          // 不能把已发送的消息当草稿回滚，否则用户重发会在历史里造成重复。
          if (this.liveAssistantText) {
            this.messages.push({
              id: `error-${Date.now()}`, role: 'ASSISTANT', content: this.liveAssistantText,
              citations: [], createdAt: new Date().toISOString()
            })
            this.liveAssistantText = ''
          }
          this.$message.error(this.$t('workspace.chat.connectionLost'))
        } else {
          const idx = this.messages.indexOf(optimisticUser)
          if (idx !== -1) this.messages.splice(idx, 1)
          this.input = content
          this.pendingAttachments = attachmentsToRestore.concat(this.pendingAttachments)
          this.$message.error(this.$t('workspace.chat.sendFailed'))
        }
      } finally {
        this.sending = false
        this.searchingQuery = null
      }
    },
    renderContent(text) {
      return renderMarkdown(text, { axiosBaseURL: this.$axios.defaults.baseURL })
    },
    onCitationClick(citation) {
      if (citation.sourceType === 'WEB') {
        window.open(citation.sourceUrl, '_blank', 'noopener')
        return
      }
      this.open = false
      if (citation.sourceType === 'NOTE') {
        this.$router.push(`/workspace/notes/${citation.sourceId}/edit`).catch(() => {})
      } else {
        this.$router.push(`/workspace/clips?highlightId=${citation.sourceId}`).catch(() => {})
      }
    },
    async onLinkCitationToNote(citation, key) {
      if (!this.currentNoteId) return
      this.linkingCitationKey = key
      try {
        if (citation.sourceType === 'WEB') {
          await this.$clipService.linkClipFromUrl(this.currentNoteId, { url: citation.sourceUrl, titleHint: citation.title })
        } else {
          await this.$clipService.linkClipToNote(this.currentNoteId, citation.sourceId)
        }
        this.$message.success(this.$t('workspace.chat.linkToNoteSuccess'))
      } catch (e) {
        this.$message.error(this.$t('workspace.chat.linkToNoteFailed'))
      } finally {
        this.linkingCitationKey = null
      }
    },
    scrollToBottom() {
      const el = this.$refs.messages
      if (el) el.scrollTop = el.scrollHeight
    },
    onAttachCommand(command) {
      this.$refs.fileInput.setAttribute('accept', command === 'image' ? 'image/*' : '*/*')
      this.$refs.fileInput.click()
    },
    async onFileSelected(event) {
      const file = event.target.files && event.target.files[0]
      event.target.value = ''
      if (!file) return
      await this.attachFile(file)
    },
    async onPaste(event) {
      const items = event.clipboardData && event.clipboardData.items
      if (!items) return
      // clipboardData.items 只在事件同步派发期间有效，第一次 await 之后再调用 getAsFile()
      // 会拿到 null，所以先把这一轮的所有图片文件同步取出来，再逐个异步处理。
      const files = []
      for (const item of items) {
        if (item.type && item.type.startsWith('image/')) {
          const file = item.getAsFile()
          if (file) files.push(file)
        }
      }
      if (files.length === 0) return
      event.preventDefault()
      for (const file of files) {
        await this.attachFile(file)
      }
    },
    async attachFile(file) {
      const MAX_SIZE = 10 * 1024 * 1024
      if (file.size > MAX_SIZE) {
        this.$message.error(this.$t('workspace.chat.attachmentTooLarge'))
        return
      }
      const isImage = file.type.startsWith('image/')
      let base64Data
      try {
        base64Data = await this.fileToBase64(file)
      } catch (e) {
        this.$message.error(this.$t('workspace.chat.attachmentUploadFailed'))
        return
      }
      // 持久化失败不阻塞发送：仍然用原始base64传给大模型，只是历史记录里没有缩略图/文件名引用
      // （见 2026-09-02-global-chat-multimodal-input-design.md §2）。
      let uploadResult = null
      try {
        uploadResult = await this.$uploadService.uploadLocal(file, 'chat', 0)
      } catch (e) {
        uploadResult = null
      }
      this.pendingAttachments.push({
        type: isImage ? 'image' : 'document',
        mimeType: file.type || 'application/octet-stream',
        base64Data,
        url: uploadResult ? uploadResult.url : null,
        fileName: (uploadResult && uploadResult.fileName) || file.name,
        previewUrl: isImage ? (uploadResult ? uploadResult.url : URL.createObjectURL(file)) : null
      })
    },
    fileToBase64(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => {
          const result = reader.result
          const base64 = result.includes(',') ? result.substring(result.indexOf(',') + 1) : result
          resolve(base64)
        }
        reader.onerror = reject
        reader.readAsDataURL(file)
      })
    },
    removeAttachment(idx) {
      this.pendingAttachments.splice(idx, 1)
    },
    toggleVoiceInput() {
      if (!this.speechSupported) return
      if (this.recognizing) {
        this.recognition && this.recognition.stop()
        return
      }
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
      this.recognition = new SpeechRecognition()
      this.recognition.lang = this.$i18n.locale === 'zh-CN' ? 'zh-CN' : 'en-US'
      this.recognition.interimResults = true
      this.recognition.continuous = false
      const baseInput = this.input
      this.recognition.onstart = () => { this.recognizing = true }
      this.recognition.onresult = (event) => {
        let transcript = ''
        for (let i = 0; i < event.results.length; i++) {
          transcript += event.results[i][0].transcript
        }
        this.input = baseInput ? `${baseInput} ${transcript}` : transcript
      }
      this.recognition.onerror = () => { this.recognizing = false }
      this.recognition.onend = () => { this.recognizing = false }
      this.recognition.start()
    }
  }
}
</script>

<style scoped lang="scss">
.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.chat-header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 6px;
}

.chat-header-close {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 16px;

  &:hover {
    color: var(--text-color);
  }
}

.chat-note-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
  min-height: 0;
}

.chat-history-loading,
.chat-history-error,
.chat-empty-state {
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
  padding: 32px 12px;
}

.chat-history-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.chat-turn { margin-bottom: 20px; }
.chat-turn.is-user { display: flex; justify-content: flex-end; }

.chat-user-bubble {
  background: var(--bg-secondary, #e8e6e1);
  color: var(--text-color);
  padding: 10px 16px;
  border-radius: 14px;
  max-width: 78%;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-assistant-block {
  display: flex;
  gap: 10px;
}

.chat-assistant-icon {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #cc785c;
  color: #fff;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  margin-top: 2px;
}

.chat-assistant-body {
  flex: 1;
  min-width: 0;
}

.chat-assistant-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-color);
  word-break: break-word;

  ::v-deep pre.md-code-block {
    background: var(--bg-secondary, #f4f4f5);
    border-radius: 6px;
    padding: 10px 12px;
    overflow-x: auto;
    font-size: 13px;
  }
  ::v-deep code.md-inline-code {
    background: var(--bg-secondary, #f4f4f5);
    border-radius: 4px;
    padding: 1px 5px;
    font-size: 13px;
  }
}

.chat-tool-indicator {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.chat-citations {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-citation-chip {
  font-size: 12px;
  color: #409eff;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 999px;
  padding: 2px 10px;
  cursor: pointer;

  &:hover {
    background: rgba(64, 158, 255, 0.08);
  }
}

.chat-citation-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.chat-citation-link-btn {
  border: none;
  background: transparent;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  &:disabled {
    opacity: .5;
    cursor: not-allowed;
  }
}

.chat-composer {
  border: 1px solid var(--border-color, #dcdfe6);
  border-radius: 16px;
  padding: 8px 8px 8px 14px;
  margin-top: 8px;
  flex-shrink: 0;
  background: var(--bg-color, #fff);
  transition: border-color 0.2s;

  &:focus-within { border-color: #409eff; }
}

.chat-composer-textarea ::v-deep .el-textarea__inner {
  border: none;
  box-shadow: none;
  padding: 6px 0;
  resize: none;
  background: transparent;
}

.chat-composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-composer-toolbar-left,
.chat-composer-toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-icon-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 18px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover { background: var(--bg-secondary, #f4f4f5); color: var(--text-color); }
  &.is-recording { color: #f56c6c; }
}

.chat-send-btn {
  border: none;
  background: #409eff;
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;

  &:hover { background: #66b1ff; }
  &:disabled { background: var(--border-color, #dcdfe6); cursor: not-allowed; }
}

.chat-file-input { display: none; }

.chat-attachment-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.chat-attachment-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-secondary, #f4f4f5);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
  color: var(--text-color);
  max-width: 200px;
}

.chat-attachment-thumb {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.chat-attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-attachment-remove {
  cursor: pointer;
  color: var(--text-muted);
  flex-shrink: 0;

  &:hover { color: #f56c6c; }
}

.chat-attachment-preview-sent {
  margin-bottom: 6px;
}
</style>
