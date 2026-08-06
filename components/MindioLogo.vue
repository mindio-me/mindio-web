<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <span class="mindio-logo" aria-hidden="true">
    <svg viewBox="0 0 64 64" focusable="false">
      <rect class="mindio-logo__field" x="8" y="8" width="48" height="48" rx="14" />
      <path
        class="mindio-logo__stroke"
        d="M19 43V28.5C19 22.5 27.2 21.6 29 27.3L32 37L35 27.3C36.8 21.6 45 22.5 45 28.5V43"
      />
      <circle class="mindio-logo__dot" cx="32" cy="37" r="4" />
    </svg>
  </span>
</template>

<script>
export default {
  name: 'MindioLogo'
}
</script>

<style scoped lang="scss">
.mindio-logo {
  display: inline-grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;

  svg {
    display: block;
    width: 100%;
    height: 100%;
  }
}

.mindio-logo__field {
  fill: var(--text-color, #15202b);
}

.mindio-logo__stroke {
  fill: none;
  stroke: var(--bg-color, #fffdf8);
  stroke-width: 5.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mindio-logo__dot {
  fill: #2f8f6b;
}
</style>
