<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <button
    type="button"
    :class="['ws-panel-collapse-toggle', side === 'left' ? 'ws-left-collapse-toggle' : 'ws-right-collapse-toggle', { 'is-collapsed': collapsed }]"
    :title="collapsed ? expandTitle : collapseTitle"
    @click="$emit('toggle')"
  ><i :class="iconClass"></i></button>
</template>

<script>
export default {
  name: 'PanelCollapseToggle',
  props: {
    side: {
      type: String,
      required: true,
      validator: (v) => v === 'left' || v === 'right',
    },
    collapsed: { type: Boolean, default: false },
    expandTitle: { type: String, required: true },
    collapseTitle: { type: String, required: true },
  },
  computed: {
    iconClass() {
      const pointsRight = this.side === 'left' ? this.collapsed : !this.collapsed
      return pointsRight ? 'el-icon-arrow-right' : 'el-icon-arrow-left'
    },
  },
}
</script>
