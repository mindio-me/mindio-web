/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// 播放器与逐字稿联动：当前句定位、跳过静音、倍速。
// 静音判断只用 ASR 逐句时间戳的空档，不分析音频。
// 详见 docs/superpowers/specs/2026-10-16-recording-phase2-design.md "前端" 一节。

export const SILENCE_GAP_MS = 2000
export const SILENCE_LEAD_MS = 300
export const SPEED_OPTIONS = [0.75, 1, 1.25, 1.5, 2]

/** 最后一个 start <= ms 的下标；在第一项之前返回 -1。items 须按 start 升序 */
export function findIndexAt(items, ms) {
  let lo = 0
  let hi = items.length - 1
  let ans = -1
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (items[mid].start <= ms) {
      ans = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  return ans
}

/** 播放位置落在超过 gap 的空档里时，返回应跳到的毫秒（下一句开始前 lead）；否则 null */
export function silenceSkipTarget(utts, ms, gap = SILENCE_GAP_MS, lead = SILENCE_LEAD_MS) {
  if (!utts.length) return null
  const i = findIndexAt(utts, ms)
  let gapStart
  let next
  if (i < 0) {
    gapStart = 0
    next = utts[0]
  } else {
    if (ms < utts[i].end || i + 1 >= utts.length) return null
    gapStart = utts[i].end
    next = utts[i + 1]
  }
  if (next.start - gapStart <= gap) return null
  const target = next.start - lead
  return target > ms ? target : null
}
