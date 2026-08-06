<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="upload-panel">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="本地上传" name="local">
        <div class="section">
          <el-alert
            title="选择本地文件上传（图片会自动插入为图片，其他文件插入为链接）"
            type="info"
            :closable="false"
            show-icon
          />
          <div class="row">
            <input ref="fileInput" type="file" class="file-input" @change="onFileChange" />
            <el-button type="primary" :loading="loading" :disabled="!selectedFile" @click="uploadLocal">
              上传
            </el-button>
            <el-button v-if="isHtmlFile" :loading="insertingHtml" :disabled="!selectedFile" @click="insertAsHtml">
              插入为正文（渲染 HTML）
            </el-button>
          </div>
          <div v-if="selectedFile" class="file-meta">
            <span class="name">{{ selectedFile.name }}</span>
            <span class="size">{{ formatSize(selectedFile.size) }}</span>
          </div>
          <el-alert
            v-if="isHtmlFile"
            title="检测到 HTML 文件：点“上传”会作为附件链接插入；点“插入为正文”会把文件内容直接渲染进正文（不转义）"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 8px"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="网络链接" name="remote">
        <div class="section">
          <el-alert
            title="输入一个可公开访问的文件URL，由后端拉取并保存"
            type="warning"
            :closable="false"
            show-icon
          />
          <div class="row">
            <el-input v-model="remoteUrl" placeholder="https://example.com/file.png" clearable />
            <el-button type="primary" :loading="loading" :disabled="!remoteUrl" @click="uploadRemote">
              拉取并上传
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="扫码上传" name="scan">
        <div class="section">
          <el-alert
            title="生成二维码，用手机打开页面上传；此处会自动轮询等待结果"
            type="success"
            :closable="false"
            show-icon
          />

          <div class="row">
            <el-button type="primary" :loading="creatingSession" @click="createSession">
              生成二维码
            </el-button>
            <el-button v-if="sessionId" :disabled="polling" @click="restartSession">重新生成</el-button>
          </div>

          <div v-if="sessionId" class="scan-area">
            <div class="qr">
              <img v-if="qrDataUrl" :src="qrDataUrl" alt="QR Code" />
              <div v-else class="qr-placeholder">生成中...</div>
            </div>
            <div class="scan-info">
              <div class="line">
                <span class="label">状态：</span>
                <span class="value">{{ sessionStatus }}</span>
              </div>
              <div class="line" v-if="expiresAt">
                <span class="label">过期时间：</span>
                <span class="value">{{ expiresAt }}</span>
              </div>
              <div class="line">
                <span class="label">链接：</span>
                <el-input :value="qrUrl" readonly size="mini" />
              </div>
              <div class="line">
                <el-button size="mini" @click="copy(qrUrl)">复制链接</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import QRCode from 'qrcode'
import { sanitizeInsertedHtml } from '~/utils/sanitizeInsertedHtml'

