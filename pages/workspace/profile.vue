<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="profile-page">
    <div class="profile-container">
      <div class="profile-header">
        <h1 class="profile-title">个人信息设置</h1>
        <p class="profile-subtitle">管理您的个人资料和联系方式等个人信息。</p>
        <p class="profile-subtitle secondary">
          站点信息与集成配置已迁移到「设置」页面，请通过顶部菜单进入。
        </p>
      </div>

      <el-form
        ref="profileForm"
        :model="profileForm"
        :rules="rules"
        label-position="top"
        class="profile-form"
        v-loading="loading"
      >
        <!-- 联系方式部分 -->
        <div class="form-section">
          <h2 class="section-title">联系方式</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="profileForm.email"
                  placeholder="you@example.com"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Twitter / X" prop="twitter">
                <el-input
                  v-model="profileForm.twitter"
                  placeholder="x.com/yourprofile"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="LinkedIn" prop="linkedin">
                <el-input
                  v-model="profileForm.linkedin"
                  placeholder="linkedin.com/in/yourprofile"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="WeChat" prop="wechat">
                <el-input
                  v-model="profileForm.wechat"
                  placeholder="微信号或二维码描述"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="微信二维码">
                <div class="qr-upload-area">
                  <div v-if="profileForm.wechatQrUrl" class="qr-preview">
                    <img :src="resolveUrl(profileForm.wechatQrUrl)" alt="微信二维码" class="qr-preview-img" />
                    <el-button size="mini" type="text" class="qr-remove-btn" @click="profileForm.wechatQrUrl = ''">
                      <i class="el-icon-delete"></i> 移除
                    </el-button>
                  </div>
                  <div v-else class="qr-upload-placeholder" @click="triggerQrUpload">
                    <i class="el-icon-plus qr-upload-icon"></i>
                    <span>点击上传二维码图片</span>
                  </div>
                  <input
                    ref="qrFileInput"
                    type="file"
                    accept="image/*"
                    style="display:none"
                    @change="handleQrUpload"
                  />
                </div>
                <p class="hint-text">建议尺寸 300×300px，支持 JPG / PNG，上传后自动保存到服务器</p>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 个人资料部分 -->
        <div class="form-section">
          <h2 class="section-title">个人资料</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="全名" prop="fullName">
                <el-input
                  v-model="profileForm.fullName"
                  placeholder="您的全名"
                  maxlength="100"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="职位/头衔" prop="title">
                <el-input
                  v-model="profileForm.title"
                  placeholder="例如：Full-Stack Developer"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="个人简介" prop="bio">
            <el-input
              v-model="profileForm.bio"
              type="textarea"
              :rows="4"
              placeholder="介绍一下您自己..."
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="头像 URL" prop="avatarUrl">
                <el-input
                  v-model="profileForm.avatarUrl"
                  placeholder="https://..."
                  maxlength="500"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="地理位置" prop="location">
                <el-input
                  v-model="profileForm.location"
                  placeholder="例如：Beijing, China"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 其他链接部分 -->
        <div class="form-section">
          <h2 class="section-title">其他链接</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="个人网站" prop="website">
                <el-input
                  v-model="profileForm.website"
                  placeholder="https://..."
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="GitHub" prop="github">
                <el-input
                  v-model="profileForm.github"
                  placeholder="github.com/yourusername"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 提交按钮 -->
        <div class="form-actions">
          <el-button type="primary" :loading="saving" @click="handleSave">
            <i class="el-icon-check"></i> 保存
          </el-button>
          <el-button @click="handleCancel">取消</el-button>
        </div>
      </el-form>

      <!-- 账户安全部分 -->
      <div class="form-section security-section">
        <h2 class="section-title">账户安全</h2>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">登录密码</span>
            <span class="security-desc">定期更换密码可以提高账户安全性</span>
          </div>
          <el-button size="small" @click="showPasswordDialog = true">
            <i class="el-icon-lock"></i> 修改密码
          </el-button>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog
      title="修改密码"
      :visible.sync="showPasswordDialog"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordForm"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
      >
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input
            v-model="passwordForm.currentPassword"
            type="password"
            placeholder="请输入当前密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="showPasswordDialog = false">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">
          确认修改
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ProfilePage',
  layout: 'workspace',
  middleware: 'auth',
  data() {
    const validateConfirmPassword = (rule, value, callback) => {
      if (value !== this.passwordForm.newPassword) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }
    return {
      loading: false,
      saving: false,
      showPasswordDialog: false,
      changingPassword: false,
      profileForm: {
        email: '',
        twitter: '',
        linkedin: '',
        wechat: '',
        wechatQrUrl: '',
        // 个人资料
        fullName: '',
        title: '',
        bio: '',
        avatarUrl: '',
        location: '',
        // 其他链接
        website: '',
        github: ''
      },
      passwordForm: {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      },
      rules: {
        email: [
          { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
        ]
      },
      passwordRules: {
        currentPassword: [
          { required: true, message: '请输入当前密码', trigger: 'blur' }
        ],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请确认新密码', trigger: 'blur' },
          { validator: validateConfirmPassword, trigger: 'blur' }
        ]
      }
    }
  },
  async mounted() {
    await this.loadProfile()
  },
  methods: {
    async loadProfile() {
      this.loading = true
      try {
        const profile = await this.$profileService.getCurrentUserProfile()
        if (profile) {
          this.profileForm = {
            email: profile.email || '',
            twitter: profile.twitter || '',
            linkedin: profile.linkedin || '',
            wechat: profile.wechat || '',
            wechatQrUrl: profile.wechatQrUrl || '',
            fullName: profile.fullName || '',
            title: profile.title || '',
            bio: profile.bio || '',
            avatarUrl: profile.avatarUrl || '',
            location: profile.location || '',
            website: profile.website || '',
            github: profile.github || ''
          }
        }
      } catch (error) {
        console.error('加载个人信息失败:', error)
        this.$message.error('加载个人信息失败')
      } finally {
        this.loading = false
      }
    },
    async handleSave() {
      this.$refs.profileForm.validate(async (valid) => {
        if (!valid) {
          return false
        }
        this.saving = true
        try {
          await this.$profileService.updateProfile(this.profileForm)
          this.$message.success('保存成功')
          // 可选：刷新页面数据
          await this.loadProfile()
        } catch (error) {
          console.error('保存个人信息失败:', error)
          this.$message.error('保存失败：' + (error.response?.data?.message || '未知错误'))
        } finally {
          this.saving = false
        }
      })
    },
    triggerQrUpload() {
      this.$refs.qrFileInput.click()
    },
    async handleQrUpload(event) {
      const file = event.target.files[0]
      if (!file) return
      try {
        const result = await this.$uploadService.uploadLocal(file, 'profile', 2)
        // 只保存相对路径，避免写死环境域名
        const base = this.$axios.defaults.baseURL || ''
        const url = result.url
        this.profileForm.wechatQrUrl = base && url.startsWith(base) ? url.slice(base.length) : url
        this.$message.success('二维码上传成功')
      } catch (error) {
        this.$message.error('上传失败：' + (error.message || '未知错误'))
      }
      event.target.value = ''
    },
    resolveUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      return (this.$axios.defaults.baseURL || '') + path
    },
    handleCancel() {
      this.$router.back()
    },
    resetPasswordForm() {
      this.passwordForm = {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      this.$refs.passwordForm?.clearValidate()
    },
    async handleChangePassword() {
      this.$refs.passwordForm.validate(async (valid) => {
        if (!valid) {
          return false
        }
        this.changingPassword = true
        try {
          await this.$profileService.changePassword(this.passwordForm)
          this.$message.success('密码修改成功')
          this.showPasswordDialog = false
        } catch (error) {
          console.error('修改密码失败:', error)
          const errorMsg = error.response?.data?.message || error.response?.data || '修改失败'
          this.$message.error(typeof errorMsg === 'string' ? errorMsg : '修改失败')
        } finally {
          this.changingPassword = false
        }
      })
    }
  }
}
</script>

