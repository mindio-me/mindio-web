<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="chat-panel">
    <div class="chat-header">
      <span class="chat-header-title">
        <i class="el-icon-chat-dot-round"></i>
        {{ $t('workspace.chat.title') }}
      </span>
      <button class="chat-header-close" @click="$emit('close')">
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
              <div class="chat-assistant-actions">
                <button
                  type="button"
                  class="chat-assistant-action-btn"
                  :title="copiedMessageId === msg.id ? $t('workspace.chat.copied') : $t('workspace.chat.copy')"
                  @click="copyMessage(msg)"
                >
                  <i :class="copiedMessageId === msg.id ? 'el-icon-check' : 'el-icon-document-copy'"></i>
                </button>
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

        <div
          v-for="confirmation in pendingConfirmations"
          :key="confirmation.proposalId"
          class="chat-confirm-card"
        >
          <div class="chat-confirm-card__text">{{ confirmationSummary(confirmation) }}</div>
          <div class="chat-confirm-card__details">
            <div
              v-for="entry in previewEntries(confirmation)"
              :key="entry.key"
              class="chat-confirm-card__detail-row"
            >
              <span class="chat-confirm-card__detail-key">{{ entry.key }}</span>
              <span class="chat-confirm-card__detail-value">{{ entry.value }}</span>
            </div>
          </div>
          <div class="chat-confirm-card__actions">
            <el-button
              size="mini"
              type="primary"
              :loading="confirmation.responding"
              @click="respondToConfirmation(confirmation, 'accept')"
            >接受</el-button>
            <el-button
              size="mini"
              :disabled="confirmation.responding"
              @click="respondToConfirmation(confirmation, 'reject')"
            >拒绝</el-button>
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
</template>

<script>
import { renderMarkdown } from '~/utils/markdown'

