/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import QRCode from 'qrcode'

const STYLE_ID = 'audio-tool-injected-styles'

function injectStyles() {
  if (typeof document === 'undefined' || document.getElementById(STYLE_ID)) return
  const el = document.createElement('style')
  el.id = STYLE_ID
  el.textContent = `
/* ── AudioTool: block ──────────────────────────────── */
.audio-tool__placeholder {
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
.audio-tool__placeholder:hover {
  border-color: #667eea;
  color: #667eea;
  background: rgba(102,126,234,0.04);
}
.audio-tool__player {
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f2ff 100%);
  border: 1px solid #e2e6f8;
  border-radius: 10px;
  padding: 14px 16px;
}
.audio-tool__player audio {
  display: block;
  width: 100%;
  outline: none;
}
.audio-tool__caption {
  margin-top: 8px;
  font-size: 13px;
  color: #aaa;
  text-align: center;
}

/* ── AudioTool: modal overlay ─────────────────────── */
.at-overlay {
  position: fixed;
  inset: 0;
  z-index: 9998;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(12,12,28,0.52);
  backdrop-filter: blur(5px);
  animation: at-bg-in 0.18s ease;
}
@keyframes at-bg-in { from { opacity:0 } to { opacity:1 } }

.at-modal {
  background: #fff;
  border-radius: 14px;
  width: 520px;
  max-width: calc(100vw - 32px);
  box-shadow: 0 28px 72px rgba(0,0,0,0.2), 0 0 0 1px rgba(0,0,0,0.05);
  overflow: hidden;
  animation: at-modal-in 0.22s cubic-bezier(0.34,1.18,0.64,1);
}
@keyframes at-modal-in {
  from { transform: scale(0.94) translateY(-10px); opacity:0 }
  to   { transform: scale(1)    translateY(0);     opacity:1 }
}

/* ── Header ───────────────────────────────────────── */
.at-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 0;
}
.at-hdr-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  display: flex;
  align-items: center;
  gap: 10px;
}
.at-hdr-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg,#667eea,#764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.at-close-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: #f2f2f7;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
  font-size: 14px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
  padding: 0;
  flex-shrink: 0;
}
.at-close-btn:hover { background: #e2e2ea; color: #333; }

/* ── Tabs ─────────────────────────────────────────── */
.at-tabs {
  display: flex;
  padding: 0 20px;
  margin-top: 18px;
  border-bottom: 1px solid #f0f0f8;
  gap: 2px;
}
.at-tab-btn {
  padding: 9px 16px;
  font-size: 13.5px;
  color: #aaa;
  cursor: pointer;
  border: none;
  background: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  border-radius: 6px 6px 0 0;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.at-tab-btn:hover { color: #667eea; background: rgba(102,126,234,0.06); }
.at-tab-btn.active {
  color: #667eea;
  font-weight: 500;
  border-bottom-color: #667eea;
  background: rgba(102,126,234,0.05);
}

/* ── Body ─────────────────────────────────────────── */
.at-body { padding: 20px; }
.at-panels-outer {
  display: grid;
  grid-template-areas: "stack";
}
.at-panel {
  grid-area: stack;
}
.at-panel.hidden {
  visibility: hidden;
  pointer-events: none;
}

/* ── Local upload ─────────────────────────────────── */
.at-dropzone {
  border: 2px dashed #d5d9f0;
  border-radius: 12px;
  padding: 36px 24px 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  text-align: center;
  position: relative;
}
.at-dropzone:hover, .at-dropzone.dragover {
  border-color: #667eea;
  background: rgba(102,126,234,0.04);
}
.at-dropzone.loading {
  opacity: 0.6;
  pointer-events: none;
}
.at-dz-icon-wrap {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg,rgba(102,126,234,0.14),rgba(118,75,162,0.1));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.at-dz-title {
  font-size: 15px;
  font-weight: 500;
  color: #2d2d4e;
}
.at-dz-hint {
  font-size: 12px;
  color: #bbb;
  line-height: 1.6;
}
.at-dz-btn {
  margin-top: 6px;
  padding: 9px 28px;
  background: linear-gradient(135deg,#667eea,#764ba2);
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(102,126,234,0.35);
  transition: opacity 0.2s, transform 0.15s;
}
.at-dz-btn:hover { opacity: 0.88; transform: translateY(-1px); }
.at-dz-status {
  font-size: 13px;
  color: #667eea;
  margin-top: 4px;
  min-height: 20px;
}

/* ── QR scan ──────────────────────────────────────── */
.at-scan-panel-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.at-scan-wrap {
  display: flex;
  gap: 20px;
  align-items: center;
}
.at-qr-frame {
  flex-shrink: 0;
  width: 180px;
  height: 180px;
  border-radius: 10px;
  border: 1px solid #eeeef8;
  background: #fafbff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.at-qr-frame img { width: 180px; height: 180px; display: block; }
.at-qr-skeleton {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.at-qr-skeleton-box {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  background: linear-gradient(90deg,#f0f0f8 25%,#e4e5f4 50%,#f0f0f8 75%);
  background-size: 200% 100%;
  animation: at-shimmer 1.4s infinite;
}
.at-qr-skeleton-text { font-size: 12px; color: #ccc; }
@keyframes at-shimmer {
  0%   { background-position: 200% 0 }
  100% { background-position: -200% 0 }
}

.at-scan-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 2px;
}
.at-scan-heading {
  font-size: 14px;
  font-weight: 500;
  color: #2d2d4e;
  line-height: 1.6;
}
.at-scan-steps {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.at-scan-steps li {
  font-size: 12.5px;
  color: #999;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  line-height: 1.5;
}
.at-step-num {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(135deg,#667eea,#764ba2);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.at-status-row {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: #888;
}
.at-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ddd;
  flex-shrink: 0;
  transition: background 0.3s;
}
.at-status-dot.waiting { background: #faad14; animation: at-pulse 1.4s infinite; }
.at-status-dot.done    { background: #52c41a; animation: none; }
.at-status-dot.failed  { background: #ff4d4f; animation: none; }
.at-status-dot.expired { background: #d9d9d9; animation: none; }
@keyframes at-pulse {
  0%,100% { opacity:1 } 50% { opacity:0.25 }
}
.at-regen-btn {
  padding: 5px 12px;
  font-size: 12px;
  color: #667eea;
  border: 1px solid #c5cdf5;
  background: rgba(102,126,234,0.06);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  align-self: flex-start;
}
.at-regen-btn:hover { background: #667eea; color: #fff; border-color: #667eea; }
`
  document.head.appendChild(el)
}

