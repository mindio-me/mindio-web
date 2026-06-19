<template>
  <div class="settings-page">
    <div class="settings-container">
      <div class="settings-header">
        <h1 class="settings-title">设置</h1>
        <p class="settings-subtitle">站点与集成配置</p>
      </div>

      <el-row :gutter="24">
        <el-col :xs="24" :sm="24" :md="12">
          <!-- 网站信息 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>网站信息</span>
            </div>
            <el-form
              ref="siteFormRef"
              :model="siteForm"
              label-position="top"
              v-loading="siteLoading"
            >
              <el-form-item label="网站名称">
                <el-input
                  v-model="siteForm.siteName"
                  placeholder="例如：MindIO"
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
                  <i class="el-icon-check"></i> 保存网站信息
                </el-button>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 集成配置：飞书 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>飞书集成</span>
              <el-tag
                v-if="feishuStatus.bound"
                size="mini"
                type="success"
                effect="plain"
              >
                已绑定
              </el-tag>
              <el-tag
                v-else-if="feishuStatus.configured"
                size="mini"
                type="warning"
                effect="plain"
              >
                已配置，未绑定账号
              </el-tag>
            </div>
            <p class="section-tip">
              配置后可在工作台中导入飞书 Wiki 文档。App Secret 将加密存储在服务端数据库中。
            </p>
            <el-form :model="feishuForm" label-position="top" v-loading="feishuLoading">
              <el-form-item label="App ID">
                <el-input v-model="feishuForm.appId" placeholder="请输入 App ID" maxlength="200" />
                <div
                  v-if="feishuStatus.configured && feishuStatus.appIdMasked"
                  class="hint-text"
                >
                  已保存：{{ feishuStatus.appIdMasked }}
                </div>
              </el-form-item>
              <el-form-item label="App Secret">
                <el-input
                  v-model="feishuForm.appSecret"
                  placeholder="请输入 App Secret（更新时需要填写）"
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
                  <i class="el-icon-check"></i> 保存飞书配置
                </el-button>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 集成配置：阿里云 OSS -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>阿里云 OSS</span>
            </div>
            <p class="section-tip">
              配置 OSS 后，可将附件或图片存储到阿里云对象存储（目前仅提供前端表单，占位待后端接口接入）。
            </p>
            <el-form :model="ossForm" label-position="top">
              <el-form-item label="AccessKey ID">
                <el-input
                  v-model="ossForm.accessKeyId"
                  placeholder="阿里云账号的 AccessKey ID"
                />
              </el-form-item>
              <el-form-item label="AccessKey Secret">
                <el-input
                  v-model="ossForm.accessKeySecret"
                  type="password"
                  placeholder="阿里云账号的 AccessKey Secret"
                  show-password
                />
              </el-form-item>
              <el-form-item label="Bucket 名称">
                <el-input
                  v-model="ossForm.bucket"
                  placeholder="例如：worknotes-bucket"
                />
              </el-form-item>
              <el-form-item label="Endpoint">
                <el-input
                  v-model="ossForm.endpoint"
                  placeholder="例如：oss-cn-beijing.aliyuncs.com"
                />
              </el-form-item>
              <el-form-item label="公共访问前缀（可选）">
                <el-input
                  v-model="ossForm.baseUrl"
                  placeholder="例如：https://worknotes-bucket.oss-cn-beijing.aliyuncs.com"
                />
              </el-form-item>
              <div class="form-actions">
                <el-button type="primary" @click="saveOssConfig">
                  <i class="el-icon-check"></i> 保存 OSS 配置
                </el-button>
                <span class="form-actions-tip">当前仅保存到前端状态，待后端接口接入后再联动保存。</span>
              </div>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12">
          <!-- 预留：大模型 / Notion 等集成 -->
          <el-card class="settings-card" shadow="never">
            <div slot="header" class="card-header">
              <span>其他集成（预留）</span>
            </div>
            <p class="section-tip">
              将来可在此配置大模型 API Key、Notion 集成等高级功能。
            </p>
            <el-alert
              title="目前尚未接入这些集成，后续版本会在此处开放配置入口。"
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
      }
    }
  },
  async mounted() {
    await Promise.all([
      this.loadProfileForSite(),
      this.loadFeishuStatus()
    ])
  },
  methods: {
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
        this.$message.error('加载网站信息失败')
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
        this.$message.success('网站信息已保存')
      } catch (error) {
        this.$message.error('保存网站信息失败：' + (error.response?.data?.message || error.message))
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
        this.$message.warning('请填写 App ID 和 App Secret')
        return
      }
      this.feishuSaving = true
      try {
        await this.$axios.$post('/v1/settings/integrations/feishu/credentials', { ...this.feishuForm })
        this.$message.success('飞书配置已保存')
        this.feishuForm.appSecret = ''
        await this.loadFeishuStatus()
      } catch (error) {
        this.$message.error('保存飞书配置失败：' + (error.response?.data?.message || error.message))
      } finally {
        this.feishuSaving = false
      }
    },
    saveOssConfig() {
      // 目前仅作占位，后续接入后端接口再实现实际保存逻辑
      this.$message.info('OSS 配置目前仅作占位，后端接口接入后将支持实际保存。')
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
  border: 1px solid var(--border-color);
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
  border: 1px solid var(--border-color);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
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

@media screen and (max-width: 768px) {
  .settings-header {
    margin-bottom: 16px;
  }

  .settings-title {
    font-size: 24px;
  }
}
</style>


