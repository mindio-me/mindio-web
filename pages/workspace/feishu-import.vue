<template>
  <div class="feishu-import-page">
    <!-- 顶部栏：logo + 用户信息 -->
    <!-- <div class="workspace-topbar">
      <div class="topbar-left">
        <div class="topbar-logo" @click="$router.push('/')">
          <i class="el-icon-notebook-2"></i>
          <span class="topbar-logo-text">MindIO</span>
        </div>
        <el-button type="text" class="topbar-home-link" @click="$router.push('/')">
          <i class="el-icon-s-home"></i> Homepage
        </el-button>
        <el-button type="text" class="topbar-home-link" @click="$router.push('/workspace/notes')">
          <i class="el-icon-document"></i> Workspace
        </el-button>
      </div>
      <div class="topbar-right">
        <el-dropdown @command="handleUserCommand">
          <span class="topbar-user">
            <i class="el-icon-user"></i>
            {{ userName }}
            <i class="el-icon-arrow-down"></i>
          </span>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="profile">
              <i class="el-icon-user"></i> Profile
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <i class="el-icon-setting"></i> Settings
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <i class="el-icon-switch-button"></i> Logout
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
    </div> -->

    <div class="feishu-container">
      <div class="feishu-header">
        <h1 class="feishu-title">飞书文档导入</h1>
        <p class="feishu-subtitle">从飞书知识库或我的文档导入为本站笔记（markdown）</p>
        <div class="header-status">
          <el-tag v-if="status.bound" type="success" size="small">
            已绑定
          </el-tag>
          <el-tag v-else type="info" size="small">
            未绑定
          </el-tag>
        </div>
      </div>

      <el-card class="feishu-card" v-loading="loading" shadow="never">

      <!-- 绑定区域 -->
      <div class="bind-section">
        <template v-if="!status.bound">
          <p class="tip">
            需要先绑定飞书账号，系统会跳转到飞书进行授权，成功后回到本页面即可开始选择文档导入。
          </p>
          <el-button type="primary" icon="el-icon-link" @click="startOAuth" :loading="bindBtnLoading">
            绑定飞书账号
          </el-button>
        </template>
        <template v-else>
          <p class="tip">
            飞书账号已绑定，可直接选择文档进行导入。
          </p>
          <el-button type="danger" icon="el-icon-unlink" plain @click="disconnect" :loading="bindBtnLoading">
            解绑飞书
          </el-button>
        </template>
      </div>

      <el-divider></el-divider>

      <!-- 文档来源选择器 -->
      <div class="source-section" v-if="status.bound">
        <div class="source-select">
          <span class="label">文档来源：</span>
          <el-radio-group v-model="docSource" @change="onSourceChange">
            <el-radio-button label="wiki">知识库（Wiki）</el-radio-button>
            <el-radio-button label="mydocs">我的文档库</el-radio-button>
            <el-radio-button label="drive">云盘</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- Wiki 模式：选择 space + node -->
      <div class="selector-section" v-if="status.bound && docSource === 'wiki'">
        <div class="space-select">
          <span class="label">选择 Wiki 空间：</span>
          <el-select
            v-model="selectedSpaceId"
            placeholder="请选择空间"
            filterable
            @change="onSpaceChange"
            style="min-width: 260px;"
          >
            <el-option
              v-for="space in spaces"
              :key="space.spaceId"
              :label="space.name"
              :value="space.spaceId"
            />
          </el-select>
        </div>

        <div class="nodes-wrap" v-if="selectedSpaceId">
          <div class="nodes-header">
            <span class="label">选择要导入的页面：</span>
          </div>
          <!-- 面包屑导航 -->
          <div class="node-breadcrumb" v-if="nodePath.length > 0">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item>
                <a href="javascript:void(0)" @click="navigateToNodePath(-1)">根目录</a>
              </el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, idx) in nodePath" :key="item.nodeToken">
                <a v-if="idx < nodePath.length - 1" href="javascript:void(0)" @click="navigateToNodePath(idx)">{{ item.title }}</a>
                <span v-else>{{ item.title }}</span>
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <!-- 批量操作工具栏 -->
          <div class="batch-toolbar" v-if="selectedNodes.length > 0 || batchImporting">
            <span class="selected-count">已选 {{ selectedNodes.length }} 篇文档</span>
            <el-button
              type="primary"
              size="small"
              :loading="batchImporting"
              :disabled="selectedNodes.length === 0"
              @click="batchImport"
            >
              <i class="el-icon-download" v-if="!batchImporting"></i>
              批量导入{{ batchImporting ? `（${batchProgress.current}/${batchProgress.total}）` : '' }}
            </el-button>
          </div>
          <el-table
            :data="nodes"
            size="small"
            border
            height="420"
            v-loading="nodesLoading"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="40" :selectable="isSelectableNode" />
            <el-table-column
              label="标题"
              min-width="260"
            >
              <template slot-scope="scope">
                <span class="node-title-cell">
                  <i :class="isDirectoryNode(scope.row) ? 'el-icon-folder node-icon-dir' : 'el-icon-document node-icon-doc'" class="node-type-icon"></i>
                  <span v-if="scope.row.title">{{ scope.row.title }}</span>
                  <span v-else style="color: #909399;">未命名文档</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column
              label="类型"
              width="100"
            >
              <template slot-scope="scope">
                <el-tag size="mini" :type="getNodeTypeTagType(scope.row)" effect="plain">
                  {{ getNodeTypeLabel(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="100">
              <template slot-scope="scope">
                <span class="time-cell">{{ formatNodeTime(scope.row.objCreateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最近修改" width="100">
              <template slot-scope="scope">
                <span class="time-cell">{{ formatNodeTime(scope.row.objEditTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="130"
              fixed="right"
            >
              <template slot-scope="scope">
                <el-button
                  v-if="isDirectoryNode(scope.row)"
                  type="text"
                  size="mini"
                  @click="enterDirectory(scope.row)"
                >
                  <i class="el-icon-arrow-right"></i> 进入
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="mini"
                  @click="importOne(scope.row)"
                  :loading="importingId === scope.row.nodeToken"
                >
                  导入为笔记
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 我的文档库模式：wiki 节点列表（my_library space） -->
      <div class="selector-section" v-if="status.bound && docSource === 'mydocs'">
        <div class="nodes-wrap">
          <div class="nodes-header">
            <span class="label">我的文档库：</span>
            <span class="hint">飞书个人创建的文档</span>
          </div>
          <!-- 面包屑导航 -->
          <div class="node-breadcrumb" v-if="nodePath.length > 0">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item>
                <a href="javascript:void(0)" @click="navigateToNodePath(-1)">根目录</a>
              </el-breadcrumb-item>
              <el-breadcrumb-item v-for="(item, idx) in nodePath" :key="item.nodeToken">
                <a v-if="idx < nodePath.length - 1" href="javascript:void(0)" @click="navigateToNodePath(idx)">{{ item.title }}</a>
                <span v-else>{{ item.title }}</span>
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <!-- 批量操作工具栏 -->
          <div class="batch-toolbar" v-if="selectedNodes.length > 0 || batchImporting">
            <span class="selected-count">已选 {{ selectedNodes.length }} 篇文档</span>
            <el-button
              type="primary"
              size="small"
              :loading="batchImporting"
              :disabled="selectedNodes.length === 0"
              @click="batchImport"
            >
              <i class="el-icon-download" v-if="!batchImporting"></i>
              批量导入{{ batchImporting ? `（${batchProgress.current}/${batchProgress.total}）` : '' }}
            </el-button>
          </div>
          <el-table
            :data="nodes"
            size="small"
            border
            height="420"
            v-loading="nodesLoading"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="40" :selectable="isSelectableNode" />
            <el-table-column label="标题" min-width="260">
              <template slot-scope="scope">
                <span class="node-title-cell">
                  <i :class="isDirectoryNode(scope.row) ? 'el-icon-folder node-icon-dir' : 'el-icon-document node-icon-doc'" class="node-type-icon"></i>
                  <span v-if="scope.row.title">{{ scope.row.title }}</span>
                  <span v-else style="color: #909399;">未命名文档</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="getNodeTypeTagType(scope.row)" effect="plain">
                  {{ getNodeTypeLabel(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="100">
              <template slot-scope="scope">
                <span class="time-cell">{{ formatNodeTime(scope.row.objCreateTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最近修改" width="100">
              <template slot-scope="scope">
                <span class="time-cell">{{ formatNodeTime(scope.row.objEditTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-if="isDirectoryNode(scope.row)"
                  type="text"
                  size="mini"
                  @click="enterDirectory(scope.row)"
                >
                  <i class="el-icon-arrow-right"></i> 进入
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="mini"
                  @click="importOne(scope.row)"
                  :loading="importingId === scope.row.nodeToken"
                >
                  导入为笔记
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 云盘模式：文件夹导航 -->
      <div class="selector-section" v-if="status.bound && docSource === 'drive'">
        <!-- 面包屑导航 -->
        <div class="breadcrumb-nav">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>
              <a href="javascript:void(0)" @click="navigateToFolder(null)">
                <i class="el-icon-folder"></i> 根目录
              </a>
            </el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="(crumb, index) in folderPath"
              :key="crumb.token"
            >
              <a
                v-if="index < folderPath.length - 1"
                href="javascript:void(0)"
                @click="navigateToFolder(crumb.token, index)"
              >
                {{ crumb.name }}
              </a>
              <span v-else>{{ crumb.name }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="nodes-wrap">
          <div class="nodes-header">
            <span class="label">文件列表：</span>
            <span class="hint">点击文件夹进入，点击文档可导入</span>
          </div>
          <el-table
            :data="driveFiles"
            size="small"
            border
            height="420"
            v-loading="driveLoading"
            @row-click="onDriveRowClick"
            :row-class-name="getDriveRowClassName"
          >
            <el-table-column
              label="名称"
              min-width="300"
            >
              <template slot-scope="scope">
                <div class="file-name-cell">
                  <i :class="getFileIcon(scope.row)" class="file-icon"></i>
                  <span v-if="scope.row.name">{{ scope.row.name }}</span>
                  <span v-else style="color: #909399;">未命名文档</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="type"
              label="类型"
              width="100"
            >
              <template slot-scope="scope">
                <el-tag size="mini" effect="plain" :type="getFileTypeTagType(scope.row)">
                  {{ getFileTypeLabel(scope.row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="150"
              fixed="right"
            >
              <template slot-scope="scope">
                <el-button
                  v-if="scope.row.isFolder"
                  type="text"
                  size="mini"
                  @click.stop="navigateToFolder(scope.row.token, null, scope.row.name)"
                >
                  <i class="el-icon-folder-opened"></i> 进入
                </el-button>
                <el-button
                  v-else-if="scope.row.canImport"
                  type="primary"
                  size="mini"
                  @click.stop="importDriveFile(scope.row)"
                  :loading="importingId === scope.row.token"
                >
                  导入为笔记
                </el-button>
                <span v-else class="cannot-import">不支持导入</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      </el-card>
    </div>
  </div>
</template>

<script>
export default {
  layout: 'workspace',
  middleware: ['auth'],
  computed: {
    userName() {
      return this.$auth.user?.username || 'User'
    }
  },
  data () {
    return {
      loading: false,
      bindBtnLoading: false,
      nodesLoading: false,
      driveLoading: false,
      importingId: null,
      status: {
        bound: false
      },
      // 文档来源：wiki / mydocs / drive
      docSource: 'wiki',
      // Wiki 相关
      spaces: [],
      selectedSpaceId: null,
      nodes: [],
      nodePath: [], // wiki/mydocs 目录导航面包屑 [{ nodeToken, title }, ...]
      selectedNodes: [], // 批量导入选中列表
      batchImporting: false,
      batchProgress: { current: 0, total: 0 },
      // 云空间相关
      driveFiles: [],
      currentFolderToken: null,
      folderPath: [] // 面包屑路径 [{ token, name }, ...]
    }
  },
  async mounted () {
    await this.refreshStatus()
    if (this.status.bound) {
      await this.loadSpaces()
    }
  },
  methods: {
    async refreshStatus () {
      this.loading = true
      try {
        const res = await this.$feishuService.getStatus()
        this.status = res
      } catch (e) {
        this.$message.error('获取飞书绑定状态失败')
      } finally {
        this.loading = false
      }
    },
    async startOAuth () {
      this.bindBtnLoading = true
      try {
        const status = await this.$feishuService.getStatus()
        if (!status.configured) {
          this.$message.warning('请先配置飞书 App ID / App Secret（可在工作台点击"导入飞书文档"时填写，或在个人信息设置中预先设置）')
          return
        }
        const res = await this.$feishuService.getOAuthUrl()
        if (res && res.url) {
          window.location.href = res.url
        } else {
          this.$message.error('未获取到授权地址')
        }
      } catch (e) {
        this.$message.error('请求飞书授权地址失败')
      } finally {
        this.bindBtnLoading = false
      }
    },
    async disconnect () {
      this.$confirm('确认要解绑飞书账号吗？', '提示', {
        type: 'warning'
      }).then(async () => {
        this.bindBtnLoading = true
        try {
          await this.$feishuService.disconnect()
          this.$message.success('已解绑飞书账号')
          this.status.bound = false
          this.spaces = []
          this.selectedSpaceId = null
          this.nodes = []
          this.driveFiles = []
          this.folderPath = []
        } catch (e) {
          this.$message.error('解绑失败')
        } finally {
          this.bindBtnLoading = false
        }
      }).catch(() => {})
    },
    // 切换文档来源
    async onSourceChange (source) {
      if (source === 'wiki') {
        if (this.spaces.length === 0) {
          await this.loadSpaces()
        }
      } else if (source === 'mydocs') {
        // 我的文档库是飞书 Wiki 体系下的个人空间，space_id 固定为 "my_library"
        this.nodePath = []
        await this.loadNodes('my_library', null)
      } else if (source === 'drive') {
        this.currentFolderToken = null
        this.folderPath = []
        await this.loadDriveFiles(null)
      }
    },
    // ========== Wiki 相关方法 ==========
    async loadSpaces () {
      try {
        const spaces = await this.$feishuService.listSpaces()
        this.spaces = spaces || []
      } catch (e) {
        this.$message.error('加载 Wiki 空间失败')
      }
    },
    async onSpaceChange (spaceId) {
      this.nodes = []
      this.nodePath = []
      if (!spaceId) return
      await this.loadNodes(spaceId, null)
    },
    async loadNodes (spaceId, parentNodeToken) {
      this.nodesLoading = true
      this.selectedNodes = []
      try {
        const nodes = await this.$feishuService.listNodes(spaceId, parentNodeToken)
        this.nodes = nodes || []
      } catch (e) {
        this.$message.error('加载页面列表失败')
      } finally {
        this.nodesLoading = false
      }
    },
    async enterDirectory (node) {
      const spaceId = this.docSource === 'mydocs' ? 'my_library' : this.selectedSpaceId
      this.nodePath.push({ nodeToken: node.nodeToken, title: node.title || '未命名文档' })
      await this.loadNodes(spaceId, node.nodeToken)
    },
    async navigateToNodePath (index) {
      const spaceId = this.docSource === 'mydocs' ? 'my_library' : this.selectedSpaceId
      if (index === -1) {
        this.nodePath = []
        await this.loadNodes(spaceId, null)
      } else {
        this.nodePath = this.nodePath.slice(0, index + 1)
        await this.loadNodes(spaceId, this.nodePath[this.nodePath.length - 1].nodeToken)
      }
    },
    async importOne (node) {
      this.importingId = node.nodeToken
      const spaceId = this.docSource === 'mydocs' ? 'my_library' : this.selectedSpaceId
      try {
        const res = await this.$feishuService.importPage({
          spaceId,
          nodeToken: node.nodeToken,
          objToken: node.objToken,
          nodeTitle: node.title,
          objType: node.objType,
          objCreateTime: node.objCreateTime,
          objEditTime: node.objEditTime
        })
        if (res && res.noteId) {
          this.$message.success('导入成功，已创建笔记')
          this.$router.push({ path: `/workspace/notes/${res.noteId}` })
        } else {
          this.$message.success('导入成功')
        }
      } catch (e) {
        this.$message.error('导入失败')
      } finally {
        this.importingId = null
      }
    },
    // ========== 云空间相关方法 ==========
    async loadDriveFiles (folderToken) {
      this.driveLoading = true
      try {
        const files = await this.$feishuService.listDriveFiles(folderToken)
        this.driveFiles = files || []
      } catch (e) {
        this.$message.error('加载文件列表失败')
      } finally {
        this.driveLoading = false
      }
    },
    async navigateToFolder (folderToken, breadcrumbIndex = null, folderName = null) {
      // 如果点击面包屑，截断路径
      if (breadcrumbIndex !== null) {
        this.folderPath = this.folderPath.slice(0, breadcrumbIndex + 1)
      } else if (folderToken === null) {
        // 回到根目录
        this.folderPath = []
      } else if (folderName) {
        // 进入新文件夹
        this.folderPath.push({ token: folderToken, name: folderName })
      }

      this.currentFolderToken = folderToken
      await this.loadDriveFiles(folderToken)
    },
    onDriveRowClick (row) {
      if (row.isFolder) {
        this.navigateToFolder(row.token, null, row.name)
      }
    },
    async importDriveFile (file) {
      this.importingId = file.token
      try {
        const res = await this.$feishuService.importDriveDocument({
          fileToken: file.token,
          fileName: file.name,
          fileType: file.type,
          fileUrl: file.url,
          createdTime: file.createdTime,
          modifiedTime: file.modifiedTime
        })
        if (res && res.noteId) {
          this.$message.success('导入成功，已创建笔记')
          this.$router.push({ path: `/workspace/notes/${res.noteId}` })
        } else {
          this.$message.success('导入成功')
        }
      } catch (e) {
        this.$message.error('导入失败：' + (e.response?.data?.message || e.message))
      } finally {
        this.importingId = null
      }
    },
    // ========== 批量导入 ==========
    onSelectionChange (selection) {
      this.selectedNodes = selection
    },
    isSelectableNode (row) {
      return !this.isDirectoryNode(row)
    },
    async batchImport () {
      if (this.selectedNodes.length === 0) return
      const spaceId = this.docSource === 'mydocs' ? 'my_library' : this.selectedSpaceId
      const nodes = [...this.selectedNodes]
      this.batchImporting = true
      this.batchProgress = { current: 0, total: nodes.length }
      let successCount = 0
      let failCount = 0
      for (const node of nodes) {
        try {
          await this.$feishuService.importPage({
            spaceId,
            nodeToken: node.nodeToken,
            objToken: node.objToken,
            nodeTitle: node.title,
            objType: node.objType,
            objCreateTime: node.objCreateTime,
            objEditTime: node.objEditTime
          })
          successCount++
        } catch (e) {
          failCount++
        }
        this.batchProgress.current++
      }
      this.batchImporting = false
      this.selectedNodes = []
      if (failCount === 0) {
        this.$message.success(`批量导入完成，共导入 ${successCount} 篇笔记`)
      } else {
        this.$message.warning(`批量导入完成：${successCount} 篇成功，${failCount} 篇失败`)
      }
    },
    // ========== Wiki 节点类型辅助 ==========
    formatNodeTime (ts) {
      if (!ts) return '-'
      const d = new Date(Number(ts) * 1000)
      if (isNaN(d.getTime())) return '-'
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },
    isDirectoryNode (node) {
      return ['catalog', 'shortcut'].includes(node.objType) || node.hasChild
    },
    getNodeTypeLabel (node) {
      switch (node.objType) {
        case 'docx':     return '文档'
        case 'doc':      return '旧版文档'
        case 'catalog':  return '目录'
        case 'shortcut': return '快捷方式'
        default:         return node.objType || '未知'
      }
    },
    getNodeTypeTagType (node) {
      if (this.isDirectoryNode(node)) return 'info'
      if (node.objType === 'docx' || node.objType === 'doc') return 'success'
      return ''
    },
    // 文件图标
    getFileIcon (file) {
      if (file.isFolder) return 'el-icon-folder'
      switch (file.type) {
        case 'docx':
        case 'doc':
          return 'el-icon-document'
        case 'sheet':
          return 'el-icon-s-grid'
        case 'bitable':
          return 'el-icon-s-data'
        case 'mindnote':
          return 'el-icon-share'
        default:
          return 'el-icon-document'
      }
    },
    getFileTypeLabel (file) {
      if (file.isFolder) return '文件夹'
      switch (file.type) {
        case 'docx': return '文档'
        case 'doc': return '旧文档'
        case 'sheet': return '表格'
        case 'bitable': return '多维表格'
        case 'mindnote': return '思维笔记'
        default: return file.type || '未知'
      }
    },
    getFileTypeTagType (file) {
      if (file.isFolder) return 'info'
      if (file.canImport) return 'success'
      return ''
    },
    getDriveRowClassName ({ row }) {
      if (row.isFolder) return 'folder-row'
      if (row.canImport) return 'importable-row'
      return 'disabled-row'
    },
    handleUserCommand(command) {
      if (command === 'logout') {
        this.$confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.$auth.logout()
          this.$message.success('已退出登录')
        }).catch(() => {})
      } else if (command === 'profile') {
        this.$router.push('/workspace/profile')
      } else if (command === 'settings') {
        this.$router.push('/workspace/settings')
      }
    }
  }
}
</script>

<style scoped lang="scss">
.feishu-import-page {
  min-height: 100vh;
  background: var(--bg-secondary);
  padding: 12px 20px;
}

// 顶部栏
.workspace-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 0 2px;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #667eea;
  cursor: pointer;

  i { font-size: 22px; }
}

.topbar-home-link {
  font-size: 13px;
  color: var(--text-muted) !important;
  padding: 4px 8px;

  &:hover { color: #667eea !important; }
}

.topbar-right {
  .topbar-user {
    cursor: pointer;
    color: var(--text-secondary);
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;

    &:hover { color: #667eea; }
  }
}

.feishu-container {
  max-width: 1000px;
  margin: 0 auto;
}

.feishu-header {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
  position: relative;
}

.feishu-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 8px 0;
}

.feishu-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.header-status {
  position: absolute;
  top: 0;
  right: 0;
}

.feishu-card {
  background: var(--card-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.bind-section {
  margin-bottom: 16px;
}

.bind-section .tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
}

// 文档来源选择器
.source-section {
  margin-bottom: 20px;
}

.source-select {
  display: flex;
  align-items: center;
}

.source-select .label {
  margin-right: 12px;
  font-size: 13px;
  color: #606266;
}

.selector-section {
  margin-top: 8px;
}

.space-select {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

.space-select .label {
  margin-right: 8px;
  font-size: 13px;
  color: #606266;
}

// 面包屑导航
.breadcrumb-nav {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-radius: 8px;

  a {
    color: #667eea;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.nodes-wrap {
  margin-top: 4px;
}

.nodes-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.nodes-header .label {
  font-size: 13px;
  color: #606266;
}

.nodes-header .hint {
  font-size: 12px;
  color: var(--text-muted);
}

// 文件名单元格
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .file-icon {
    font-size: 16px;
    color: #909399;
  }
}

// 行样式
::v-deep .folder-row {
  cursor: pointer;

  &:hover {
    background-color: #ecf5ff !important;
  }

  .file-icon {
    color: #e6a23c;
  }
}

::v-deep .importable-row {
  .file-icon {
    color: #409eff;
  }
}

::v-deep .disabled-row {
  color: #c0c4cc;

  .file-icon {
    color: #c0c4cc;
  }
}

.cannot-import {
  font-size: 12px;
  color: #c0c4cc;
}

// Wiki / 我的文档库节点列表样式
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 6px;
  border: 1px solid #d9ecff;
}

.selected-count {
  font-size: 13px;
  color: #409eff;
}

.node-breadcrumb {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: var(--bg-secondary);
  border-radius: 6px;

  a {
    color: #667eea;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}

.node-title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-type-icon {
  font-size: 15px;
  flex-shrink: 0;

  &.node-icon-dir {
    color: #e6a23c;
  }

  &.node-icon-doc {
    color: #909399;
  }
}

.is-directory-hint {
  font-size: 12px;
  color: #c0c4cc;
}

.time-cell {
  font-size: 12px;
  color: var(--text-muted);
}

@media screen and (max-width: 768px) {
  .feishu-import-page {
    padding: 8px;
  }

  .workspace-topbar {
    .topbar-logo-text { display: none; }
  }

  .feishu-title {
    font-size: 24px;
  }

  .header-status {
    position: static;
    margin-top: 12px;
  }
}
</style>