class AudioTool {
  static get toolbox() {
    return {
      title: '音频',
      icon: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9 18V5l12-2v13"/>
        <circle cx="6" cy="18" r="3"/>
        <circle cx="18" cy="16" r="3"/>
      </svg>`
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, config, api, readOnly }) {
    this.data = data || {}
    this.config = config || {}
    this.api = api
    this.readOnly = readOnly
    this._element = null
    this._overlay = null
    this._pollTimer = null
    this._qrFrame = null
    this._qrStatusDot = null
    this._qrStatusText = null
    this._regenBtn = null
    this._activeSession = null
  }

  destroy() {
    this._stopPolling()
    if (this._overlay && this._overlay.parentNode) {
      this._overlay.parentNode.removeChild(this._overlay)
    }
    this._overlay = null
  }

  render() {
    injectStyles()
    const wrapper = document.createElement('div')
    wrapper.classList.add('audio-tool')
    if (this.data.url) {
      this._renderPlayer(wrapper)
    } else if (!this.readOnly) {
      this._renderPlaceholder(wrapper)
    }
    this._element = wrapper
    return wrapper
  }

  _renderPlaceholder(wrapper) {
    const ph = document.createElement('div')
    ph.classList.add('audio-tool__placeholder')
    ph.innerHTML = `
      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
      </svg>
      <span>点击添加音频</span>
    `
    ph.addEventListener('click', () => this._openModal())
    wrapper.appendChild(ph)
  }

  _renderPlayer(wrapper) {
    const playerDiv = document.createElement('div')
    playerDiv.classList.add('audio-tool__player')
    const audio = document.createElement('audio')
    audio.src = this.data.url
    audio.controls = true
    playerDiv.appendChild(audio)
    wrapper.appendChild(playerDiv)
    if (this.data.caption) {
      const cap = document.createElement('div')
      cap.classList.add('audio-tool__caption')
      cap.textContent = this.data.caption
      wrapper.appendChild(cap)
    }
  }

  _openModal() {
    if (this._overlay) return

    const overlay = document.createElement('div')
    overlay.classList.add('at-overlay')

    const modal = document.createElement('div')
    modal.classList.add('at-modal')

    // Header
    const hdr = document.createElement('div')
    hdr.classList.add('at-hdr')
    hdr.innerHTML = `
      <div class="at-hdr-title">
        <div class="at-hdr-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
          </svg>
        </div>
        上传音频
      </div>
      <button class="at-close-btn" title="关闭">✕</button>
    `
    hdr.querySelector('.at-close-btn').addEventListener('click', () => this._closeModal())
    modal.appendChild(hdr)

    // Tabs
    const tabsEl = document.createElement('div')
    tabsEl.classList.add('at-tabs')
    const btnLocal = document.createElement('button')
    btnLocal.classList.add('at-tab-btn', 'active')
    btnLocal.textContent = '本地上传'
    const btnScan = document.createElement('button')
    btnScan.classList.add('at-tab-btn')
    btnScan.textContent = '手机扫码上传'
    tabsEl.appendChild(btnLocal)
    tabsEl.appendChild(btnScan)
    modal.appendChild(tabsEl)

    // Body
    const body = document.createElement('div')
    body.classList.add('at-body')

    const panelLocal = this._buildLocalPanel()
    panelLocal.classList.add('at-panel')

    const panelScan = this._buildScanPanel()
    panelScan.classList.add('at-panel', 'hidden')

    const panelsOuter = document.createElement('div')
    panelsOuter.classList.add('at-panels-outer')
    panelsOuter.appendChild(panelLocal)
    panelsOuter.appendChild(panelScan)
    body.appendChild(panelsOuter)
    modal.appendChild(body)

    // Tab switching
    btnLocal.addEventListener('click', () => {
      btnLocal.classList.add('active')
      btnScan.classList.remove('active')
      panelLocal.classList.remove('hidden')
      panelScan.classList.add('hidden')
    })
    btnScan.addEventListener('click', () => {
      btnScan.classList.add('active')
      btnLocal.classList.remove('active')
      panelScan.classList.remove('hidden')
      panelLocal.classList.add('hidden')
      // 自动生成二维码，无需点击按钮
      if (!this._pollTimer && !this._activeSession) {
        this._generateQR()
      }
    })

    overlay.appendChild(modal)
    document.body.appendChild(overlay)
    this._overlay = overlay

    // Close on backdrop click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) this._closeModal()
    })
  }

  _closeModal() {
    this._stopPolling()
    if (this._overlay && this._overlay.parentNode) {
      this._overlay.parentNode.removeChild(this._overlay)
    }
    this._overlay = null
    this._activeSession = null
    this._qrFrame = null
    this._qrStatusDot = null
    this._qrStatusText = null
    this._regenBtn = null
  }

  _buildLocalPanel() {
    const panel = document.createElement('div')

    const fileInput = document.createElement('input')
    fileInput.type = 'file'
    fileInput.accept = 'audio/*'
    fileInput.style.display = 'none'
    panel.appendChild(fileInput)

    const dropzone = document.createElement('div')
    dropzone.classList.add('at-dropzone')
    dropzone.innerHTML = `
      <div class="at-dz-icon-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#667eea" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
        </svg>
      </div>
      <div class="at-dz-title">拖拽音频文件至此</div>
      <div class="at-dz-hint">支持 MP3、WAV、M4A、FLAC、OGG、AAC 等格式<br>单文件最大 50 MB</div>
      <button class="at-dz-btn">选择文件</button>
      <div class="at-dz-status"></div>
    `
    panel.appendChild(dropzone)

    const statusEl = dropzone.querySelector('.at-dz-status')

    const doUpload = async (file) => {
      if (!file || !file.type.startsWith('audio/')) {
        statusEl.textContent = '请选择音频文件'
        return
      }
      dropzone.classList.add('loading')
      statusEl.textContent = '上传中...'

      try {
        const result = await this.config.uploader?.uploadByFile(file)
        if (result?.success && result?.file?.url) {
          this.data.url = result.file.url
          this._closeModal()
          if (this._element) {
            this._element.innerHTML = ''
            this._renderPlayer(this._element)
          }
        } else {
          dropzone.classList.remove('loading')
          statusEl.textContent = '上传失败，请重试'
        }
      } catch (e) {
        dropzone.classList.remove('loading')
        statusEl.textContent = '上传失败，请重试'
      }
    }

    dropzone.querySelector('.at-dz-btn').addEventListener('click', (e) => {
      e.stopPropagation()
      fileInput.click()
    })
    dropzone.addEventListener('click', () => fileInput.click())

    fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0]
      fileInput.value = ''
      if (file) doUpload(file)
    })
    dropzone.addEventListener('dragover', (e) => {
      e.preventDefault()
      dropzone.classList.add('dragover')
    })
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'))
    dropzone.addEventListener('drop', (e) => {
      e.preventDefault()
      dropzone.classList.remove('dragover')
      const file = e.dataTransfer.files[0]
      if (file) doUpload(file)
    })

    return panel
  }

  _buildScanPanel() {
    const panel = document.createElement('div')

    const inner = document.createElement('div')
    inner.classList.add('at-scan-panel-inner')

    const wrap = document.createElement('div')
    wrap.classList.add('at-scan-wrap')

    // QR frame (skeleton until generated)
    const qrFrame = document.createElement('div')
    qrFrame.classList.add('at-qr-frame')
    qrFrame.innerHTML = `
      <div class="at-qr-skeleton">
        <div class="at-qr-skeleton-box"></div>
        <div class="at-qr-skeleton-text">稍候...</div>
      </div>
    `
    this._qrFrame = qrFrame

    // Info column
    const info = document.createElement('div')
    info.classList.add('at-scan-info')
    info.innerHTML = `
      <div class="at-scan-heading">用手机扫描二维码<br>上传录音文件</div>
      <ul class="at-scan-steps">
        <li><span class="at-step-num">1</span>打开手机相机或微信扫一扫</li>
        <li><span class="at-step-num">2</span>在手机上选择录音文件</li>
        <li><span class="at-step-num">3</span>上传完成后此处自动更新</li>
      </ul>
      <div class="at-status-row">
        <div class="at-status-dot waiting"></div>
        <span class="at-status-text">正在生成二维码...</span>
      </div>
    `
    this._qrStatusDot = info.querySelector('.at-status-dot')
    this._qrStatusText = info.querySelector('.at-status-text')

    const regenBtn = document.createElement('button')
    regenBtn.classList.add('at-regen-btn')
    regenBtn.textContent = '重新生成'
    regenBtn.style.display = 'none'
    regenBtn.addEventListener('click', () => {
      regenBtn.style.display = 'none'
      this._generateQR()
    })
    info.appendChild(regenBtn)
    this._regenBtn = regenBtn

    wrap.appendChild(qrFrame)
    wrap.appendChild(info)
    inner.appendChild(wrap)
    panel.appendChild(inner)
    return panel
  }

  async _generateQR() {
    this._stopPolling()
    this._activeSession = null

    if (this._qrFrame) {
      this._qrFrame.innerHTML = `
        <div class="at-qr-skeleton">
          <div class="at-qr-skeleton-box"></div>
          <div class="at-qr-skeleton-text">生成中...</div>
        </div>
      `
    }
    this._setStatus('waiting', '正在生成二维码...')
    if (this._regenBtn) this._regenBtn.style.display = 'none'

    try {
      const session = await this.config.session?.createSession()
      if (!session) throw new Error('no session')
      this._activeSession = { sessionId: session.sessionId, token: session.token }

      const dataUrl = await QRCode.toDataURL(session.qrUrl, { margin: 1, width: 180 })
      if (this._qrFrame) {
        this._qrFrame.innerHTML = ''
        const img = document.createElement('img')
        img.src = dataUrl
        this._qrFrame.appendChild(img)
      }
      this._setStatus('waiting', '等待手机扫码上传...')
      this._startPolling(session.sessionId, session.token)
    } catch (e) {
      this._setStatus('failed', '生成失败，请重试')
      if (this._regenBtn) this._regenBtn.style.display = ''
    }
  }

  _setStatus(type, text) {
    if (this._qrStatusDot) {
      this._qrStatusDot.className = 'at-status-dot' + (type ? ' ' + type : '')
    }
    if (this._qrStatusText) this._qrStatusText.textContent = text
  }

  _startPolling(sessionId, token) {
    this._stopPolling()
    this._pollTimer = setInterval(async () => {
      try {
        const session = await this.config.session?.getSession(sessionId, token)
        if (!session) return
        if (session.status === 'COMPLETED' && session.result) {
          this._stopPolling()
          this.data.url = session.result.url
          this._closeModal()
          if (this._element) {
            this._element.innerHTML = ''
            this._renderPlayer(this._element)
          }
        } else if (session.status === 'FAILED') {
          this._stopPolling()
          this._setStatus('failed', '上传失败')
          if (this._regenBtn) this._regenBtn.style.display = ''
        } else if (session.status === 'EXPIRED') {
          this._stopPolling()
          this._setStatus('expired', '二维码已过期')
          if (this._regenBtn) this._regenBtn.style.display = ''
        }
      } catch (e) {
        // 吞掉轮询错误
      }
    }, 1000)
  }

  _stopPolling() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer)
      this._pollTimer = null
    }
  }

  save() {
    return {
      url: this.data.url || '',
      caption: this.data.caption || ''
    }
  }

  validate(savedData) {
    return !!(savedData && savedData.url && savedData.url.trim())
  }
}

export default AudioTool
