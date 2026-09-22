/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

const SERVICES = [
  {
    name: 'bilibili',
    regex: /bilibili\.com\/video\/(BV\w+)/,
    embed: (m) => `https://player.bilibili.com/player.html?bvid=${m[1]}&danmaku=0`,
    aspectRatio: '16/9'
    // 没有可靠的公开封面接口（官方 view 接口需要服务端代理，见方案2），渲染层用通用占位图
  },
  {
    name: 'youtube',
    regex: /(?:youtu\.be\/|youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|v\/|shorts\/))([^?&\s/]+)/,
    embed: (m) => `https://www.youtube.com/embed/${m[1]}`,
    // YouTube 缩略图地址是按 video id 拼接的固定规律，不用调接口
    poster: (m) => `https://i.ytimg.com/vi/${m[1]}/hqdefault.jpg`,
    aspectRatio: '16/9'
  },
  {
    name: 'vimeo',
    regex: /vimeo\.com\/(\d+)/,
    embed: (m) => `https://player.vimeo.com/video/${m[1]}`,
    // Vimeo 没有固定规律的封面地址，要调 oEmbed 接口才能拿到，交给 fetchVimeoPoster()异步处理
    needsOembedPoster: true,
    aspectRatio: '16/9'
  },
  {
    name: 'douyin',
    regex: /douyin\.com.*?(?:\/video\/|[?&]modal_id=)(\d+)/,
    embed: (m) => `https://open.douyin.com/player/video?vid=${m[1]}&autoplay=0`,
    // 抖音同样没有可靠的公开封面渠道，渲染层用通用占位图
    fixedWidth: 324,
    fixedHeight: 720
  }
]

export function resolveVideoEmbed(url) {
  for (const svc of SERVICES) {
    const m = svc.regex.exec(url)
    if (m) return {
      service: svc.name,
      embedUrl: svc.embed(m),
      posterUrl: svc.poster ? svc.poster(m) : null,
      needsOembedPoster: !!svc.needsOembedPoster,
      aspectRatio: svc.aspectRatio || '16/9',
      defaultWidth: svc.defaultWidth || 100,
      fixedWidth: svc.fixedWidth || null,
      fixedHeight: svc.fixedHeight || null
    }
  }
  return null
}

/**
 * Vimeo 封面没有固定规律的地址，需要调它的 oEmbed 接口拿 thumbnail_url（该接口允许跨域，
 * 浏览器可直接调）。只在插入链接的那一刻调一次，拿到后连同 embedUrl 一起存进笔记内容里，
 * 以后重新打开笔记读的是已经存好的 posterUrl，不会再发这个请求。失败（网络问题/私有视频）
 * 时返回 null，调用方回退到通用占位图，不影响插入视频这个主流程。
 */
export async function fetchVimeoPoster(sourceUrl) {
  try {
    const res = await fetch(`https://vimeo.com/api/oembed.json?url=${encodeURIComponent(sourceUrl)}`)
    if (!res.ok) return null
    const json = await res.json()
    return json.thumbnail_url || null
  } catch (e) {
    return null
  }
}
