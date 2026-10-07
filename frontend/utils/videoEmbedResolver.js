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
  },
  {
    name: 'twitter',
    regex: /(?:twitter\.com|x\.com)\/(?:\w+|i\/web)\/status(?:es)?\/(\d+)/,
    // X 没有"纯视频播放器"的嵌入地址，只能嵌整张推文卡片（视频在卡片里点开播放），
    // 这个页面就是官方 widgets.js 自己创建的 iframe，直接用可以避免往笔记里注入第三方脚本。
    embed: (m) => `https://platform.twitter.com/embed/Tweet.html?id=${m[1]}&dnt=true`,
    // 封面接口 cdn.syndication.twimg.com 只允许 platform.twitter.com 跨域，浏览器拿不到，用通用占位图。
    // 推文卡片高度随正文长短变化，这里只是初始高度，播放后按 iframe 发来的 resize 消息自适应。
    fixedWidth: 550,
    fixedHeight: 640,
    autoHeight: true
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
      fixedHeight: svc.fixedHeight || null,
      autoHeight: !!svc.autoHeight
    }
  }
  return null
}

/**
 * 推文卡片高度随正文长短变化，Tweet.html 加载后会 postMessage 一条 twttr.private.resize
 * 告诉父页面实际高度（widgets.js 本身就是靠这个调 iframe 高度的）。传入 message 事件和
 * 对应的 iframe，是这个 iframe 发来的 resize 消息就返回高度（px），否则返回 null。
 */
export function getTweetResizeHeight(e, iframe) {
  if (!iframe || e.source !== iframe.contentWindow || e.origin !== 'https://platform.twitter.com') return null
  let msg = e.data
  if (typeof msg === 'string') {
    try { msg = JSON.parse(msg) } catch (err) { return null }
  }
  const embed = msg && msg['twttr.embed']
  if (!embed || embed.method !== 'twttr.private.resize') return null
  const height = embed.params && embed.params[0] && Math.ceil(embed.params[0].height)
  return height || null
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
