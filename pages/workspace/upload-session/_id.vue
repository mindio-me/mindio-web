<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="us-page">
    <div class="us-card">
      <!-- Header -->
      <div class="us-header">
        <div class="us-logo">
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
          </svg>
        </div>
        <div>
          <div class="us-title">扫码上传</div>
          <div class="us-subtitle">选择文件上传后，可以直接关闭本页面</div>
        </div>
      </div>

      <!-- Error state -->
      <div v-if="initError" class="us-state us-state--error">
        <div class="us-state-icon">✕</div>
        <div class="us-state-msg">{{ initError }}</div>
        <a href="/" class="us-home-link">返回首页</a>
      </div>

      <!-- Upload form -->
      <template v-else-if="!done">
        <div class="us-status-row">
          <span class="us-status-label">会话状态</span>
          <span class="us-status-badge" :class="statusClass">{{ statusText }}</span>
        </div>

        <div class="us-dropzone" :class="{ 'us-dropzone--active': selectedFile }" @click="$refs.fileInput.click()">
          <input ref="fileInput" type="file" class="us-file-input" :accept="accept || undefined" @change="onFileChange" />
          <div v-if="!selectedFile" class="us-dz-placeholder">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#667eea" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="16 16 12 12 8 16"/><line x1="12" y1="12" x2="12" y2="21"/>
              <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"/>
            </svg>
            <div class="us-dz-text">点击选择文件</div>
            <div class="us-dz-hint">{{ acceptHint }}</div>
          </div>
          <div v-else class="us-dz-selected">
            <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#667eea" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>
            </svg>
            <div class="us-dz-filename">{{ selectedFile.name }}</div>
            <div class="us-dz-filesize">{{ formatSize(selectedFile.size) }}</div>
            <div class="us-dz-change">点击重新选择</div>
          </div>
        </div>

        <div v-if="uploadError" class="us-error-msg">{{ uploadError }}</div>

        <button class="us-btn" :class="{ 'us-btn--loading': uploading }" :disabled="!selectedFile || uploading || sessionExpired" @click="upload">
          <span v-if="uploading" class="us-spinner" />
          <span>{{ uploading ? '上传中...' : '上传' }}</span>
        </button>

        <div v-if="sessionExpired" class="us-expire-tip">二维码已过期，请重新扫码</div>
      </template>

      <!-- Success state -->
      <div v-else class="us-state us-state--success">
        <div class="us-state-icon us-state-icon--success">✓</div>
        <div class="us-state-title">上传成功</div>
        <div class="us-state-msg">文件已上传，可以关闭此页面，<br>编辑器中将自动插入音频。</div>
        <a href="/" class="us-home-link">返回首页</a>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  layout: 'blank',
  auth: false,
  data() {
    return {
      sessionId: '',
      token: '',
      model: 'note',
      pid: 0,
      accept: '',
      status: '',
      selectedFile: null,
      uploading: false,
      uploadError: '',
      initError: '',
      done: false
    }
  },
  computed: {
    sessionExpired() {
      return this.status === 'EXPIRED'
    },
    statusText() {
      const map = { PENDING: '等待上传', COMPLETED: '已完成', FAILED: '失败', EXPIRED: '已过期' }
      return map[this.status] || this.status || '加载中...'
    },
    statusClass() {
      const map = { PENDING: 'badge--pending', COMPLETED: 'badge--done', FAILED: 'badge--fail', EXPIRED: 'badge--expire' }
      return map[this.status] || ''
    },
    acceptHint() {
      if (this.accept && this.accept.includes('audio')) return '支持 MP3、M4A、WAV、AAC 等音频格式'
      return '选择要上传的文件'
    }
  },
  mounted() {
    this.sessionId = this.$route.params.id
    this.token = this.$route.query.token || ''
    this.model = this.$route.query.model || 'note'
    this.pid = parseInt(this.$route.query.pid || '0', 10)
    this.accept = this.$route.query.accept || ''

    if (!this.sessionId || !this.token) {
      this.initError = '二维码链接无效（缺少必要参数）'
      return
    }
    this.refreshStatus()
  },
  methods: {
    async refreshStatus() {
      try {
        const session = await this.$uploadService.getSession(this.sessionId, this.token)
        this.status = session.status
        if (session.status === 'COMPLETED') this.done = true
      } catch (e) {
        // ignore
      }
    },
    onFileChange(e) {
      this.uploadError = ''
      this.selectedFile = (e.target.files && e.target.files[0]) || null
    },
    async upload() {
      if (!this.selectedFile) return
      this.uploading = true
      this.uploadError = ''
      try {
        const session = await this.$uploadService.uploadToSession(
          this.sessionId,
          this.token,
          this.selectedFile,
          this.model,
          this.pid
        )
        this.status = session.status
        if (session.status === 'COMPLETED') {
          this.done = true
        } else {
          this.uploadError = '上传未完成，请重试'
        }
      } catch (e) {
        this.uploadError = e.response?.data?.message || e.message || '上传失败，请重试'
      } finally {
        this.uploading = false
      }
    },
    formatSize(bytes) {
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1024 / 1024).toFixed(1) + ' MB'
    }
  }
}
</script>

