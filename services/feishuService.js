/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

export default class FeishuService {
  constructor(axios) {
    this.axios = axios
  }

  // 获取绑定状态
  getStatus() {
    return this.axios.$get('/v1/integrations/feishu/status')
  }

  // 获取 OAuth 授权 URL
  getOAuthUrl() {
    return this.axios.$get('/v1/integrations/feishu/oauth/url')
  }

  // 保存/更新飞书应用凭证（按用户）
  saveCredentials(payload) {
    // payload: { appId, appSecret }
    // 已迁移到 /v1/settings/integrations/feishu/credentials，仅保留兼容调用时的代理
    return this.axios.$post('/v1/settings/integrations/feishu/credentials', payload)
  }

  // 解绑
  disconnect() {
    return this.axios.$post('/v1/integrations/feishu/disconnect')
  }

  // 列出 Wiki spaces
  listSpaces() {
    return this.axios.$get('/v1/integrations/feishu/wiki/spaces')
  }

  // 列出某个 space 下的 nodes，parentNodeToken 为空时列出根节点
  listNodes(spaceId, parentNodeToken = null) {
    const params = { spaceId }
    if (parentNodeToken) params.parentNodeToken = parentNodeToken
    return this.axios.$get('/v1/integrations/feishu/wiki/nodes', { params })
  }

  // 导入单个页面
  importPage(payload) {
    // payload: { spaceId, nodeToken, nodeTitle, objType }
    return this.axios.$post('/v1/integrations/feishu/wiki/import', payload)
  }

  // ========== 云空间（我的文档资料库）相关方法 ==========

  // 列出云空间文件
  listDriveFiles(folderToken = null) {
    const params = folderToken ? { folderToken } : {}
    return this.axios.$get('/v1/integrations/feishu/drive/files', { params })
  }

  // 获取我的文档库根目录 folder token
  getMyDocsRoot() {
    return this.axios.$get('/v1/integrations/feishu/drive/my-docs-root')
  }

  // 导入云空间单个文档
  importDriveDocument(payload) {
    // payload: { fileToken, fileName, fileType, fileUrl }
    return this.axios.$post('/v1/integrations/feishu/drive/import', payload)
  }
}


