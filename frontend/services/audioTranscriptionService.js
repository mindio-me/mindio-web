/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ApiService from './api'

/** 录音转写记录：结果不在笔记正文里，按笔记单独拉取。详见 2026-10-10-recording-auto-notes-design.md */
class AudioTranscriptionService extends ApiService {
  list(noteId) {
    return this.get(`/v1/notes/${noteId}/audio-transcriptions`)
  }

  /** AUTO 被过滤（设置关闭、太短、已有记录等）时后端回 204，这里返回 null */
  async trigger(noteId, blockId, trigger = 'MANUAL', stage = 'FULL') {
    const params = new URLSearchParams({ trigger, stage })
    const res = await this.post(`/v1/notes/${noteId}/audio-transcriptions/${encodeURIComponent(blockId)}?${params}`)
    return res || null
  }

  /** 改错：只提交改过的句子 [{ index, text }]；记录不是 DONE 时后端回 409 */
  saveUtterances(noteId, blockId, edits) {
    return this.put(`/v1/notes/${noteId}/audio-transcriptions/${encodeURIComponent(blockId)}/utterances`, edits)
  }

  /** body: { speakerNames?, scene? }；改场景会自动重新整理 */
  update(noteId, blockId, body) {
    return this.patch(`/v1/notes/${noteId}/audio-transcriptions/${encodeURIComponent(blockId)}`, body)
  }
}

export default AudioTranscriptionService
