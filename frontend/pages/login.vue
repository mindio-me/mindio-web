<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div class="login-container">
    <button class="login-lang-toggle" @click="toggleLang">{{ $t('lang.toggle') }}</button>
    <el-card class="login-card">
      <div class="login-header">
        <MindioLogo class="login-logo" />
        <h2>{{ isLogin ? $t('login.titleLogin') : $t('login.titleRegister') }}</h2>
        <p>{{ isLogin ? $t('login.welcome') : $t('login.createAccount') }}</p>
      </div>

      <el-form
        ref="loginForm"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="$t('login.usernamePlaceholder')"
            prefix-icon="el-icon-user"
            clearable
          />
        </el-form-item>

        <el-form-item v-if="!isLogin" prop="email">
          <el-input
            v-model="loginForm.email"
            :placeholder="$t('login.emailPlaceholder')"
            prefix-icon="el-icon-message"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="$t('login.passwordPlaceholder')"
            prefix-icon="el-icon-lock"
            show-password
            @keyup.enter.native="handleSubmit"
          />
        </el-form-item>

        <el-form-item v-if="!isLogin" prop="confirmPassword">
          <el-input
            v-model="loginForm.confirmPassword"
            type="password"
            :placeholder="$t('login.confirmPasswordPlaceholder')"
            prefix-icon="el-icon-lock"
            show-password
            @keyup.enter.native="handleSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            {{ isLogin ? $t('login.submitLogin') : $t('login.submitRegister') }}
          </el-button>
        </el-form-item>

        <div class="login-footer">
          <el-button type="text" @click="toggleMode">
            {{ isLogin ? $t('login.noAccount') : $t('login.hasAccount') }}
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'LoginPage',
  layout: 'blank',
  auth: false,
  middleware({ app, redirect }) {
    if (app.$auth && app.$auth.loggedIn) {
      return redirect('/notes')
    }
  },
  data() {
    return {
      isLogin: true,
      loading: false,
      loginForm: {
        username: '',
        email: '',
        password: '',
        confirmPassword: ''
      }
    }
  },
  computed: {
    loginRules() {
      return {
        username: [
          { required: true, message: this.$t('login.usernameRequired'), trigger: 'blur' },
          { min: 3, max: 50, message: this.$t('login.usernameLength'), trigger: 'blur' }
        ],
        email: [
          { type: 'email', message: this.$t('login.emailFormat'), trigger: 'blur' }
        ],
        password: [
          { required: true, validator: this.validatePassword, trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, validator: this.validateConfirmPassword, trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    toggleLang() {
      const next = this.$i18n.locale === 'zh-CN' ? 'en' : 'zh-CN'
      this.$i18n.setLocale(next)
    },
    validatePassword(_rule, value, callback) {
      if (!value) {
        callback(new Error(this.$t('login.passwordRequired')))
      } else if (value.length < 6) {
        callback(new Error(this.$t('login.passwordMinLength')))
      } else {
        callback()
      }
    },
    validateConfirmPassword(_rule, value, callback) {
      if (!value) {
        callback(new Error(this.$t('login.confirmPasswordRequired')))
      } else if (value !== this.loginForm.password) {
        callback(new Error(this.$t('login.passwordMismatch')))
      } else {
        callback()
      }
    },
    toggleMode() {
      this.isLogin = !this.isLogin
      this.$refs.loginForm.resetFields()
    },
    handleSubmit() {
      this.$refs.loginForm.validate(async (valid) => {
        if (!valid) return

        this.loading = true
        try {
          if (this.isLogin) {
            await this.handleLogin()
          } else {
            await this.handleRegister()
          }
        } catch (error) {
          console.error(error)
        } finally {
          this.loading = false
        }
      })
    },
    async handleLogin() {
      try {
        await this.$auth.loginWith('local', {
          data: {
            username: this.loginForm.username,
            password: this.loginForm.password
          }
        })
        this.$message.success(this.$t('login.loginSuccess'))
        this.$router.push('/')
      } catch (error) {
        this.$message.error(this.$t('login.loginFailed'))
      }
    },
    async handleRegister() {
      try {
        const { data } = await this.$axios.post('/v1/auth/register', {
          username: this.loginForm.username,
          password: this.loginForm.password,
          email: this.loginForm.email || null
        })

        this.$auth.setUserToken(data.token)
        await this.$auth.fetchUser()

        this.$message.success(this.$t('login.registerSuccess'))
        this.$router.push('/notes')
      } catch (error) {
        const message = error.response?.data?.message || this.$t('login.registerFailed')
        this.$message.error(message)
      }
    }
  }
}
</script>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
}

.login-lang-toggle {
  position: fixed;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--border-color, #dcdfe6);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary, #909399);
  font-size: 12px;
  font-weight: 600;
  transition: all 0.2s;

  &:hover {
    background: rgba(64, 158, 255, 0.08);
    color: #409eff;
  }
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 8px;

  .login-header {
    text-align: center;
    margin-bottom: 30px;

    .login-logo {
      width: 54px;
      height: 54px;
      margin: 0 auto 10px;
    }

    h2 {
      font-size: 24px;
      color: #303133;
      margin: 10px 0;
    }

    p {
      color: #909399;
      font-size: 14px;
    }
  }

  .login-form {
    .submit-btn {
      width: 100%;
      margin-top: 10px;
    }

    .login-footer {
      text-align: center;
      margin-top: 20px;
    }
  }
}
</style>
