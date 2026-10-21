/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// EditorJS 自身有一套独立于 vue-i18n 的内部 i18n 机制（块工具/菜单文案）。
// 自定义工具里的文案一律写英文原文，经 this.api.i18n.t() 取译文（命名空间是 tools.<注册名>，
// tune 是 blockTunes.<注册名>，工具栏里的工具名走 toolNames），中文都放在这里。
// 所以这里 tools 下的键必须和 editorjsHost、公开笔记页注册工具时用的名字一致。
const ZH_MESSAGES = {
  ui: {
    blockTunes: { toggler: { 'Click to tune': '点击调整', 'or drag to move': '或拖动移动' } },
    inlineToolbar: { converter: { 'Convert to': '转换为' } },
    toolbar: { toolbox: { Add: '添加' } }
  },
  toolNames: {
    Text: '文本', Heading: '标题', List: '列表', Quote: '引用',
    Code: '代码块', Delimiter: '分割线', Table: '表格', Image: '图片',
    InlineCode: '行内代码', Marker: '高亮', Strikethrough: '删除线', Superscript: '上标', Math: '公式', Checklist: '任务列表', Warning: '提示框', Attachment: '附件', Markdown: 'Markdown', Embed: '嵌入视频2', Video: '视频2', Audio: '音频', AudioRecord: '录音', Bold: '加粗', Italic: '斜体', Link: '链接',
    'Media Gallery': '媒体画廊', Recording: '录音', 'Local Video': '本地视频', 'Web Video': '网络视频', References: '参考资料', Timeline: '时间线'
  },
  tools: {
    header: { 'Heading 1': '标题 1', 'Heading 2': '标题 2', 'Heading 3': '标题 3', 'Heading 4': '标题 4', 'Heading 5': '标题 5', 'Heading 6': '标题 6' },
    list: { Ordered: '有序列表', Unordered: '无序列表' },
    quote: { 'Align Left': '左对齐', 'Align Center': '居中' },
    table: { 'With headings': '带表头', 'Without headings': '无表头', 'Add row above': '上方插入行', 'Add row below': '下方插入行', 'Delete row': '删除行', 'Add column to the left': '左侧插入列', 'Add column to the right': '右侧插入列', 'Delete column': '删除列' },
    image: { Caption: '图片说明', 'Select an Image': '选择图片', 'With border': '带边框', 'Stretch image': '拉伸图片', 'With background': '带背景', 'With caption': '图片说明' },
    mediaGallery: {
      'Upload image/audio': '上传图片/音频',
      'Paste link (image/video)': '粘贴链接（图片/视频）',
      'Upload failed': '上传失败',
      'Paste an image link, or a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video link...': '粘贴图片链接，或 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频链接...',
      'Adding...': '添加中...',
      Add: '添加',
      "Can't recognize this link. Paste an image URL, or a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video URL": '无法识别链接，请粘贴图片地址，或 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频地址',
      // 以下是点开大图/视频的弹窗（editorjsMediaLightbox）
      'View original': '查看原图',
      Close: '关闭',
      Previous: '上一个',
      Next: '下一个',
      'Loading video...': '视频加载中...',
      'Failed to load video': '视频加载失败'
    },
    audio: {
      'Click to add audio': '点击添加音频',
      'View full transcript': '查看完整逐字稿',
      'Hide transcript': '收起逐字稿',
      'Upload audio': '上传音频',
      Close: '关闭',
      'Upload from computer': '本地上传',
      'Upload from phone': '手机扫码上传',
      'Drop an audio file here': '拖拽音频文件至此',
      'Supports MP3, WAV, M4A, FLAC, OGG, AAC and more': '支持 MP3、WAV、M4A、FLAC、OGG、AAC 等格式',
      'Up to 50 MB per file': '单文件最大 50 MB',
      'Choose file': '选择文件',
      'Please choose an audio file': '请选择音频文件',
      'Uploading...': '上传中...',
      'Upload failed, please try again': '上传失败，请重试',
      'Just a moment...': '稍候...',
      'Scan the QR code with your phone': '用手机扫描二维码',
      'to upload a recording': '上传录音文件',
      'Open your phone camera or WeChat scanner': '打开手机相机或微信扫一扫',
      'Choose the recording on your phone': '在手机上选择录音文件',
      'This updates automatically once uploaded': '上传完成后此处自动更新',
      'Generating QR code...': '正在生成二维码...',
      Regenerate: '重新生成',
      'Generating...': '生成中...',
      'Waiting for upload from phone...': '等待手机扫码上传...',
      'Failed to generate, please try again': '生成失败，请重试',
      'Upload failed': '上传失败',
      'QR code expired': '二维码已过期'
    },
    audioRecord: {
      'Already recording, see the capsule at the bottom right': '已经在录音了，看右下角胶囊',
      'Click to start recording': '点击开始录音',
      'Recording, see the capsule at the bottom right': '录音中，看右下角胶囊',
      "This recording hasn't been saved yet. You can retry or download it from the panel at the bottom right": '录音还没保存成功，在右下角面板里，可以重试或下载到本地',
      'This recording is in progress on another page': '这段录音正在另一个页面里录制',
      "This recording isn't saved yet. It's being processed on another page, or you'll be prompted to recover it at the bottom right": '这段录音还没保存好，正在另一个页面里处理，或稍后会在右下角提示恢复',
      "This recording didn't finish properly": '这段录音没能正常完成',
      "This recording didn't finish properly. Click to remove": '这段录音没能正常完成，点击移除',
      'View full transcript': '查看完整逐字稿',
      'Hide transcript': '收起逐字稿'
    },
    video: {
      'Click to upload a video or drop a file here': '点击上传视频或拖入文件',
      'Uploading...': '上传中...',
      'Upload failed, please try again': '上传失败，请重试'
    },
    embed: {
      'Paste a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video link...': '粘贴 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频链接...',
      Embed: '嵌入',
      "Can't recognize this link. Paste a YouTube / Bilibili / Vimeo / Douyin / X (Twitter) video URL": '无法识别链接，请粘贴 YouTube / Bilibili / Vimeo / 抖音 / X(Twitter) 视频地址'
    },
    references: {
      '+ Add reference': '+ 添加资料',
      '(Untitled)': '(未命名)',
      'Search clips': '从收藏搜',
      'Search notes': '从笔记搜',
      'Add manually': '手动添加',
      Title: '标题',
      URL: '链接地址',
      'Note (optional)': '备注（可选）',
      'Uploading…': '上传中…',
      'Selected: ': '已选择：',
      'Upload failed, please try again': '上传失败，请重试',
      'No file chosen': '未选择文件',
      'Choose a file to upload': '选择文件上传',
      Add: '添加',
      'Search by keyword…': '关键词搜索…',
      'Searching…': '搜索中…',
      'No matches found': '没有找到匹配的结果',
      'Search failed, please try again': '搜索失败，请重试',
      Search: '搜索'
    },
    timeline: {
      '+ Add event': '+ 添加时间点',
      'Date (e.g. 2024-01 or 2024-01-15)': '日期（如 2024-01 或 2024-01-15）',
      'What happened': '发生了什么',
      'Details (optional)': '详细说明（可选）',
      'Related link (optional)': '相关链接（可选）',
      Add: '添加'
    },
    markdown: {
      'Enter Markdown...': '输入 Markdown 内容...',
      Edit: '编辑',
      Split: '分栏',
      Preview: '预览',
      'Preview area': '预览区域'
    },
    math: {
      'TeX, e.g. E = mc^2': 'TeX，例如 E = mc^2'
    }
  },
  blockTunes: {
    delete: { Delete: '删除', 'Click to delete': '点击确认删除' },
    moveUp: { 'Move up': '上移' },
    moveDown: { 'Move down': '下移' },
    codeWrap: { 'Word wrap': '自动换行', 'Turn off word wrap': '取消自动换行' },
    imageCopy: {
      'Copy image': '复制图片',
      'Image copied': '图片已复制',
      'Copied as link only; the image itself could not be copied': '只复制了图片链接，图片本身未能复制',
      'Copy failed': '复制失败'
    }
  }
}

/**
 * new EditorJS({ i18n }) 用的配置。这套字典是模块级全局单例（I18n.currentDictionary），
 * 只有传入非空 messages 时才会调用 setDictionary() 覆盖它——英文分支必须显式传空字典触发重置，
 * 传 undefined 只会导致沿用上一次（通常是中文）构造过的编辑器留下的全局字典，
 * 表现上就像英文模式下菜单文案"写死"成中文了一样
 */
export function editorjsI18nConfig(locale) {
  return locale === 'zh-CN' ? { messages: ZH_MESSAGES } : { messages: {} }
}
