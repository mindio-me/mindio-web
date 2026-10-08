/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/*
 * 录音的本地副本（IndexedDB）。录音期间每秒的数据块都同步写一份到这里，保存成功后才删除。
 * 页面刷新、浏览器崩溃、关掉页签、上传失败——只要没保存成功，下次打开 workspace 都能恢复。
 *
 * 这里的每个操作都只是"尽力而为"：IndexedDB 不可用（隐私模式、配额满、被禁用）时只打日志，
 * 绝不能反过来把录音本身弄坏——内存里那份 chunks 仍然是主路径。
 *
 * 多页签：同一段录音不能被两个页面同时拿去保存。持有它的页面会回应 BroadcastChannel 上的询问，
 * 并且每隔几秒刷新一次 heartbeatAt。没人回应（页面已经刷新/关掉/崩溃）或心跳停了很久的会话才算
 * "没人管"；认领时再拿 heartbeatAt 做一次比较并交换，保证只有一个页面认领成功。
 *
 * 长录音还有一路"分段"录音器（专供转写，见 recordingController）：分段的数据块也写在这里，
 * 用 segment 字段区分（主录音的块没有这个字段）；每段的起止时间和上传后的地址记在会话的 segments 里。
 * 同一个广播通道也用来告诉别的页签"有录音开始/停止/存好了"，让那边的录音块跟着刷新。
 */

const DB_NAME = 'mindio-recordings'
const DB_VERSION = 1
const SESSIONS = 'sessions'
const CHUNKS = 'chunks'
const HEARTBEAT_MS = 5000
// 后台页签的定时器会被浏览器节流到约一分钟一次，阈值要比这个宽，免得把别的页签还管着的录音抢走
const ORPHAN_AFTER_MS = 150000

// 问"这段录音还有人管吗"之后等回应的时间
const PROBE_MS = 600

let dbPromise = null
const held = new Map() // sessionId -> intervalId（本页面正持有的会话）
let channel = null
const messageListeners = []

function getChannel() {
  if (channel !== null) return channel
  channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(DB_NAME) : false
  if (channel) {
    channel.onmessage = (e) => {
      const m = e.data || {}
      if (m.type === 'ping' && held.has(m.id)) {
        channel.postMessage({ type: 'pong', id: m.id })
        return
      }
      if (m.type === 'changed' || m.type === 'resolved') {
        messageListeners.slice().forEach((cb) => {
          try {
            cb(m)
          } catch (_) {}
        })
      }
    }
  }
  return channel
}

/** 别的页签发来的 'changed'（有录音开始/停止/删除）和 'resolved'（录音放回块里了） */
function onMessage(cb) {
  getChannel()
  messageListeners.push(cb)
  return () => {
    const i = messageListeners.indexOf(cb)
    if (i !== -1) messageListeners.splice(i, 1)
  }
}

function post(message) {
  const ch = getChannel()
  if (ch) ch.postMessage(message)
}

/** 问一圈：这些会话里哪些还有页面在管。没有 BroadcastChannel 时返回 null（只能靠心跳判断） */
function probeAlive(ids) {
  const ch = getChannel()
  if (!ch) return Promise.resolve(null)
  if (!ids.length) return Promise.resolve(new Set())
  return new Promise((resolve) => {
    const alive = new Set()
    // 自己发出的消息自己的 channel 收不到，回应要用另一个实例来听
    const listener = new BroadcastChannel(DB_NAME)
    listener.onmessage = (e) => {
      const m = e.data || {}
      if (m.type === 'pong') alive.add(m.id)
    }
    ids.forEach((id) => listener.postMessage({ type: 'ping', id }))
    setTimeout(() => {
      listener.close()
      resolve(alive)
    }, PROBE_MS)
  })
}

function available() {
  return typeof window !== 'undefined' && !!window.indexedDB
}