export default {
  name: 'UploadPanel',
  props: {
    model: { type: String, default: 'note' },
    pid: { type: Number, default: 0 }
  },
  data() {
    return {
      activeTab: 'local',
      loading: false,
      selectedFile: null,
      remoteUrl: '',
      insertingHtml: false,

      creatingSession: false,
      sessionId: '',
      sessionToken: '',
      sessionStatus: '',
      expiresAt: '',
      qrUrl: '',
      qrDataUrl: '',
      polling: false,
      pollTimer: null
    }
  },
  computed: {
    isHtmlFile() {
      return !!this.selectedFile && /\.html?$/i.test(this.selectedFile.name || '')
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    onFileChange(e) {
      const file = e.target.files && e.target.files[0]
      this.selectedFile = file || null
    },
    async insertAsHtml() {
      if (!this.selectedFile) return
      this.insertingHtml = true
      try {
        const text = await this.selectedFile.text()
        this.$emit('insert-html', sanitizeInsertedHtml(text))
        this.resetLocal()
      } catch (e) {
        this.$message.error('读取文件失败')
      } finally {
        this.insertingHtml = false
      }
    },
    async uploadLocal() {
      if (!this.selectedFile) return
      this.loading = true
      try {
        const result = await this.$uploadService.uploadLocal(this.selectedFile, this.model, this.pid)
        this.$message.success('上传成功')
        this.$emit('success', result)
        this.resetLocal()
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '上传失败')
      } finally {
        this.loading = false
      }
    },
    async uploadRemote() {
      if (!this.remoteUrl) return
      this.loading = true
      try {
        const result = await this.$uploadService.uploadRemote(this.remoteUrl, this.model, this.pid)
        this.$message.success('上传成功')
        this.$emit('success', result)
        this.remoteUrl = ''
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '上传失败')
      } finally {
        this.loading = false
      }
    },
    async createSession() {
      this.creatingSession = true
      try {
        const session = await this.$uploadService.createSession(this.model, this.pid)
        this.sessionId = session.sessionId
        this.sessionToken = session.token
        this.sessionStatus = session.status
        this.expiresAt = session.expiresAt
        this.qrUrl = session.qrUrl
        this.qrDataUrl = await QRCode.toDataURL(this.qrUrl, { margin: 1, width: 220 })
        this.startPolling()
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '生成二维码失败')
      } finally {
        this.creatingSession = false
      }
    },
    restartSession() {
      this.stopPolling()
      this.sessionId = ''
      this.sessionToken = ''
      this.sessionStatus = ''
      this.expiresAt = ''
      this.qrUrl = ''
      this.qrDataUrl = ''
      this.createSession()
    },
    startPolling() {
      if (!this.sessionId || !this.sessionToken) return
      this.stopPolling()
      this.polling = true
      this.pollTimer = setInterval(async () => {
        try {
          const session = await this.$uploadService.getSession(this.sessionId, this.sessionToken)
          this.sessionStatus = session.status
          this.expiresAt = session.expiresAt

          if (session.status === 'COMPLETED' && session.result) {
            this.$message.success('扫码上传完成')
            this.$emit('success', session.result)
            this.stopPolling()
          } else if (session.status === 'FAILED') {
            this.$message.error(session.error || '扫码上传失败')
            this.stopPolling()
          } else if (session.status === 'EXPIRED') {
            this.$message.warning('二维码已过期，请重新生成')
            this.stopPolling()
          }
        } catch (e) {
          // 轮询失败不弹错误，避免刷屏
        }
      }, 1000)
    },
    stopPolling() {
      this.polling = false
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    resetLocal() {
      this.selectedFile = null
      if (this.$refs.fileInput) this.$refs.fileInput.value = ''
    },
    formatSize(bytes) {
      if (!bytes && bytes !== 0) return ''
      const kb = bytes / 1024
      if (kb < 1024) return `${kb.toFixed(1)} KB`
      const mb = kb / 1024
      return `${mb.toFixed(1)} MB`
    },
    async copy(text) {
      try {
        await navigator.clipboard.writeText(text)
        this.$message.success('已复制')
      } catch (e) {
        this.$message.warning('复制失败，请手动复制')
      }
    }
  }
}
</script>

<style scoped>
.upload-panel {
  width: 100%;
}
.section {
  padding: 8px 0;
}
.row {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
}
.file-input {
  flex: 1;
}
.file-meta {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  color: #606266;
  font-size: 13px;
}
.scan-area {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
  align-items: start;
}
.qr {
  width: 240px;
  height: 240px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}
.qr img {
  width: 220px;
  height: 220px;
}
.qr-placeholder {
  color: #909399;
}
.scan-info .line {
  margin-bottom: 10px;
}
.label {
  color: #909399;
}
.value {
  color: #303133;
  font-weight: 500;
}
@media (max-width: 768px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }
  .scan-area {
    grid-template-columns: 1fr;
  }
  .qr {
    width: 100%;
    height: auto;
    padding: 12px 0;
  }
}
</style>


