<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <el-card class="settings-card ai-settings-card" shadow="never">
    <div slot="header" class="card-header">
      <span>{{ $t('aiSettings.title') }}</span>
    </div>
    <div v-loading="loading">
      <p class="section-tip">{{ $t('aiSettings.intro') }}</p>
      <el-alert
        v-if="!cryptoKeyConfigured"
        type="warning"
        :closable="false"
        show-icon
        :title="$t('aiSettings.cryptoKeyMissing')"
        class="ai-settings-alert"
      />
      <el-form label-position="top" @submit.native.prevent>
        <el-form-item :label="$t('aiSettings.defaultProvider')">
          <el-select v-model="form['ai.default_provider']" :placeholder="placeholderOf('ai.default_provider')" clearable>
            <el-option v-for="p in providers" :key="p.id" :label="p.label" :value="p.id" />
          </el-select>
        </el-form-item>

        <el-collapse v-model="openSections">
          <el-collapse-item v-for="p in providers" :key="p.id" :name="p.id" :title="p.label">
            <ai-setting-secret
              :item="items[`ai.${p.id}.api_key`]"
              v-model="form[`ai.${p.id}.api_key`]"
              :label="$t('aiSettings.apiKey')"
              @clear="clearKey(`ai.${p.id}.api_key`)"
            />
            <el-form-item :label="$t('aiSettings.model')">
              <el-input v-model="form[`ai.${p.id}.model`]" :placeholder="placeholderOf(`ai.${p.id}.model`)" clearable />
            </el-form-item>
            <el-form-item :label="$t('aiSettings.baseUrl')">
              <el-input v-model="form[`ai.${p.id}.base_url`]" :placeholder="placeholderOf(`ai.${p.id}.base_url`)" clearable />
            </el-form-item>
            <el-form-item v-if="p.id === 'doubao'" :label="$t('aiSettings.chatPath')">
              <el-input v-model="form['ai.doubao.chat_path']" :placeholder="placeholderOf('ai.doubao.chat_path')" clearable />
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item name="embedding" :title="$t('aiSettings.embeddingTitle')">
            <p class="section-tip">{{ $t('aiSettings.embeddingTip') }}</p>
            <el-form-item :label="$t('aiSettings.model')">
              <el-input v-model="form['ai.embedding.model']" :placeholder="placeholderOf('ai.embedding.model')" clearable />
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item name="asr" :title="$t('aiSettings.asrTitle')">
            <p class="section-tip">{{ $t('aiSettings.asrTip') }}</p>
            <ai-setting-secret
              :item="items['asr.volcengine.app_key']"
              v-model="form['asr.volcengine.app_key']"
              :label="$t('aiSettings.asrAppKey')"
              @clear="clearKey('asr.volcengine.app_key')"
            />
            <ai-setting-secret
              :item="items['asr.volcengine.access_key']"
              v-model="form['asr.volcengine.access_key']"
              :label="$t('aiSettings.asrAccessKey')"
              @clear="clearKey('asr.volcengine.access_key')"
            />
            <el-form-item :label="$t('aiSettings.autoTranscribe')">
              <el-switch
                :value="form['asr.auto_transcribe'] !== 'false'"
                @change="v => $set(form, 'asr.auto_transcribe', v ? 'true' : 'false')"
              />
              <div class="hint-text">{{ $t('aiSettings.autoTranscribeTip') }}</div>
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item name="image" :title="$t('aiSettings.imageTitle')">
            <p class="section-tip">{{ $t('aiSettings.imageTip') }}</p>
            <el-form-item :label="$t('aiSettings.imageProvider')">
              <el-select v-model="form['ai.image.provider']" style="width: 100%">
                <el-option value="" :label="$t('aiSettings.imageProviderNone')" />
                <el-option value="openai" label="OpenAI" />
                <el-option value="doubao" label="豆包 / Doubao" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('aiSettings.imageModel')">
              <el-input
                v-model="form['ai.image.model']"
                :placeholder="imageModelPlaceholder"
                :disabled="!form['ai.image.provider']"
                clearable
              />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <div class="inline-actions">
          <el-button type="primary" size="small" :loading="saving" :disabled="saving" @click="save">
            <i class="el-icon-check"></i> {{ $t('aiSettings.save') }}
          </el-button>
        </div>
      </el-form>
    </div>
  </el-card>
