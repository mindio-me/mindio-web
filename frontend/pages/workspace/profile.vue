<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="profile-page">
    <div class="profile-container">
      <div class="profile-header">
        <h1 class="profile-title">{{ $t('workspace.profile.title') }}</h1>
        <p class="profile-subtitle">{{ $t('workspace.profile.subtitle') }}</p>
        <p class="profile-subtitle secondary">
          {{ $t('workspace.profile.subtitleSecondary') }}
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
        <!-- 个人资料部分 -->
        <div class="form-section">
          <h2 class="section-title">{{ $t('workspace.profile.sectionProfile') }}</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldFullName')" prop="fullName">
                <el-input
                  v-model="profileForm.fullName"
                  :placeholder="$t('workspace.profile.fullNamePlaceholder')"
                  maxlength="100"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldTitleEn')" prop="title">
                <el-input
                  v-model="profileForm.title"
                  :placeholder="$t('workspace.profile.titlePlaceholder')"
                  maxlength="200"
                />
              </el-form-item>
              <el-form-item :label="$t('workspace.profile.fieldTitleZh')">
                <el-input
                  v-model="profileForm.titleZh"
                  :placeholder="$t('workspace.profile.titleZhPlaceholder')"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item :label="$t('workspace.profile.fieldAvailabilityStatusEn')">
            <el-input
              v-model="profileForm.availabilityStatus"
              :placeholder="$t('workspace.profile.availabilityStatusPlaceholder')"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldAvailabilityStatusZh')">
            <el-input
              v-model="profileForm.availabilityStatusZh"
              :placeholder="$t('workspace.profile.availabilityStatusZhPlaceholder')"
              maxlength="200"
            />
            <p class="hint-text">{{ $t('workspace.profile.availabilityStatusHint') }}</p>
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldBioEn')" prop="bio">
            <el-input
              v-model="profileForm.bio"
              type="textarea"
              :rows="4"
              :placeholder="$t('workspace.profile.bioPlaceholder')"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldBioZh')">
            <el-input
              v-model="profileForm.bioZh"
              type="textarea"
              :rows="4"
              :placeholder="$t('workspace.profile.bioZhPlaceholder')"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldPhilosophyEn')" prop="philosophy">
            <el-input
              v-model="profileForm.philosophy"
              type="textarea"
              :rows="6"
              :placeholder="$t('workspace.profile.philosophyPlaceholder')"
              maxlength="3000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldPhilosophyZh')">
            <el-input
              v-model="profileForm.philosophyZh"
              type="textarea"
              :rows="6"
              :placeholder="$t('workspace.profile.philosophyZhPlaceholder')"
              maxlength="3000"
              show-word-limit
            />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldAvatarUrl')">
                <div class="avatar-upload-area">
                  <div v-if="profileForm.avatarUrl" class="avatar-preview">
                    <img :src="resolveUrl(profileForm.avatarUrl)" :alt="$t('workspace.profile.fieldAvatarUrl')" class="avatar-preview-img" />
                    <el-button size="mini" type="text" class="avatar-remove-btn" @click="profileForm.avatarUrl = ''">
                      <i class="el-icon-delete"></i> {{ $t('workspace.profile.removeAvatar') }}
                    </el-button>
                  </div>
                  <div v-else class="avatar-upload-placeholder" @click="triggerAvatarUpload">
                    <i class="el-icon-plus qr-upload-icon"></i>
                    <span>{{ $t('workspace.profile.uploadHint') }}</span>
                  </div>
                  <input
                    ref="avatarFileInput"
                    type="file"
                    accept="image/*"
                    style="display:none"
                    @change="handleAvatarUpload"
                  />
                </div>
                <p class="hint-text">{{ $t('workspace.profile.avatarHint') }}</p>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldLocation')" prop="location">
                <el-input
                  v-model="profileForm.location"
                  :placeholder="$t('workspace.profile.locationPlaceholder')"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item :label="$t('workspace.profile.fieldSkillsEn')">
            <div class="dynamic-tags">
              <el-tag
                v-for="(skill, idx) in profileForm.skills"
                :key="idx"
                closable
                size="small"
                @close="profileForm.skills.splice(idx, 1)"
              >{{ skill }}</el-tag>
              <el-input
                v-if="skillTagInputVisible"
                ref="skillTagInput"
                v-model="skillTagInputValue"
                size="small"
                class="tag-input"
                @keyup.enter.native="addSkillTag"
                @blur="addSkillTag"
              />
              <el-button v-else size="small" class="tag-add-btn" @click="showSkillTagInput">{{ $t('workspace.profile.addSkillTag') }}</el-button>
            </div>
            <p class="hint-text">{{ $t('workspace.profile.skillsHint') }}</p>
          </el-form-item>
          <el-form-item :label="$t('workspace.profile.fieldSkillsZh')">
            <div class="dynamic-tags">
              <el-tag
                v-for="(skill, idx) in profileForm.skillsZh"
                :key="idx"
                closable
                size="small"
                @close="profileForm.skillsZh.splice(idx, 1)"
              >{{ skill }}</el-tag>
              <el-input
                v-if="skillZhTagInputVisible"
                ref="skillZhTagInput"
                v-model="skillZhTagInputValue"
                size="small"
                class="tag-input"
                @keyup.enter.native="addSkillZhTag"
                @blur="addSkillZhTag"
              />
              <el-button v-else size="small" class="tag-add-btn" @click="showSkillZhTagInput">{{ $t('workspace.profile.addSkillTag') }}</el-button>
            </div>
          </el-form-item>
        </div>

        <!-- 联系方式部分 -->
        <div class="form-section">
          <h2 class="section-title">{{ $t('workspace.profile.sectionContact') }}</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldEmail')" prop="email">
                <el-input
                  v-model="profileForm.email"
                  placeholder="you@example.com"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldTwitter')" prop="twitter">
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
              <el-form-item :label="$t('workspace.profile.fieldLinkedin')" prop="linkedin">
                <el-input
                  v-model="profileForm.linkedin"
                  placeholder="linkedin.com/in/yourprofile"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldWechat')" prop="wechat">
                <el-input
                  v-model="profileForm.wechat"
                  :placeholder="$t('workspace.profile.wechatPlaceholder')"
                  maxlength="200"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item :label="$t('workspace.profile.wechatQrLabel')">
                <div class="qr-upload-area">
                  <div v-if="profileForm.wechatQrUrl" class="qr-preview">
                    <img :src="resolveUrl(profileForm.wechatQrUrl)" :alt="$t('workspace.profile.wechatQrLabel')" class="qr-preview-img" />
                    <el-button size="mini" type="text" class="qr-remove-btn" @click="profileForm.wechatQrUrl = ''">
                      <i class="el-icon-delete"></i> {{ $t('workspace.profile.removeQr') }}
                    </el-button>
                  </div>
                  <div v-else class="qr-upload-placeholder" @click="triggerQrUpload">
                    <i class="el-icon-plus qr-upload-icon"></i>
                    <span>{{ $t('workspace.profile.uploadHint') }}</span>
                  </div>
                  <input
                    ref="qrFileInput"
                    type="file"
                    accept="image/*"
                    style="display:none"
                    @change="handleQrUpload"
                  />
                </div>
                <p class="hint-text">{{ $t('workspace.profile.qrHint') }}</p>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 网站设置部分 -->
        <div class="form-section" v-loading="siteLoading">
          <h2 class="section-title">{{ $t('workspace.settings.siteInfoTitle') }}</h2>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('workspace.settings.fieldSiteName')">
                <el-input
                  v-model="siteForm.siteName"
                  :placeholder="$t('workspace.settings.siteNamePlaceholder')"
                  maxlength="100"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item :label="$t('workspace.settings.fieldLogo')">
                <div class="avatar-upload-area">
                  <div v-if="siteForm.logoUrl" class="avatar-preview">
                    <img :src="resolveUrl(siteForm.logoUrl)" :alt="$t('workspace.settings.fieldLogo')" class="avatar-preview-img" />
                    <el-button size="mini" type="text" class="avatar-remove-btn" @click="siteForm.logoUrl = ''">
                      <i class="el-icon-delete"></i> {{ $t('workspace.profile.removeAvatar') }}
                    </el-button>
                  </div>
                  <div v-else class="avatar-upload-placeholder" @click="triggerLogoUpload">
                    <i class="el-icon-plus qr-upload-icon"></i>
                    <span>{{ $t('workspace.profile.uploadHint') }}</span>
                  </div>
                  <input
                    ref="logoFileInput"
                    type="file"
                    accept="image/*"
                    style="display:none"
                    @change="handleLogoUpload"
                  />
                </div>
                <p class="hint-text">{{ $t('workspace.settings.logoHint') }}</p>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 其他链接部分 -->
        <div class="form-section">
          <h2 class="section-title">{{ $t('workspace.profile.sectionLinks') }}</h2>
          <el-row :gutter="20">
            <!-- <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldWebsite')" prop="website">
                <el-input
                  v-model="profileForm.website"
                  placeholder="https://..."
                  maxlength="200"
                />
              </el-form-item>
            </el-col> -->
            <el-col :span="12">
              <el-form-item :label="$t('workspace.profile.fieldGithub')" prop="github">
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
            <i class="el-icon-check"></i> {{ $t('common.save') }}
          </el-button>
          <el-button @click="handleCancel">{{ $t('common.cancel') }}</el-button>
        </div>
      </el-form>

      <!-- 账户安全部分 -->
      <div class="form-section security-section">
        <h2 class="section-title">{{ $t('workspace.profile.sectionSecurity') }}</h2>
        <div class="security-item">
          <div class="security-info">
            <span class="security-label">{{ $t('workspace.profile.securityLoginPassword') }}</span>
            <span class="security-desc">{{ $t('workspace.profile.securityPasswordDesc') }}</span>
          </div>
          <el-button size="small" @click="showPasswordDialog = true">
            <i class="el-icon-lock"></i> {{ $t('workspace.profile.changePassword') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗；forcePasswordChange 时（首次登录用默认密码）不允许关闭，必须改完密码才能继续 -->
    <el-dialog
      :title="$t('workspace.profile.changePassword')"
      :visible.sync="showPasswordDialog"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="!forcePasswordChange"
      :show-close="!forcePasswordChange"
      @closed="resetPasswordForm"
    >
      <p v-if="forcePasswordChange" class="force-password-change-hint">
        {{ $t('workspace.profile.forcePasswordChangeHint') }}
      </p>
      <el-form
        ref="passwordForm"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
      >
        <el-form-item :label="$t('workspace.profile.fieldCurrentPassword')" prop="currentPassword">
          <el-input
            v-model="passwordForm.currentPassword"
            type="password"
            :placeholder="$t('workspace.profile.currentPasswordPlaceholder')"
            show-password
          />
        </el-form-item>
        <el-form-item :label="$t('workspace.profile.fieldNewPassword')" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            :placeholder="$t('workspace.profile.newPasswordPlaceholder')"
            show-password
          />
        </el-form-item>
        <el-form-item :label="$t('workspace.profile.fieldConfirmPassword')" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            :placeholder="$t('workspace.profile.confirmPasswordPlaceholder')"
            show-password
          />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button v-if="!forcePasswordChange" @click="showPasswordDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">
          {{ $t('workspace.profile.confirmChange') }}
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
    return {
      loading: false,
      saving: false,
      showPasswordDialog: false,
      forcePasswordChange: false,
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
        titleZh: '',
        bio: '',
        bioZh: '',
        philosophy: '',
        philosophyZh: '',
        availabilityStatus: '',
        availabilityStatusZh: '',
        skills: [],
        skillsZh: [],
        avatarUrl: '',
        location: '',
        // 其他链接
        website: '',
        github: ''
      },
      skillTagInputVisible: false,
      skillTagInputValue: '',
      skillZhTagInputVisible: false,
      skillZhTagInputValue: '',
      // 网站设置
      siteLoading: false,
      siteForm: {
        siteName: '',
        logoUrl: ''
      },
      passwordForm: {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
    }
  },
  computed: {
    rules() {
      return {
        email: [
          { type: 'email', message: this.$t('workspace.profile.emailFormat'), trigger: 'blur' }
        ]
      }
    },
    passwordRules() {
      return {
        currentPassword: [
          { required: true, message: this.$t('workspace.profile.currentPasswordRequired'), trigger: 'blur' }
        ],
        newPassword: [
          { required: true, message: this.$t('workspace.profile.newPasswordRequired'), trigger: 'blur' },
          { min: 6, message: this.$t('workspace.profile.passwordMinLength'), trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: this.$t('workspace.profile.confirmPasswordRequired'), trigger: 'blur' },
          { validator: this.validateConfirmPassword, trigger: 'blur' }
        ]
      }
    }
  },
  async mounted() {
    if (this.$route.query.forcePasswordChange) {
      this.forcePasswordChange = true
      this.showPasswordDialog = true
    }
    await Promise.all([this.loadProfile(), this.loadSiteConfig()])
  },
  methods: {
    async loadSiteConfig() {
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
      const payload = {
        siteName: this.siteForm.siteName || '',
        logoUrl: this.siteForm.logoUrl || ''
      }
      await this.$axios.$put('/v1/settings/site', payload)
    },
    triggerLogoUpload() {
      this.$refs.logoFileInput.click()
    },
    async handleLogoUpload(event) {
      const file = event.target.files[0]
      if (!file) return
      try {
        const result = await this.$uploadService.uploadLocal(file, 'profile', 2)
        // 只保存相对路径，避免写死环境域名
        const base = this.$axios.defaults.baseURL || ''
        const url = result.url
        this.siteForm.logoUrl = base && url.startsWith(base) ? url.slice(base.length) : url
        this.$message.success(this.$t('workspace.settings.logoUploadSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.profile.uploadFailed', { message: error.message || this.$t('workspace.profile.unknownError') }))
      }
      event.target.value = ''
    },
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
            titleZh: profile.titleZh || '',
            bio: profile.bio || '',
            bioZh: profile.bioZh || '',
            philosophy: profile.philosophy || '',
            philosophyZh: profile.philosophyZh || '',
            availabilityStatus: profile.availabilityStatus || '',
            availabilityStatusZh: profile.availabilityStatusZh || '',
            skills: profile.skills ? profile.skills.split(',').map(s => s.trim()).filter(Boolean) : [],
            skillsZh: profile.skillsZh ? profile.skillsZh.split(',').map(s => s.trim()).filter(Boolean) : [],
            avatarUrl: profile.avatarUrl || '',
            location: profile.location || '',
            website: profile.website || '',
            github: profile.github || ''
          }
        }
      } catch (error) {
        console.error('加载个人信息失败:', error)
        this.$message.error(this.$t('workspace.profile.loadFailed'))
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
          const submitData = {
            ...this.profileForm,
            skills: Array.isArray(this.profileForm.skills) ? this.profileForm.skills.join(',') : this.profileForm.skills,
            skillsZh: Array.isArray(this.profileForm.skillsZh) ? this.profileForm.skillsZh.join(',') : this.profileForm.skillsZh
          }
          await Promise.all([
            this.$profileService.updateProfile(submitData),
            this.saveSiteConfig()
          ])
          this.$message.success(this.$t('workspace.profile.saveSuccess'))
          // 可选：刷新页面数据
          await this.loadProfile()
        } catch (error) {
          console.error('保存个人信息失败:', error)
          this.$message.error(this.$t('workspace.profile.saveFailed', { message: error.response?.data?.message || this.$t('workspace.profile.unknownError') }))
        } finally {
          this.saving = false
        }
      })
    },
    showSkillTagInput() {
      this.skillTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.skillTagInput) {
          this.$refs.skillTagInput.focus()
        }
      })
    },
    addSkillTag() {
      const val = this.skillTagInputValue.trim()
      if (val && !this.profileForm.skills.includes(val)) {
        this.profileForm.skills.push(val)
      }
      this.skillTagInputVisible = false
      this.skillTagInputValue = ''
    },
    showSkillZhTagInput() {
      this.skillZhTagInputVisible = true
      this.$nextTick(() => {
        if (this.$refs.skillZhTagInput) {
          this.$refs.skillZhTagInput.focus()
        }
      })
    },
    addSkillZhTag() {
      const val = this.skillZhTagInputValue.trim()
      if (val && !this.profileForm.skillsZh.includes(val)) {
        this.profileForm.skillsZh.push(val)
      }
      this.skillZhTagInputVisible = false
      this.skillZhTagInputValue = ''
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
        this.$message.success(this.$t('workspace.profile.qrUploadSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.profile.uploadFailed', { message: error.message || this.$t('workspace.profile.unknownError') }))
      }
      event.target.value = ''
    },
    triggerAvatarUpload() {
      this.$refs.avatarFileInput.click()
    },
    async handleAvatarUpload(event) {
      const file = event.target.files[0]
      if (!file) return
      try {
        const result = await this.$uploadService.uploadLocal(file, 'profile', 2)
        // 只保存相对路径，避免写死环境域名
        const base = this.$axios.defaults.baseURL || ''
        const url = result.url
        this.profileForm.avatarUrl = base && url.startsWith(base) ? url.slice(base.length) : url
        this.$message.success(this.$t('workspace.profile.avatarUploadSuccess'))
      } catch (error) {
        this.$message.error(this.$t('workspace.profile.uploadFailed', { message: error.message || this.$t('workspace.profile.unknownError') }))
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
          this.$message.success(this.$t('workspace.profile.passwordChangeSuccess'))
          this.showPasswordDialog = false
          if (this.forcePasswordChange) {
            this.forcePasswordChange = false
            this.$router.replace('/workspace/profile')
          }
        } catch (error) {
          console.error('修改密码失败:', error)
          const errorMsg = error.response?.data?.message || error.response?.data || this.$t('workspace.profile.passwordChangeFailed')
          this.$message.error(typeof errorMsg === 'string' ? errorMsg : this.$t('workspace.profile.passwordChangeFailed'))
        } finally {
          this.changingPassword = false
        }
      })
    },
    validateConfirmPassword(_rule, value, callback) {
      if (value !== this.passwordForm.newPassword) {
        callback(new Error(this.$t('workspace.profile.passwordMismatch')))
      } else {
        callback()
      }
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
  // border: 1px solid var(--border-color);
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

.avatar-upload-area {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.avatar-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.avatar-preview-img {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border: 1px solid var(--border-color);
  border-radius: 50%;
  background: var(--bg-secondary);
}

.avatar-remove-btn {
  color: #f56c6c;
  font-size: 12px;
}

.avatar-upload-placeholder {
  width: 96px;
  height: 96px;
  border: 2px dashed var(--border-color);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  color: var(--text-muted);
  font-size: 11px;
  text-align: center;
  padding: 0 8px;
  transition: border-color 0.2s, color 0.2s;

  &:hover {
    border-color: #667eea;
    color: #667eea;
  }
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
  // border: 1px solid var(--border-color);
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

.force-password-change-hint {
  margin: 0 0 16px;
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--el-color-warning, #e6a23c);
  font-size: 13px;
}
</style>

