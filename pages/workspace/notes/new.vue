<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="note-editor-page">
    <el-card>
      <div class="editor-header">
        <h2 class="page-title">创建笔记</h2>
        <div class="editor-actions">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveNote">
            保存
          </el-button>
        </div>
      </div>

      <el-form ref="noteForm" :model="noteForm" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="noteForm.title"
            placeholder="请输入笔记标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="内容类型2" prop="contentType">
          <el-radio-group v-model="noteForm.contentType" @change="handleReadonlyChange">
            <el-radio label="richtext">富文本</el-radio>
            <el-radio label="markdown">Markdown</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="标签" prop="tags">
          <div class="tag-selector">
            <div class="tag-list">
              <el-tag
                v-for="tag in tags"
                :key="tag.id"
                :type="noteForm.tagIds.includes(tag.id) ? 'primary' : 'info'"
                effect="plain"
                size="small"
                class="tag-item"
                @click.native="toggleTag(tag.id)"
              >
                {{ tag.name }}
              </el-tag>
            </div>
            <div class="tag-input-wrapper">
              <el-input
                v-model="newTagName"
                placeholder="输入新标签名，按回车创建"
                size="small"
                class="tag-input"
                @keyup.enter.native="createNewTag"
              />
              <el-button
                size="small"
                type="primary"
                :disabled="!newTagName || !newTagName.trim()"
                @click="createNewTag"
              >
                创建
              </el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="公开设置">
          <el-switch
            v-model="noteForm.isPublic"
            active-text="公开"
            inactive-text="私密"
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-if="noteForm.contentType === 'markdown'"
            v-model="noteForm.content"
            type="textarea"
            :rows="15"
            placeholder="请输入 Markdown 内容"
          />
          <div v-else id="richTextEditor" class="rich-text-editor"></div>
        </el-form-item>

        <el-form-item label="摘要">
          <el-input
            v-model="noteForm.summary"
            type="textarea"
            :rows="3"
            placeholder="笔记摘要（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'NoteNewPage',
  async asyncData({ $axios }) {
    try {
      const { data } = await $axios.get('/v1/tags?scope=note')
      return {
        tags: data || []
      }
    } catch (error) {
      return {
        tags: []
      }
    }
  },
  data() {
    return {
      saving: false,
      editor: null,
      tags: [],
      newTagName: '',
      readonlyFieldsBackup: {
        contentType: 'richtext'
      },
      noteForm: {
        title: '',
        content: '',
        contentType: 'richtext',
        isPublic: false,
        tagIds: [],
        summary: '',
        sectionContents: [],
        sectionTypes: []
      },
      rules: {
        title: [
          { required: true, message: '请输入笔记标题', trigger: 'blur' },
          { max: 200, message: '标题长度不能超过200个字符', trigger: 'blur' }
        ],
        content: [
          { required: true, message: '请输入笔记内容', trigger: 'blur' }
        ]
      }
    }
  },
  mounted() {
    this.readonlyFieldsBackup.contentType = this.noteForm.contentType;
    if (process.client && this.noteForm.contentType === 'richtext') {
      this.initRichTextEditor()
    }
  },
  beforeDestroy() {
    if (this.editor) {
      this.editor.destroy()
    }
  },
  methods: {
    initRichTextEditor() {
      // 动态导入 wangeditor
      import('wangeditor').then((WangEditor) => {
        const E = WangEditor.default || WangEditor
        this.editor = new E('#richTextEditor')
        this.editor.config.placeholder = '请输入笔记内容'
        this.editor.config.zIndex = 1
        this.editor.config.onchange = (html) => {
          this.noteForm.content = html
        }
        this.editor.create()
      })
    },
    // 处理内容类型变更（阻止修改，恢复原值）
    handleReadonlyChange() {
      // 使用nextTick确保在DOM更新后恢复值
      this.$nextTick(() => {
        this.noteForm.contentType = this.readonlyFieldsBackup.contentType;
      });
    },
    toggleTag(tagId) {
      console.log('toggleTag called with tagId:', tagId)
      console.log('Current tagIds:', this.noteForm.tagIds)
      const index = this.noteForm.tagIds.indexOf(tagId)
      if (index > -1) {
        // 创建新数组以确保响应式更新
        this.$set(this.noteForm, 'tagIds', this.noteForm.tagIds.filter(id => id !== tagId))
        console.log('Tag removed, new tagIds:', this.noteForm.tagIds)
      } else {
        // 创建新数组以确保响应式更新
        this.$set(this.noteForm, 'tagIds', [...this.noteForm.tagIds, tagId])
        console.log('Tag added, new tagIds:', this.noteForm.tagIds)
      }
    },
    async createNewTag() {
      const tagName = this.newTagName?.trim()
      if (!tagName) {
        return
      }

      // 检查标签是否已存在
      const existingTag = this.tags.find(
        (t) => t.name.toLowerCase() === tagName.toLowerCase()
      )
      if (existingTag) {
        // 如果已存在，直接选中
        if (!this.noteForm.tagIds.includes(existingTag.id)) {
          this.noteForm.tagIds.push(existingTag.id)
        }
        this.newTagName = ''
        this.$message.info('标签已存在，已自动选中')
        return
      }

      try {
        const res = await this.$axios.post('/v1/tags', { name: tagName })
        this.tags.push(res.data)
        this.noteForm.tagIds.push(res.data.id)
        this.newTagName = ''
        this.$message.success('标签创建成功')
      } catch (error) {
        this.$message.error('创建标签失败')
      }
    },
    async saveNote() {
      this.$refs.noteForm.validate(async (valid) => {
        if (!valid) return

        // 如果是富文本编辑器，获取内容
        if (this.noteForm.contentType === 'richtext' && this.editor) {
          this.noteForm.content = this.editor.txt.html()
        }

        if (!this.noteForm.content) {
          this.$message.warning('请输入笔记内容')
          return
        }

        this.saving = true
        try {
          await this.$axios.post('/v1/notes', this.noteForm)
          this.$message.success('笔记创建成功')
          this.$router.push('/notes')
        } catch (error) {
          const message = error.response?.data?.message || '保存失败'
          this.$message.error(message)
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>

<style scoped lang="scss">
.note-editor-page {
  max-width: 900px;
  margin: 0 auto;

  .editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 30px;

    .page-title {
      margin: 0;
    }

    .editor-actions {
      display: flex;
      gap: 10px;
    }
  }

  .rich-text-editor {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    min-height: 400px;

    ::v-deep .w-e-toolbar {
      background: var(--input-bg) !important;
      border-color: var(--input-border) !important;
    }
    ::v-deep .w-e-text-container {
      background: var(--input-bg) !important;
      border-color: var(--input-border) !important;
    }
    ::v-deep .w-e-text {
      background: var(--input-bg) !important;
      color: var(--text-color) !important;
    }
    ::v-deep .w-e-text blockquote {
      background-color: var(--bg-tertiary) !important;
      border-left-color: #4a6fa5;
      color: var(--text-secondary);
    }
    ::v-deep .w-e-text code {
      background-color: var(--bg-tertiary) !important;
      color: var(--text-color);
    }
    ::v-deep .w-e-text table th {
      background-color: var(--bg-tertiary) !important;
    }
    ::v-deep .w-e-text table,
    ::v-deep .w-e-text table td,
    ::v-deep .w-e-text table th {
      border-color: var(--border-color) !important;
    }
    ::v-deep .w-e-text p,
    ::v-deep .w-e-text div,
    ::v-deep .w-e-text span {
      color: var(--text-color) !important;
    }
    ::v-deep .w-e-toolbar .w-e-menu {
      color: var(--text-secondary);
      &:hover { background: var(--bg-secondary); }
    }
    ::v-deep .w-e-menu i {
      color: var(--text-secondary);
    }
  }
}

// 标签选择器样式
.tag-selector {
  width: 100%;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  min-height: 32px;
}

.tag-item {
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;

  &:hover {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

.tag-input-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tag-input {
  flex: 1;
}

@media screen and (max-width: 768px) {
  .note-editor-page {
    .editor-header {
      flex-direction: column;
      align-items: stretch;
      gap: 15px;

      .editor-actions {
        justify-content: flex-end;
      }
    }
  }
}
</style>
