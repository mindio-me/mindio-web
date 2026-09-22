<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    title="发布到微信公众号"
    :visible.sync="visible"
    width="480px"
    :close-on-click-modal="false"
    @close="onClose"
  >
    <el-form :model="form" label-width="60px" label-position="left">
      <el-form-item label="封面图">
        <div class="cover-upload-area" @click="triggerFileInput">
          <img v-if="coverImageUrl" :src="coverImageUrl" class="cover-preview" />
          <div v-else class="cover-placeholder">
            <i class="el-icon-picture-outline"></i>
            <span>点击上传封面图</span>
          </div>
          <div v-if="coverImageUrl" class="cover-replace-hint">点击更换</div>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png"
          style="display:none"
          @change="onFileSelected"
        />
        <div class="cover-tip">微信草稿必须有封面图（jpg/png，≤ 2MB）</div>
      </el-form-item>

      <el-form-item label="标题">
        <el-input v-model="form.title" placeholder="默认使用笔记标题" clearable />
      </el-form-item>
      <el-form-item label="作者">
        <el-input v-model="form.author" placeholder="可选" clearable />
      </el-form-item>
      <el-form-item label="摘要">
        <el-input
          v-model="form.digest"
          type="textarea"
          :rows="2"
          placeholder="可选，默认取正文前120字"
        />
      </el-form-item>
      <el-form-item label="方式">
        <el-radio-group v-model="form.mode">
          <el-radio label="draft">保存为草稿（在公众号后台确认后发送）</el-radio>
          <el-radio label="publish">直接群发</el-radio>
        </el-radio-group>
      </el-form-item>
      <div v-if="form.mode === 'publish'" class="publish-warning">
        <i class="el-icon-warning"></i> 群发后不可撤回，且每天限 1 次
      </div>
    </el-form>

    <span slot="footer">
      <el-button @click="onClose">取消</el-button>
      <el-button
        :type="form.mode === 'publish' ? 'danger' : 'primary'"
        :loading="loading"
        @click="onConfirm"
      >
        确认发布
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'WechatPublishDialog',

  props: {
    noteId: { type: Number, default: null },
    noteTitle: { type: String, default: '' },
    noteContent: { type: String, default: '' }
  },

  data() {
    return {
      visible: false,
      loading: false,
      coverImageUrl: '',
      coverImageFile: null,
      form: {
        title: '',
        author: '',
        digest: '',
        mode: 'draft'
      }
    }
  },

  watch: {
    noteTitle(val) {
      this.form.title = val
    }
  },

  methods: {
    open(content) {
      this.form.title = this.noteTitle
      this.form.author = ''
      this.form.digest = ''
      this.form.mode = 'draft'
      this.coverImageFile = null
      const src = content !== undefined ? content : this.noteContent
      this.coverImageUrl = this._extractFirstImageUrl(src)
      this.visible = true
    },

    onClose() {
      this.visible = false
    },

    triggerFileInput() {
      this.$refs.fileInput.value = ''
      this.$refs.fileInput.click()
    },

    onFileSelected(event) {
      const file = event.target.files[0]
      if (!file) return

      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        this.$message.error('仅支持 jpg/png 格式')
        return
      }
      if (file.size > 2 * 1024 * 1024) {
        this.$message.error('封面图不能超过 2MB')
        return
      }

      this.coverImageFile = file
      const reader = new FileReader()
      reader.onload = (e) => { this.coverImageUrl = e.target.result }
      reader.readAsDataURL(file)
    },

    async onConfirm() {
      if (!this.noteId) {
        this.$message.error('请先保存笔记')
        return
      }
      this.loading = true
      try {
        let thumbMediaId

        if (this.coverImageFile) {
          // 用户手动选择了封面文件
          const uploadResult = await this.$wechatService.uploadCoverImage(this.coverImageFile)
          thumbMediaId = uploadResult.mediaId
        }

        const payload = {
          title: this.form.title || undefined,
          author: this.form.author || undefined,
          digest: this.form.digest || undefined,
          thumbMediaId: thumbMediaId || undefined,
          // 没有手动上传文件但有预览图时，把本地 URL 传给后端，由后端下载并上传微信
          coverImageUrl: (!thumbMediaId && this.coverImageUrl) ? this.coverImageUrl : undefined
        }

        let result
        if (this.form.mode === 'publish') {
          result = await this.$wechatService.publish(this.noteId, payload)
        } else {
          result = await this.$wechatService.pushDraft(this.noteId, payload)
        }

        const modeText = this.form.mode === 'publish' ? '群发成功！' : '草稿已推送至公众号后台'
        this.$message.success(modeText)
        this.visible = false
        this.$emit('published', result)
      } catch (err) {
        const msg = err?.response?.data?.message || err.message || '发布失败'
        this.$message.error(msg)
      } finally {
        this.loading = false
      }
    },

    _extractFirstImageUrl(content) {
      if (!content) return ''
      try {
        const parsed = typeof content === 'string' ? JSON.parse(content) : content
        const blocks = parsed.blocks || []
        for (const block of blocks) {
          if (block.type === 'image') {
            const url = block.data?.file?.url
            if (url) return url
          }
        }
      } catch (e) {
        // 非 EditorJS JSON，忽略
      }
      return ''
    }
  }
}
</script>

<style scoped>
.cover-upload-area {
  position: relative;
  width: 100px;
  height: 100px;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
}
.cover-upload-area:hover {
  border-color: #409eff;
}
.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #c0c4cc;
  font-size: 12px;
  gap: 4px;
}
.cover-placeholder .el-icon-picture-outline {
  font-size: 24px;
}
.cover-replace-hint {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0,0,0,0.45);
  color: #fff;
  font-size: 12px;
  text-align: center;
  padding: 3px 0;
  opacity: 0;
  transition: opacity 0.2s;
}
.cover-upload-area:hover .cover-replace-hint {
  opacity: 1;
}
.cover-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.publish-warning {
  color: #e6622e;
  font-size: 13px;
  margin-top: 4px;
  padding: 6px 8px;
  background: #fff5f0;
  border-radius: 4px;
}
.publish-warning .el-icon-warning {
  margin-right: 4px;
}
</style>
