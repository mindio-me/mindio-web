import ApiService from './api'

class LocalMediaService extends ApiService {
  getDirectories() {
    return this.get('/v1/local-media/directories')
  }

  addDirectory(data) {
    return this.post('/v1/local-media/directories', data)
  }

  deleteDirectory(id) {
    return this.delete(`/v1/local-media/directories/${id}`)
  }

  rescan(id) {
    return this.post(`/v1/local-media/directories/${id}/scan`)
  }

  listDirectory(path) {
    const params = path ? `?path=${encodeURIComponent(path)}` : ''
    return this.get(`/v1/local-docs/fs/list${params}`)
  }

  getFiles(dirId, { keyword, mediaType, page = 0, size = 40, sortBy = 'fileName', direction = 'ASC' } = {}) {
    const params = new URLSearchParams({ page, size, sortBy, direction })
    if (keyword) params.append('keyword', keyword)
    if (mediaType) params.append('mediaType', mediaType)
    return this.get(`/v1/local-media/directories/${dirId}/files?${params}`)
  }

  getAllFiles({ keyword, mediaType, page = 0, size = 40, sortBy = 'fileName', direction = 'ASC' } = {}) {
    const params = new URLSearchParams({ page, size, sortBy, direction })
    if (keyword) params.append('keyword', keyword)
    if (mediaType) params.append('mediaType', mediaType)
    return this.get(`/v1/local-media/files?${params}`)
  }

  thumbnailUrl(fileId, token) {
    const base = this.$axios.defaults.baseURL || ''
    return `${base}/v1/local-media/thumbnails/${fileId}?token=${encodeURIComponent(token)}`
  }

  fileUrl(fileId, token) {
    const base = this.$axios.defaults.baseURL || ''
    return `${base}/v1/local-media/files/${fileId}?token=${encodeURIComponent(token)}`
  }
}

export default LocalMediaService
