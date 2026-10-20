<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :title="$t('shareCard.title')"
    :visible="visible"
    :width="dialogWidth"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    custom-class="share-card-dialog"
    append-to-body
    @open="onOpen"
    @closed="onClosed"
    @update:visible="onVisibleChange"
  >
    <div v-loading="loadingCard" class="share-card-body">
      <!-- 来源切换 -->
      <el-radio-group v-model="source" size="small" class="source-group">
        <el-radio-button label="upload">{{ $t('shareCard.sourceUpload') }}</el-radio-button>
        <el-tooltip :disabled="aiAvailable" :content="aiUnavailableHint" placement="top">
          <el-radio-button label="ai" :disabled="!aiAvailable">{{ $t('shareCard.sourceAi') }}</el-radio-button>
        </el-tooltip>
        <el-radio-button label="gradient">{{ $t('shareCard.sourceGradient') }}</el-radio-button>
      </el-radio-group>
      <p v-if="!aiAvailable && aiAvailability" class="share-card-hint">{{ aiUnavailableHint }}</p>

      <!-- 实时预览（1200×630 比例） -->
      <div class="preview-frame">
        <img v-if="previewUrl" :src="previewUrl" class="preview-image" alt="">
        <div v-if="showEmptyPlaceholder" class="preview-empty">
          <i :class="source === 'upload' ? 'el-icon-picture-outline' : 'el-icon-magic-stick'"></i>
          <span>{{ source === 'upload' ? $t('shareCard.uploadHint') : $t('shareCard.aiEmptyHint') }}</span>
        </div>
        <div v-if="generating" class="preview-busy">
          <i class="el-icon-loading"></i>
          <span>{{ $t('shareCard.generating') }}</span>
        </div>
      </div>

      <!-- 上传 -->
      <div v-if="source === 'upload'" class="source-panel">
        <el-upload
          action=""
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onFileChange"
          accept="image/png,image/jpeg,image/webp,image/gif"
        >
          <el-button size="small" icon="el-icon-upload2">
            {{ uploadObjectUrl ? $t('shareCard.changeImage') : $t('shareCard.chooseImage') }}
          </el-button>
        </el-upload>
      </div>

      <!-- AI 生成 -->
      <div v-if="source === 'ai'" class="source-panel">
        <el-input
          v-model="aiPrompt"
          type="textarea"
          :rows="3"
          maxlength="2000"
          :placeholder="$t('shareCard.aiPromptPlaceholder')"
          :disabled="generating"
        />
        <div class="ai-actions">
          <el-button
            size="small"
            type="primary"
            plain
            icon="el-icon-magic-stick"
            :loading="generating"
            :disabled="generating || saving"
            @click="onGenerate"
          >
            {{ aiObjectUrl ? $t('shareCard.regenerate') : $t('shareCard.generate') }}
          </el-button>
        </div>
      </div>

      <!-- 渐变色块 -->
      <div v-if="source === 'gradient'" class="source-panel gradient-swatches">
        <button
          v-for="(pair, idx) in gradients"
          :key="idx"
          type="button"
          class="gradient-swatch"
          :class="{ active: gradient === idx }"
          :style="{ background: `linear-gradient(135deg, ${pair[0]}, ${pair[1]})` }"
          :aria-label="`${$t('shareCard.sourceGradient')} ${idx + 1}`"
          @click="gradient = idx"
        ></button>
      </div>

      <el-form label-position="top" class="share-card-form" @submit.native.prevent>
        <el-form-item :label="$t('shareCard.cardTitle')">
          <el-input v-model="title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item class="overlay-item">
          <div class="overlay-row">
            <span>{{ $t('shareCard.overlayTitle') }}</span>
            <el-switch v-model="overlayTitle" />
          </div>
        </el-form-item>
        <el-form-item v-if="isEditMode" :label="$t('shareCard.shareLink')">
          <el-input :value="shareLink" readonly>
            <el-button slot="append" icon="el-icon-document-copy" @click="copyLink">
              {{ $t('shareCard.copyLink') }}
            </el-button>
          </el-input>
        </el-form-item>
      </el-form>
    </div>

    <span slot="footer">
      <el-button :disabled="saving" @click="close">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="loadingCard || generating" @click="onConfirm">
        {{ isEditMode ? $t('shareCard.save') : $t('shareCard.confirmPublish') }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import {
  SHARE_CARD_GRADIENTS,
  defaultGradientIndex,
  loadImageAsObjectUrl,
  composeShareCard
} from '~/utils/shareCard'

const PREVIEW_DEBOUNCE_MS = 150
const AI_TIMEOUT_MS = 150000

export default {
  name: 'ShareCardDialog',

  props: {
    visible: { type: Boolean, default: false },
    // { id, title, isPublic }
    note: { type: Object, default: null }
  },

  data() {
    return {
      gradients: SHARE_CARD_GRADIENTS,
      loadingCard: false,
      generating: false,
      saving: false,
      aiAvailability: null,
      source: 'gradient',
      gradient: 0,
      title: '',
      overlayTitle: false,
      aiPrompt: '',
      // 每种来源各自保留背景，来回切换不丢
      uploadFile: null,            // 新选的本地文件（确认时才上传）
      uploadBackgroundUrl: null,   // 已保存卡片的上传背景（未换图时直接复用）
      uploadObjectUrl: null,
      aiBackgroundUrl: null,
      aiObjectUrl: null,
      previewUrl: null,
      // 打开会话号：关闭/重开后丢弃上一轮迟到的异步结果
      session: 0
    }
  },

  computed: {
    dialogWidth() {
      return this.$device && this.$device.isMobile ? '95%' : '640px'
    },
    noteId() {
      return this.note && this.note.id
    },
    isEditMode() {
      return Boolean(this.note && this.note.isPublic)
    },
    shareLink() {
      if (!process.client || !this.noteId) return ''
      return `${window.location.origin}/notes/${this.noteId}`
    },
    aiAvailable() {
      return this.aiAvailability === 'available'
    },
    aiUnavailableHint() {
      return this.aiAvailability === 'disabled'
        ? this.$t('shareCard.aiDisabled')
        : this.$t('shareCard.aiNotConfigured')
    },
    currentBackgroundObjectUrl() {
      if (this.source === 'upload') return this.uploadObjectUrl
      if (this.source === 'ai') return this.aiObjectUrl
      return null
    },
    showEmptyPlaceholder() {
      return this.source !== 'gradient' && !this.currentBackgroundObjectUrl && !this.generating
    },
    previewKey() {
      return [
        this.source, this.gradient, this.title, this.overlayTitle, this.currentBackgroundObjectUrl
      ].join('|')
    }
  },

  watch: {
    previewKey() {
      if (this.visible) this.schedulePreview()
    }
  },

  beforeDestroy() {
    clearTimeout(this.previewTimer)
    this.resetState()
  },

  methods: {
    onVisibleChange(val) {
      // 保存进行中不允许关：写请求已经发出，结果必须回传给父组件
      if (!val && !this.saving) this.close()
    },

    close() {
      this.$emit('update:visible', false)
    },

    onClosed() {
      this.session++
      clearTimeout(this.previewTimer)
      this.resetState()
    },

    revoke(url) {
      if (url) URL.revokeObjectURL(url)
    },

    resetState() {
      this.revoke(this.uploadObjectUrl)
      this.revoke(this.aiObjectUrl)
      this.revoke(this.previewUrl)
      Object.assign(this, {
        loadingCard: false,
        generating: false,
        saving: false,
        aiAvailability: null,
        source: 'gradient',
        gradient: 0,
        title: '',
        overlayTitle: false,
        aiPrompt: '',
        uploadFile: null,
        uploadBackgroundUrl: null,
        uploadObjectUrl: null,
        aiBackgroundUrl: null,
        aiObjectUrl: null,
        previewUrl: null
      })
    },

    async onOpen() {
      const session = ++this.session
      this.resetState()
      const id = this.noteId
      this.gradient = defaultGradientIndex(id)
      this.title = (this.note && this.note.title) || ''
      this.schedulePreview()
      if (!id) return

      this.loadingCard = true
      let card = null
      try {
        const { data } = await this.$axios.get(`/v1/notes/${id}/share-card`, { suppressErrorToast: true })
        card = data
      } catch (e) {
        if (session !== this.session) return
        this.$message.error(e?.response?.data?.message || this.$t('shareCard.loadFailed'))
      }
      if (session !== this.session) return

      if (card) {
        this.aiAvailability = card.aiAvailability || null
        if (card.source) await this.applySavedCard(card, session)
      }
      if (session === this.session) this.loadingCard = false
    },

    async applySavedCard(card, session) {
      if (card.title) this.title = card.title
      this.overlayTitle = card.overlayTitle !== false
      if (card.aiPrompt) this.aiPrompt = card.aiPrompt
      if (Number.isInteger(card.gradient) && card.gradient >= 0 && card.gradient < SHARE_CARD_GRADIENTS.length) {
        this.gradient = card.gradient
      }

      if ((card.source === 'upload' || card.source === 'ai') && card.backgroundUrl) {
        try {
          const objectUrl = await loadImageAsObjectUrl(this.$axios, card.backgroundUrl)
          if (session !== this.session) {
            this.revoke(objectUrl)
            return
          }
          if (card.source === 'upload') {
            this.uploadBackgroundUrl = card.backgroundUrl
            this.uploadObjectUrl = objectUrl
          } else {
            this.aiBackgroundUrl = card.backgroundUrl
            this.aiObjectUrl = objectUrl
          }
          this.source = card.source
        } catch (e) {
          if (session !== this.session) return
          // 原背景取不到（文件被删等）：退回渐变，用户仍可直接保存
          this.$message.warning(this.$t('shareCard.backgroundLoadFailed'))
          this.source = 'gradient'
        }
      } else if (card.source === 'gradient') {
        this.source = 'gradient'
      }
    },

    schedulePreview() {
      clearTimeout(this.previewTimer)
      this.previewTimer = setTimeout(() => this.renderPreview(), PREVIEW_DEBOUNCE_MS)
    },

    async renderPreview() {
      const session = this.session
      const key = this.previewKey
      try {
        const blob = await composeShareCard(this.composeOptions())
        // 合成期间状态又变了或弹窗已关：丢弃这次结果，等下一轮
        if (session !== this.session || key !== this.previewKey) return
        const url = URL.createObjectURL(blob)
        this.revoke(this.previewUrl)
        this.previewUrl = url
      } catch (e) {
        // 预览失败不打扰用户，确认时会再合成一次并提示
      }
    },

    composeOptions() {
      return {
        backgroundObjectUrl: this.currentBackgroundObjectUrl,
        gradient: this.gradient,
        title: this.title,
        overlayTitle: this.overlayTitle
      }
    },

    onFileChange(file) {
      const raw = file && file.raw
      if (!raw) return
      if (!/^image\/(png|jpe?g|webp|gif)$/i.test(raw.type || '')) {
        this.$message.error(this.$t('shareCard.imageOnly'))
        return
      }
      this.revoke(this.uploadObjectUrl)
      this.uploadFile = raw
      this.uploadBackgroundUrl = null
      this.uploadObjectUrl = URL.createObjectURL(raw)
    },

    async onGenerate() {
      if (!this.noteId || this.generating) return
      const session = this.session
      this.generating = true
      try {
        const prompt = (this.aiPrompt || '').trim()
        const { data } = await this.$axios.post(
          `/v1/notes/${this.noteId}/share-card/background`,
          { prompt: prompt || null },
          { timeout: AI_TIMEOUT_MS, suppressErrorToast: true }
        )
        if (session !== this.session) return
        const objectUrl = await loadImageAsObjectUrl(this.$axios, data.backgroundUrl)
        if (session !== this.session) {
          this.revoke(objectUrl)
          return
        }
        if (data.prompt) this.aiPrompt = data.prompt
        this.revoke(this.aiObjectUrl)
        this.aiBackgroundUrl = data.backgroundUrl
        this.aiObjectUrl = objectUrl
      } catch (e) {
        if (session !== this.session) return
        this.$message.error(e?.response?.data?.message || this.$t('shareCard.generateFailed'))
      } finally {
        if (session === this.session) this.generating = false
      }
    },

    async uploadImage(blobOrFile, fileName) {
      const formData = new FormData()
      formData.append('file', blobOrFile, fileName)
      formData.append('model', 'share-card')
      formData.append('pid', '3')
      const { data } = await this.$axios.post('/v1/upload/image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        suppressErrorToast: true
      })
      if (!data || !data.url) throw new Error('upload returned no url')
      return data.url
    },

    async onConfirm() {
      if (!this.noteId || this.saving) return
      const source = this.source
      if (source === 'upload' && !this.uploadObjectUrl) {
        this.$message.warning(this.$t('shareCard.uploadRequired'))
        return
      }
      if (source === 'ai' && !this.aiObjectUrl) {
        this.$message.warning(this.$t('shareCard.generateRequired'))
        return
      }

      // 保存期间弹窗不可关（见 onVisibleChange），写请求的结果一定回传父组件
      const publish = !this.isEditMode
      this.saving = true
      try {
        // ① 上传来源：新选的原图先上传，拿到背景地址（未换图则复用已保存的）
        let backgroundUrl = null
        if (source === 'upload') {
          backgroundUrl = this.uploadBackgroundUrl
          if (this.uploadFile) {
            backgroundUrl = await this.uploadImage(this.uploadFile, this.uploadFile.name || 'share-card-bg.jpg')
            this.uploadBackgroundUrl = backgroundUrl
            this.uploadFile = null
          }
        } else if (source === 'ai') {
          backgroundUrl = this.aiBackgroundUrl
        }

        // ② 合成成品并上传
        const blob = await composeShareCard(this.composeOptions())
        const imageUrl = await this.uploadImage(blob, `share-card-${this.noteId}.jpg`)

        // ③ 一次 PUT：保存卡片（+ 公开）
        const title = (this.title || '').trim().slice(0, 200)
        const { data } = await this.$axios.put(`/v1/notes/${this.noteId}/share-card`, {
          source,
          imageUrl,
          backgroundUrl,
          gradient: source === 'gradient' ? this.gradient : null,
          aiPrompt: source === 'ai' ? ((this.aiPrompt || '').trim() || null) : null,
          title: title || null,
          overlayTitle: this.overlayTitle,
          publish
        }, { suppressErrorToast: true })

        this.$message.success(publish ? this.$t('shareCard.published') : this.$t('shareCard.saved'))
        this.$emit(publish ? 'published' : 'saved', data)
        this.saving = false
        this.close()
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('shareCard.saveFailed'))
      } finally {
        this.saving = false
      }
    },

    async copyLink() {
      const link = this.shareLink
      try {
        await navigator.clipboard.writeText(link)
        this.$message.success(this.$t('shareCard.copied'))
      } catch (e) {
        this.$message.error(this.$t('shareCard.copyFailed') + link)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.share-card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.source-group {
  align-self: flex-start;
}

.share-card-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted);
}

.preview-frame {
  position: relative;
  width: 100%;
  aspect-ratio: 1200 / 630;
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
}

.preview-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-empty,
.preview-busy {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-muted);
  background: var(--bg-secondary);

  i {
    font-size: 28px;
  }
}

.preview-busy {
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
}

.source-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-actions {
  display: flex;
  justify-content: flex-end;
}

.gradient-swatches {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 10px;
}

.gradient-swatch {
  width: 40px;
  height: 28px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  box-shadow: 0 0 0 1px var(--border-color);
  transition: box-shadow 0.15s ease;

  &.active {
    box-shadow: 0 0 0 2px var(--color-action);
  }

  &:focus-visible {
    outline: 2px solid var(--color-action);
    outline-offset: 2px;
  }
}

.share-card-form {
  ::v-deep .el-form-item {
    margin-bottom: 12px;
  }

  ::v-deep .el-form-item__label {
    padding-bottom: 4px;
    line-height: 1.5;
  }
}

.overlay-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-color);
  font-size: 14px;
}
</style>
