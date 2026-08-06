<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :title="$t('actions.newClip')"
    :visible.sync="visible"
    width="680px"
    :close-on-click-modal="false"
    @close="reset"
  >
    <el-form label-width="70px" size="small">
      <el-form-item label="网址">
        <el-input
          v-model="urlInput"
          :placeholder="$t('workspace.clips.urlPlaceholder')"
          clearable
          @keyup.enter.native="fetchUrl"
        >
          <el-button
            slot="append"
            :loading="fetching"
            @click="fetchUrl"
          >{{ $t('workspace.clips.previewButton') }}</el-button>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="linkOnly">{{ $t('workspace.clips.linkOnlyCheckbox') }}</el-checkbox>
      </el-form-item>
    </el-form>

    <div v-if="draft" class="draft-preview">
      <el-alert
        v-if="!linkOnly && !draft.fetchSuccess"
        :title="draft.fetchFailReason || $t('workspace.clips.fetchFailedWillLinkOnly')"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom:12px"
      />
      <el-form label-width="70px" size="small">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item v-if="!linkOnly" label="作者">
          <el-input v-model="form.sourceAuthor" />
        </el-form-item>
        <el-form-item v-if="!linkOnly && form.content" label="正文">
          <div class="content-preview">
            <div class="content-preview-body" v-html="form.content" />
          </div>
        </el-form-item>
      </el-form>
    </div>

    <span slot="footer">
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'SourceClipCreateDialog',
  data() {
    return {
      visible: false,
      urlInput: '',
      linkOnly: false,
      fetching: false,
      saving: false,
      draft: null,
      form: this.emptyForm(),
    }
  },
  methods: {
    emptyForm() {
      return {
        sourceType: 'WEBPAGE',
        sourceUrl: '',
        sourceTitle: '',
        sourceAuthor: '',
        extractionMode: 'FULL',
        extractionStatus: null,
        title: '',
        content: '',
        contentFormat: 'html',
        tagIds: [],
      }
    },
    open() {
      this.visible = true
    },
    reset() {
      this.urlInput = ''
      this.linkOnly = false
      this.draft = null
      this.form = this.emptyForm()
    },
    async fetchUrl() {
      if (!this.urlInput.trim()) return
      this.fetching = true
      this.draft = null
      const mode = this.linkOnly ? 'LINK_ONLY' : 'FULL'
      try {
        const draft = await this.$clipService.importFromUrl(this.urlInput.trim(), mode)
        this.draft = draft
        this.form.sourceType = draft.sourceType
        this.form.sourceUrl = draft.sourceUrl || this.urlInput.trim()
        this.form.sourceTitle = draft.sourceTitle || ''
        this.form.sourceAuthor = draft.sourceAuthor || ''
        this.form.extractionMode = draft.extractionMode || mode
        this.form.extractionStatus = draft.extractionStatus || null
        this.form.title = draft.suggestedTitle || draft.sourceTitle || ''
        this.form.content = draft.content || ''
        this.form.contentFormat = draft.contentFormat || 'html'
      } catch (e) {
        this.$message.error('抓取失败')
        this.draft = { fetchSuccess: false, fetchFailReason: e?.response?.data?.message || '网络错误' }
      } finally {
        this.fetching = false
      }
    },
    async save() {
      if (!this.draft) {
        if (!this.urlInput.trim()) {
          this.$message.warning('请输入网址')
          return
        }
        await this.fetchUrl()
      }
      if (!this.form.title.trim()) {
        this.$message.warning('请填写标题')
        return
      }
      this.saving = true
      try {
        const clip = await this.$clipService.createClip(this.form)
        this.$message.success('收藏已保存')
        this.visible = false
        this.$emit('created', clip)
      } catch (e) {
        this.$message.error('保存失败')
      } finally {
        this.saving = false
      }
    },
  },
}
</script>

<style scoped>
.draft-preview { margin-top: 4px; }
.content-preview {
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 4px;
  overflow: hidden;
}
.content-preview-body {
  padding: 10px 12px;
  max-height: 280px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.content-preview-body img { max-width: 100%; height: auto; }
</style>