export default {
  name: 'ChatPanel',
  props: {
    noteId: { type: Number, default: null },
  },
  data() {
    return {
      historyLoaded: false,
      loadingHistory: false,
      historyError: false,
      messages: [],
      input: '',
      sending: false,
      searchingQuery: null,
      liveAssistantText: '',
      pendingAttachments: [],
      pendingConfirmations: [],
      speechSupported: false,
      recognizing: false,
      recognition: null,
      // /workspace/notes 是"列表+右侧预览/编辑"单页模式，切换笔记不改URL，路由识别不到，
      // 靠 notes/index.vue 广播出来的当前笔记id兜底（见下面的事件监听）。
      broadcastNoteId: null,
      linkingCitationKey: null,
      copiedMessageId: null
    }
  },
  computed: {
    currentNoteId() {
      // 传入 noteId 优先（笔记页停靠时用）
      if (this.noteId != null) return this.noteId
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
    this.$nuxt.$on('workspace:current-note-id', this.onBroadcastNoteId)
    // current-note-id 只在切换笔记时广播一次、不回放；惰性挂载的本面板会错过挂载前
    // 的那次广播，主动问一声让页面把当前值回传过来
    this.$nuxt.$emit('workspace:request-current-note-id')
    this.speechSupported = process.client && !!(window.SpeechRecognition || window.webkitSpeechRecognition)
    // 挂载即"首次可见"：抽屉用 v-if="hasOpened" 惰性挂载，笔记页用 v-if="aiPanelActive"
    if (!this.historyLoaded) this.loadHistory()
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:current-note-id', this.onBroadcastNoteId)
    if (this.recognition) this.recognition.stop()
  },
  methods: {
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
            return
          }
          this.handleStreamEvent(event)
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
    // sendMessage() 自己处理 user_message 事件（需要闭包里的 optimisticUser/userMessageConfirmed
    // 变量），其余事件类型统一转发到这里；resumeStream 的回调也直接复用本方法。
    handleStreamEvent(event) {
      if (event.type === 'text_delta') {
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
      } else if (event.type === 'confirm_request') {
        if (this.liveAssistantText) {
          this.messages.push({
            id: `pending-text-${Date.now()}`, role: 'ASSISTANT', content: this.liveAssistantText,
            citations: [], createdAt: new Date().toISOString()
          })
          this.liveAssistantText = ''
        }
        this.pendingConfirmations.push({
          proposalId: event.proposalId,
          blockType: event.blockType,
          noteId: event.noteId,
          preview: event.preview,
          responding: false
        })
      } else if (event.type === 'block_updated') {
        this.$nuxt.$emit('topic-block-updated', {
          noteId: event.noteId,
          blockType: event.blockType,
          items: event.items
        })
      } else if (event.type === 'media_block_updated') {
        this.$nuxt.$emit('media-block-updated', {
          noteId: event.noteId,
          blockId: event.blockId,
          data: event.data
        })
      }
      this.$nextTick(this.scrollToBottom)
    },
    async respondToConfirmation(confirmation, decision) {
      if (this.sending) return
      confirmation.responding = true
      this.sending = true
      try {
        await this.$globalChatService.resumeStream(confirmation.proposalId, decision, (event) => {
          this.handleStreamEvent(event)
        })
        const idx = this.pendingConfirmations.indexOf(confirmation)
        if (idx !== -1) this.pendingConfirmations.splice(idx, 1)
      } catch (e) {
        // 保留卡片让用户重试——网络层失败不代表服务端没处理，但至少不能悄无声息地
        // 把卡片丢掉，让用户以为操作完成了。
        this.$message.error(this.$t('workspace.chat.confirmFailed'))
      } finally {
        confirmation.responding = false
        this.sending = false
      }
    },
    confirmationSummary(confirmation) {
      const labels = { references: '参考资料', mediaGallery: '媒体画廊', timeline: '时间线', checklist: '任务清单' }
      const label = labels[confirmation.blockType] || confirmation.blockType
      const preview = confirmation.preview || {}
      const title = preview.title || preview.date || preview.embedUrl || preview.text || ''
      return `建议往「${label}」添加：${title}`
    },
    previewEntries(confirmation) {
      const preview = confirmation.preview || {}
      return Object.entries(preview)
        .filter(([, value]) => value !== null && value !== undefined && value !== '')
        .map(([key, value]) => ({ key, value }))
    },
    renderContent(text) {
      return renderMarkdown(text, { axiosBaseURL: this.$axios.defaults.baseURL })
    },
    onCitationClick(citation) {
      if (citation.sourceType === 'WEB') {
        window.open(citation.sourceUrl, '_blank', 'noopener')
        return
      }
      if (citation.sourceType === 'NOTE') {
        if (this.noteId != null) {
          // 已经停靠在 /workspace/notes 里：不要走路由。实测哪怕只改query、同一个路由，
          // router.push 也会把这个页面整个重新挂载（右栏AI助手状态、笔记列表全部重置，
          // 观感像整页刷新），而且这个页面本来的设计就是"切换笔记不改URL"（原地更新
          // activeNoteId），所以改用它已有的广播事件机制，直接原地切换要显示的笔记。
          this.$nuxt.$emit('workspace:open-note-request', citation.sourceId)
          return
        }
        // 不在笔记页里（浮动抽屉）：这种是真的要跳转过去，走路由 + /workspace/notes 自己
        // mounted() 里对 ?openNoteId= 的一次性处理逻辑，同时把抽屉关掉，不然会悬浮挡住
        // 刚跳转过去的笔记内容。
        this.$emit('close')
        this.$router.push(`/workspace/notes?openNoteId=${citation.sourceId}`).catch(() => {})
        return
      }
      this.$emit('close')
      if (citation.sourceType === 'LOCAL_MEDIA') {
        this.$router.push('/workspace/local-media').catch(() => {})
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
    async copyMessage(msg) {
      try {
        await navigator.clipboard.writeText(msg.content)
        this.copiedMessageId = msg.id
        setTimeout(() => {
          if (this.copiedMessageId === msg.id) this.copiedMessageId = null
        }, 1500)
      } catch (e) {
        this.$message.error(this.$t('workspace.chat.copyFailed'))
      }
    },
    scrollToBottom() {
      const el = this.$refs.messages
      if (!el) return
      el.scrollTop = el.scrollHeight
      // $nextTick 只保证 DOM 打上去了，不保证浏览器已经完成排版——历史消息里常有长文本/
      // 代码块/引用，实际 reflow 有时会比这次回调晚一点点，这时读到的 scrollHeight 还不是
      // 最终高度，导致停在半山腰而不是真正的底部。再叠一次 rAF，确保这次读到的是浏览器
      // 完成排版之后的高度。
      window.requestAnimationFrame(() => {
        if (el) el.scrollTop = el.scrollHeight
      })
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

.chat-assistant-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.chat-assistant-action-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 14px;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover { background: var(--bg-secondary, #f4f4f5); color: var(--text-color); }
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
  color: var(--text-color) !important;

  &::placeholder {
    color: var(--text-muted) !important;
  }
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

.chat-confirm-card {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 10px;
  padding: 10px 14px;
  margin: 12px 0;
  background: var(--bg-secondary, #f4f4f5);
}

.chat-confirm-card__text {
  font-size: 13px;
  color: var(--text-color);
  line-height: 1.6;
  margin-bottom: 8px;
}

.chat-confirm-card__details {
  margin-bottom: 8px;
}

.chat-confirm-card__detail-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-confirm-card__detail-key {
  color: var(--text-muted);
  flex-shrink: 0;
}

.chat-confirm-card__detail-value {
  color: var(--text-color);
}

.chat-confirm-card__actions {
  display: flex;
  gap: 8px;
}
</style>
