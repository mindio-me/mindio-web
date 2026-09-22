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

  // handle browser-initiated recorder termination
  mediaRecorder.onerror = (e) => {
    cleanup()
    emit('error', { reason: 'recorder-error', error: e })
  }

  mediaStream.getTracks().forEach((track) => {
    track.onended = () => {
      if (state.status !== 'idle') {
        cleanup()
        emit('error', { reason: 'track-ended' })
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
  emit('start', {})
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
    mediaRecorder.onstop = () => {
      const blob = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' })
      const durationSeconds = state.elapsedSeconds
      const origin = state.origin
      cleanup()
      const result = { blob, durationSeconds, origin }
      emit('stop', result)
      resolve(result)
    }
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
  // 先摘掉 onerror/onended 再收尾：规范上主动 stop() 不派发 ended，但摘干净更稳妥 ——
  // 现在 'error' 事件真的会弹 toast 了，正常结束录音时误报"录音意外中断"是不可接受的
  if (mediaRecorder) mediaRecorder.onerror = null
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
