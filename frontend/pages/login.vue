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
        <h2>{{ $t('login.titleLogin') }}</h2>
        <p>{{ $t('login.welcome') }}</p>
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

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            {{ $t('login.submitLogin') }}
          </el-button>
        </el-form-item>
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
      return redirect('/workspace')
    }
  },
  data() {
    return {
      loading: false,
      loginForm: {
        username: '',
        password: ''
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
        password: [
          { required: true, validator: this.validatePassword, trigger: 'blur' }
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
    handleSubmit() {
      this.$refs.loginForm.validate(async (valid) => {
        if (!valid) return

        this.loading = true
        try {
          await this.handleLogin()
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
        if (this.$auth.user && this.$auth.user.mustChangePassword) {
          this.$router.push('/workspace/profile?forcePasswordChange=1')
        } else {
          this.$router.push('/workspace')
        }
      } catch (error) {
        this.$message.error(this.$t('login.loginFailed'))
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
  }
}
</style>
