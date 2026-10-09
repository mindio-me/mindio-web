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
        <button
          class="rc-btn rc-sys"
          :class="{ on: recording.systemAudio === 'on', connecting: recording.systemAudio === 'connecting', unavailable: !recording.systemAudioSupported }"
          :title="systemAudioTitle"
          :aria-pressed="recording.systemAudio === 'on' ? 'true' : 'false'"
          :aria-disabled="!recording.systemAudioSupported || recording.systemAudio === 'connecting' ? 'true' : 'false'"
          @click="toggleSystemAudio"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/><path d="M9.5 8.5v3M12 7v6M14.5 8.5v3"/></svg>
        </button>
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

    <!-- 保存失败时的面板和提示条会同时出现，统一放进一个纵向堆叠容器里，避免两者叠在同一个右下角 -->
    <div v-if="(showAttachPopover && stopResult) || toast" class="recording-stack" :style="dragStyle">
      <div v-if="showAttachPopover && stopResult" class="recording-attach-pop">
        <div class="rap-hd">
          <span>{{ stopResult.recovered ? '恢复的录音 · ' + formatStartedAt(stopResult.startedAt) : '录音还没保存成功' }}</span>
          <span class="rap-hd-right">
            <span class="rap-dur">{{ formatDuration(stopResult.durationSeconds) }}</span>
            <button class="rap-close-btn" title="放弃这段录音" @click="discardRecording">✕</button>
          </span>
        </div>
        <button class="rap-opt" :disabled="saving" @click="saveRecording">
          <div class="rap-opt-title">{{ saving ? '正在保存…' : '重新保存' }}</div>
          <div class="rap-opt-sub">{{ saveTargetText }}</div>
        </button>
        <button class="rap-opt" @click="downloadRecording">
          <div class="rap-opt-title">下载到本地</div>
          <div class="rap-opt-sub">不需要网络；下载后这里仍保留，确认存好了再关</div>
        </button>
      </div>

      <div v-if="toast" class="recording-toast">
        <span>{{ toast }}</span>
        <span v-if="toastAction" class="rt-actions">
          <button class="rt-action" @click="runToastAction">{{ toastAction.label }}</button>
          <button class="rt-close" title="关闭" @click="dismissToast">✕</button>
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import recordingController from '~/utils/recordingController'
import recordingStash from '~/utils/recordingStash'
import { resolveBlockInNote, createNoteWithRecording, blobToFile } from '~/services/noteAttachService'

// 等 notes 页回执的超时时间：替换块 + 一次直存的往返，8s 足够；超时按"当前没有活的
// 编辑器实例接得住"处理，转去后端改写笔记的兜底路径
const RESOLVE_ACK_TIMEOUT_MS = 8000

