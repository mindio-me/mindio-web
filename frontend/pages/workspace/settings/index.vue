<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="settings-page">
    <div class="settings-container">
      <div class="settings-header">
        <h1 class="settings-title">{{ $t('workspace.settings.title') }}</h1>
        <p class="settings-subtitle">{{ $t('workspace.settings.subtitle') }}</p>
      </div>

      <el-row :gutter="24">
        <el-col :xs="24" :sm="24" :md="12">
          <!-- 网站信息 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.siteInfoTitle') }}</span>
            </div>
            <el-form
              ref="siteFormRef"
              :model="siteForm"
              label-position="top"
              v-loading="siteLoading"
            >
              <el-form-item :label="$t('workspace.settings.fieldSiteName')">
                <el-input
                  v-model="siteForm.siteName"
                  :placeholder="$t('workspace.settings.siteNamePlaceholder')"
                  maxlength="100"
                />
              </el-form-item>
              <el-form-item label="Logo URL">
                <el-input
                  v-model="siteForm.logoUrl"
                  placeholder="https://..."
                  maxlength="500"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" :loading="siteSaving" @click="saveSiteConfig">
                  <i class="el-icon-check"></i> {{ $t('workspace.settings.saveSiteInfo') }}
                </el-button>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 集成配置：飞书 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.feishuTitle') }}</span>
              <el-tag
                v-if="feishuStatus.bound"
                size="mini"
                type="success"
                effect="plain"
              >
                {{ $t('workspace.settings.feishuBound') }}
              </el-tag>
              <el-tag
                v-else-if="feishuStatus.configured"
                size="mini"
                type="warning"
                effect="plain"
              >
                {{ $t('workspace.settings.feishuConfiguredNotBound') }}
              </el-tag>
            </div>
            <p class="section-tip">
              {{ $t('workspace.settings.feishuIntro') }}
            </p>
            <el-form :model="feishuForm" label-position="top" v-loading="feishuLoading">
              <el-form-item label="App ID">
                <el-input v-model="feishuForm.appId" :placeholder="$t('workspace.settings.feishuAppIdPlaceholder')" maxlength="200" />
                <div
                  v-if="feishuStatus.configured && feishuStatus.appIdMasked"
                  class="hint-text"
                >
                  {{ $t('workspace.settings.savedPrefix') }}{{ feishuStatus.appIdMasked }}
                </div>
              </el-form-item>
              <el-form-item label="App Secret">
                <el-input
                  v-model="feishuForm.appSecret"
                  :placeholder="$t('workspace.settings.feishuAppSecretPlaceholder')"
                  show-password
                />
              </el-form-item>
              <div class="inline-actions">
                <el-button
                  type="primary"
                  size="small"
                  :loading="feishuSaving"
                  @click="saveFeishuConfig"
                >
                  <i class="el-icon-check"></i> {{ $t('workspace.settings.saveFeishuConfigBtn') }}
                </el-button>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 集成配置：微信聊天导入 -->
          <el-card v-if="wechatBindingStatus.configured" class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.wechatImportTitle') }}</span>
            </div>
            <p class="section-tip">
              {{ $t('workspace.settings.wechatImportIntro') }}
            </p>
            <div v-loading="wechatBindingLoading">
              <div v-if="wechatBindCode" class="wechat-bind-code-box">
                <div class="wechat-bind-code">{{ wechatBindCode }}</div>
                <p class="section-tip">
                  {{ $t('workspace.settings.wechatBindCodeHint', { n: wechatBindCodeCountdown }) }}
                </p>
              </div>
              <div v-else class="form-actions">
                <el-button size="small" type="primary" @click="generateWechatBindCode">
                  <i class="el-icon-connection"></i> {{ $t('workspace.settings.generateBindCode') }}
                </el-button>
              </div>

              <div v-if="wechatBindings.length" class="wechat-binding-list">
                <div v-for="item in wechatBindings" :key="item.id" class="wechat-binding-item">
                  <span>{{ $t('workspace.settings.boundAtPrefix') }}{{ formatDateTime(item.boundAt) }}</span>
                  <el-button size="mini" type="text" @click="unbindWechat(item.id)">{{ $t('workspace.settings.unbindButton') }}</el-button>
                </div>
              </div>
            </div>
          </el-card>
          <el-card v-else class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.wechatImportTitle') }}</span>
            </div>
            <el-alert
              :title="$t('workspace.settings.wechatNotConfigured')"
              type="info"
              :closable="false"
              show-icon
            />
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 集成配置：阿里云 OSS -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.ossTitle') }}</span>
            </div>
            <p class="section-tip">
              {{ $t('workspace.settings.ossIntro') }}
            </p>
            <el-form :model="ossForm" label-position="top">
              <el-form-item label="AccessKey ID">
                <el-input
                  v-model="ossForm.accessKeyId"
                  :placeholder="$t('workspace.settings.ossAccessKeyIdPlaceholder')"
                />
              </el-form-item>
              <el-form-item label="AccessKey Secret">
                <el-input
                  v-model="ossForm.accessKeySecret"
                  type="password"
                  :placeholder="$t('workspace.settings.ossAccessKeySecretPlaceholder')"
                  show-password
                />
              </el-form-item>
              <el-form-item :label="$t('workspace.settings.fieldBucketName')">
                <el-input
                  v-model="ossForm.bucket"
                  :placeholder="$t('workspace.settings.bucketPlaceholder')"
                />
              </el-form-item>
              <el-form-item label="Endpoint">
                <el-input
                  v-model="ossForm.endpoint"
                  :placeholder="$t('workspace.settings.endpointPlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="$t('workspace.settings.fieldBaseUrl')">
                <el-input
                  v-model="ossForm.baseUrl"
                  :placeholder="$t('workspace.settings.baseUrlPlaceholder')"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="saveOssConfig">
                  <i class="el-icon-check"></i> {{ $t('workspace.settings.saveOssConfigBtn') }}
                </el-button>
                <span class="form-actions-tip">{{ $t('workspace.settings.ossSaveNote') }}</span>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 预留：大模型 / Notion 等集成 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>{{ $t('workspace.settings.otherIntegrationsTitle') }}</span>
            </div>
            <p class="section-tip">
              {{ $t('workspace.settings.otherIntegrationsIntro') }}
            </p>
            <el-alert
              :title="$t('workspace.settings.otherIntegrationsNotice')"
              type="info"
              :closable="false"
              show-icon
            />
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SettingsPage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    return {
      // 网站信息
      siteLoading: false,
      siteSaving: false,
      siteForm: {
        siteName: '',
        logoUrl: ''
      },
      // 飞书配置
      feishuLoading: false,
      feishuSaving: false,
      feishuStatus: { configured: false, appIdMasked: null, bound: false },
      feishuForm: {
        appId: '',
        appSecret: ''
      },
      // OSS 配置（前端占位）
      ossForm: {
        accessKeyId: '',
        accessKeySecret: '',
        bucket: '',
        endpoint: '',
        baseUrl: ''
      },
      // 微信聊天导入
      wechatBindingLoading: false,
      wechatBindingStatus: { configured: false },
      wechatBindCode: '',
      wechatBindCodeCountdown: 0,
      wechatBindCodeTimer: null,
      wechatBindings: []
    }
  },
  async mounted() {
    await Promise.all([
      this.loadProfileForSite(),
      this.loadFeishuStatus(),
      this.loadWechatBindingStatus()
    ])
  },
  methods: {
    formatDateTime(value) {
      if (!value) return this.$t('workspace.settings.noneYet')
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return value
      return date.toLocaleString()
    },
    async loadProfileForSite() {
      this.siteLoading = true
      try {
        const resp = await this.$axios.$get('/v1/settings/site')
        if (resp) {
          this.siteForm.siteName = resp.siteName || ''
          this.siteForm.logoUrl = resp.logoUrl || ''
        }
      } catch (error) {
        // 网站信息失败也不阻塞页面
        this.$message.error(this.$t('workspace.settings.loadSiteInfoFailed'))
      } finally {
        this.siteLoading = false
      }
    },
    async saveSiteConfig() {
      this.siteSaving = true
      try {
        const payload = {
          siteName: this.siteForm.siteName || '',
          logoUrl: this.siteForm.logoUrl || ''
        }
        await this.$axios.$put('/v1/settings/site', payload)
        this.$message.success(this.$t('workspace.settings.siteInfoSaved'))
      } catch (error) {
        this.$message.error(this.$t('workspace.settings.saveSiteInfoFailed', { message: error.response?.data?.message || error.message }))
      } finally {
        this.siteSaving = false
      }
    },
    async loadFeishuStatus() {
      this.feishuLoading = true
      try {
        const status = await this.$feishuService.getStatus()
        this.feishuStatus = status || { configured: false }
      } catch (e) {
        // ignore
      } finally {
        this.feishuLoading = false
      }
    },
    async saveFeishuConfig() {
      if (!this.feishuForm.appId || !this.feishuForm.appSecret) {
        this.$message.warning(this.$t('workspace.settings.feishuFieldsRequired'))
        return
      }
      this.feishuSaving = true
      try {
        await this.$axios.$post('/v1/settings/integrations/feishu/credentials', { ...this.feishuForm })
        this.$message.success(this.$t('workspace.settings.feishuConfigSaved'))
        this.feishuForm.appSecret = ''
        await this.loadFeishuStatus()
      } catch (error) {
        this.$message.error(this.$t('workspace.settings.saveFeishuConfigFailed', { message: error.response?.data?.message || error.message }))
      } finally {
        this.feishuSaving = false
      }
    },
    saveOssConfig() {
      // 目前仅作占位，后续接入后端接口再实现实际保存逻辑
      this.$message.info(this.$t('workspace.settings.ossConfigPlaceholderNotice'))
    },
    async loadWechatBindingStatus() {
      try {
        this.wechatBindingStatus = await this.$wechatBindingService.getStatus()
        if (this.wechatBindingStatus.configured) {
          await this.loadWechatBindings()
        }
      } catch (e) {
        this.wechatBindingStatus = { configured: false }
      }
    },
    async loadWechatBindings() {
      try {
        this.wechatBindings = await this.$wechatBindingService.list()
      } catch (e) {
        // ignore
      }
    },
    async generateWechatBindCode() {
      this.wechatBindingLoading = true
      try {
        const result = await this.$wechatBindingService.generateCode()
        this.wechatBindCode = result.code
        this.wechatBindCodeCountdown = result.expiresInSeconds
        this.startWechatBindCodeCountdown()
      } catch (error) {
        this.$message.error(this.$t('workspace.settings.generateBindCodeFailed', { message: error.response?.data?.message || error.message }))
      } finally {
        this.wechatBindingLoading = false
      }
    },
    startWechatBindCodeCountdown() {
      if (this.wechatBindCodeTimer) clearInterval(this.wechatBindCodeTimer)
      this.wechatBindCodeTimer = setInterval(() => {
        this.wechatBindCodeCountdown -= 1
        if (this.wechatBindCodeCountdown <= 0) {
          clearInterval(this.wechatBindCodeTimer)
          this.wechatBindCode = ''
          this.loadWechatBindings()
        }
      }, 1000)
    },
    async unbindWechat(id) {
      try {
        await this.$wechatBindingService.unbind(id)
        this.$message.success(this.$t('workspace.settings.unbindSuccess'))
        await this.loadWechatBindings()
      } catch (error) {
        this.$message.error(this.$t('workspace.settings.unbindFailed', { message: error.response?.data?.message || error.message }))
      }
    }
  }
}
</script>

