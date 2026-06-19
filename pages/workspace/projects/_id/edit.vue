<template>
  <div class="project-editor-page">
    <!-- 顶部栏：logo + 用户信息 -->
    <div class="workspace-topbar">
      <div class="topbar-left">
        <div class="topbar-logo" @click="$router.push('/')">
          <i class="el-icon-folder-opened"></i>
          <span class="topbar-logo-text">MindIO</span>
        </div>
        <el-button type="text" class="topbar-home-link" @click="$router.push('/')">
          <i class="el-icon-s-home"></i> Homepage
        </el-button>
        <el-button type="text" class="topbar-home-link" @click="$router.push('/workspace/notes')">
          <i class="el-icon-document"></i> Workspace
        </el-button>
      </div>
      <div class="topbar-right">
        <div class="editor-actions">
          <el-button size="small" @click="$router.back()">Cancel</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="saveProject">
            Save
          </el-button>
        </div>
        <el-dropdown @command="handleUserCommand">
          <span class="topbar-user">
            <i class="el-icon-user"></i>
            {{ userName }}
            <i class="el-icon-arrow-down"></i>
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="profile">
              <i class="el-icon-user"></i> Profile
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <i class="el-icon-switch-button"></i> Logout
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div>

    <div class="editor-container">
      <div class="editor-header">
        <h1 class="editor-title">Edit Project</h1>
      </div>

      <el-form ref="projectForm" :model="projectForm" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="名称" prop="name">
              <el-input
                v-model="projectForm.name"
                placeholder="请输入项目名称"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="简称">
              <el-input
                v-model="projectForm.shortName"
                placeholder="如: WN"
                maxlength="20"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="副标题">
          <el-input
            v-model="projectForm.subtitle"
            placeholder="简短副标题（可选）"
            maxlength="200"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="projectForm.description"
            type="textarea"
            :rows="3"
            placeholder="项目描述"
          />
        </el-form-item>

        <el-form-item label="正文内容">
          <div id="projectRichTextEditor" class="rich-text-editor"></div>
          <div class="upload-actions">
            <el-button size="mini" icon="el-icon-upload" @click="uploadDialogVisible = true">
              插入附件/图片
            </el-button>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-input v-model="projectForm.category" placeholder="如: Web, Mobile, Tool" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标">
              <el-input v-model="projectForm.icon" placeholder="图标名称或URL" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面图片URL">
          <el-input v-model="projectForm.imageUrl" placeholder="https://..." />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目链接">
              <el-input v-model="projectForm.projectUrl" placeholder="https://..." />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="GitHub 链接">
              <el-input v-model="projectForm.githubUrl" placeholder="https://github.com/..." />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="技术栈">
          <div class="dynamic-tags">
            <el-tag
              v-for="(tech, idx) in projectForm.technologies"
              :key="idx"
              closable
              size="small"
              @close="projectForm.technologies.splice(idx, 1)"
            >{{ tech }}</el-tag>
            <el-input
              v-if="tagInputVisible"
              ref="tagInput"
              v-model="tagInputValue"
              size="small"
              class="tag-input"
              @keyup.enter.native="addTag"
              @blur="addTag"
            />
            <el-button v-else size="small" class="tag-add-btn" @click="showTagInput">+ 添加</el-button>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="projectForm.displayOrder" :min="0" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="公开">
              <el-switch v-model="projectForm.isPublic" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="精选">
              <el-switch v-model="projectForm.isFeatured" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <el-dialog
      title="上传并插入"
      :visible.sync="uploadDialogVisible"
      width="780px"
      :append-to-body="true"
    >
      <UploadPanel :model="'note'" :pid="1" @success="handleUploadSuccess" />
      <span slot="footer" class="dialog-footer">
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ProjectEditPage',
  layout: 'workspace',
  middleware: 'auth',
  computed: {
    userName() {
      return this.$auth.user?.username || 'User'
    }
  },
  async asyncData({ params, $projectService }) {
    try {
      const project = await $projectService.getProjectById(params.id)
      return {
        projectId: params.id,
        projectForm: {
          name: project.name || '',
          shortName: project.shortName || '',
          subtitle: project.subtitle || '',
          description: project.description || '',
          icon: project.icon || '',
          imageUrl: project.imageUrl || '',
          projectUrl: project.projectUrl || '',
          githubUrl: project.githubUrl || '',
          category: project.category || '',
          technologies: Array.isArray(project.technologies)
            ? [...project.technologies]
            : (project.technologies ? project.technologies.split(',').map(t => t.trim()).filter(t => t) : []),
          content: project.content || '',
          contentType: project.contentType || 'richtext',
          isPublic: project.isPublic !== false,
          isFeatured: project.isFeatured || false,
          displayOrder: project.displayOrder || 0
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
      projectId: null,
      tagInputVisible: false,
      tagInputValue: '',
      projectForm: {
        name: '',
        shortName: '',
        subtitle: '',
        description: '',
        icon: '',
        imageUrl: '',
        projectUrl: '',
        githubUrl: '',
        category: '',
        technologies: [],
        content: '',
        contentType: 'richtext',
        isPublic: true,
        isFeatured: false,
        displayOrder: 0
      },
      rules: {
        name: [
          { required: true, message: '请输入项目名称', trigger: 'blur' },
          { max: 200, message: '项目名称长度不能超过200个字符', trigger: 'blur' }
        ],
        description: [
          { required: true, message: '请输入项目描述', trigger: 'blur' }
        ]
      }
    }
  },
  mounted() {
    if (process.client && this.projectForm.contentType === 'richtext') {
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
      import('wangeditor').then((WangEditor) => {
        const E = WangEditor.default || WangEditor
        this.editor = new E('#projectRichTextEditor')
        this.editor.config.placeholder = '请输入项目正文内容'
        this.editor.config.zIndex = 1000
        this.editor.config.onchange = (html) => {
          this.projectForm.content = html
        }
        this.editor.create()
        if (this.projectForm.content) {
          this.editor.txt.html(this.projectForm.content)
        }
      }).catch((error) => {
        console.error('Failed to load wangeditor:', error)
      })
    },
    handleUploadSuccess(result) {
      this.uploadDialogVisible = false
      if (!result || !result.url) return

      const url = result.url
      const fileName = result.fileName || '附件'
      const type = result.type || ''
      const isImage = type.startsWith('image/') || /\.(png|jpg|jpeg|gif|bmp|webp|svg)$/i.test(url)

      if (this.projectForm.contentType === 'richtext' && this.editor) {
        const html = isImage
          ? `<p><img src="${url}" alt="${fileName}" style="max-width: 100%;" /></p>`
          : `<p><a href="${url}" target="_blank" rel="noopener noreferrer">${fileName}</a></p>`

        try {
          this.editor.cmd.do('insertHTML', html)
          this.projectForm.content = this.editor.txt.html()
        } catch (e) {
          this.projectForm.content = (this.projectForm.content || '') + html
        }

        this.$message.success('已插入到内容中')
      }
    },
    showTagInput() {
      this.tagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.tagInput) {
          this.$refs.tagInput.focus()
        }
      })
    },
    addTag() {
      const val = this.tagInputValue.trim()
      if (val && !this.projectForm.technologies.includes(val)) {
        this.projectForm.technologies.push(val)
      }
      this.tagInputVisible = false
      this.tagInputValue = ''
    },
    async saveProject() {
      this.$refs.projectForm.validate(async (valid) => {
        if (!valid) return

        // 从编辑器获取内容
        if (this.projectForm.contentType === 'richtext' && this.editor) {
          this.projectForm.content = this.editor.txt.html()
        }

        // 将技术栈数组转换为逗号分隔的字符串
        const submitData = {
          ...this.projectForm,
          technologies: this.projectForm.technologies.join(',')
        }

        this.saving = true
        try {
          await this.$projectService.updateProject(this.projectId, submitData)
          this.$message.success('项目更新成功')
          this.$router.push('/workspace/notes')
        } catch (error) {
          const message = error.response?.data?.message || '保存失败'
          this.$message.error(message)
        } finally {
          this.saving = false
        }
      })
    },
    handleUserCommand(command) {
      if (command === 'logout') {
        this.$confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.$auth.logout()
          this.$message.success('已退出登录')
        }).catch(() => {})
      } else if (command === 'profile') {
        this.$router.push('/workspace/profile')
      }
    }
  }
}
</script>

