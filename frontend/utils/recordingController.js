/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Vue from 'vue'

const state = Vue.observable({
  status: 'idle', // 'idle' | 'starting' | 'recording' | 'paused' | 'stopping'
  elapsedSeconds: 0,
  level: 0, // 0..1，实时音量（桌面版是混音后的音量），给波形条用
  origin: null, // { type: 'block', noteId } | { type: 'topbar' } | null —— 录音是从哪儿发起的
  systemAudio: 'off', // 'off' | 'connecting' | 'on' —— 电脑声音有没有接进混音台
  systemAudioSupported: false // 桌面版且混音台建起来了才为 true，start() 时更新
})

const listeners = {}
let stopRequested = false

function on(event, cb) {
  ;(listeners[event] || (listeners[event] = [])).push(cb)
}

function off(event, cb) {
  const arr = listeners[event]
  if (!arr) return
  const i = arr.indexOf(cb)
  if (i !== -1) arr.splice(i, 1)
}

function emit(event, payload) {
  ;(listeners[event] || []).slice().forEach((cb) => cb(payload))
}

let mediaRecorder = null
let mediaStream = null
let chunks = []
let timerId = null
let audioCtx = null
let analyser = null
let levelRafId = null
let wakeLock = null
let pendingStopResolve = null
let pendingErrorPayload = null
let forcedStopTimer = null
// 桌面版混音台：mic/电脑声音 → mixBus → mixDest(MediaRecorder 录这个) + analyser(波形条)
let micSource = null
let mixBus = null
let mixDest = null
let sysStream = null
let sysSource = null
// 每次断开/清理都 +1：连接电脑声音要等授权弹窗，期间录音可能已经结束或开关被关掉，
// 迟到的流靠这个识别出来并立刻停掉
let systemAudioToken = 0

const SYSTEM_AUDIO_PREF_KEY = 'mindio.recording.systemAudio'

function desktopRecordingApi() {
  return (typeof window !== 'undefined' && window.mindioDesktop && window.mindioDesktop.recording) || null
}

function readSystemAudioPref() {
  try {
    return window.localStorage.getItem(SYSTEM_AUDIO_PREF_KEY) === '1'
  } catch (e) {
    return false
  }
}

function writeSystemAudioPref(enabled) {
  try {
    window.localStorage.setItem(SYSTEM_AUDIO_PREF_KEY, enabled ? '1' : '0')
  } catch (e) {
    // 存不下只是下次不记得选择，不影响录音
  }
}