<style scoped lang="scss">
.settings-page {
  height: 100%;
  overflow-y: auto;
}

.settings-container {
  max-width: 1100px;
  margin: 0 auto;
  background: var(--card-bg-color);
  border-radius: 12px;
  // border: 1px solid var(--border-color);
  padding: 32px;
}

.settings-header {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.settings-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 8px 0;
}

.settings-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.settings-card {
  margin-bottom: 20px;
  background: var(--bg-secondary);
  border-radius: 8px;
  // border: 1px solid var(--border-color);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: bold;
}

.section-tip {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 14px 0;
}

.hint-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

.inline-actions {
  display: flex;
  justify-content: flex-end;
}

.form-actions {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-actions-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.wechat-bind-code-box {
  text-align: center;
  padding: 16px 0;
}

.wechat-bind-code {
  font-size: 32px;
  font-weight: 600;
  letter-spacing: 6px;
  color: var(--text-color);
  margin-bottom: 8px;
}

.wechat-binding-list {
  margin-top: 16px;
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
}

.wechat-binding-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: var(--text-muted);
}

::v-deep .el-form-item__label {
  font-weight: bold;
}

@media screen and (max-width: 768px) {
  .settings-header {
    margin-bottom: 16px;
  }

  .settings-title {
    font-size: 24px;
  }
}
</style>