</template>

<script>
const AiSettingSecret = {
  name: 'AiSettingSecret',
  props: { item: { type: Object, default: null }, value: { type: String, default: '' }, label: { type: String, required: true } },
  render(h) {
    const item = this.item || {}
    const status = item.unreadable
      ? this.$t('aiSettings.unreadable')
      : item.configured
        ? `${this.$t(`aiSettings.source.${item.source}`)} ····${item.last4 || ''}`
        : this.$t('aiSettings.notConfigured')
    return h('el-form-item', { props: { label: this.label } }, [
      h('el-input', {
        props: { value: this.value, showPassword: true },
        attrs: { placeholder: this.$t('aiSettings.keyPlaceholder') },
        on: { input: v => this.$emit('input', v) }
      }),
      h('div', { class: 'hint-text' }, [
        status,
        item.source === 'database'
          ? h('el-button', { props: { type: 'text', size: 'mini' }, on: { click: () => this.$emit('clear') } }, this.$t('aiSettings.clear'))
          : null
      ])
    ])
  }
}

const PROVIDERS = [
  { id: 'anthropic', label: 'Anthropic' },
  { id: 'openai', label: 'OpenAI' },
  { id: 'deepseek', label: 'DeepSeek' },
  { id: 'doubao', label: '豆包 / Doubao' }
]

const IMAGE_DEFAULT_MODELS = {
  openai: 'gpt-image-1',
  doubao: 'doubao-seedream-4-0-250828'
}

export default {
  name: 'AiSettingsCard',
  components: { AiSettingSecret },
  data() {
    return {
      loading: false,
      saving: false,
      cryptoKeyConfigured: true,
      items: {},
      original: {},
      form: {},
      openSections: [],
      providers: PROVIDERS
    }
  },
  computed: {
    imageModelPlaceholder() {
      return IMAGE_DEFAULT_MODELS[this.form['ai.image.provider']] || ''
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const res = await this.$aiSettingsService.get()
        const items = {}
        const form = {}
        ;(res.items || []).forEach(item => {
          items[item.key] = item
          form[item.key] = item.secret ? '' : (item.value || '')
        })
        this.items = items
        this.original = { ...form }
        this.form = form
        this.cryptoKeyConfigured = res.cryptoKeyConfigured
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('aiSettings.loadFailed'))
      } finally {
        this.loading = false
      }
    },
    placeholderOf(key) {
      const item = this.items[key]
      return item && item.effective ? item.effective : this.$t('aiSettings.notConfigured')
    },
    changes() {
      const changes = {}
      Object.keys(this.form).forEach(key => {
        const value = (this.form[key] || '').trim()
        if (this.items[key] && this.items[key].secret) {
          if (value) changes[key] = value
        } else if (value !== (this.original[key] || '')) {
          changes[key] = value
        }
      })
      return changes
    },
    async save() {
      // :disabled 要等下一次渲染才生效，同一轮事件里的连点要在这里挡住
      if (this.saving) return
      const changes = this.changes()
      if (!Object.keys(changes).length) {
        this.$message.info(this.$t('aiSettings.nothingChanged'))
        return
      }
      await this.submit(changes)
    },
    async clearKey(key) {
      if (this.saving) return
      await this.submit({ [key]: '' })
    },
    async submit(changes) {
      this.saving = true
      try {
        await this.$aiSettingsService.update(changes)
        this.$message.success(this.$t('aiSettings.saved'))
        await this.load()
      } catch (e) {
        this.$message.error(e?.response?.data?.message || this.$t('aiSettings.saveFailed'))
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.ai-settings-card {
  background: var(--bg-secondary);
  color: var(--text-color);
  border-color: var(--border-color);

  ::v-deep .el-card__header,
  ::v-deep .el-collapse {
    border-color: var(--border-color);
  }

  ::v-deep .el-collapse-item__header,
  ::v-deep .el-collapse-item__wrap {
    background: transparent;
    color: var(--text-color);
    border-color: var(--border-color);
  }

  ::v-deep .el-collapse-item__content {
    color: var(--text-color);
  }

}

.ai-settings-alert {
  margin-bottom: 12px;
}
</style>
