<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="note-editor-page">
    <div class="editor-layout">
      <!-- 左侧栏：表单字段 -->
      <aside class="editor-sidebar">
        <el-form ref="noteForm" :model="noteForm" :rules="rules" label-width="80px">
          <!-- 操作按钮 -->
          <div class="sidebar-actions">
            <el-button size="small" @click="$router.back()">取消</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveNote">
              保存
            </el-button>
          </div>
          <div class="sidebar-actions" style="margin-top:8px;">
            <el-button size="small" icon="el-icon-paperclip" @click="goToClips">参考素材</el-button>
          </div>

          <el-form-item label="标题" prop="title">
            <el-input
              v-model="noteForm.title"
              placeholder="请输入笔记标题"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="内容类型" prop="contentType">
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
                  :type="getTagType(tag.id)"
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

          <el-form-item label="项目">
            <el-select
              v-model="noteForm.projectId"
              placeholder="选择项目（可选）"
              clearable
              style="width: 100%;"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.name"
                :value="project.id"
              >
                <span>
                  <i v-if="project.icon" :class="project.icon" style="margin-right: 4px;"></i>
                  {{ project.name }}
                </span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="公开设置">
            <el-switch
              v-model="noteForm.isPublic"
              active-text="公开"
              inactive-text="私密"
            />
          </el-form-item>

          <el-form-item label="摘要">
            <el-input
              v-model="noteForm.summary"
              type="textarea"
              :rows="4"
              placeholder="笔记摘要（可选）"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </aside>

      <!-- 右侧栏：编辑器 -->
      <main class="editor-main">
        <div class="editor-content-wrapper">
          <div class="editor-content-header">
            <span class="content-label">内容</span>
            <el-button size="mini" icon="el-icon-upload" @click="uploadDialogVisible = true">
              插入附件/图片
            </el-button>
          </div>
          <div class="editor-content-area">
            <el-input
              v-if="noteForm.contentType === 'markdown'"
              v-model="noteForm.content"
              type="textarea"
              :rows="30"
              placeholder="请输入 Markdown 内容"
              class="markdown-editor"
            />
            <div v-else id="richTextEditor" class="rich-text-editor"></div>
          </div>
        </div>
      </main>
    </div>

    <el-dialog
      title="上传并插入"
      :visible.sync="uploadDialogVisible"
      width="780px"
      :append-to-body="true"
    >
      <UploadPanel :model="'note'" :pid="1" @success="handleUploadSuccess" @insert-html="handleInsertHtml" />
      <span slot="footer" class="dialog-footer">
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'NoteEditPage',
  layout: 'workspace',
  middleware: 'auth',
  async asyncData({ params, $axios }) {
    try {
      const [noteRes, tagsRes, projectsRes] = await Promise.all([
        $axios.get(`/v1/notes/${params.id}`),
        $axios.get('/v1/tags'),
        $axios.get('/v1/projects/my')
      ])

      const note = noteRes.data
      return {
        noteId: params.id,
        tags: tagsRes.data || [],
        projects: projectsRes.data || [],
        noteForm: {
          title: note.title,
          content: note.content,
          contentType: note.contentType,
          isPublic: note.isPublic,
          tagIds: note.tags.map((t) => t.id),
          projectId: note.projectId || null,
          summary: note.summary || '',
          sectionContents: note.sectionContents || [],
          sectionTypes: note.sectionTypes || []
        }
      }
    } catch (error) {
      return {
        error: true
      }
    }
  },
  data() {
    return {
      loading: false,
      saving: false,
      editor: null,
      uploadDialogVisible: false,
      noteId: null,
      tags: [],
      projects: [],
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
    console.log('noteForm.contentType', this.noteForm.contentType);
    console.log('noteForm', this.noteForm);
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
    goToClips() {
      this.$router.push(`/workspace/clips?linkTo=${this.noteId}`)
    },
    // 处理内容类型变更（阻止修改，恢复原值）
    handleReadonlyChange() {
      console.log('handleReadonlyChange');
      // 使用nextTick确保在DOM更新后恢复值
      this.$nextTick(() => {
        console.log('nextTick');
        this.noteForm.contentType = this.readonlyFieldsBackup.contentType;
      });
    },
    initRichTextEditor() {
      import('wangeditor').then((WangEditor) => {
        const E = WangEditor.default || WangEditor
        this.editor = new E('#richTextEditor')
        this.editor.config.placeholder = '请输入笔记内容'
        this.editor.config.zIndex = 1
        this.editor.config.onchange = (html) => {
          this.noteForm.content = html
        }
        this.editor.create()
        this.editor.txt.html(this.noteForm.content)
      })
    },
    handleUploadSuccess(result) {
      this.uploadDialogVisible = false
      if (!result || !result.url) return

      const url = result.url
      const fileName = result.fileName || '附件'
      const type = result.type || ''
      const isImage = type.startsWith('image/') || /\.(png|jpg|jpeg|gif|bmp|webp|svg)$/i.test(url)

      if (this.noteForm.contentType === 'richtext' && this.editor) {
        const html = isImage
          ? `<p><img src="${url}" alt="${fileName}" style="max-width: 100%;" /></p>`
          : `<p><a href="${url}" target="_blank" rel="noopener noreferrer">${fileName}</a></p>`

        try {
          this.editor.cmd.do('insertHTML', html)
          this.noteForm.content = this.editor.txt.html()
        } catch (e) {
          this.noteForm.content = (this.noteForm.content || '') + html
        }

        this.$message.success('已插入到内容中')
        return
      }

      const md = isImage ? `\n\n![${fileName}](${url})\n` : `\n\n[${fileName}](${url})\n`
      this.noteForm.content = (this.noteForm.content || '') + md
      this.$message.success('已插入到内容中')
    },
    handleInsertHtml(htmlContent) {
      this.uploadDialogVisible = false
      if (!htmlContent) return

      if (this.noteForm.contentType === 'richtext' && this.editor) {
        try {
          this.editor.cmd.do('insertHTML', htmlContent)
          this.noteForm.content = this.editor.txt.html()
        } catch (e) {
          this.noteForm.content = (this.noteForm.content || '') + htmlContent
        }
        this.$message.success('HTML 内容已插入正文')
      } else {
        this.$message.warning('仅富文本模式支持直接插入 HTML 文件内容')
      }
    },
    // 获取标签的类型（用于响应式更新）
    getTagType(tagId) {
      return this.noteForm.tagIds.includes(tagId) ? 'primary' : 'info'
    },
    // 检查标签是否被选中
    isTagSelected(tagId) {
      return this.noteForm.tagIds.includes(tagId)
    },
    toggleTag(tagId) {
      console.log('toggleTag called with tagId:', tagId)
      console.log('Current tagIds:', this.noteForm.tagIds)
      const index = this.noteForm.tagIds.indexOf(tagId)
      if (index > -1) {
        // 使用 splice 移除元素，Vue 2 能检测到这种变化
        this.noteForm.tagIds.splice(index, 1)
        console.log('Tag removed, new tagIds:', this.noteForm.tagIds)
      } else {
        // 使用 push 添加元素，Vue 2 能检测到这种变化
        this.noteForm.tagIds.push(tagId)
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

        if (this.noteForm.contentType === 'richtext' && this.editor) {
          this.noteForm.content = this.editor.txt.html()
        }

        if (!this.noteForm.content) {
          this.$message.warning('请输入笔记内容')
          return
        }

        this.saving = true
        try {
          await this.$axios.put(`/v1/notes/${this.noteId}`, this.noteForm)
          this.$message.success('笔记更新成功')
          this.$router.push('/workspace/notes')
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
  min-height: calc(100vh - 110px);
  background: var(--bg-secondary);
  padding: 16px 20px;
}

// 两栏布局
.editor-layout {
  display: grid;
  grid-template-columns: 25% 1fr;
  gap: 20px;
  max-width: 100%;
  height: 100%;
}

// 左侧栏
.editor-sidebar {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 24px;
  height: fit-content;
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 140px);
  overflow-y: auto;

  // 自定义滚动条样式
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 3px;
    &:hover {
      background: var(--text-muted);
    }
  }
  scrollbar-width: thin;
  scrollbar-color: var(--border-color) transparent;
}

.sidebar-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

// 右侧栏
.editor-main {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 24px;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 140px);
  max-height: calc(100vh - 140px);
  overflow-y: auto;
}

.editor-content-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
}

