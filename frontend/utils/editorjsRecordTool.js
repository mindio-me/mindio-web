/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import recordingController from './recordingController'

const STYLE_ID = 'record-tool-injected-styles'

function injectStyles() {
  if (typeof document === 'undefined' || document.getElementById(STYLE_ID)) return
  const el = document.createElement('style')
  el.id = STYLE_ID
  el.textContent = `
.record-tool__placeholder {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 2px dashed #d5d9f0;
  border-radius: 10px;
  cursor: pointer;
  color: #b0b6cc;
  font-size: 14px;
  transition: all 0.2s;
  user-select: none;
}
.record-tool__placeholder:hover {
  border-color: #667eea;
  color: #667eea;
  background: rgba(102,126,234,0.04);
}
.record-tool__placeholder--recording {
  cursor: default;
  color: #ef4444;
  border-color: #f3c9c9;
  background: rgba(239,68,68,0.03);
}
.record-tool__placeholder--recording:hover {
  border-color: #f3c9c9;
  color: #ef4444;
  background: rgba(239,68,68,0.03);
}
.record-tool__placeholder--stale {
  cursor: pointer;
  color: #b0b6cc;
  border-color: #e2c9c9;
}
.record-tool__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  flex-shrink: 0;
}
@media (prefers-reduced-motion: no-preference) {
  .record-tool__placeholder--recording .record-tool__dot {
    animation: record-tool-pulse 1.6s ease-in-out infinite;
  }
}
@keyframes record-tool-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
.record-tool__player {
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);
  border: 1px solid #e2e6f8;
  border-radius: 10px;
  padding: 14px 16px;
}
.record-tool__player audio {
  display: block;
  width: 100%;
  outline: none;
}
.record-tool__caption {
  margin-top: 8px;
  font-size: 13px;
  color: #aaa;
  text-align: center;
}
.record-tool__summary {
  margin-top: 10px;
  padding: 10px 12px;
  background: rgba(102,126,234,0.06);
  border-radius: 8px;
  font-size: 13px;
  color: #4a4a6a;
  line-height: 1.6;
  white-space: pre-wrap;
}
.record-tool__transcript-toggle {
  margin-top: 8px;
  font-size: 12.5px;
  color: #667eea;
  cursor: pointer;
  user-select: none;
  display: inline-block;
}
.record-tool__transcript-toggle:hover { text-decoration: underline; }
.record-tool__transcript {
  margin-top: 8px;
  padding: 10px 12px;
  background: #fafbff;
  border: 1px solid #eeeef8;
  border-radius: 8px;
  font-size: 12.5px;
  color: #888;
  line-height: 1.7;
  white-space: pre-wrap;
}
`
  document.head.appendChild(el)
}

function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds || 0))
  const m = Math.floor(s / 60)
  const r = s % 60
  return (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r
}

class RecordTool {
  static get toolbox() {
    return {
      title: '录音',
      icon: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3z"/>
        <path d="M19 11a7 7 0 0 1-14 0"/>
        <line x1="12" y1="19" x2="12" y2="22"/>
      </svg>`
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, api, readOnly, block, config }) {
    this.data = data || {}
    this.api = api
    this.readOnly = readOnly
    this.block = block
    this.config = config || {}
    this._element = null
    this._onControllerChange = this._onControllerChange.bind(this)
  }

  render() {
    injectStyles()
    const wrapper = document.createElement('div')
    wrapper.classList.add('record-tool')
    this._element = wrapper
    this._renderCurrentState(wrapper)
    // 只有还没点过、显示"点击开始录音"的这个状态需要跟着全局录音状态实时刷新——
    // 别处已经在录音时，这里得从"可点"变成"已经在录音了"，反过来录完了也要变回可点。
    // 一旦点了（this.data.recording === true），这个块自己不再需要监听——它是被
    // RecordingCapsule 广播出的 'recording:resolve-block' 通过 delete+insert 换掉的，
    // 那本身就会销毁并重建一个新的工具实例。
    recordingController.on('start', this._onControllerChange)
    recordingController.on('stop', this._onControllerChange)
    recordingController.on('error', this._onControllerChange)
    return wrapper
  }

  destroy() {
    recordingController.off('start', this._onControllerChange)
    recordingController.off('stop', this._onControllerChange)
    recordingController.off('error', this._onControllerChange)
  }

  _onControllerChange() {
    // data 一旦变成 recording/url 就不用再管了，交给 delete+insert 的重建流程
    if (!this._element || this.data.recording || this.data.url) return
    this._renderCurrentState(this._element)
  }

  _renderCurrentState(wrapper) {
    wrapper.innerHTML = ''
    if (this.data.url) {
      this._renderPlayer(wrapper)
    } else if (this.data.recording) {
      this._renderRecordingState(wrapper)
    } else if (!this.readOnly) {
      this._renderTrigger(wrapper)
    }
  }

  _renderTrigger(wrapper) {
    const busy = recordingController.state.status !== 'idle'
    const el = document.createElement('div')
    el.classList.add('record-tool__placeholder')
    if (busy) {
      el.classList.add('record-tool__placeholder--recording')
      el.innerHTML = `<span class="record-tool__dot"></span><span>已经在录音了，看右下角胶囊</span>`
    } else {
      el.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3z"/><path d="M19 11a7 7 0 0 1-14 0"/>
        </svg>
        <span>点击开始录音</span>
      `
      el.addEventListener('click', () => this._handleTriggerClick())
    }
    wrapper.appendChild(el)
  }

