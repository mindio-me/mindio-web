<!--
 Copyright (c) 2026 Fasong Wu
 SPDX-License-Identifier: AGPL-3.0-only
-->
<template>
  <div>
    <div
      v-if="recording.status !== 'idle'"
      class="recording-capsule"
      :class="{ collapsed: collapsed, entering: entering }"
      :style="dragStyle"
    >
      <template v-if="collapsed">
        <span class="rc-dot" @click="collapsed = false"></span>
      </template>
      <template v-else-if="recording.status === 'starting'">
        <span class="rc-dot"></span>
        <span class="rc-time">请求麦克风权限…</span>
      </template>
      <template v-else>
        <span class="rc-grip" title="拖动移到别处" @pointerdown="onGripDown">
          <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="9" cy="6" r="1.4"/><circle cx="15" cy="6" r="1.4"/><circle cx="9" cy="12" r="1.4"/><circle cx="15" cy="12" r="1.4"/><circle cx="9" cy="18" r="1.4"/><circle cx="15" cy="18" r="1.4"/></svg>
        </span>
        <span class="rc-dot"></span>
        <span class="rc-time">{{ formattedTime }}</span>
        <span class="rc-wave">
          <span v-for="i in 20" :key="i" :style="{ height: barHeight(i) + 'px' }"></span>
        </span>
        <button class="rc-btn" :title="recording.status === 'paused' ? '继续' : '暂停'" @click="togglePause">
          <svg v-if="recording.status === 'paused'" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/></svg>
        </button>
        <button class="rc-btn stop" title="结束录音" @click="onStop">
          <svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
        </button>
        <button class="rc-collapse" title="收起为圆点" @click="collapsed = true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
      </template>
    </div>

    <!-- 选择面板和提示条会同时出现（保存中/保存失败后重新弹回选择面板），统一放进一个
         纵向堆叠容器里，避免两者叠在同一个右下角 -->
    <div v-if="(showAttachPopover && stopResult) || toast" class="recording-stack" :style="dragStyle">
      <div v-if="showAttachPopover && stopResult" class="recording-attach-pop">
        <div class="rap-hd">
          <span>保存录音到</span>
          <span class="rap-hd-right">
            <span class="rap-dur">{{ formatDuration(stopResult.durationSeconds) }}</span>
            <button class="rap-close-btn" title="放弃这段录音" @click="discardRecording">✕</button>
          </span>
        </div>
        <button v-if="canAttachToCurrentNote" class="rap-opt" @click="chooseTarget('current')">
          <div class="rap-opt-title">当前笔记</div>
        </button>
        <button class="rap-opt" @click="chooseTarget('new')">
          <div class="rap-opt-title">新建笔记</div>
        </button>
        <button class="rap-opt" @click="chooseTarget('library')">
          <div class="rap-opt-title">仅存入素材库</div>
          <div class="rap-opt-sub">之后可在任意笔记中插入</div>
        </button>
      </div>

      <div v-if="toast" class="recording-toast">{{ toast }}</div>
    </div>
  </div>
</template>

<script>
import recordingController from '~/utils/recordingController'
import { attachRecording, resolveBlockInNote, blobToFile } from '~/services/noteAttachService'

// 等页面（editor.vue / notes/index.vue）回执的超时时间：插入/替换块 + 一次直存的
// 往返，8s 足够；超时按"当前没有活的编辑器实例接得住"处理，转去后端兜底路径
const INSERT_ACK_TIMEOUT_MS = 8000

