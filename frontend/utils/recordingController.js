/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import Vue from 'vue'

const state = Vue.observable({
  status: 'idle', // 'idle' | 'starting' | 'recording' | 'paused' | 'stopping'
  elapsedSeconds: 0,
  level: 0, // 0..1，实时麦克风音量，给波形条用
  origin: null // { type: 'block', noteId } | { type: 'topbar' } | null —— 录音是从哪儿发起的
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

  const preferredType = 'audio/webm;codecs=opus'
  const mimeType = window.MediaRecorder && MediaRecorder.isTypeSupported(preferredType) ? preferredType : ''
  mediaRecorder = mimeType ? new MediaRecorder(mediaStream, { mimeType }) : new MediaRecorder(mediaStream)

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

  setupLevelMeter()
  requestWakeLock()
  emit('start', {})
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

function setupLevelMeter() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    audioCtx = new AudioContextClass()
    const source = audioCtx.createMediaStreamSource(mediaStream)
    analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    source.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)

    const tick = () => {
      if (state.status === 'idle') return
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
  } catch (e) {
    state.level = 0
  }
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
  if (audioCtx) audioCtx.close().catch(() => {})
  audioCtx = null
  analyser = null
  state.status = 'idle'
  state.elapsedSeconds = 0
  state.level = 0
  state.origin = null
}

export default { state, on, off, start, pause, resume, stop, cancel }
