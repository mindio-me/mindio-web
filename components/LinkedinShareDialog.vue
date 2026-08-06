<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :title="$t('workspace.linkedinShare.title')"
    :visible.sync="visible"
    width="560px"
    :close-on-click-modal="false"
    @close="onClose"
  >
    <el-form label-position="top">
      <el-form-item :label="$t('workspace.linkedinShare.coverImage')">
        <div class="li-cover-wrap">
          <div class="li-cover-upload" @click="triggerFileInput">
            <img v-if="coverImageUrl" :src="coverImageUrl" class="li-cover-preview" />
            <div v-else class="li-cover-placeholder">
              <i class="el-icon-picture-outline" />
              <span>{{ $t('workspace.linkedinShare.uploadCover') }}</span>
            </div>
            <div v-if="coverImageUrl" class="li-cover-replace">{{ $t('workspace.linkedinShare.replaceCover') }}</div>
          </div>
          <a
            v-if="coverImageUrl"
            class="li-cover-download"
            :title="$t('workspace.linkedinShare.downloadImage')"
            @click.stop="downloadCover"
          >
            <i class="el-icon-download" /> {{ $t('workspace.linkedinShare.download') }}
          </a>
        </div>
        <div class="hint">{{ $t('workspace.linkedinShare.coverHint') }}</div>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          style="display:none"
          @change="onFileSelected"
        />
      </el-form-item>

      <el-form-item :label="$t('workspace.linkedinShare.body')">
        <el-input
          v-model="content"
          type="textarea"
          :rows="10"
          :maxlength="3000"
          show-word-limit
          :placeholder="$t('workspace.linkedinShare.bodyPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="$t('workspace.linkedinShare.hashtags')">
        <el-input
          v-model="hashtags"
          :placeholder="$t('workspace.linkedinShare.hashtagsPlaceholder')"
          :disabled="hashtagsLoading"
        >
          <template v-if="hashtagsLoading" slot="suffix">
            <i class="el-icon-loading" />
          </template>
        </el-input>
        <div class="hint">{{ $t('workspace.linkedinShare.hashtagsHint') }}</div>
      </el-form-item>
    </el-form>

    <span slot="footer">
      <el-button @click="onClose">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="onConfirm">{{ $t('workspace.linkedinShare.copyAndOpen') }}</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'LinkedinShareDialog',

  props: {
    noteId: { type: Number, default: null }
  },

  data() {
    return {
      visible: false,
      coverImageUrl: '',
      coverImageFile: null,
      content: '',
      hashtags: '',
      hashtagsLoading: false
    }
  },

  methods: {
    open(noteTitle, noteBlocks) {
      this.coverImageUrl = this._extractCoverImage(noteBlocks)
      this.coverImageFile = null
      this.content = this._blocksToText(noteTitle, noteBlocks)
      this.hashtags = ''
      this.visible = true
      this._loadHashtags()
    },

    onClose() {
      this.visible = false
    },

    triggerFileInput() {
      this.$refs.fileInput.click()
    },

    onFileSelected(e) {
      const file = e.target.files[0]
      if (!file) return
      if (!file.type.startsWith('image/')) {
        this.$message.error(this.$t('workspace.linkedinShare.errorImageOnly'))
        return
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$message.error(this.$t('workspace.linkedinShare.errorImageSize'))
        return
      }
      this.coverImageFile = file
      const reader = new FileReader()
      reader.onload = ev => { this.coverImageUrl = ev.target.result }
      reader.readAsDataURL(file)
      e.target.value = ''
    },

    async onConfirm() {
      const tags = this.hashtags.trim()
      const full = tags ? `${this.content.trim()}\n\n${tags}` : this.content.trim()
      try {
        await navigator.clipboard.writeText(full)
      } catch (e) {
        this.$message.warning(this.$t('workspace.linkedinShare.clipboardError'))
      }
      window.open('https://www.linkedin.com/feed/', '_blank')
      this.$message.success(this.$t('workspace.linkedinShare.successMessage'))
      this.visible = false
    },

    async _loadHashtags() {
      if (!this.noteId) return
      this.hashtagsLoading = true
      try {
        const res = await this.$aiService.generateLinkedinHashtags(this.noteId)
        const tags = (res.hashtags || []).map(t => `#${t}`).join(' ')
        this.hashtags = tags
      } catch (e) {
        this.hashtags = '#AI #Tech'
      } finally {
        this.hashtagsLoading = false
      }
    },

    async downloadCover() {
      try {
        const res = await fetch(this.coverImageUrl)
        const blob = await res.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'linkedin-cover'
        a.click()
        URL.revokeObjectURL(url)
      } catch (e) {
        this.$message.error(this.$t('workspace.linkedinShare.downloadError'))
      }
    },

    _extractCoverImage(blocks) {
      if (!Array.isArray(blocks)) return ''
      for (const block of blocks) {
        if (block.type === 'image') {
          return (block.data && block.data.file && block.data.file.url) || ''
        }
      }
      return ''
    },

    _htmlToText(html) {
      const el = document.createElement('div')
      el.innerHTML = (html || '').replace(/<br\s*\/?>/gi, '\n')
      return el.textContent || ''
    },

    _blocksToText(title, blocks) {
      const lines = []
      if (title && title.trim()) lines.push(title.trim())
      if (Array.isArray(blocks)) {
        for (const block of blocks) {
          const d = block.data || {}
          switch (block.type) {
            case 'paragraph':
            case 'header': {
              const text = this._htmlToText(d.text).trim()
              if (text) lines.push(text)
              break
            }
            case 'quote': {
              const text = this._htmlToText(d.text).trim()
              if (text) lines.push(`"${text}"`)
              break
            }
            case 'list': {
              const items = Array.isArray(d.items) ? d.items : []
              for (const item of items) {
                const raw = typeof item === 'object' ? (item.content || '') : String(item)
                const text = this._htmlToText(raw).trim()
                if (text) lines.push(`• ${text}`)
              }
              break
            }
          }
        }
      }
      const joined = lines.join('\n\n')
      return joined.length > 2800 ? joined.substring(0, 2800) + '…' : joined
    }
  }
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.li-cover-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.li-cover-upload {
  position: relative;
  width: 160px;
  height: 100px;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  background: #fafafa;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;
}
.li-cover-upload:hover {
  border-color: #409eff;
}
.li-cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.li-cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 12px;
  gap: 6px;
}
.li-cover-placeholder i {
  font-size: 24px;
}
.li-cover-replace {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  text-align: center;
  padding: 3px 0;
  opacity: 0;
  transition: opacity 0.2s;
}
.li-cover-upload:hover .li-cover-replace {
  opacity: 1;
}
.li-cover-download {
  font-size: 12px;
  color: #606266;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}
.li-cover-download:hover {
  color: #409eff;
}
</style>
