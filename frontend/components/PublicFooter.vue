<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <footer class="page-footer">
    <div class="footer-container">
      <div class="footer-left">
        <p>{{ $t('site.footer.copyright', { name: ownerName, year: currentYear }) }}</p>
      </div>
      <div class="footer-right">
        <a v-if="ownerProfile && ownerProfile.github" :href="formatUrl(ownerProfile.github)" target="_blank" rel="noopener" class="footer-link">GitHub</a>
        <template v-if="$i18n.locale === 'zh-CN'">
          <a href="/contact" class="footer-link" :title="ownerProfile && ownerProfile.wechat ? ownerProfile.wechat : '微信'">
            <i class="el-icon-chat-dot-round"></i> 微信
          </a>
        </template>
        <template v-else>
          <a v-if="ownerProfile && ownerProfile.linkedin" :href="formatUrl(ownerProfile.linkedin)" target="_blank" rel="noopener" class="footer-link">LinkedIn</a>
          <a v-if="ownerProfile && ownerProfile.twitter" :href="formatUrl(ownerProfile.twitter)" target="_blank" rel="noopener" class="footer-link">Twitter</a>
        </template>
        <nuxt-link to="/contact" class="footer-link">{{ $t('site.nav.contact') }}</nuxt-link>
      </div>
    </div>
  </footer>
</template>

<script>
export default {
  name: 'PublicFooter',
  props: {
    ownerProfile: { type: Object, default: () => ({}) }
  },
  computed: {
    ownerName() {
      return (this.ownerProfile && (this.ownerProfile.fullName || this.ownerProfile.title)) || 'MindIO'
    },
    currentYear() {
      return new Date().getFullYear()
    }
  },
  methods: {
    formatUrl(url) {
      if (!url) return '#'
      return url.startsWith('http') ? url : 'https://' + url
    }
  }
}
</script>

<style scoped lang="scss">
.page-footer {
  background: #1a202c;
  padding: 40px 0;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.footer-left p {
  color: #a0aec0;
  margin: 0; font-size: 14px;
}

.footer-right {
  display: flex; gap: 24px; flex-wrap: wrap;
}

.footer-link {
  color: #a0aec0;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
  &:hover { color: white; }
}

@media screen and (max-width: 768px) {
  .footer-container { flex-direction: column; gap: 16px; text-align: center; padding: 0 20px; }
}
</style>