async function start(origin) {
  if (state.status !== 'idle') return
  state.status = 'starting'
  state.origin = origin || { type: 'topbar' }
  stopRequested = false

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch (e) {
    state.status = 'idle'
    // 通知全局 UI（RecordingCapsule）弹提示：麦克风权限被拒 / 没有设备 / 非安全上下文。
    // 仍然继续往外抛，想在调用点单独处理的调用方不受影响。
    emit('error', { reason: 'start-failed', error: e })
    throw e
  }

  // if stop() or cancel() was called during the permission prompt, abort early
  if (stopRequested) {
    mediaStream.getTracks().forEach((t) => t.stop())
    mediaStream = null
    state.status = 'idle'
    stopRequested = false
    return
  }

  const recordStream = setupAudioGraph()
  const preferredType = 'audio/webm;codecs=opus'
  const mimeType = window.MediaRecorder && MediaRecorder.isTypeSupported(preferredType) ? preferredType : ''
  mediaRecorder = mimeType ? new MediaRecorder(recordStream, { mimeType }) : new MediaRecorder(recordStream)

  chunks = []
  mediaRecorder.ondataavailable = (e) => {
    if (e.data && e.data.size) chunks.push(e.data)
  }

  // 统一的收尾出口：不管是用户主动点停止，还是设备被拔掉/系统睡眠把 track 掐断，
  // 都走这一个 onstop —— 已经录到的内容从这里打包成 Blob 往外发出去，不会被直接丢弃。
  // 用 state.status 在这一刻是不是还是 'stopping' 来分辨这次停止是不是用户主动点的。
  mediaRecorder.onstop = () => {
    clearTimeout(forcedStopTimer)
    forcedStopTimer = null
    const interrupted = state.status !== 'stopping'
    const blob = new Blob(chunks, { type: (mediaRecorder && mediaRecorder.mimeType) || 'audio/webm' })
    const durationSeconds = state.elapsedSeconds
    const stopOrigin = state.origin
    const errorPayload = pendingErrorPayload
    pendingErrorPayload = null
    cleanup()
    if (interrupted && errorPayload) emit('error', errorPayload)
    const result = { blob, durationSeconds, origin: stopOrigin, interrupted }
    emit('stop', result)
    if (pendingStopResolve) {
      const resolve = pendingStopResolve
      pendingStopResolve = null
      resolve(result)
    }
  }

  // handle browser-initiated recorder termination —— 尝试把已经录到的内容抢救出来，
  // 而不是直接丢弃（forceStop 会尝试 mediaRecorder.stop() 触发上面的 onstop 兜底）
  mediaRecorder.onerror = (e) => {
    pendingErrorPayload = { reason: 'recorder-error', error: e }
    forceStop()
  }

  mediaStream.getTracks().forEach((track) => {
    track.onended = () => {
      if (state.status !== 'idle') {
        pendingErrorPayload = { reason: 'track-ended' }
        forceStop()
      }
    }
  })

  mediaRecorder.start(1000)

  state.status = 'recording'
  state.elapsedSeconds = 0

  timerId = setInterval(() => {
    if (state.status !== 'recording') return
    state.elapsedSeconds += 1
    emit('tick', state.elapsedSeconds)
  }, 1000)

  startLevelMeter()
  requestWakeLock()
  emit('start', {})

  // 上次录音开着电脑声音就自动接上；不 await，不拖慢 start() 返回，失败只会弹提示、录音照常
  if (state.systemAudioSupported && readSystemAudioPref()) {
    setSystemAudio(true).catch(() => {})
  }
}

/**
 * 设备被拔掉 / 系统睡眠把音频轨道掐断 / 录制器自身报错时的强制收尾。
 * 优先尝试 mediaRecorder.stop()，让已经录到的内容经由统一的 onstop 打包成 Blob 抢救出来；
 * 只有在录制器压根还没起来、或者已经彻底停掉拿不到任何数据时，才退化为直接丢弃。
 */
function forceStop() {
  if (!mediaRecorder || mediaRecorder.state === 'inactive') {
    const errorPayload = pendingErrorPayload
    pendingErrorPayload = null
    cleanup()
    if (errorPayload) emit('error', errorPayload)
    if (pendingStopResolve) {
      const resolve = pendingStopResolve
      pendingStopResolve = null
      resolve(null)
    }
    return
  }
  try {
    mediaRecorder.stop()
  } catch (e) {
    // 大概率已经在停止过程中了，onstop 早晚会触发善后，这里不用额外处理
  }
  // 极端情况下（设备被强制拔掉）onstop 可能永远不会触发——兜底超时后按"数据无法挽回"
  // 处理，至少保证用户能看到提示、状态机不会卡死在 'stopping'。
  clearTimeout(forcedStopTimer)
  forcedStopTimer = setTimeout(() => {
    if (state.status === 'idle') return
    const errorPayload = pendingErrorPayload || { reason: 'track-ended' }
    pendingErrorPayload = null
    cleanup()
    emit('error', errorPayload)
    if (pendingStopResolve) {
      const resolve = pendingStopResolve
      pendingStopResolve = null
      resolve(null)
    }
  }, 4000)
}

/**
 * 建音频图，返回 MediaRecorder 该录的流。
 * 桌面版：mic → mixBus → mixDest，录 mixDest.stream，电脑声音之后也接进 mixBus；
 * 网页版：和以前一样直接录麦克风流，AudioContext 只给波形条用。
 * 建不起来（AudioContext 不可用等）就退回直接录麦克风，电脑声音开关显示为不可用。
 */