export default {
  name: 'RecordingCapsule',
  data() {
    return {
      collapsed: false,
      entering: false,
      showAttachPopover: false,
      stopResult: null,
      saving: false,
      toast: '',
      toastAction: null, // { label, handler } —— 有值时 toast 带一个操作按钮
      toastTimer: null,
      orphanTimer: null,
      orphanInterval: null,
      recovering: false,
      offStashMessage: null,
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
    // 录音存去哪儿不用问：从录音块发起的放回那个块，从顶栏发起的新建一篇笔记
    saveTargetText() {
      const origin = this.stopResult && this.stopResult.origin
      return origin && origin.type === 'block' ? '放回原来的录音块' : '保存为新笔记'
    },
    dragStyle() {
      return this.dragPos ? { left: this.dragPos.left + 'px', top: this.dragPos.top + 'px', right: 'auto', bottom: 'auto' } : {}
    },
    systemAudioTitle() {
      if (!this.recording.systemAudioSupported) {
        const isDesktop = typeof window !== 'undefined' && !!window.mindioDesktop
        return isDesktop ? '当前无法录制电脑声音' : '桌面版可录制电脑声音'
      }
      if (this.recording.systemAudio === 'connecting') return '正在连接电脑声音…'
      if (this.recording.systemAudio === 'on') return '正在录制电脑声音，点击关闭'
      return '同时录制电脑声音'
    }
  },
  watch: {
    // 录音块靠这个知道"录完了但还没存成功"，显示成"录音还在右下角"
    stopResult(result) {
      const origin = result && result.origin
      recordingController.setPendingBlock(origin && origin.type === 'block' ? origin.blockId : null)
    },
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
    recordingController.on('error', this.onRecordingError)
    recordingController.on('stop', this.onControllerStop)
    recordingController.on('orphaned', this.onOrphaned)
    recordingController.on('segment', this.onSegment)
    recordingController.setOwner(this.$auth && this.$auth.user && this.$auth.user.username)
    this.offStashMessage = recordingStash.onMessage(this.onStashMessage)
    // 上次没存成功的录音（刷新/崩溃/关页签/上传失败后离开）：页面稳定下来后找一次，之后每分钟再看一次
    // （浏览器不支持 BroadcastChannel 时只能等旧会话的心跳过期，要过两三分钟）
    this.orphanTimer = setTimeout(() => this.recoverOrphans(), 1500)
    this.orphanInterval = setInterval(() => this.recoverOrphans(), 60000)
  },
  beforeDestroy() {
    recordingController.off('error', this.onRecordingError)
    recordingController.off('stop', this.onControllerStop)
    recordingController.off('orphaned', this.onOrphaned)
    recordingController.off('segment', this.onSegment)
    if (this.offStashMessage) this.offStashMessage()
    clearTimeout(this.toastTimer)
    clearTimeout(this.orphanTimer)
    clearInterval(this.orphanInterval)
  },
  methods: {
    // 正常的"用户点了停止"已经在 onStop() 里通过 await recordingController.stop() 处理过了
    // （result.interrupted === false，这里直接忽略，避免同一段录音被存两遍）。这个监听只
    // 接住设备被拔掉/系统睡眠打断录音时controller 自己发起的强制停止——那种情况下没有人
    // 点按钮，必须靠事件才能拿到抢救下来的录音内容，走一遍同样的保存管线。
    onControllerStop(result) {
      if (!result || !result.interrupted) return
      this.adoptStopResult(result)
    },
    /**
     * 录音没能正常收尾（onstop 没触发，内存里的数据拿不到了），但本地副本里有已经录到的部分：
     * 从本地副本拼回来，走和正常停止一样的保存流程
     */
    async onOrphaned({ stashId, durationSeconds, origin }) {
      const blob = await recordingStash.loadBlob(stashId)
      if (!blob) return
      const segments = await recordingStash.loadSegments(stashId)
      this.adoptStopResult({ blob, durationSeconds, origin, interrupted: true, segments, stash: Promise.resolve(stashId) })
    },
    /** 找本地副本里"没人管"的录音，一次恢复一段；手上已经有一段待保存、或正在录音时先不管 */
    async recoverOrphans() {
      if (this.recovering || this.stopResult || this.recording.status !== 'idle') return
      this.recovering = true
      try {
        const owner = this.$auth && this.$auth.user && this.$auth.user.username
        const sessions = await recordingStash.listOrphans(owner)
        for (const session of sessions) {
          if (this.stopResult || this.recording.status !== 'idle') return
          if (!(await recordingStash.claim(session))) continue
          const blob = await recordingStash.loadBlob(session.id)
          if (!blob) {
            await recordingStash.remove(session.id)
            continue
          }
          const segments = await recordingStash.loadSegments(session.id)
          this.adoptStopResult({
            blob,
            durationSeconds: session.durationSeconds,
            origin: session.origin,
            interrupted: true,
            recovered: true,
            startedAt: session.startedAt,
            url: session.url || null, // 上次已经传上去了就不用再传
            segments,
            stash: Promise.resolve(session.id)
          })
          return
        }
      } finally {
        this.recovering = false
      }
    },
    /** 接手一段录完了但还没保存的录音，直接保存；只有保存失败才弹面板 */
    adoptStopResult(result) {
      this.stopResult = result
      this.saveRecording()
    },
    async onStop() {
      const result = await recordingController.stop()
      if (!result) return
      this.adoptStopResult(result)
    },
    /** 录音过程中每录完一段（30 分钟）就先传上去：之后浏览器崩了，这些段已经在服务器上 */
    async onSegment({ stash, segment }) {
      try {
        const res = await this.$uploadService.uploadLocal(blobToFile(segment.blob), 'note', 0)
        segment.url = res.url
        const id = await stash
        if (id) recordingStash.setSegment(id, { index: segment.index, url: res.url })
      } catch (e) {
        // 失败没关系：停止录音保存时还会再传一次
      }
    },
    /** 把还没传的分段和完整录音传上去，返回录音块的数据 { url, duration, segments? } */
    async uploadRecording(result) {
      const id = result.stash ? await result.stash : null
      const segments = result.segments || []
      for (const seg of segments) {
        if (seg.url || !seg.blob) continue
        const res = await this.$uploadService.uploadLocal(blobToFile(seg.blob), 'note', 0)
        seg.url = res.url
        if (id) recordingStash.setSegment(id, { index: seg.index, url: res.url })
      }
      if (!result.url) {
        const res = await this.$uploadService.uploadLocal(blobToFile(result.blob), 'note', 0, {
          onProgress: this.uploadProgressHandler()
        })
        result.url = res.url
        if (id) recordingStash.update(id, { url: res.url })
      }
      const data = { url: result.url, duration: result.durationSeconds }
      // 分段只有齐全才有用，缺了哪段就整段转写（2 小时/100MB 以内都没问题）
      if (segments.length > 1 && segments.every((s) => s.url)) {
        data.segments = segments.map((s) => ({ url: s.url, start: s.start, duration: s.duration }))
      }
      return data
    },
    /**
     * 保存录音：从录音块发起的放回那个块（块被删了就追加到那篇笔记末尾，整篇笔记没了就新建一篇），
     * 从顶栏发起的新建一篇笔记。失败时录音留在面板里，可以重试或下载到本地。
     */
    async saveRecording() {
      const result = this.stopResult
      if (!result || this.saving) return
      this.saving = true
      this.showAttachPopover = false
      this.showPendingToast('正在保存录音…')
      try {
        const data = await this.uploadRecording(result)
        const origin = result.origin
        let message = result.recovered ? '已恢复并保存上次没存好的录音' : '录音已保存'
        let action = null
        if (origin && origin.type === 'block' && origin.noteId && origin.blockId) {
          const placed = await this.placeIntoBlock(origin, data, result.startedAt)
          if (placed.newNote) {
            message = '原来那篇笔记已经不在了，录音已保存为新笔记'
            action = this.openNoteAction(placed.newNote.id)
          }
          // 别的页签可能也开着这篇笔记：让它们把那个块也换成播放器，免得它们之后自动保存时把这里的结果盖掉
          recordingStash.post({ type: 'resolved', noteId: origin.noteId, blockId: origin.blockId, data })
        } else {
          const note = await createNoteWithRecording({ data, startedAt: result.startedAt, noteService: this.$noteService })
          this.$nuxt.$emit('workspace:note-created', note)
          message = result.recovered ? '已恢复上次没存好的录音，保存为新笔记' : '录音已保存为新笔记'
          action = this.openNoteAction(note.id)
        }
        this.settleStopResult()
        this.showToast(message, action)
      } catch (e) {
        this.showToast((e && e.message) || '保存失败，请重试')
        // 失败时绝不能丢掉录音——那可能是唯一的副本。面板里可以重试或下载到本地
        this.showAttachPopover = true
      } finally {
        this.saving = false
      }
    },
    /**
     * 把录音放回它当初插入的那个块。优先走"活实例"路径：广播出去，如果眼下正好有
     * 页面开着这篇笔记，由那个页面自己的编辑器实例原地换掉那个块、走它自己的保存管线——
     * 不会和它自己的自动保存抢着写。等不到回执就退到后端读-改-写兜底路径（整篇笔记的字段
     * 都原样带回去，不会丢标签/公开状态之类的）；那篇笔记已经不在了就新建一篇。
     */
    async placeIntoBlock(origin, data, startedAt) {
      const ackPromise = this.waitForResolveAck(origin.noteId, origin.blockId)
      this.$nuxt.$emit('recording:resolve-block', { noteId: origin.noteId, blockId: origin.blockId, data })
      if (await ackPromise) return {}
      try {
        await resolveBlockInNote({ noteId: origin.noteId, blockId: origin.blockId, data, noteService: this.$noteService })
        return {}
      } catch (e) {
        const status = e && e.response && e.response.status
        if (status !== 404 && status !== 403 && !(e && e.notEditorJs)) throw e
        const note = await createNoteWithRecording({ data, startedAt, noteService: this.$noteService })
        this.$nuxt.$emit('workspace:note-created', note)
        return { newNote: note }
      }
    },
    openNoteAction(noteId) {
      return {
        label: '打开',
        handler: () => {
          // 已经在笔记页时只改 query 不会重新挂载页面，mounted 里读 openNoteId 那段不会再跑——
          // 改成和 AI 助手的笔记引用一样，让笔记页原地切过去
          if (this.$route.path.replace(/\/$/, '') === '/workspace/notes') {
            this.$nuxt.$emit('workspace:open-note-request', noteId)
          } else {
            this.$router.push({ path: '/workspace/notes', query: { openNoteId: noteId } })
          }
        }
      }
    },
    /** 别的页签把录音放回了某个块：这个页面要是也开着那篇笔记，让它把那个块换成播放器 */
    onStashMessage(message) {
      if (!message || message.type !== 'resolved') return
      this.$nuxt.$emit('recording:resolve-block', { noteId: message.noteId, blockId: message.blockId, data: message.data })
    },
    /** 这段录音的去向定了（存好了，或确认放弃）：删掉本地副本，再看看还有没有别的待恢复 */
    settleStopResult() {
      const result = this.stopResult
      this.stopResult = null
      this.showAttachPopover = false
      if (result && result.stash) result.stash.then((id) => recordingStash.remove(id))
      clearTimeout(this.orphanTimer)
      this.orphanTimer = setTimeout(() => this.recoverOrphans(), 1000)
    },
    downloadRecording() {
      if (!this.stopResult) return
      const { blob, startedAt } = this.stopResult
      const ext = (blob.type || 'audio/webm').split('/')[1].split(';')[0] || 'webm'
      const d = new Date(startedAt || Date.now())
      const pad = (n) => (n < 10 ? '0' : '') + n
      const name = `录音-${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}.${ext}`
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = name
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 60000)
      this.showToast('已开始下载。确认文件存好后，再点 ✕ 关掉这段录音')
    },
    formatStartedAt(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      const pad = (n) => (n < 10 ? '0' : '') + n
      return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
    uploadProgressHandler() {
      return (percent) => this.showPendingToast(percent >= 100 ? '上传完成，正在保存…' : `正在上传 ${percent}%…`)
    },
    onRecordingError(payload) {
      const reason = (payload && payload.reason) || ''
      if (reason === 'start-failed') {
        this.showToast('无法开始录音，请检查麦克风权限')
      } else if (reason === 'system-audio-failed') {
        this.showSystemAudioFailedToast(payload)
      } else if (reason === 'system-audio-ended') {
        this.showToast('电脑声音已断开，继续仅录制麦克风')
      } else {
        // 'track-ended'（设备被拔掉/被别的程序抢走）与 'recorder-error'（录制器自身报错）
        this.showToast('录音意外中断')
      }
    },
    async showSystemAudioFailedToast(payload) {
      const api = typeof window !== 'undefined' && window.mindioDesktop && window.mindioDesktop.recording
      let needsPermission = !!(payload.error && payload.error.name === 'NotAllowedError')
      // 授权框被关掉之后，Mac 上的失败不一定报 NotAllowedError（主进程 getSources 失败、回调空结果），
      // 以系统给的权限状态为准
      if (api && payload.platform === 'darwin' && !needsPermission && api.getSystemAudioPermission) {
        try {
          needsPermission = (await api.getSystemAudioPermission()) !== 'granted'
        } catch (e) {
          // 查不到就按错误名判断
        }
      }
      if (api && payload.platform === 'darwin' && needsPermission) {
        // macOS 授予屏幕录制/系统音频权限后，正在运行的进程拿不到新权限，必须重启应用
        this.showToast('需要在系统设置中允许 MindIO 录制屏幕和系统音频，开启后需重启应用', {
          label: '打开系统设置',
          handler: () => api.openSystemAudioSettings()
        })
      } else {
        this.showToast('无法录制电脑声音，已继续仅录制麦克风')
      }
    },
    formatDuration(totalSeconds) {
      const s = Math.max(0, Math.floor(totalSeconds || 0))
      const m = Math.floor(s / 60)
      const r = s % 60
      return (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r
    },
    barHeight(i) {
      // 正常说话时麦克风 RMS 电平其实不高，乘 22 很难用满整个高度区间，看起来像是
      // 没在动——先放大灵敏度（乘 2.6 再夹到 1），让平常说话的音量就能撑满整根条。
      const boosted = Math.min(1, this.recording.level * 2.6)
      const base = 4 + boosted * 22
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
    toggleSystemAudio() {
      if (!this.recording.systemAudioSupported || this.recording.systemAudio === 'connecting') return
      recordingController.setSystemAudio(this.recording.systemAudio !== 'on').catch(() => {})
    },
    /** 等某个页面对 'recording:resolve-block' 的回执，同一套 request/ack 约定 */
    waitForResolveAck(noteId, blockId, timeoutMs = RESOLVE_ACK_TIMEOUT_MS) {
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
    discardRecording() {
      // 这可能是一场会议唯一的录音，放弃前必须确认；确认后连本地副本一起删
      this.$confirm('放弃后这段录音无法恢复。还没存好的话，可以先选"下载到本地"。', '放弃这段录音？', {
        type: 'warning',
        confirmButtonText: '放弃录音',
        cancelButtonText: '再想想'
      }).then(() => {
        this.settleStopResult()
        this.showToast('已放弃录音')
      }).catch(() => {})
    },
    showToast(text, action) {
      clearTimeout(this.toastTimer)
      this.toast = text
      this.toastAction = action || null
      // 带操作按钮的提示要留时间给用户读完再点
      this.toastTimer = setTimeout(() => this.dismissToast(), action ? 8000 : 3200)
    },
    /** 进行中状态：不自动消失，等真正的结果 toast 把它替换掉 */
    showPendingToast(text) {
      clearTimeout(this.toastTimer)
      this.toastTimer = null
      this.toast = text
      this.toastAction = null
    },
    dismissToast() {
      clearTimeout(this.toastTimer)
      this.toastTimer = null
      this.toast = ''
      this.toastAction = null
    },
    runToastAction() {
      const action = this.toastAction
      this.dismissToast()
      if (action && action.handler) Promise.resolve(action.handler()).catch(() => {})
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
.rc-sys {
  svg { width: 14px; height: 14px; }
  // 开着时反白，和 macOS 控制中心里"已开启"的按钮一样
  &.on { background: #fff; color: var(--text-color, #1a202c); }
  &.on:hover { background: rgba(255, 255, 255, 0.88); }
  &.connecting svg { animation: rc-pulse 1.2s ease-in-out infinite; }
  &.connecting { cursor: progress; }
  &.unavailable { opacity: 0.4; cursor: not-allowed; }
  &.unavailable:hover { background: rgba(255, 255, 255, 0.14); }
}
@media (prefers-reduced-motion: reduce) {
  .rc-sys.connecting svg { animation: none; }
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
  &:focus-visible { outline: 2px solid var(--color-nav); outline-offset: 2px; }
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
  &:focus-visible { outline: 2px solid var(--color-nav); outline-offset: -2px; }
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
.rt-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.rt-action {
  border: none;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  background: var(--card-bg-color, #fff);
  color: var(--text-color, #1a202c);
  &:hover { filter: brightness(0.94); }
  &:focus-visible { outline: 2px solid var(--card-bg-color, #fff); outline-offset: 2px; }
}
.rt-close {
  border: none;
  background: none;
  color: inherit;
  opacity: 0.6;
  cursor: pointer;
  font-size: 12px;
  padding: 2px 4px;
  margin-left: auto;
  &:hover { opacity: 1; }
}
</style>
