/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

// EditorJS 图片缩放辅助
// handle 放在 .image-tool__image 内部，自然跟随图片右边缘，不受 overflow 影响

export function createEditorImageResizer({ getEditor, getContainer, markDirtyAndSave }) {
  let observer = null
  let savedWidthMap = new Map() // imageIndex -> widthPercent
  let savedVideoWidthMap = new Map() // videoIndex -> widthPercent
  let savedEmbedWidthMap = new Map() // embedIndex -> widthPercent

  /**
   * 从 editorData 中提取 image/video block 的 widthPercent
   */
  const setInitialData = (editorData) => {
    savedWidthMap.clear()
    savedVideoWidthMap.clear()
    savedEmbedWidthMap.clear()
    if (!editorData || !editorData.blocks) {
      console.log('[ImageResize] setInitialData: no blocks')
      return
    }
    let imageIndex = 0
    let videoIndex = 0
    let embedIndex = 0
    editorData.blocks.forEach(block => {
      if (block.type === 'image') {
        console.log(`[ImageResize] setInitialData: image[${imageIndex}] widthPercent =`, block.data && block.data.widthPercent)
        if (block.data && block.data.widthPercent) {
          savedWidthMap.set(imageIndex, block.data.widthPercent)
        }
        imageIndex++
      } else if (block.type === 'video') {
        if (block.data && block.data.widthPercent) {
          savedVideoWidthMap.set(videoIndex, block.data.widthPercent)
        }
        videoIndex++
      } else if (block.type === 'embed') {
        if (block.data && block.data.widthPercent) {
          savedEmbedWidthMap.set(embedIndex, block.data.widthPercent)
        }
        embedIndex++
      }
    })
    console.log('[ImageResize] setInitialData done, savedWidthMap size =', savedWidthMap.size, ', entries:', [...savedWidthMap.entries()])
  }

  /**
   * 为 .image-tool__image 注入 resize handle
   * handle 在 imageContainer 内部，position: absolute; right: 0
   * 这样 handle 自然紧贴图片右边缘，缩放后也跟着走
   */
  const injectResizeHandle = (imageContainer) => {
    if (!imageContainer || imageContainer.querySelector('.image-resize-handle')) return

    const isFixed = imageContainer.getAttribute('data-fixed') === 'true'

    imageContainer.style.position = 'relative'

    // iframe 和 video 都会捕获鼠标事件并阻断 CSS :hover，需要特殊处理
    const hasIframe = !!imageContainer.querySelector('iframe')
    const hasVideo = !!imageContainer.querySelector('video')
    const hasBlockingMedia = hasIframe || hasVideo
    if (hasBlockingMedia) {
      if (!isFixed) {
        imageContainer.style.paddingRight = '10px'
        imageContainer.style.boxSizing = 'border-box'
      }
      // 确保外层 wrapper 用 flex 居中（不依赖 CSS 热重载）
      const outerWrapper = imageContainer.parentElement
      if (outerWrapper) {
        outerWrapper.style.display = 'flex'
        outerWrapper.style.flexDirection = 'column'
        outerWrapper.style.alignItems = 'center'
      }
    }

    if (isFixed) return

    const handle = document.createElement('div')
    handle.className = 'image-resize-handle'
    if (hasBlockingMedia) {
      // 媒体容器（iframe/video）：handle 样式全部用 inline，不依赖 CSS 文件
      handle.style.cssText = 'position:absolute;right:0;top:0;bottom:0;width:10px;cursor:col-resize;z-index:10'
    }
    imageContainer.appendChild(handle)

    // 找到外层 wrapper 用于计算百分比基准
    const wrapper = imageContainer.closest('.image-tool, .video-tool, .embed-video-tool')

    // 媒体容器（iframe/video）：用真实子元素替代 ::after，用 JS 事件替代 CSS :hover
    let mediaBar = null
    if (hasBlockingMedia) {
      mediaBar = document.createElement('div')
      mediaBar.style.cssText = 'position:absolute;right:1px;top:50%;transform:translateY(-50%);width:4px;height:36px;border-radius:2px;background:rgba(102,126,234,0.6);opacity:0;transition:opacity 0.2s;pointer-events:none'
      handle.appendChild(mediaBar)
      const hoverTarget = wrapper || imageContainer
      hoverTarget.addEventListener('mouseenter', () => { mediaBar.style.opacity = '1' })
      hoverTarget.addEventListener('mouseleave', () => {
        if (!handle.classList.contains('resizing')) mediaBar.style.opacity = '0'
      })
    }

    let startX = 0
    let startWidth = 0
    let blockContentWidth = 0
    let tooltip = null
    let iframeOverlay = null

    const onMouseDown = (e) => {
      e.preventDefault()
      e.stopPropagation()
      startX = e.clientX
      startWidth = imageContainer.offsetWidth
      const blockContent = wrapper ? wrapper.closest('.ce-block__content') : null
      blockContentWidth = (blockContent && blockContent.offsetWidth) ||
        (wrapper && wrapper.parentElement && wrapper.parentElement.offsetWidth) ||
        startWidth

      handle.classList.add('resizing')
      if (mediaBar) mediaBar.style.opacity = '1'

      // 覆盖透明遮罩，防止 iframe/video 在拖拽过程中截获 mousemove/mouseup
      if (hasBlockingMedia) {
        iframeOverlay = document.createElement('div')
        iframeOverlay.style.cssText = 'position:absolute;top:0;left:0;right:0;bottom:0;z-index:5;cursor:col-resize'
        imageContainer.appendChild(iframeOverlay)
      }

      // tooltip 放在 wrapper 上（避免被 overflow:hidden 裁剪）
      if (wrapper) {
        tooltip = document.createElement('div')
        tooltip.className = 'image-resize-tooltip'
        Object.assign(tooltip.style, {
          position: 'absolute', top: '-30px', right: '0',
          background: 'rgba(0,0,0,0.75)', color: '#fff',
          padding: '3px 10px', borderRadius: '4px',
          fontSize: '12px', whiteSpace: 'nowrap', pointerEvents: 'none'
        })
        wrapper.appendChild(tooltip)
      }

      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
    }

    const onMouseMove = (e) => {
      const dx = e.clientX - startX
      const newWidth = startWidth + dx
      if (!blockContentWidth) return
      const percent = Math.round((newWidth / blockContentWidth) * 100)
      const clampedPercent = Math.max(20, Math.min(100, percent))

      imageContainer.style.width = clampedPercent + '%'

      if (tooltip) {
        tooltip.textContent = clampedPercent + '%'
      }
    }

    const onMouseUp = () => {
      handle.classList.remove('resizing')
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''

      if (tooltip) {
        tooltip.remove()
        tooltip = null
      }

      if (iframeOverlay) {
        iframeOverlay.remove()
        iframeOverlay = null
      }

      if (mediaBar) mediaBar.style.opacity = '0'

      if (typeof markDirtyAndSave === 'function') {
        markDirtyAndSave()
      }
    }

    handle.addEventListener('mousedown', onMouseDown)
  }

  /**
   * 恢复单个图片元素的宽度（根据其在 DOM 中的 image index）
   */
  const restoreWidthForElement = (el) => {
    if (savedWidthMap.size === 0) return
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container) return
    const allImages = container.querySelectorAll('.image-tool .image-tool__image')
    const index = Array.from(allImages).indexOf(el)
    if (index >= 0) {
      const wp = savedWidthMap.get(index)
      if (wp) {
        el.style.width = wp + '%'
      }
    }
  }

  /**
   * 根据 savedWidthMap 恢复所有图片宽度
   */
  const restoreImageWidths = () => {
    const container = typeof getContainer === 'function' ? getContainer() : null
    console.log('[ImageResize] restoreImageWidths called, container =', !!container, ', savedWidthMap size =', savedWidthMap.size)
    if (!container || savedWidthMap.size === 0) return

    const imageContainers = container.querySelectorAll('.image-tool .image-tool__image')
    console.log('[ImageResize] restoreImageWidths: found', imageContainers.length, 'image containers in DOM')
    let imageIndex = 0
    imageContainers.forEach(el => {
      const wp = savedWidthMap.get(imageIndex)
      console.log(`[ImageResize] restoreImageWidths: image[${imageIndex}] wp=${wp}, current style.width="${el.style.width}"`)
      if (wp) {
        el.style.width = wp + '%'
        console.log(`[ImageResize] restoreImageWidths: set image[${imageIndex}] width to ${wp}%`)
      }
      imageIndex++
    })
  }

  const restoreWidthForEmbedElement = (el) => {
    if (savedEmbedWidthMap.size === 0) return
    if (el.getAttribute('data-fixed') === 'true') return
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container) return
    const allEmbeds = container.querySelectorAll('.embed-video-tool .embed-video-tool__frame')
    const index = Array.from(allEmbeds).indexOf(el)
    if (index >= 0) {
      const wp = savedEmbedWidthMap.get(index)
      if (wp) el.style.width = wp + '%'
    }
  }

  const restoreEmbedWidths = () => {
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container || savedEmbedWidthMap.size === 0) return
    const embedFrames = container.querySelectorAll('.embed-video-tool .embed-video-tool__frame')
    let embedIndex = 0
    embedFrames.forEach(el => {
      if (el.getAttribute('data-fixed') !== 'true') {
        const wp = savedEmbedWidthMap.get(embedIndex)
        if (wp) el.style.width = wp + '%'
      }
      embedIndex++
    })
  }

  const restoreWidthForVideoElement = (el) => {
    if (savedVideoWidthMap.size === 0) return
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container) return
    const allVideos = container.querySelectorAll('.video-tool .video-tool__video')
    const index = Array.from(allVideos).indexOf(el)
    if (index >= 0) {
      const wp = savedVideoWidthMap.get(index)
      if (wp) {
        el.style.width = wp + '%'
      }
    }
  }

  const restoreVideoWidths = () => {
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container || savedVideoWidthMap.size === 0) return
    const videoContainers = container.querySelectorAll('.video-tool .video-tool__video')
    let videoIndex = 0
    videoContainers.forEach(el => {
      const wp = savedVideoWidthMap.get(videoIndex)
      if (wp) {
        el.style.width = wp + '%'
      }
      videoIndex++
    })
  }

  /**
   * 初始化：为已有图片注入 handle，监听新增 image block
   */
  const setupImageResize = () => {
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container) return

    // 如果 savedWidthMap 为空，自动从 EditorJS 实例的配置数据中提取 widthPercent
    if (savedWidthMap.size === 0) {
      const editor = typeof getEditor === 'function' ? getEditor() : null
      const cfgData = editor && editor.configuration && editor.configuration.data
      console.log('[ImageResize] setupImageResize: savedWidthMap empty, trying editor.configuration.data =', !!cfgData)
      if (cfgData) {
        setInitialData(cfgData)
      }
    }

    if (observer) {
      observer.disconnect()
      observer = null
    }

    // 立即尝试恢复宽度（EditorJS isReady 后 DOM 通常已就绪）
    const tryInitImages = () => {
      const images = container.querySelectorAll('.image-tool__image')
      const videos = container.querySelectorAll('.video-tool__video')
      const embeds = container.querySelectorAll('.embed-video-tool__frame')
      if (images.length > 0 || videos.length > 0 || embeds.length > 0) {
        images.forEach(el => injectResizeHandle(el))
        restoreImageWidths()
        videos.forEach(el => injectResizeHandle(el))
        restoreVideoWidths()
        embeds.forEach(el => injectResizeHandle(el))
        restoreEmbedWidths()
        return true
      }
      return false
    }

    // 先立即尝试，如果 DOM 还没就绪则短延迟重试
    if (!tryInitImages()) {
      setTimeout(tryInitImages, 200)
    }

    observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeType !== 1) continue
          if (node.classList && node.classList.contains('image-tool__image')) {
            injectResizeHandle(node)
            restoreWidthForElement(node)
          }
          if (node.classList && node.classList.contains('video-tool__video')) {
            injectResizeHandle(node)
            restoreWidthForVideoElement(node)
          }
          if (node.classList && node.classList.contains('embed-video-tool__frame')) {
            injectResizeHandle(node)
            restoreWidthForEmbedElement(node)
          }
          // 也检查子节点
          const imgEls = node.querySelectorAll ? node.querySelectorAll('.image-tool__image') : []
          imgEls.forEach(el => {
            injectResizeHandle(el)
            restoreWidthForElement(el)
          })
          const vidEls = node.querySelectorAll ? node.querySelectorAll('.video-tool__video') : []
          vidEls.forEach(el => {
            injectResizeHandle(el)
            restoreWidthForVideoElement(el)
          })
          const embEls = node.querySelectorAll ? node.querySelectorAll('.embed-video-tool__frame') : []
          embEls.forEach(el => {
            injectResizeHandle(el)
            restoreWidthForEmbedElement(el)
          })
        }
      }
    })
    observer.observe(container, { childList: true, subtree: true })
  }

  /**
   * 保存前：将 DOM 宽度写回 block.data.widthPercent，同时更新 savedWidthMap
   */
  const syncWidthsToBlocks = (outputData) => {
    console.log('[ImageResize] syncWidthsToBlocks called, savedWidthMap entries:', [...savedWidthMap.entries()])
    if (!outputData || !outputData.blocks) return
    const container = typeof getContainer === 'function' ? getContainer() : null
    if (!container) return

    const imageContainers = container.querySelectorAll('.image-tool .image-tool__image')
    const videoContainers = container.querySelectorAll('.video-tool .video-tool__video')
    const embedContainers = container.querySelectorAll('.embed-video-tool .embed-video-tool__frame')
    let imageIndex = 0
    let videoIndex = 0
    let embedIndex = 0
    const newImageMap = new Map()
    const newVideoMap = new Map()
    const newEmbedMap = new Map()

    outputData.blocks.forEach((block) => {
      if (block.type === 'image') {
        const el = imageContainers[imageIndex]
        let wp = null
        if (el && el.style.width) {
          wp = parseInt(el.style.width, 10)
        }
        if (!wp && savedWidthMap.has(imageIndex)) {
          wp = savedWidthMap.get(imageIndex)
        }
        console.log(`[ImageResize] syncWidthsToBlocks: image[${imageIndex}] DOM width="${el && el.style.width}", fallback=${savedWidthMap.get(imageIndex)}, final wp=${wp}`)
        if (wp) {
          if (!block.data) block.data = {}
          block.data.widthPercent = wp
          newImageMap.set(imageIndex, wp)
        }
        imageIndex++
      } else if (block.type === 'video') {
        const el = videoContainers[videoIndex]
        let wp = null
        if (el && el.style.width) {
          wp = parseInt(el.style.width, 10)
        }
        if (!wp && savedVideoWidthMap.has(videoIndex)) {
          wp = savedVideoWidthMap.get(videoIndex)
        }
        if (wp) {
          if (!block.data) block.data = {}
          block.data.widthPercent = wp
          newVideoMap.set(videoIndex, wp)
        }
        videoIndex++
      } else if (block.type === 'embed') {
        const el = embedContainers[embedIndex]
        if (el && el.getAttribute('data-fixed') !== 'true') {
          let wp = null
          if (el.style.width) {
            wp = parseInt(el.style.width, 10)
          }
          if (!wp && savedEmbedWidthMap.has(embedIndex)) {
            wp = savedEmbedWidthMap.get(embedIndex)
          }
          if (wp) {
            if (!block.data) block.data = {}
            block.data.widthPercent = wp
            newEmbedMap.set(embedIndex, wp)
          }
        }
        embedIndex++
      }
    })
    savedWidthMap = newImageMap
    savedVideoWidthMap = newVideoMap
    savedEmbedWidthMap = newEmbedMap
  }

  const destroy = () => {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  }

  return {
    setInitialData,
    setupImageResize,
    syncWidthsToBlocks,
    destroy
  }
}