function setupAudioGraph() {
  const api = desktopRecordingApi()
  const wantsMixer = !!(api && api.systemAudioSupported)
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    audioCtx = new AudioContextClass()
    // suspended 状态下 MediaStreamDestination 输出的是静音——必须 resume
    if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {})
    micSource = audioCtx.createMediaStreamSource(mediaStream)
    analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    if (!wantsMixer) {
      micSource.connect(analyser)
      state.systemAudioSupported = false
      return mediaStream
    }
    mixBus = audioCtx.createGain()
    mixDest = audioCtx.createMediaStreamDestination()
    micSource.connect(mixBus)
    mixBus.connect(mixDest)
    mixBus.connect(analyser)
    state.systemAudioSupported = true
    return mixDest.stream
  } catch (e) {
    teardownAudioGraph()
    state.systemAudioSupported = false
    return mediaStream
  }
}

function teardownAudioGraph() {
  if (audioCtx) audioCtx.close().catch(() => {})
  audioCtx = null
  analyser = null
  micSource = null
  mixBus = null
  mixDest = null
}

function startLevelMeter() {
  if (!analyser) {
    state.level = 0
    return
  }
  const data = new Uint8Array(analyser.frequencyBinCount)
  const tick = () => {
    if (state.status === 'idle' || !analyser) return
    analyser.getByteTimeDomainData(data)
    let sumSquares = 0
    for (let i = 0; i < data.length; i++) {
      const v = (data[i] - 128) / 128
      sumSquares += v * v
    }
    state.level = Math.sqrt(sumSquares / data.length)
    levelRafId = requestAnimationFrame(tick)
  }
  levelRafId = requestAnimationFrame(tick)
}

/** 录音期间请求 Screen Wake Lock，防止 macOS/Windows 笔记本几分钟无操作后自动黑屏/待机
 *  打断录音（尤其是长录音）。拿不到锁（浏览器不支持、权限被拒、标签页不在前台等）时静默
 *  失败，不影响录音本身——这只是体验层面的加强，不是录音能否进行的前提条件。 */
function requestWakeLock() {
  if (typeof navigator === 'undefined' || !navigator.wakeLock) return
  navigator.wakeLock
    .request('screen')
    .then((sentinel) => {
      // 极快点了停止——拿到锁的时候录音已经结束了，立刻把锁放掉
      if (state.status === 'idle') {
        sentinel.release().catch(() => {})
        return
      }
      wakeLock = sentinel
    })
    .catch(() => {})
}

function releaseWakeLock() {
  if (wakeLock) {
    wakeLock.release().catch(() => {})
    wakeLock = null
  }
}

function handleVisibilityChange() {
  // Wake Lock 规范规定标签页切到后台时锁会被浏览器自动释放；重新回到前台时如果还在
  // 录音，得自己把锁申请回来，否则屏幕还是会照常黑掉。
  if (document.visibilityState === 'visible' && state.status !== 'idle' && !wakeLock) {
    requestWakeLock()
  }
}

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', handleVisibilityChange)
}

/**
 * 录音中接上/断开电脑声音。只改混音台的连接，MediaRecorder 不中断，最后仍是一个文件。
 * 打开失败时录音继续（退回仅麦克风），并发出 'system-audio-failed'。
 */
