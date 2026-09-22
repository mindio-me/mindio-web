/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

const EDITORJS_VERSION = '2.28.2'

/**
 * 把录音 Blob 包成 File，扩展名按真实 mime 推导（Firefox 是 ogg、Safari 是 mp4，
 * 不能一律写死 .webm，否则服务端按扩展名推 Content-Type 时会和实际内容对不上）。
 * RecordingCapsule.vue 的"插入到当前笔记"分支也复用这个函数，避免两处实现漂移。
 */
export function blobToFile(blob) {
  const ext = (blob.type || 'audio/webm').split('/')[1].split(';')[0] || 'webm'
  return new File([blob], `recording-${Date.now()}.${ext}`, { type: blob.type || 'audio/webm' })
}

function formatTimestamp(date) {
  const pad = (n) => (n < 10 ? '0' + n : '' + n)
  return `${date.getMonth() + 1}/${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 拉笔记 -> 校验是 EditorJS 格式 -> 存回去，且把 title/isPublic/tagIds/projectId/summary
 * 这些字段原样带回去。NoteService.updateNote 在后端是整份替换，不是合并：漏传一个字段
 * 就等于把它清空——不像 editor.vue 自己的自动保存那样可以图省事写死 isPublic:false、
 * tagIds:[]（那是因为那个页面压根没有对应的编辑控件），这里没有"用户正盯着表单"这层
 * 保障，必须把读到的值原样带回去。
 */
async function saveNotePreservingFields(noteId, note, content, noteService) {
  await noteService.updateNote(noteId, {
    title: note.title,
    content: JSON.stringify(content),
    contentType: note.contentType,
    isPublic: note.isPublic,
    tagIds: (note.tags || []).map((t) => t.id),
    projectId: note.projectId || null,
    summary: note.summary || '',
    sectionContents: content.blocks.map((b) => JSON.stringify(b.data)),
    sectionTypes: content.blocks.map((b) => b.type)
  })
}

export async function attachRecording({ target, blob, durationSeconds, noteId, uploadService, noteService, clipService }) {
  const file = blobToFile(blob)

  if (target === 'library') {
    const uploadResult = await uploadService.uploadLocal(file, 'note', 0)
    const clip = await clipService.createClip({
      sourceType: 'AUDIO_RECORDING',
      sourceUrl: uploadResult.url,
      title: '录音 · ' + formatTimestamp(new Date()),
      contentFormat: 'audio',
      durationSeconds
    })
    return { mode: 'library', clip }
  }

  if (target === 'new') {
    const uploadResult = await uploadService.uploadLocal(file, 'note', 0)
    const block = { type: 'audioRecord', data: { url: uploadResult.url, duration: durationSeconds } }
    const note = await noteService.createNote({
      title: '未命名笔记',
      content: JSON.stringify({ time: Date.now(), blocks: [block], version: EDITORJS_VERSION }),
      contentType: 'editorjs',
      isPublic: false,
      tagIds: [],
      sectionContents: [JSON.stringify(block.data)],
      sectionTypes: [block.type]
    })
    return { mode: 'new', note, url: uploadResult.url, duration: durationSeconds }
  }

  // target === 'current': 追加一个新块到笔记末尾（没有具体挂在哪个块上的场景，比如从
  // 顶栏麦克风发起、没有块 id 可循）。
  const note = await noteService.getNoteById(noteId)
  if (note.contentType !== 'editorjs') {
    throw new Error('这篇笔记不是 EditorJS 格式，暂不支持插入录音块，请选择"新建笔记"')
  }

  const uploadResult = await uploadService.uploadLocal(file, 'note', noteId || 0)
  const content = JSON.parse(note.content || `{"time":0,"blocks":[],"version":"${EDITORJS_VERSION}"}`)
  content.blocks = content.blocks || []
  content.blocks.push({ type: 'audioRecord', data: { url: uploadResult.url, duration: durationSeconds } })

  await saveNotePreservingFields(noteId, note, content, noteService)

  return { mode: 'current', noteId, url: uploadResult.url, duration: durationSeconds }
}

/**
 * 从笔记正文里的"录音"块发起、且没有任何正在显示这篇笔记的活实例能接住
 * 'recording:resolve-block' 广播时的兜底路径：直接读笔记内容，按 blockId 找到那个
 * 占位块换成真正的播放器数据。找不到（比如录音过程中用户手动把那个块删了）就退化成
 * 追加到末尾，保证录音本身不会因为占位块没了就被静默丢弃。
 */
export async function resolveBlockInNote({ noteId, blockId, url, duration, noteService }) {
  const note = await noteService.getNoteById(noteId)
  if (note.contentType !== 'editorjs') {
    throw new Error('这篇笔记不是 EditorJS 格式，暂不支持插入录音块')
  }

  const content = JSON.parse(note.content || `{"time":0,"blocks":[],"version":"${EDITORJS_VERSION}"}`)
  content.blocks = content.blocks || []
  const idx = content.blocks.findIndex((b) => b.id === blockId)
  const resolvedBlock = { id: blockId, type: 'audioRecord', data: { url, duration } }

  if (idx >= 0) {
    content.blocks[idx] = resolvedBlock
  } else {
    content.blocks.push(resolvedBlock)
  }

  await saveNotePreservingFields(noteId, note, content, noteService)
  return { noteId, url, duration }
}