export default {
  name: 'RecordingCapsule',
  data() {
    return {
      collapsed: false,
      entering: false,
      showAttachPopover: false,
      stopResult: null,
      toast: '',
      toastTimer: null,
      broadcastNoteId: null,
      dragPos: null // {left, top} once dragged; else default bottom-right via CSS
    }
  },
  computed: {
    recording() {
      return recordingController.state
    },
    formattedTime() {
      return this.formatDuration(this.recording.elapsedSeconds)
    },
    currentNoteId() {
      const path = this.$route.path
      let m = path.match(/^\/workspace\/notes\/(\d+)\/edit$/)
      if (m) return Number(m[1])
      if (path === '/workspace/editor' && this.$route.query.id) return Number(this.$route.query.id)
      m = path.match(/^\/workspace\/notes\/(\d+)$/)
      if (m) return Number(m[1])
      return this.broadcastNoteId
    },
    // "当前笔记"该指向哪篇笔记：如果这段录音是从某篇笔记正文的"录音"块里开始的，就该
    // 一直指向那篇笔记本身——而不是"不管哪篇，只要现在眼前开着的那篇"。没有这层区分的话，
    // 在笔记 A 里开始录音、停止前又打开了笔记 B，"当前笔记"会悄悄指向 B（因为 B 正开着），
    // 用户以为存回了 A，实际存去了完全不相关的 B。只有找不到明确来源（比如从顶栏麦克风
    // 发起、没有笔记上下文）时，才退回"当前打开的随便哪篇"这个老逻辑。
    attachTargetNoteId() {
      const origin = this.stopResult && this.stopResult.origin
      if (origin && origin.type === 'block' && origin.noteId) return origin.noteId
      return this.currentNoteId
    },
    canAttachToCurrentNote() {
      // 只有 /workspace/editor 正开着目标笔记时，写入才是安全的：那种情况下由 editor.vue
      // 自己的 onRecordingInsertBlock 走它自己的保存管线，不会和任何别的编辑器抢着写。
      // 其他页面（尤其 /workspace/notes，它有自己的实时编辑器 + 自动保存）会和本功能的
      // 读-改-写互相覆盖，宁可不提供"当前笔记"这一项，也不能把录音写没了。这层校验同时也
      // 保证了：只要"当前笔记"这个选项还显示着，它指向的笔记就一定是眼前真正开着的那篇——
      // 录音来源笔记如果已经不再是当前打开的笔记，会在这里被拦下，而不是悄悄接到别的笔记上。
      return !!this.attachTargetNoteId && this.isEditorLiveOnNote(this.attachTargetNoteId)
    },
    dragStyle() {
      return this.dragPos ? { left: this.dragPos.left + 'px', top: this.dragPos.top + 'px', right: 'auto', bottom: 'auto' } : {}
    }
  },
  watch: {
    'recording.status'(next, prev) {
      // 有了 'starting' 中间态后，真正的"开始录音"过渡是 starting -> recording
      // （极端情况下 starting 环节被跳过也兼容 idle -> recording）
      if (next === 'recording' && (prev === 'idle' || prev === 'starting')) {
        this.entering = true
        this.collapsed = false
        setTimeout(() => { this.entering = false }, 250)
      }
    }
  },
  mounted() {
    this.$nuxt.$on('workspace:current-note-id', this.onBroadcastNoteId)
    this.$nuxt.$emit('workspace:request-current-note-id')
    recordingController.on('error', this.onRecordingError)
  },
  beforeDestroy() {
    this.$nuxt.$off('workspace:current-note-id', this.onBroadcastNoteId)
    recordingController.off('error', this.onRecordingError)
    clearTimeout(this.toastTimer)
  },
  methods: {
    onBroadcastNoteId(id) {
      this.broadcastNoteId = id || null
    },
    onRecordingError(payload) {
      const reason = (payload && payload.reason) || ''
      if (reason === 'start-failed') {
        this.showToast('无法开始录音，请检查麦克风权限')
      } else {
        // 'track-ended'（设备被拔掉/被别的程序抢走）与 'recorder-error'（录制器自身报错）
        this.showToast('录音意外中断')
      }
    },
    formatDuration(totalSeconds) {
      const s = Math.max(0, Math.floor(totalSeconds || 0))
      const m = Math.floor(s / 60)
      const r = s % 60
      return (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r
    },
    barHeight(i) {
      const base = 4 + this.recording.level * 22
      const jitter = ((i * 37) % 11) - 5 // 固定的伪随机抖动，避免所有条一样高
      return Math.max(4, Math.min(20, base + jitter))
    },
    togglePause() {
      if (this.recording.status === 'paused') {
        recordingController.resume()
      } else {
        recordingController.pause()
      }
    },
    async onStop() {
      const result = await recordingController.stop()
      if (!result) return
      this.stopResult = result

      // 从笔记正文的"录音"块里点出来的——那个块从点开始录音那一刻起就占着位置，
      // 存去哪儿早就定死了，不用再问。不管用户录音过程中有没有切笔记、切页签，
      // 都直接把这个块换成真正的播放器；只有这一步本身失败了（比如上传失败、
      // 笔记被删了），才退回旧的"选个存的地方"弹窗兜底，保证录音不会真的丢。
      const origin = result.origin
      if (origin && origin.type === 'block' && origin.blockId) {
        const ok = await this.autoResolveBlock(origin)
        if (ok) return
      }

      this.showAttachPopover = true
    },
    isEditorLiveOnNote(noteId) {
      return this.$route.path === '/workspace/editor' && String(this.$route.query.id) === String(noteId)
    },
    /**
     * 把录音填回它当初插入的那个块。优先走"活实例"路径：广播出去，如果眼下正好有
     * 页面开着这篇笔记（不管是 /workspace/editor 还是 /workspace/notes），由那个页面
     * 自己的编辑器实例原地换掉那个块、走它自己的保存管线——不会和它自己的自动保存
     * 抢着写。等不到回执（没有页面开着这篇笔记，或者那个页面存的时候失败了）就退到
     * 后端读-改-写兜底路径，同样是安全的（整篇笔记的字段都原样带回去，不会丢标签/
     * 公开状态之类的）。
     */
    async autoResolveBlock(origin) {
      if (!this.stopResult) return false
      this.showPendingToast('正在保存…')
      const { blob, durationSeconds } = this.stopResult

      try {
        const file = blobToFile(blob)
        const uploadResult = await this.$uploadService.uploadLocal(file, 'note', origin.noteId || 0)

        // 先挂上回执监听再广播，免得页面回得太快而漏接
        const ackPromise = this.waitForResolveAck(origin.noteId, origin.blockId)
        this.$nuxt.$emit('recording:resolve-block', {
          noteId: origin.noteId,
          blockId: origin.blockId,
          url: uploadResult.url,
          duration: durationSeconds
        })
        const liveOk = await ackPromise

        if (!liveOk) {
          // 没有活实例接住广播——走后端兜底：按 blockId 找到那个占位块换掉；
          // 万一那个块已经被手动删了，退化成追加到笔记末尾，总之录音不会丢。
          await resolveBlockInNote({
            noteId: origin.noteId,
            blockId: origin.blockId,
            url: uploadResult.url,
            duration: durationSeconds,
            noteService: this.$noteService
          })
        }

        this.showToast('已保存')
        this.stopResult = null
        return true
      } catch (e) {
        this.showToast((e && e.message) || '自动保存失败，请手动选择保存位置')
        return false
      }
    },
    /** 等某个页面对 'recording:resolve-block' 的回执，同一套 request/ack 约定 */
    waitForResolveAck(noteId, blockId, timeoutMs = INSERT_ACK_TIMEOUT_MS) {
      return new Promise((resolve) => {
        let timer = null
        const finish = (ok) => {
          clearTimeout(timer)
          this.$nuxt.$off('recording:resolve-block:ack', onAck)
          resolve(ok)
        }
        const onAck = (payload) => {
          if (!payload || String(payload.noteId) !== String(noteId) || payload.blockId !== blockId) return
          finish(!!payload.ok)
        }
        timer = setTimeout(() => finish(false), timeoutMs)
        this.$nuxt.$on('recording:resolve-block:ack', onAck)
      })
    },
    async chooseTarget(target) {
      if (!this.stopResult) return
      this.showAttachPopover = false
      const { blob, durationSeconds } = this.stopResult
      // 长录音上传可能要几十秒，先给一个不自动消失的进行中提示，之后被真正的结果 toast 覆盖
      this.showPendingToast('正在保存…')
      let succeeded = false

      try {
        const targetNoteId = this.attachTargetNoteId
        if (target === 'current' && this.isEditorLiveOnNote(targetNoteId)) {
          const file = blobToFile(blob)
          const uploadResult = await this.$uploadService.uploadLocal(file, 'note', targetNoteId || 0)
          // 先挂上回执监听再广播，免得 editor.vue 回得太快而漏接
          const ackPromise = this.waitForInsertBlockAck(targetNoteId)
          this.$nuxt.$emit('recording:insert-block', {
            noteId: targetNoteId,
            url: uploadResult.url,
            duration: durationSeconds
          })
          // 上传可能耗时很久，期间用户完全可能已经离开编辑器页（监听器随之注销），
          // 或者插入成功但保存失败 —— 没等到 ok 的回执就不能报"已插入"
          const ok = await ackPromise
          if (!ok) throw new Error('保存失败，请重试')
          this.showToast('已插入到当前笔记')
          succeeded = true
          return
        }

        const result = await attachRecording({
          target,
          blob,
          durationSeconds,
          noteId: this.currentNoteId,
          uploadService: this.$uploadService,
          noteService: this.$noteService,
          clipService: this.$clipService
        })

        if (result.mode === 'library') {
          this.showToast('已保存到素材库，之后可以在任意笔记里插入这段录音')
        } else if (result.mode === 'new') {
          this.showToast('已保存为新笔记')
        } else {
          this.showToast('已插入到当前笔记')
        }
        succeeded = true
      } catch (e) {
        this.showToast(e.message || '保存失败，请重试')
      } finally {
        if (succeeded) {
          this.stopResult = null
        } else {
          // 失败时绝不能丢掉 blob —— 那是这段录音唯一的副本。把选择面板重新弹出来，
          // 用户可以换一个目标重试（比如改存素材库）。
          this.showAttachPopover = true
        }
      }
    },
    /**
     * 等 editor.vue 对某次 'recording:insert-block' 的回执。没人监听（用户已离开编辑器页）
     * 时会走超时分支，同样按失败处理，避免报假成功。
     */
    waitForInsertBlockAck(noteId, timeoutMs = INSERT_ACK_TIMEOUT_MS) {
      return new Promise((resolve) => {
        let timer = null
        const finish = (ok) => {
          clearTimeout(timer)
          this.$nuxt.$off('recording:insert-block:ack', onAck)
          resolve(ok)
        }
        const onAck = (payload) => {
          if (!payload || String(payload.noteId) !== String(noteId)) return
          finish(!!payload.ok)
        }
        timer = setTimeout(() => finish(false), timeoutMs)
        this.$nuxt.$on('recording:insert-block:ack', onAck)
      })
    },
    discardRecording() {
      // blob 已经由 recordingController.stop() 产出，controller 这边没什么可再 cancel 的，
      // 丢掉本地这份引用即可
      this.stopResult = null
      this.showAttachPopover = false
      this.showToast('已放弃录音')
    },
    showToast(text) {
      clearTimeout(this.toastTimer)
      this.toast = text
      this.toastTimer = setTimeout(() => { this.toast = '' }, 3200)
    },
    /** 进行中状态：不自动消失，等真正的结果 toast 把它替换掉 */
    showPendingToast(text) {
      clearTimeout(this.toastTimer)
      this.toastTimer = null
      this.toast = text
    },
    onGripDown(e) {
      const capsuleEl = e.currentTarget.closest('.recording-capsule')
      const startRect = capsuleEl.getBoundingClientRect()
      const offX = e.clientX - startRect.left
      const offY = e.clientY - startRect.top

      const onMove = (ev) => {
        const left = Math.max(8, Math.min(ev.clientX - offX, window.innerWidth - startRect.width - 8))
        const top = Math.max(8, Math.min(ev.clientY - offY, window.innerHeight - startRect.height - 8))
        this.dragPos = { left, top }
      }
      const onUp = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    }
  }
}
</script>

<style scoped lang="scss">
.recording-capsule,
.recording-stack {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 2100;
}

.recording-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.recording-capsule {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: var(--text-color, #1a202c);
  color: #fff;
  padding: 9px 10px 9px 12px;
  border-radius: 999px;
  box-shadow: 0 18px 40px rgba(23, 25, 40, 0.25);

  &.entering {
    animation: rc-in 0.22s cubic-bezier(0.3, 1.15, 0.6, 1);
  }
  &.collapsed {
    padding: 0;
    width: 20px;
    height: 20px;
    justify-content: center;
  }
}
@keyframes rc-in {
  from { transform: scale(0.85); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .recording-capsule.entering { animation: none; }
}

.rc-grip {
  display: flex;
  align-items: center;
  cursor: grab;
  color: rgba(255, 255, 255, 0.4);
  touch-action: none;
  svg { width: 12px; height: 12px; }
}
.rc-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.16);
  flex-shrink: 0;
  cursor: pointer;
  animation: rc-pulse 1.6s ease-in-out infinite;
}
@keyframes rc-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
@media (prefers-reduced-motion: reduce) {
  .rc-dot { animation: none; }
}
.rc-time {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  min-width: 38px;
}
.rc-wave {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 18px;
  span {
    width: 2.5px;
    background: rgba(255, 255, 255, 0.55);
    border-radius: 2px;
    transition: height 0.12s ease;
  }
}
.rc-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  svg { width: 11px; height: 11px; }
  &:hover { background: rgba(255, 255, 255, 0.24); }
  &:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
  &.stop { background: #ef4444; }
  &.stop:hover { filter: brightness(1.1); }
}
.rc-collapse {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  display: flex;
  padding: 2px;
  svg { width: 13px; height: 13px; }
  &:hover { color: #fff; }
}

.recording-attach-pop {
  width: 260px;
  background: var(--card-bg-color, #fff);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 12px;
  box-shadow: 0 18px 40px rgba(23, 25, 40, 0.18);
  overflow: hidden;
}
.rap-hd {
  padding: 12px 14px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-color, #1a202c);
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rap-hd-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rap-dur {
  font-weight: 400;
  color: var(--text-muted, #718096);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
/* 视觉上沿用 editorjsAudioTool 模态框的 .at-close-btn 约定（圆形淡底 ✕） */
.rap-close-btn {
  width: 22px;
  height: 22px;
  border: none;
  background: var(--bg-secondary, #f2f2f7);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #86868b);
  font-size: 12px;
  line-height: 1;
  padding: 0;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
  &:hover {
    background: var(--border-color, #e2e2ea);
    color: var(--text-color, #333);
  }
  &:focus-visible { outline: 2px solid #667eea; outline-offset: 2px; }
}
.rap-opt {
  width: 100%;
  display: block;
  padding: 10px 14px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  color: var(--text-color, #1a202c);
  &:hover { background: var(--bg-secondary, #f5f7fa); }
  &:focus-visible { outline: 2px solid #667eea; outline-offset: -2px; }
}
.rap-opt-title { font-size: 13.5px; }
.rap-opt-sub { font-size: 12px; color: var(--text-muted, #718096); margin-top: 1px; }

.recording-toast {
  background: var(--text-color, #1a202c);
  color: var(--card-bg-color, #fff);
  font-size: 12.5px;
  padding: 10px 14px;
  border-radius: 10px;
  box-shadow: 0 18px 40px rgba(23, 25, 40, 0.18);
  max-width: 230px;
  line-height: 1.6;
}
</style>
