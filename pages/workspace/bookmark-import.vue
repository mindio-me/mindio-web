<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="bookmark-import-page">
    <div class="bookmark-import-container">
      <div class="bookmark-import-header">
        <h1>{{ $t('workspace.bookmarkImport.title') }}</h1>
        <p class="tip">{{ $t('workspace.bookmarkImport.uploadTip') }}</p>
      </div>

      <el-card shadow="never" v-loading="parsing">
        <template v-if="!job">
          <el-upload
            drag
            action=""
            accept=".html,.htm"
            :auto-upload="false"
            :on-change="onFileChosen"
            :show-file-list="false"
          >
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">{{ $t('workspace.bookmarkImport.chooseFile') }}</div>
          </el-upload>
        </template>

        <template v-else-if="job.status === 'CHECKING'">
          <el-progress
            :percentage="checkProgress"
            :format="() => $t('workspace.bookmarkImport.checking', { checked: job.checkedCount, total: job.totalCount })"
          />
        </template>

        <template v-else-if="job.status === 'READY'">
          <el-tabs v-model="activeGroup">
            <el-tab-pane
              v-for="group in groupOrder"
              :key="group"
              :label="`${groupLabel(group)} (${(job.groups[group] || []).length})`"
              :name="group"
            >
              <div class="group-toolbar">
                <el-button
                  v-if="group !== 'DEAD_LINK'"
                  size="small"
                  type="primary"
                  @click="confirmGroup(group, 'CONFIRMED')"
                >{{ $t('workspace.bookmarkImport.confirmGroup') }}</el-button>
                <el-button v-if="group !== 'DEAD_LINK'" size="small" @click="confirmGroup(group, 'SKIPPED')">
                  {{ $t('workspace.bookmarkImport.skipGroup') }}
                </el-button>
              </div>
              <el-table
                :data="job.groups[group] || []"
                size="small"
                border
                height="360"
                @selection-change="handleDeadLinkSelectionChange"
              >
                <el-table-column v-if="group === 'DEAD_LINK'" type="selection" width="40" />
                <el-table-column prop="rawTitle" :label="$t('workspace.clips.content')" min-width="220" />
                <el-table-column prop="rawUrl" label="URL" min-width="260" show-overflow-tooltip />
                <el-table-column prop="folderPath" label="Folder" width="160" />
                <el-table-column :label="$t('workspace.bookmarkImport.statusColumn')" width="110">
                  <template slot-scope="scope">
                    <el-tag size="mini" :type="rowStatusType(group, scope.row)">
                      {{ rowStatusLabel(group, scope.row) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column v-if="group === 'DEAD_LINK'" width="90">
                  <template slot-scope="scope">
                    <el-button
                      size="mini"
                      type="text"
                      @click="visitLink(scope.row)"
                    >{{ $t('workspace.bookmarkImport.visitLink') }}</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>

          <div class="merge-toolbar">
            <el-button type="primary" :loading="merging" @click="doMerge">
              {{ $t('workspace.bookmarkImport.mergeButton') }}
            </el-button>
          </div>
        </template>

        <template v-else-if="job.status === 'DONE'">
          <el-result icon="success" :title="$t('workspace.bookmarkImport.mergeSuccess')">
            <template #extra>
              <el-button type="primary" @click="$router.push('/workspace/clips')">
                {{ $t('workspace.bookmarkImport.goToClips') }}
              </el-button>
            </template>
          </el-result>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BookmarkImportPage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    return {
      parsing: false,
      merging: false,
      job: null,
      activeGroup: 'IMPORTABLE',
      groupOrder: ['IMPORTABLE', 'DUPLICATE', 'NOISE', 'DEAD_LINK'],
      pollTimer: null,
      deadLinkSelection: [],
    }
  },
  computed: {
    checkProgress() {
      if (!this.job || !this.job.totalCount) return 0
      return Math.round((this.job.checkedCount / this.job.totalCount) * 100)
    },
  },
  beforeDestroy() {
    clearTimeout(this.pollTimer)
  },
  methods: {
    groupLabel(group) {
      const map = {
        IMPORTABLE: this.$t('workspace.bookmarkImport.groupImportable'),
        DUPLICATE: this.$t('workspace.bookmarkImport.groupDuplicate'),
        NOISE: this.$t('workspace.bookmarkImport.groupNoise'),
        DEAD_LINK: this.$t('workspace.bookmarkImport.groupDeadLink'),
      }
      return map[group] || group
    },
    async onFileChosen(file) {
      this.parsing = true
      try {
        this.job = await this.$bookmarkImportService.parse(file.raw)
        this.pollUntilReady()
      } catch (e) {
        this.$message.error(this.$t('workspace.bookmarkImport.parseFailed'))
      } finally {
        this.parsing = false
      }
    },
    pollUntilReady() {
      this.pollTimer = setTimeout(async () => {
        this.job = await this.$bookmarkImportService.getJob(this.job.id)
        if (this.job.status === 'CHECKING') {
          this.pollUntilReady()
        }
      }, 1500)
    },
    async confirmGroup(category, decision) {
      await this.$bookmarkImportService.confirmGroup(this.job.id, category, decision)
      this.job = await this.$bookmarkImportService.getJob(this.job.id)
    },
    handleDeadLinkSelectionChange(selection) {
      this.deadLinkSelection = selection
    },
    visitLink(item) {
      window.open(item.rawUrl, '_blank', 'noopener')
    },
    decisionTagType(decision) {
      return { PENDING: 'info', CONFIRMED: 'success', SKIPPED: 'warning' }[decision]
    },
    decisionLabel(decision) {
      const map = {
        PENDING: this.$t('workspace.bookmarkImport.statusPending'),
        CONFIRMED: this.$t('workspace.bookmarkImport.statusConfirmed'),
        SKIPPED: this.$t('workspace.bookmarkImport.statusSkipped'),
      }
      return map[decision] || decision
    },
    rowStatusType(group, row) {
      if (group === 'DEAD_LINK') {
        return this.deadLinkSelection.some(r => r.id === row.id) ? 'success' : 'warning'
      }
      return this.decisionTagType(row.userDecision)
    },
    rowStatusLabel(group, row) {
      if (group === 'DEAD_LINK') {
        const confirmed = this.deadLinkSelection.some(r => r.id === row.id)
        return confirmed
          ? this.$t('workspace.bookmarkImport.statusConfirmed')
          : this.$t('workspace.bookmarkImport.statusSkipped')
      }
      return this.decisionLabel(row.userDecision)
    },
    async doMerge() {
      this.merging = true
      try {
        if (this.deadLinkSelection.length > 0) {
          const itemIds = this.deadLinkSelection.map(row => row.id)
          await this.$bookmarkImportService.confirmItems(this.job.id, itemIds, 'CONFIRMED')
        }
        await this.$bookmarkImportService.merge(this.job.id)
        this.job.status = 'DONE'
        this.$message.success(this.$t('workspace.bookmarkImport.mergeSuccess'))
      } catch (e) {
        this.$message.error(this.$t('workspace.bookmarkImport.mergeFailed'))
      } finally {
        this.merging = false
      }
    },
  },
}
</script>

<style scoped>
.bookmark-import-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}
.bookmark-import-header h1 {
  font-size: 20px;
  margin-bottom: 4px;
}
.bookmark-import-header .tip {
  color: #909399;
  font-size: 13px;
}
.group-toolbar {
  margin-bottom: 12px;
}
.merge-toolbar {
  margin-top: 16px;
  text-align: right;
}
::v-deep .el-upload,
::v-deep .el-upload-dragger {
  width: 100%;
}
</style>