async function setSystemAudio(enabled) {
  if (!enabled) {
    disconnectSystemAudio()
    writeSystemAudioPref(false)
    return
  }
  if (!state.systemAudioSupported || !mixBus) return
  if (state.status !== 'recording' && state.status !== 'paused') return
  if (state.systemAudio !== 'off') return

  state.systemAudio = 'connecting'
  const token = ++systemAudioToken
  let stream
  try {
    stream = await navigator.mediaDevices.getDisplayMedia({ audio: true, video: true })
  } catch (error) {
    if (token !== systemAudioToken) return
    failSystemAudio(error)
    return
  }

  // 只要声音：画面轨立刻停掉（不停的话 macOS 的屏幕录制指示会一直亮着）
  stream.getVideoTracks().forEach((t) => t.stop())

  // 等授权期间录音结束了 / 开关被关了——这条流已经没人要，直接停掉
  if (token !== systemAudioToken || !mixBus) {
    stream.getTracks().forEach((t) => t.stop())
    return
  }

  const audioTracks = stream.getAudioTracks()
  if (!audioTracks.length) {
    stream.getTracks().forEach((t) => t.stop())
    failSystemAudio(new Error('no-audio-track'))
    return
  }

  sysStream = stream
  sysSource = audioCtx.createMediaStreamSource(new MediaStream(audioTracks))
  sysSource.connect(mixBus)
  audioTracks.forEach((track) => {
    track.onended = () => {
      if (sysStream !== stream) return
      // 被动断开（授权被收回、系统停止共享）：不改记住的选择，录音继续
      disconnectSystemAudio()
      emit('error', { reason: 'system-audio-ended' })
    }
  })
  state.systemAudio = 'on'
  writeSystemAudioPref(true)
}

function failSystemAudio(error) {
  state.systemAudio = 'off'
  // 失败时把记住的选择改回"关"，免得每次录音都自动重试、反复弹同一条错误
  writeSystemAudioPref(false)
  const api = desktopRecordingApi()
  emit('error', { reason: 'system-audio-failed', error, platform: api ? api.platform : '' })
}

function disconnectSystemAudio() {
  systemAudioToken++
  if (sysSource) {
    try {
      sysSource.disconnect()
    } catch (e) {
      // 已经断开过
    }
  }
  sysSource = null
  if (sysStream) {
    // 先摘 onended 再 stop，避免正常关闭/结束录音时误报"电脑声音已断开"
    sysStream.getTracks().forEach((t) => {
      t.onended = null
      t.stop()
    })
  }
  sysStream = null
  state.systemAudio = 'off'
}

function pause() {
  if (state.status !== 'recording') return
  mediaRecorder.pause()
  state.status = 'paused'
  emit('pause', {})
}

function resume() {
  if (state.status !== 'paused') return
  mediaRecorder.resume()
  state.status = 'recording'
  emit('resume', {})
}

function stop() {
  return new Promise((resolve) => {
    if (state.status === 'starting') {
      stopRequested = true
      resolve(null)
      return
    }
    if (state.status === 'idle' || !mediaRecorder) {
      resolve(null)
      return
    }
    state.status = 'stopping'
    pendingStopResolve = resolve
    mediaRecorder.stop()
  })
}

function cancel() {
  if (state.status === 'starting') {
    stopRequested = true
    return
  }
  if (state.status === 'idle') return
  cleanup()
}

function cleanup() {
  clearInterval(timerId)
  timerId = null
  if (levelRafId) cancelAnimationFrame(levelRafId)
  levelRafId = null
  clearTimeout(forcedStopTimer)
  forcedStopTimer = null
  releaseWakeLock()
  disconnectSystemAudio()
  // 先摘掉 onerror/onended/onstop 再收尾：规范上主动 stop() 不派发 ended，但摘干净更稳妥——
  // 现在 'error' 事件真的会弹 toast 了，正常结束录音时误报"录音意外中断"是不可接受的
  if (mediaRecorder) {
    mediaRecorder.onerror = null
    mediaRecorder.onstop = null
    mediaRecorder.ondataavailable = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => {
      t.onended = null
      t.stop()
    })
  }
  mediaStream = null
  mediaRecorder = null
  chunks = []
  teardownAudioGraph()
  state.status = 'idle'
  state.elapsedSeconds = 0
  state.level = 0
  state.origin = null
}

export default { state, on, off, start, pause, resume, stop, cancel, setSystemAudio }