  // 这段录音后续存去哪儿，从点下去的这一刻就定死在这个块的位置上了——不再是
  // "删掉占位块，之后再问用户存哪"，而是块本身就地变成"录音中"，占住这个位置；
  // 不管录音过程中用户切去编辑别的笔记、切到别的页签、甚至整个刷新页面，停止时
  // RecordingCapsule 都会回来找这个块（靠 blockId），把它换成真正的播放器。
  _handleTriggerClick() {
    if (recordingController.state.status !== 'idle') {
      // 全局只允许同时有一段录音；这里理论上不会被点到，因为按钮点了就转成
      // "录音中" 状态、不再可点——留着这个判断只是防御性的。
      return
    }
    const noteId = this.config.getNoteId ? this.config.getNoteId() : null
    const blockId = this.block && this.block.id
    this.data = { recording: true }
    this._renderCurrentState(this._element)
    // start() 失败（没权限/没设备）时的用户提示由 RecordingCapsule 订阅 controller
    // 的 'error' 事件统一弹出；这里只负责把这个块自己退回"点击开始录音"。
    recordingController.start({ type: 'block', noteId, blockId }).catch(() => {
      this.data = {}
      this._renderCurrentState(this._element)
    })
  }

  _renderRecordingState(wrapper) {
    const isThisBlockRecording =
      this.block &&
      recordingController.state.status !== 'idle' &&
      recordingController.state.origin &&
      recordingController.state.origin.type === 'block' &&
      recordingController.state.origin.blockId === this.block.id

    const el = document.createElement('div')
    el.classList.add('record-tool__placeholder', 'record-tool__placeholder--recording')

    if (isThisBlockRecording) {
      el.innerHTML = `<span class="record-tool__dot"></span><span>录音中，看右下角胶囊</span>`
    } else {
      // 全局并没有一段"正对着这个块"的录音在跑——多半是页面刷新过、或者上次录音
      // 中途出了意外（浏览器崩溃/关闭）没能正常收尾。留着一个转不动的"录音中"
      // 状态对用户没有意义，允许点掉它（只读模式下不给这个交互）。
      el.classList.remove('record-tool__placeholder--recording')
      el.classList.add('record-tool__placeholder--stale')
      el.innerHTML = `<span>这段录音没能正常完成${this.readOnly ? '' : '，点击移除'}</span>`
      if (!this.readOnly) {
        el.addEventListener('click', () => {
          const idx = this.block && this.block.id
            ? this.api.blocks.getBlockIndex(this.block.id)
            : this.api.blocks.getCurrentBlockIndex()
          this.api.blocks.delete(idx)
        })
      }
    }

    wrapper.appendChild(el)
  }

  _renderPlayer(wrapper) {
    const playerDiv = document.createElement('div')
    playerDiv.classList.add('record-tool__player')
    const audio = document.createElement('audio')
    audio.src = this.data.url
    audio.controls = true
    playerDiv.appendChild(audio)
    wrapper.appendChild(playerDiv)

    const cap = document.createElement('div')
    cap.classList.add('record-tool__caption')
    cap.textContent = formatDuration(this.data.duration)
    wrapper.appendChild(cap)

    this._renderTranscriptAndSummary(wrapper)
  }

  _renderTranscriptAndSummary(wrapper) {
    if (this.data.summary) {
      const summaryEl = document.createElement('div')
      summaryEl.classList.add('record-tool__summary')
      summaryEl.textContent = this.data.summary
      wrapper.appendChild(summaryEl)
    }
    if (this.data.transcript) {
      const toggle = document.createElement('span')
      toggle.classList.add('record-tool__transcript-toggle')
      toggle.textContent = '查看完整逐字稿'
      const transcriptEl = document.createElement('div')
      transcriptEl.classList.add('record-tool__transcript')
      transcriptEl.textContent = this.data.transcript
      transcriptEl.style.display = 'none'
      toggle.addEventListener('click', () => {
        const showing = transcriptEl.style.display !== 'none'
        transcriptEl.style.display = showing ? 'none' : ''
        toggle.textContent = showing ? '查看完整逐字稿' : '收起逐字稿'
      })
      wrapper.appendChild(toggle)
      wrapper.appendChild(transcriptEl)
    }
  }

  save() {
    if (this.data.url) {
      return {
        url: this.data.url,
        duration: this.data.duration || 0,
        transcript: this.data.transcript || '',
        summary: this.data.summary || ''
      }
    }
    if (this.data.recording) {
      return { recording: true }
    }
    return {}
  }

  validate(savedData) {
    if (savedData && savedData.url) return !!savedData.url.trim()
    if (savedData && savedData.recording) return true
    return false
  }
}

export default RecordTool
