<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-dialog
    :visible.sync="visible"
    title="连接飞书"
    width="520px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="feishu-cred-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-position="top">
        <el-form-item label="App ID" prop="appId">
          <el-input v-model="form.appId" placeholder="请输入 App ID" autocomplete="off" />
        </el-form-item>
        <el-form-item label="App Secret" prop="appSecret">
          <el-input
            v-model="form.appSecret"
            placeholder="请输入 App Secret"
            autocomplete="off"
            show-password
          />
        </el-form-item>
      </el-form>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">连接</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'FeishuCredentialDialog',
  props: {
    value: { type: Boolean, default: false }
  },
  data() {
    return {
      visible: this.value,
      saving: false,
      form: {
        appId: '',
        appSecret: ''
      },
      rules: {
        appId: [{ required: true, message: '请输入 App ID', trigger: 'blur' }],
        appSecret: [{ required: true, message: '请输入 App Secret', trigger: 'blur' }]
      }
    }
  },
  watch: {
    value(v) {
      this.visible = v
    },
    visible(v) {
      this.$emit('input', v)
    }
  },
  methods: {
    handleClose() {
      this.visible = false
      this.$emit('input', false)
    },
    handleSubmit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        this.saving = true
        try {
          await this.$emit('submit', { ...this.form })
          this.handleClose()
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>

<style scoped lang="scss">
.feishu-cred-dialog {
  padding-top: 6px;
}
</style>