<style scoped lang="scss">
.project-editor-page {
  min-height: 100vh;
  background: var(--bg-secondary);
  padding: 12px 20px;
}

// 顶部栏
.workspace-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 0 2px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #667eea;
  cursor: pointer;

  i { font-size: 22px; }
}

.topbar-home-link {
  font-size: 13px;
  color: var(--text-muted) !important;
  padding: 4px 8px;

  &:hover { color: #667eea !important; }
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;

  .editor-actions {
    display: flex;
    gap: 8px;
  }

  .topbar-user {
    cursor: pointer;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;

    &:hover { color: #667eea; }
  }
}

// 编辑器容器
.editor-container {
  max-width: 1000px;
  margin: 0 auto;
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 32px;
}

.editor-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);

  .editor-title {
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
    margin: 0;
  }
}

.rich-text-editor {
  border: 1px solid var(--input-border);
  border-radius: 4px;
  min-height: 400px;
}

.upload-actions {
  margin-top: 10px;
}

// 动态标签样式
.dynamic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.tag-input {
  width: 120px;
}

.tag-add-btn {
  border-style: dashed;
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

// Element UI 数字输入框适配深色模式
::v-deep .el-input-number {
  .el-input__inner {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
    color: var(--text-color) !important;
  }

  .el-input-number__decrease,
  .el-input-number__increase {
    background: var(--input-bg) !important;
    border-color: var(--input-border) !important;
    color: var(--text-secondary) !important;

    &:hover {
      color: #667eea !important;
    }
  }
}

// Element UI Switch 适配深色模式
::v-deep .el-switch {
  .el-switch__core {
    background-color: var(--input-border) !important;
  }

  &.is-checked .el-switch__core {
    background-color: #667eea !important;
  }
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

  ::v-deep .w-e-toolbar .w-e-menu {
    color: var(--text-secondary);

    &:hover {
      background: var(--bg-secondary);
    }
  }

  ::v-deep .w-e-menu i {
    color: var(--text-secondary);
  }

  ::v-deep .w-e-text-container .placeholder {
    color: var(--text-muted) !important;
  }

  ::v-deep .w-e-text p,
  ::v-deep .w-e-text div,
  ::v-deep .w-e-text span,
  ::v-deep .w-e-text-container p,
  ::v-deep .w-e-text-container div,
  ::v-deep .w-e-text-container span {
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
}

@media screen and (max-width: 768px) {
  .project-editor-page {
    padding: 8px;
  }

  .workspace-topbar {
    .topbar-logo-text { display: none; }
  }

  .topbar-right {
    .editor-actions {
      .el-button span {
        display: none;
      }
    }
  }

  .editor-container {
    padding: 20px;
  }

  .editor-header .editor-title {
    font-size: 20px;
  }
}
</style>

