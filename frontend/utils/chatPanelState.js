/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Vue from 'vue'

// 全局AI助手同一时间最多有两处模板会挂载 ChatPanel（笔记页停靠 / 其它页面走抽屉），
// 让它们的 data() 都返回这同一个 Vue.observable 单例，读写天然同步——不管切到哪个 tab，
// 看到的都是同一份会话历史和未发送的草稿。
//
// open 是"AI助手当前是否处于打开状态"这个全局唯一开关——不属于某一处展现形式，
// 笔记页的右栏停靠、GlobalChatDrawer 的抽屉都只是读写它来决定自己要不要显示；
// 在任一处打开/关闭，切到别的 tab 后应该看到同样的开关状态（有右栏的页面显示在右栏，
// 没有的走抽屉），而不是每个入口各管各的。
// dockMountCount：当前有多少个"自己管右栏"的页面（接了 workspaceAiDock mixin）处于
// 挂载状态。用计数而不是布尔，是因为两个这样的页面之间切路由时，新页面的 mounted()
// 和旧页面的 beforeDestroy() 谁先谁后不保证；写在这个共享单例里而不是靠事件广播，
// 是因为 GlobalChatDrawer 在 layout 里排在 <nuxt/> 后面挂载，首屏若直接落在有右栏的
// 页面上，事件早就发完了它才开始监听，会错过——读这个响应式字段就没有先后顺序问题。
export default Vue.observable({
  open: false,
  dockMountCount: 0,
  historyLoaded: false,
  loadingHistory: false,
  historyError: false,
  messages: [],
  input: '',
  sending: false,
  searchingQuery: null,
  liveAssistantText: '',
  pendingAttachments: [],
  pendingConfirmations: [],
  speechSupported: false,
  recognizing: false,
  recognition: null,
  broadcastNoteId: null,
  linkingCitationKey: null,
  copiedMessageId: null,
})