<style scoped lang="scss">
.profile-page {
  height: 100%;
  overflow-y: auto;
}

.profile-container {
  max-width: 1000px;
  margin: 0 auto;
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 32px;
}

.profile-header {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border-color);
}

.profile-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 8px 0;
}

.profile-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.profile-subtitle.secondary {
  margin-top: 4px;
  font-size: 13px;
}

.profile-form {
  .form-section {
    margin-bottom: 32px;
    padding-bottom: 24px;
    border-bottom: 1px solid var(--border-color);

    &:last-of-type {
      border-bottom: none;
      margin-bottom: 0;
      padding-bottom: 0;
    }
  }

  .section-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--text-color);
    margin: 0 0 20px 0;
  }

  ::v-deep .el-form-item__label {
    font-size: 14px;
    font-weight: bold;
    color: var(--text-secondary);
    padding-bottom: 8px;
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
  ::v-deep .el-textarea .el-input__count {
    background: var(--input-bg);
    color: var(--text-muted);
  }
}

.form-actions {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

@media screen and (max-width: 768px) {
  .profile-container {
    padding: 20px;
  }

  .profile-title {
    font-size: 24px;
  }
}

.section-tip {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: -10px;
  margin-bottom: 14px;
}

.hint-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

.qr-upload-area {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.qr-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.qr-preview-img {
  width: 120px;
  height: 120px;
  object-fit: contain;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.qr-remove-btn {
  color: #f56c6c;
  font-size: 12px;
}

.qr-upload-placeholder {
  width: 120px;
  height: 120px;
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 12px;
  transition: border-color 0.2s, color 0.2s;

  &:hover {
    border-color: #667eea;
    color: #667eea;
  }
}

.qr-upload-icon {
  font-size: 24px;
}

.inline-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: -6px;
}

// 账户安全部分
.security-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
}

.security-item {
  margin: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--bg-secondary);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.security-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
}

.security-desc {
  font-size: 12px;
  color: var(--text-muted);
}
</style>

