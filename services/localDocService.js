import ApiService from './api'

class LocalDocService extends ApiService {
  getDirectories() {
    return this.get('/v1/local-docs/directories')
  }

  addDirectory(data) {
    return this.post('/v1/local-docs/directories', data)
  }

  deleteDirectory(id) {
    return this.delete(`/v1/local-docs/directories/${id}`)
  }

  rescan(id) {
    return this.post(`/v1/local-docs/directories/${id}/scan`)
  }

  listDirectory(path) {
    const params = path ? `?path=${encodeURIComponent(path)}` : ''
    return this.get(`/v1/local-docs/fs/list${params}`)
  }

  getDocuments(dirId, { keyword, fileType, page = 0, size = 20, sortBy = 'fileName', direction = 'ASC' } = {}) {
    const params = new URLSearchParams({ page, size, sortBy, direction })
    if (keyword) params.append('keyword', keyword)
    if (fileType) params.append('fileType', fileType)
    return this.get(`/v1/local-docs/directories/${dirId}/documents?${params}`)
  }

  getAllDocuments({ keyword, fileType, page = 0, size = 20, sortBy = 'fileName', direction = 'ASC' } = {}) {
    const params = new URLSearchParams({ page, size, sortBy, direction })
    if (keyword) params.append('keyword', keyword)
    if (fileType) params.append('fileType', fileType)
    return this.get(`/v1/local-docs/documents?${params}`)
  }
}

export default LocalDocService
