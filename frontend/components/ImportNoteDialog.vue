<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :visible.sync="visible"
    :title="$t('workspace.createNote.importTitle')"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-note-dialog">
      <p class="dialog-subtitle">{{ $t('workspace.createNote.importSubtitle') }}</p>

      <div class="option-cards">
        <div class="option-card" @click="handleSelect('feishu')">
          <div class="option-icon">
            <i class="el-icon-cloudy"></i>
          </div>
          <div class="option-content">
            <h3 class="option-title">{{ $t('workspace.createNote.feishu') }}</h3>
            <p class="option-desc">{{ $t('workspace.createNote.feishuDesc') }}</p>
          </div>
        </div>

        <div class="option-card" @click="handleSelect('markdown')">
          <div class="option-icon">
            <i class="el-icon-document-add"></i>
          </div>
          <div class="option-content">
            <h3 class="option-title">{{ $t('workspace.createNote.markdown') }}</h3>
            <p class="option-desc">{{ $t('workspace.createNote.markdownDesc') }}</p>
          </div>
        </div>

        <div class="option-card" @click="handleSelect('mindio')">
          <div class="option-icon">
            <i class="el-icon-upload2"></i>
          </div>
          <div class="option-content">
            <h3 class="option-title">{{ $t('workspace.noteBackup.importOption') }}</h3>
            <p class="option-desc">{{ $t('workspace.noteBackup.importOptionDesc') }}</p>
          </div>
        </div>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      accept=".md,.markdown"
      style="display: none"
      @change="handleFileSelect"
    />
    <input
      ref="mindioInput"
      type="file"
      accept=".mindio"
      style="display: none"
      @change="handleMindioSelect"
    />
  </el-dialog>
</template>

<script>
export default {
  name: 'ImportNoteDialog',
  props: {
    value: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      visible: this.value
    }
  },
  watch: {
    value(newVal) {
      this.visible = newVal
    },
    visible(newVal) {
      this.$emit('input', newVal)
    }
  },
  methods: {
    handleSelect(type) {
      if (type === 'feishu') {
        this.$emit('select-feishu')
        this.handleClose()
      } else if (type === 'markdown') {
        this.$refs.fileInput.click()
      } else if (type === 'mindio') {
        this.$refs.mindioInput.click()
      }
    },
    handleMindioSelect(event) {
      const file = event.target.files[0]
      event.target.value = ''
      if (!file) return
      if (!/\.mindio$/i.test(file.name)) {
        this.$message.error(this.$t('workspace.noteBackup.invalidFile'))
        return
      }
      this.$emit('select-mindio', { file })
      this.handleClose()
    },
    async handleFileSelect(event) {
      const file = event.target.files[0]
      if (!file) return

      if (!file.name.match(/\.(md|markdown)$/i)) {
        this.$message.error(this.$t('workspace.createNote.invalidMarkdown'))
        return
      }

      try {
        const content = await this.readFileAsText(file)
        const title = file.name.replace(/\.(md|markdown)$/i, '') || this.$t('workspace.createNote.defaultTitle')

        this.$emit('select-markdown', { title, content, file })
        this.handleClose()
      } catch (error) {
        this.$message.error(this.$t('workspace.createNote.readError') + error.message)
      } finally {
        event.target.value = ''
      }
    },
    readFileAsText(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = (e) => resolve(e.target.result)
        reader.onerror = () => reject(new Error(this.$t('workspace.createNote.readError')))
        reader.readAsText(file, 'UTF-8')
      })
    },
    handleClose() {
      this.visible = false
      this.$emit('input', false)
    }
  }
}
</script>

<style scoped lang="scss">
.import-note-dialog {
  padding: 8px 0;
}

.dialog-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 20px;
  text-align: center;
}

.option-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--card-bg-color);

  &:hover {
    border-color: var(--color-nav);
    background: var(--bg-secondary);
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);
  }

  &:active {
    transform: scale(0.98);
  }
}

.option-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--icon-bg);
  border-radius: 8px;
  color: var(--color-nav);

  i {
    font-size: 24px;
  }
}

.option-content {
  flex: 1;
  min-width: 0;
}

.option-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 4px 0;
}

.option-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.5;
}

.theme-dark {
  .option-card {
    &:hover {
      background: var(--bg-tertiary);
    }
  }
}

@media screen and (max-width: 768px) {
  .option-card {
    padding: 12px;
    gap: 12px;
  }

  .option-icon {
    width: 40px;
    height: 40px;

    i {
      font-size: 20px;
    }
  }

  .option-title {
    font-size: 15px;
  }

  .option-desc {
    font-size: 12px;
  }
}
</style>