<style scoped>
.us-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f2ff 0%, #fafbff 60%, #f5f0ff 100%);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 32px 16px 48px;
  box-sizing: border-box;
}

.us-card {
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 40px rgba(102, 126, 234, 0.12), 0 2px 8px rgba(0,0,0,0.06);
  width: 100%;
  max-width: 440px;
  padding: 28px 24px 32px;
  box-sizing: border-box;
}

/* Header */
.us-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}
.us-logo {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.us-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  line-height: 1.3;
}
.us-subtitle {
  font-size: 12.5px;
  color: #aaa;
  margin-top: 2px;
}

/* Status badge */
.us-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.us-status-label {
  font-size: 13px;
  color: #999;
}
.us-status-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 20px;
  font-weight: 500;
}
.badge--pending  { background: #fff7e6; color: #fa8c16; }
.badge--done     { background: #f6ffed; color: #52c41a; }
.badge--fail     { background: #fff1f0; color: #ff4d4f; }
.badge--expire   { background: #f5f5f5; color: #bbb; }

/* Dropzone */
.us-dropzone {
  border: 2px dashed #d5d9f0;
  border-radius: 14px;
  padding: 32px 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  margin-bottom: 16px;
  min-height: 160px;
  box-sizing: border-box;
}
.us-dropzone:hover,
.us-dropzone--active {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.04);
}
.us-file-input {
  display: none;
}
.us-dz-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}
.us-dz-text {
  font-size: 15px;
  font-weight: 500;
  color: #2d2d4e;
}
.us-dz-hint {
  font-size: 12px;
  color: #bbb;
}
.us-dz-selected {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}
.us-dz-filename {
  font-size: 14px;
  font-weight: 500;
  color: #2d2d4e;
  word-break: break-all;
  max-width: 260px;
}
.us-dz-filesize {
  font-size: 12px;
  color: #999;
}
.us-dz-change {
  font-size: 12px;
  color: #667eea;
  margin-top: 2px;
}

/* Error */
.us-error-msg {
  font-size: 13px;
  color: #ff4d4f;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fff1f0;
  border-radius: 8px;
}

/* Button */
.us-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.35);
  transition: opacity 0.2s, transform 0.15s;
}
.us-btn:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.us-btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
.us-btn--loading { opacity: 0.75; cursor: wait; }

.us-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: us-spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes us-spin { to { transform: rotate(360deg); } }

.us-expire-tip {
  font-size: 12px;
  color: #bbb;
  text-align: center;
  margin-top: 10px;
}

/* States */
.us-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 0 8px;
  text-align: center;
}
.us-state-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #fff1f0;
  color: #ff4d4f;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.us-state-icon--success {
  background: #f6ffed;
  color: #52c41a;
}
.us-state-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.us-state-msg {
  font-size: 14px;
  color: #888;
  line-height: 1.7;
}
.us-home-link {
  margin-top: 8px;
  display: inline-block;
  padding: 10px 28px;
  border: 1.5px solid #c5cdf5;
  border-radius: 20px;
  font-size: 13.5px;
  color: #667eea;
  text-decoration: none;
  transition: all 0.15s;
}
.us-home-link:hover {
  background: #667eea;
  color: #fff;
  border-color: #667eea;
}
</style>