function openDb() {
  if (!available()) return Promise.reject(new Error('indexedDB unavailable'))
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const req = window.indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(SESSIONS)) db.createObjectStore(SESSIONS, { keyPath: 'id' })
      if (!db.objectStoreNames.contains(CHUNKS)) {
        const chunks = db.createObjectStore(CHUNKS, { autoIncrement: true })
        chunks.createIndex('sessionId', 'sessionId', { unique: false })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
    req.onblocked = () => reject(new Error('indexedDB blocked'))
  }).catch((e) => {
    dbPromise = null
    throw e
  })
  return dbPromise
}

function run(storeNames, mode, work) {
  return openDb().then(
    (db) =>
      new Promise((resolve, reject) => {
        const tx = db.transaction(storeNames, mode)
        let result
        Promise.resolve(work(tx)).then(
          (r) => { result = r },
          () => { try { tx.abort() } catch (_) {} }
        )
        tx.oncomplete = () => resolve(result)
        tx.onerror = () => reject(tx.error)
        tx.onabort = () => reject(tx.error || new Error('transaction aborted'))
      })
  )
}

function requestResult(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

function warn(what, e) {
  // eslint-disable-next-line no-console
  console.warn(`[recordingStash] ${what} failed`, e)
}

function newSessionId() {
  return `rec-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/** 开始一段录音的本地副本。返回 sessionId；失败时返回 null（录音照常进行，只是没有本地副本）。 */
async function begin({ mimeType, origin, owner }) {
  if (!available()) return null
  const id = newSessionId()
  const now = Date.now()
  try {
    await run([SESSIONS], 'readwrite', (tx) => {
      tx.objectStore(SESSIONS).put({
        id,
        owner: owner || null,
        mimeType: mimeType || 'audio/webm',
        origin: origin || null,
        startedAt: now,
        heartbeatAt: now,
        durationSeconds: 0,
        status: 'recording',
        chunkCount: 0,
        segments: [],
        url: null
      })
    })
    post({ type: 'changed' })
    // 尽量让浏览器别在空间紧张时清掉这份数据；拒绝了也不影响
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {})
    hold(id)
    return id
  } catch (e) {
    warn('begin', e)
    return null
  }
}

// 同一会话的写入串成一条链：IndexedDB 事务本身按创建顺序执行，这里再保证 seq 严格递增
const chains = new Map()

/** segment 为 null 是主录音的数据块；数字表示第几段分段录音的数据块 */
function append(id, blob, seq, segment = null) {
  if (!id || !blob || !blob.size) return
  const prev = chains.get(id) || Promise.resolve()
  const next = prev
    .then(() =>
      run([CHUNKS, SESSIONS], 'readwrite', (tx) => {
        const record = { sessionId: id, seq, blob }
        if (segment !== null) record.segment = segment
        tx.objectStore(CHUNKS).add(record)
        const sessions = tx.objectStore(SESSIONS)
        return requestResult(sessions.get(id)).then((s) => {
          if (!s) return
          if (segment === null) s.chunkCount = seq + 1
          s.heartbeatAt = Date.now()
          sessions.put(s)
        })
      })
    )
    .catch((e) => warn('append', e))
  chains.set(id, next)
}

async function update(id, patch) {
  if (!id) return
  try {
    await (chains.get(id) || Promise.resolve())
    await run([SESSIONS], 'readwrite', (tx) => {
      const sessions = tx.objectStore(SESSIONS)
      return requestResult(sessions.get(id)).then((s) => {
        if (!s) return
        sessions.put(Object.assign(s, patch, { heartbeatAt: Date.now() }))
      })
    })
  } catch (e) {
    warn('update', e)
  }
}

/** 记一段分段的信息（开始时间、时长、上传后的地址），按 index 合并 */
async function setSegment(id, patch) {
  if (!id || patch == null || patch.index == null) return
  try {
    await (chains.get(id) || Promise.resolve())
    await run([SESSIONS], 'readwrite', (tx) => {
      const sessions = tx.objectStore(SESSIONS)
      return requestResult(sessions.get(id)).then((s) => {
        if (!s) return
        const list = Array.isArray(s.segments) ? s.segments : []
        const existing = list.find((x) => x.index === patch.index)
        if (existing) Object.assign(existing, patch)
        else list.push(Object.assign({}, patch))
        s.segments = list
        s.heartbeatAt = Date.now()
        sessions.put(s)
      })
    })
  } catch (e) {
    warn('setSegment', e)
  }
}

/** 录音停止：标成 stopped，等待保存。仍由本页面持有（心跳继续）。 */
async function markStopped(id, durationSeconds) {
  await update(id, { status: 'stopped', durationSeconds })
  post({ type: 'changed' })
}

/** 保存成功或用户确认放弃后调用：删掉本地副本 */
async function remove(id) {
  if (!id) return
  release(id)
  try {
    await (chains.get(id) || Promise.resolve())
    chains.delete(id)
    await run([SESSIONS, CHUNKS], 'readwrite', (tx) => {
      tx.objectStore(SESSIONS).delete(id)
      const index = tx.objectStore(CHUNKS).index('sessionId')
      return requestResult(index.getAllKeys(window.IDBKeyRange.only(id))).then((keys) => {
        keys.forEach((k) => tx.objectStore(CHUNKS).delete(k))
      })
    })
    post({ type: 'changed' })
  } catch (e) {
    warn('remove', e)
  }
}

/** 读一段录音的会话信息（不含数据） */
async function getSession(id) {
  if (!id || !available()) return null
  try {
    return await run([SESSIONS], 'readonly', (tx) => requestResult(tx.objectStore(SESSIONS).get(id)))
  } catch (e) {
    warn('getSession', e)
    return null
  }
}

/** 某个录音块有没有还没存好的录音（不管在哪个页签手上） */
async function findSessionForBlock(blockId) {
  if (!blockId || !available()) return null
  try {
    const all = await run([SESSIONS], 'readonly', (tx) => requestResult(tx.objectStore(SESSIONS).getAll()))
    return (all || []).find((s) => s.origin && s.origin.type === 'block' && s.origin.blockId === blockId) || null
  } catch (e) {
    warn('findSessionForBlock', e)
    return null
  }
}

/**
 * 分段录音拼回各自的 Blob，带上会话里记的起止时间和已上传的地址。只剩一段的不算分段（返回空）。
 * 崩溃时最后一段可能没来得及记时长，用整段录音的时长补。
 */
async function loadSegments(id) {
  try {
    await (chains.get(id) || Promise.resolve())
    const { session, chunks } = await run([SESSIONS, CHUNKS], 'readonly', (tx) =>
      Promise.all([
        requestResult(tx.objectStore(SESSIONS).get(id)),
        requestResult(tx.objectStore(CHUNKS).index('sessionId').getAll(window.IDBKeyRange.only(id)))
      ]).then(([session, chunks]) => ({ session, chunks }))
    )
    if (!session) return []
    const byIndex = new Map()
    ;(chunks || []).filter((c) => c.segment != null).forEach((c) => {
      if (!byIndex.has(c.segment)) byIndex.set(c.segment, [])
      byIndex.get(c.segment).push(c)
    })
    const meta = Array.isArray(session.segments) ? session.segments : []
    const indexes = new Set([...byIndex.keys(), ...meta.map((m) => m.index)])
    if (indexes.size <= 1) return []
    return [...indexes].sort((a, b) => a - b).map((index) => {
      const m = meta.find((x) => x.index === index) || { index }
      const parts = (byIndex.get(index) || []).sort((a, b) => a.seq - b.seq)
      const start = m.start || 0
      const duration = m.duration != null ? m.duration : Math.max(0, (session.durationSeconds || 0) - start)
      return {
        index,
        start,
        duration,
        url: m.url || null,
        blob: parts.length ? new Blob(parts.map((c) => c.blob), { type: session.mimeType || 'audio/webm' }) : null
      }
    }).filter((x) => x.url || x.blob)
  } catch (e) {
    warn('loadSegments', e)
    return []
  }
}

/** 本地副本拼回一个 Blob（按 seq 排序）。没有数据时返回 null。 */
async function loadBlob(id) {
  try {
    await (chains.get(id) || Promise.resolve())
    const { session, chunks: all } = await run([SESSIONS, CHUNKS], 'readonly', (tx) =>
      Promise.all([
        requestResult(tx.objectStore(SESSIONS).get(id)),
        requestResult(tx.objectStore(CHUNKS).index('sessionId').getAll(window.IDBKeyRange.only(id)))
      ]).then(([session, chunks]) => ({ session, chunks }))
    )
    // 只拼主录音的块，分段录音的块由 loadSegments 处理
    const chunks = (all || []).filter((c) => c.segment == null)
    if (!session || !chunks.length) return null
    chunks.sort((a, b) => a.seq - b.seq)
    return new Blob(chunks.map((c) => c.blob), { type: session.mimeType || 'audio/webm' })
  } catch (e) {
    warn('loadBlob', e)
    return null
  }
}

/** 心跳：证明这段录音还有页面在管，别的页签不要把它当成"没人管"去恢复 */
function hold(id) {
  if (!id || held.has(id)) return
  getChannel()
  const timer = setInterval(() => {
    run([SESSIONS], 'readwrite', (tx) => {
      const sessions = tx.objectStore(SESSIONS)
      return requestResult(sessions.get(id)).then((s) => {
        if (!s) return
        s.heartbeatAt = Date.now()
        sessions.put(s)
      })
    }).catch(() => {})
  }, HEARTBEAT_MS)
  held.set(id, timer)
}

function release(id) {
  const timer = held.get(id)
  if (timer) clearInterval(timer)
  held.delete(id)
}

/**
 * 找出"没人管"的录音：不是本页面持有的、心跳已经停了一阵子的、属于当前用户（或没记录用户）的。
 * 按开始时间从早到晚。
 */
async function listOrphans(owner) {
  if (!available()) return []
  try {
    const all = await run([SESSIONS], 'readonly', (tx) => requestResult(tx.objectStore(SESSIONS).getAll()))
    const now = Date.now()
    const candidates = (all || [])
      .filter((s) => !held.has(s.id))
      .filter((s) => !s.owner || !owner || s.owner === owner)
    const fresh = candidates.filter((s) => now - (s.heartbeatAt || 0) <= ORPHAN_AFTER_MS)
    const alive = await probeAlive(fresh.map((s) => s.id))
    const orphans = candidates.filter((s) => now - (s.heartbeatAt || 0) > ORPHAN_AFTER_MS || (alive && !alive.has(s.id)))
    // 刚开始录就被关掉、一个数据块都没有的会话：没什么可恢复的，顺手删掉，免得永远留在本地
    orphans.filter((s) => !(s.chunkCount > 0)).forEach((s) => remove(s.id))
    return orphans.filter((s) => s.chunkCount > 0).sort((a, b) => a.startedAt - b.startedAt)
  } catch (e) {
    warn('listOrphans', e)
    return []
  }
}

/**
 * 认领 listOrphans 找到的一段录音：在同一个读写事务里确认 heartbeatAt 还是列出来时看到的值
 * （或者已经停了很久），再写上自己的心跳。IndexedDB 的读写事务跨页签也是串行的，
 * 两个页面同时认领时，后一个会发现心跳已经变了，认领失败。
 */
async function claim(session) {
  const id = session && session.id
  if (!id) return false
  try {
    const ok = await run([SESSIONS], 'readwrite', (tx) => {
      const sessions = tx.objectStore(SESSIONS)
      return requestResult(sessions.get(id)).then((s) => {
        if (!s) return false
        const unchanged = s.heartbeatAt === session.heartbeatAt
        const stale = Date.now() - (s.heartbeatAt || 0) > ORPHAN_AFTER_MS
        if (!unchanged && !stale) return false
        s.heartbeatAt = Date.now()
        sessions.put(s)
        return true
      })
    })
    if (ok) hold(id)
    return !!ok
  } catch (e) {
    warn('claim', e)
    return false
  }
}

export default {
  begin, append, update, setSegment, markStopped, remove, loadBlob, loadSegments, getSession,
  findSessionForBlock, hold, release, listOrphans, claim, onMessage, post
}
