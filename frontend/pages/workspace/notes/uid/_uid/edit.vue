<!--
  Copyright (c) 2026 Fasong Wu
  SPDX-License-Identifier: AGPL-3.0-only
-->
<script>
/**
 * /workspace/notes/uid/<uid>/edit：导出时保留了路径后缀，复用 index.vue 的查找逻辑，找到后跳转到 /workspace/notes/<id>/edit。
 */
import UidPage from './index.vue'

export default {
  extends: UidPage,
  name: 'NoteByUidEditPage'
}
</script>
