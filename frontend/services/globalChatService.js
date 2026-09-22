/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

class GlobalChatService extends ApiService {
  getMessages(limit = 50) {
    return this.get('/v1/chat/messages', { params: { limit }, suppressErrorToast: true })
  }

  // 流式请求：用现有 axios 实例的 onDownloadProgress 增量读取响应文本，不用 fetch，
  // 这样 plugins/axios.js 里现成的 JWT 请求拦截器和401响应拦截器都照常生效。
  // SSE 每帧是 "data:{json}\n\n"（注意 data: 后面没有空格）。
  _streamViaAxios(url, body, onEvent) {
    let lastLength = 0
    let buffer = ''

    const parseNewChunk = (fullText) => {
      const newChunk = fullText.slice(lastLength)
      lastLength = fullText.length
      buffer += newChunk
      const frames = buffer.split('\n\n')
      buffer = frames.pop()
      for (const frame of frames) {
        const line = frame.split('\n').find(l => l.startsWith('data:'))
        if (!line) continue
        const json = line.slice(5).trim()
        if (!json) continue
        try {
          onEvent(JSON.parse(json))
        } catch (e) {
          // 单帧解析失败不影响后续帧
        }
      }
    }

    return this.$axios.post(url, body, {
      responseType: 'text',
      suppressErrorToast: true,
      onDownloadProgress: (progressEvent) => {
        parseNewChunk(progressEvent.target.responseText)
      }
    })
  }

  sendMessageStream(content, currentNoteId, attachments, onEvent) {
    return this._streamViaAxios('/v1/chat/messages', { content, currentNoteId, attachments }, onEvent)
  }

  resumeStream(proposalId, decision, onEvent) {
    return this._streamViaAxios('/v1/chat/resume', { proposalId, decision }, onEvent)
  }
}

export default GlobalChatService
