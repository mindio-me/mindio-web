<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="public-notes-page">
    <div class="page-header">
      <h1 class="page-title">公开笔记</h1>
    </div>

    <el-card v-loading="loading" class="notes-list">
      <el-row v-if="notes.length > 0" :gutter="20">
        <el-col
          v-for="note in notes"
          :key="note.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
        >
          <div class="note-card" @click="viewNote(note.id)">
            <div class="note-header">
              <h3 class="note-title">{{ note.title }}</h3>
            </div>
            <div class="note-content">
              {{ getContentPreview(note.content) }}
            </div>
            <div class="note-tags">
              <el-tag
                v-for="tag in note.tags"
                :key="tag.id"
                size="small"
                type="info"
              >
                {{ tag.name }}
              </el-tag>
            </div>
            <div class="note-footer">
              <span class="note-author">
                <i class="el-icon-user"></i>
                {{ note.ownerUsername }}
              </span>
              <span class="note-time">
                <i class="el-icon-time"></i>
                {{ formatTime(note.modifiedAt) }}
              </span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div v-else class="empty-state">
        <i class="el-icon-document empty-icon"></i>
        <p class="empty-text">暂无公开笔记</p>
      </div>

      <!-- 分页 -->
      <el-pagination
        v-if="total > 0"
        :current-page="page + 1"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 30, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'PublicNotesPage',
  auth: false,
  async asyncData({ $axios }) {
    try {
      const { data } = await $axios.get('/v1/notes/public', {
        params: { page: 0, size: 10, sortBy: 'modifiedAt', direction: 'DESC' }
      })

      return {
        notes: data.content || [],
        total: data.totalElements || 0
      }
    } catch (error) {
      return {
        notes: [],
        total: 0
      }
    }
  },
  data() {
    return {
      loading: false,
      page: 0,
      pageSize: 10,
      notes: [],
      total: 0
    }
  },
  methods: {
    async loadNotes() {
      this.loading = true
      try {
        const { data } = await this.$axios.get('/v1/notes/public', {
          params: {
            page: this.page,
            size: this.pageSize,
            sortBy: 'modifiedAt',
            direction: 'DESC'
          }
        })
        this.notes = data.content || []
        this.total = data.totalElements || 0
      } catch (error) {
        this.$message.error('加载笔记失败')
      } finally {
        this.loading = false
      }
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.page = 0
      this.loadNotes()
    },
    handlePageChange(page) {
      this.page = page - 1
      this.loadNotes()
    },
    viewNote(id) {
      this.$router.push(`/public/${id}`)
    },
    getContentPreview(content) {
      if (!content) return ''
      const text = content.replace(/<[^>]+>/g, '')
      return text.length > 100 ? text.substring(0, 100) + '...' : text
    },
    formatTime(time) {
      if (!time) return ''
      const normalized = typeof time === 'string' && !/Z$|[+-]\d{2}:\d{2}$/.test(time) ? time + 'Z' : time
      const date = new Date(normalized)
      const now = new Date()
      const diff = now - date
      const days = Math.floor(diff / (1000 * 60 * 60 * 24))

      if (days === 0) {
        const hours = Math.floor(diff / (1000 * 60 * 60))
        if (hours === 0) {
          const minutes = Math.floor(diff / (1000 * 60))
          return minutes === 0 ? '刚刚' : `${minutes}分钟前`
        }
        return `${hours}小时前`
      } else if (days < 7) {
        return `${days}天前`
      } else {
        return date.toLocaleDateString()
      }
    }
  }
}
</script>

<style scoped lang="scss">
.public-notes-page {
  max-width: 1200px;
  margin: 0 auto;

  .page-header {
    margin-bottom: 20px;
  }

  .notes-list {
    min-height: 400px;

    .note-card {
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 4px;
      padding: 16px;
      margin-bottom: 20px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        transform: translateY(-4px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      }

      .note-header {
        margin-bottom: 12px;

        .note-title {
          font-size: 16px;
          font-weight: 500;
          color: #303133;
          margin: 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .note-content {
        color: #606266;
        font-size: 14px;
        line-height: 1.6;
        margin-bottom: 12px;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
      }

      .note-tags {
        margin-bottom: 12px;
        min-height: 24px;

        .el-tag {
          margin-right: 8px;
          margin-bottom: 4px;
        }
      }

      .note-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 12px;
        color: #909399;

        .note-author,
        .note-time {
          display: flex;
          align-items: center;
          gap: 4px;
        }
      }
    }

    .pagination {
      margin-top: 30px;
      text-align: center;
    }
  }
}
</style>
