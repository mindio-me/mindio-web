<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="note-detail-page">
    <el-card v-loading="loading">
      <div class="note-header">
        <el-button icon="el-icon-back" @click="$router.back()">
          返回
        </el-button>
      </div>

      <div v-if="note" class="note-content">
        <h1 class="note-title">{{ note.title }}</h1>

        <div class="note-meta">
          <span class="meta-item">
            <i class="el-icon-user"></i>
            {{ note.ownerUsername }}
          </span>
          <span class="meta-item">
            <i class="el-icon-time"></i>
            创建于 {{ formatTime(note.createdAt) }}
          </span>
          <span class="meta-item">
            <i class="el-icon-edit"></i>
            修改于 {{ formatTime(note.modifiedAt) }}
          </span>
        </div>

        <div v-if="note.tags.length > 0" class="note-tags">
          <el-tag
            v-for="tag in note.tags"
            :key="tag.id"
            type="info"
            effect="plain"
          >
            {{ tag.name }}
          </el-tag>
        </div>

        <el-divider></el-divider>

        <div v-if="note.summary" class="note-summary">
          <h3>摘要</h3>
          <p>{{ note.summary }}</p>
        </div>

        <div class="note-body">
          <div
            v-if="note.contentType === 'richtext'"
            class="rich-content"
            v-html="note.content"
          ></div>
          <div v-else class="markdown-content">
            <pre>{{ note.content }}</pre>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'PublicNoteDetailPage',
  auth: false,
  async asyncData({ params, $axios }) {
    try {
      const { data } = await $axios.get(`/v1/notes/public/${params.id}`)
      return {
        note: data
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
      note: null
    }
  },
  methods: {
    formatTime(time) {
      if (!time) return ''
      const normalized = typeof time === 'string' && !/Z$|[+-]\d{2}:\d{2}$/.test(time) ? time + 'Z' : time
      return new Date(normalized).toLocaleString('zh-CN')
    }
  }
}
</script>

<style scoped lang="scss">
.note-detail-page {
  max-width: 900px;
  margin: 0 auto;

  .note-header {
    margin-bottom: 30px;
  }

  .note-content {
    .note-title {
      font-size: 32px;
      font-weight: bold;
      color: #303133;
      margin: 0 0 20px 0;
    }

    .note-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;
      color: #909399;
      font-size: 14px;
      margin-bottom: 15px;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 5px;
      }
    }

    .note-tags {
      margin-bottom: 20px;

      .el-tag {
        margin-right: 10px;
        margin-bottom: 8px;
      }
    }

    .note-summary {
      background: #f5f7fa;
      padding: 20px;
      border-radius: 4px;
      margin-bottom: 30px;

      h3 {
        margin: 0 0 10px 0;
        color: #606266;
      }

      p {
        margin: 0;
        color: #606266;
        line-height: 1.6;
      }
    }

    .note-body {
      .rich-content,
      .markdown-content {
        font-size: 16px;
        line-height: 1.8;
        color: #303133;

        ::v-deep img {
          max-width: 100%;
          height: auto;
        }

        ::v-deep pre {
          background: #f5f7fa;
          padding: 15px;
          border-radius: 4px;
          overflow-x: auto;
        }

        ::v-deep code {
          background: #f5f7fa;
          padding: 2px 6px;
          border-radius: 2px;
          font-family: 'Courier New', monospace;
        }
      }
    }
  }
}

@media screen and (max-width: 768px) {
  .note-detail-page {
    .note-content {
      .note-title {
        font-size: 24px;
      }

      .note-meta {
        flex-direction: column;
        gap: 8px;
      }
    }
  }
}
</style>