.editor-content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.content-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.editor-content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.markdown-editor {
  flex: 1;
  
  ::v-deep .el-textarea__inner {
    height: 100%;
    min-height: 600px;
    resize: none;
  }
}

.rich-text-editor {
  border: 1px solid var(--input-border);
  border-radius: 4px;
  min-height: 600px;
  flex: 1;
  display: flex;
  flex-direction: column;

  // 确保编辑器容器占满空间
  ::v-deep .w-e-text-container {
    flex: 1;
    min-height: 600px;
  }
}

// 表单样式
::v-deep .el-form-item__label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

::v-deep .el-input__inner,
::v-deep .el-textarea__inner {
  background: var(--input-bg);
  border-color: var(--input-border);
  color: var(--text-color);

  &:focus {
    border-color: #667eea;
  }
}

// 字数统计区域适配深色模式
::v-deep .el-input__count,
::v-deep .el-input__count-inner {
  background: transparent !important;
  color: var(--text-muted) !important;
}

::v-deep .el-input .el-input__count .el-input__count-inner {
  background: var(--input-bg) !important;
  color: var(--text-muted) !important;
}

::v-deep .el-textarea .el-input__count {
  background: var(--input-bg);
  color: var(--text-muted);
}

// 富文本编辑器适配深色模式
.rich-text-editor {
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

  /* 暗色模式：覆盖 WangEditor CSS 中的浅色背景 */
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

    &:hover {
      background: var(--bg-secondary);
    }
  }

  ::v-deep .w-e-menu i {
    color: var(--text-secondary);
  }
}

// 响应式设计
@media screen and (max-width: 1024px) {
  .editor-layout {
    grid-template-columns: 280px 1fr;
    gap: 16px;
  }

  .editor-sidebar {
    padding: 20px;
  }

  .editor-main {
    padding: 20px;
  }
}

@media screen and (max-width: 768px) {
  .note-editor-page {
    padding: 8px;
  }

  .editor-layout {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .editor-sidebar {
    position: static;
    max-height: none;
    padding: 16px;
  }

  .sidebar-actions {
    .el-button span {
      display: none;
    }
  }

  .editor-main {
    padding: 16px;
    min-height: auto;
  }

  .markdown-editor {
    ::v-deep .el-textarea__inner {
      min-height: 400px;
    }
  }

  .rich-text-editor {
    min-height: 400px;
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
</style>
