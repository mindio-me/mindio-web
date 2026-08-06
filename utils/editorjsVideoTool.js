/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

class VideoTool {
  static get toolbox() {
    return {
      title: '视频',
      icon: `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polygon points="23 7 16 12 23 17 23 7"/>
        <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
      </svg>`
    }
  }

  static get isReadOnlySupported() {
    return true
  }

  constructor({ data, config, api, readOnly }) {
    this.data = data || {}
    this.config = config || {}
    this.api = api
    this.readOnly = readOnly
    this._element = null
  }

  render() {
    const wrapper = document.createElement('div')
    wrapper.classList.add('video-tool')

    if (this.data.url) {
      this._renderVideo(wrapper)
    } else if (!this.readOnly) {
      this._renderUploadUI(wrapper)
    }

    this._element = wrapper
    return wrapper
  }

  _renderVideo(wrapper) {
    const videoWrapper = document.createElement('div')
    videoWrapper.classList.add('video-tool__video')

    if (this.data.widthPercent && this.data.widthPercent !== 100) {
      videoWrapper.style.width = this.data.widthPercent + '%'
    }

    const video = document.createElement('video')
    video.src = this.data.url
    video.controls = true
    video.style.width = '100%'
    video.style.display = 'block'

    videoWrapper.appendChild(video)
    wrapper.appendChild(videoWrapper)

    if (this.data.caption) {
      const caption = document.createElement('div')
      caption.classList.add('video-tool__caption')
      caption.textContent = this.data.caption
      wrapper.appendChild(caption)
    }
  }

  _renderUploadUI(wrapper) {
    const uploadArea = document.createElement('div')
    uploadArea.classList.add('video-tool__upload-area')

    uploadArea.innerHTML = `
      <div class="video-tool__upload-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="23 7 16 12 23 17 23 7"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
      </div>
      <span class="video-tool__upload-text">点击上传视频或拖入文件</span>
    `

    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'video/*'
    input.style.display = 'none'

    wrapper.appendChild(uploadArea)
    wrapper.appendChild(input)

    const textEl = () => uploadArea.querySelector('.video-tool__upload-text')

    const doUpload = async (file) => {
      if (!file || !file.type.startsWith('video/')) return
      textEl().textContent = '上传中...'
      uploadArea.classList.add('video-tool__upload-area--loading')

      try {
        if (this.config.uploader && this.config.uploader.uploadByFile) {
          const result = await this.config.uploader.uploadByFile(file)
          if (result && result.success && result.file && result.file.url) {
            this.data.url = result.file.url
            wrapper.innerHTML = ''
            this._renderVideo(wrapper)
          } else {
            textEl().textContent = '上传失败，请重试'
            uploadArea.classList.remove('video-tool__upload-area--loading')
          }
        }
      } catch (e) {
        textEl().textContent = '上传失败，请重试'
        uploadArea.classList.remove('video-tool__upload-area--loading')
      }
    }

    uploadArea.addEventListener('click', () => input.click())

    input.addEventListener('change', (e) => {
      const file = e.target.files[0]
      input.value = ''
      if (file) doUpload(file)
    })

    uploadArea.addEventListener('dragover', (e) => {
      e.preventDefault()
      uploadArea.classList.add('video-tool__upload-area--dragover')
    })

    uploadArea.addEventListener('dragleave', () => {
      uploadArea.classList.remove('video-tool__upload-area--dragover')
    })

    uploadArea.addEventListener('drop', (e) => {
      e.preventDefault()
      uploadArea.classList.remove('video-tool__upload-area--dragover')
      const file = e.dataTransfer.files[0]
      if (file) doUpload(file)
    })
  }

  save() {
    return {
      url: this.data.url || '',
      caption: this.data.caption || '',
      widthPercent: this.data.widthPercent || 100
    }
  }

  validate(savedData) {
    return !!(savedData && savedData.url && savedData.url.trim())
  }
}

export default VideoTool
