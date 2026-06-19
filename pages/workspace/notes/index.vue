<template>
  <div class="notes-page">
    <div 
      class="workspace-layout" 
      :class="{ 'right-collapsed': rightPanelCollapsed }"
      :style="{ height: workspaceHeight }"
    >
      <!-- ========== 左侧列表 ========== -->
      <aside class="workspace-sidebar">
        <!-- 视图模式切换 -->
        <div class="sidebar-view-toggle">
          <button
            class="view-toggle-btn"
            :class="{ active: viewMode === 'list' }"
            @click="switchToListMode"
            title="列表视图"
          >
            <i class="el-icon-menu"></i>
          </button>
          <button
            class="view-toggle-btn"
            :class="{ active: viewMode === 'calendar' }"
            @click="switchToCalendarMode"
            title="月历视图"
          >
            <i class="el-icon-date"></i>
          </button>
        </div>
        <!-- 笔记列表 -->
          <div v-if="viewMode === 'list'" class="sidebar-section">
            <div class="sidebar-search">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索笔记标题"
                prefix-icon="el-icon-search"
                clearable
                size="small"
                @clear="handleSearch"
                @keyup.enter.native="handleSearch"
              />
            </div>
            <div class="sidebar-filters">
              <div class="filter-header">
                <span class="filter-title filter-toggle" @click="toggleSidebarSection('tags')">
                  <i class="el-icon-arrow-right filter-arrow" :class="{ 'filter-arrow--collapsed': tagsCollapsed }"></i>
                  {{ $t('workspace.notes.tags') }}
                </span>
                <el-button type="text" size="mini" @click="selectedTags = []; handleTagFilter()">清除</el-button>
              </div>
              <div v-show="!tagsCollapsed" class="filter-tags">
                <el-tag
                  v-for="tag in tags"
                  :key="tag.id"
                  size="mini"
                  :type="selectedTags.includes(tag.id) ? 'primary' : 'info'"
                  effect="plain"
                  class="filter-tag-item"
                  @click="toggleTag(tag.id)"
                >{{ tag.name }}</el-tag>
              </div>
            </div>
            <div class="sidebar-filters">
              <div class="filter-header">
                <span class="filter-title filter-toggle" @click="toggleSidebarSection('projects')">
                  <i class="el-icon-arrow-right filter-arrow" :class="{ 'filter-arrow--collapsed': projectsCollapsed }"></i>
                  {{ $t('workspace.notes.projects') }}
                </span>
                <el-button type="text" size="mini" @click="selectedProjects = []; handleProjectFilter()">清除</el-button>
              </div>
              <div v-show="!projectsCollapsed" class="filter-tags">
                <el-tag
                  v-for="project in projects"
                  :key="project.id"
                  size="mini"
                  :type="selectedProjects.includes(project.id) ? 'primary' : 'info'"
                  effect="plain"
                  class="filter-tag-item"
                  @click="toggleProject(project.id)"
                >
                  <i v-if="project.icon" :class="project.icon" style="margin-right: 4px;"></i>
                  {{ project.shortName || project.name }}
                </el-tag>
              </div>
            </div>
          </div>
          <div v-if="viewMode === 'list'" class="sidebar-section sidebar-notes">
            <div class="sidebar-section-header">
              <span class="section-title">{{ $t('workspace.notes.allNotes') }}</span>
              <span class="section-subtitle">{{ total }} 条</span>
            </div>
            <div v-loading="loading" class="note-list-wrapper">
              <div v-if="notes.length > 0" class="note-list">
                <div
                  v-for="note in notes"
                  :key="note.id"
                  class="note-list-item"
                  :class="{ active: note.id === activeNoteId }"
                  @click="selectNote(note)"
                >
                  <div class="note-list-title">
                    {{ note.title || '未命名笔记' }}
                    <span v-if="note.language === 'en'" class="note-lang-badge">EN</span>
                  </div>
                  <div class="note-list-meta">
                    <span class="note-list-time">{{ formatTime(note.createdAt) }}</span>
                    <span v-if="note.tags && note.tags.length > 0" class="note-list-tag">{{ note.tags[0].name }}</span>
                  </div>
                </div>
              </div>
              <div v-else-if="!loading" class="sidebar-empty">
                <p>暂无笔记</p>
                <el-button type="primary" size="mini" @click="showCreateNoteDialog">立即创建</el-button>
              </div>
            </div>
            <div v-if="total > pageSize" class="sidebar-pagination">
              <el-pagination
                :current-page="page + 1"
                :page-size="pageSize"
                :total="total"
                :pager-count="5"
                layout="prev, pager, next"
                small
                @current-change="handlePageChange"
              />
            </div>
          </div>
          <!-- 月历视图 -->
          <div v-if="viewMode === 'calendar'" class="sidebar-section sidebar-calendar">
            <NotesCalendar
              :notes-by-date="notesByDate"
              :selected-date="selectedDate"
              :current-date="currentDate"
              storage-key="workspace-calendar-scroll"
              @date-selected="selectDate"
              @month-header-click="openMonthCalendar"
            />
          </div>
      </aside>

      <!-- ========== 中间编辑区 ========== -->
      <main class="workspace-main" :class="{ 'workspace-main--fullscreen': isFullscreen }">
        <!-- 大月カレンダー -->
        <div v-if="showMonthCalendar" key="month-calendar" class="month-calendar-view">
          <div class="month-calendar-header">
            <div class="month-calendar-left">
              <span class="cal-month-title">{{ formatCalendarMonthTitle(calendarMonth) }}</span>
              <div class="cal-nav-group">
                <button class="cal-nav-btn" title="上个月" @click="navigateCalendarMonth(-1)">
                  <i class="el-icon-arrow-left"></i>
                </button>
                <button class="cal-nav-btn" title="下个月" @click="navigateCalendarMonth(1)">
                  <i class="el-icon-arrow-right"></i>
                </button>
              </div>
            </div>
            <button class="cal-close-btn" title="关闭" @click="closeMonthCalendar()">
              <i class="el-icon-close"></i>
            </button>
          </div>
          <div class="cal-grid-container">
            <div class="cal-weekdays">
              <div v-for="wd in ['一','二','三','四','五','六','日']" :key="wd" class="cal-weekday">{{ wd }}</div>
            </div>
            <div class="cal-grid">
              <div
                v-for="(dayObj, idx) in calendarDays"
                :key="idx"
                class="cal-day"
                :class="{
                  'cal-day-empty': !dayObj,
                  'cal-day-weekend': idx % 7 >= 5
                }"
              >
                <template v-if="dayObj">
                  <div class="cal-day-header">
                    <span class="cal-day-num" :class="{ 'cal-day-num--today': dayObj.isToday }">{{ dayObj.day }}</span>
                  </div>
                  <div class="cal-day-notes">
                    <div
                      v-for="note in dayObj.notes"
                      :key="note.id"
                      class="cal-note-chip"
                      @click="openNoteFromCalendar(note)"
                    >
                      {{ note.title || '未命名笔记' }}
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
        <!-- 笔记编辑 -->
          <div v-if="activeNote && !showMonthCalendar" key="note-editor" class="note-main">
            <div v-if="calendarFromMonth" class="calendar-breadcrumb" @click="returnToCalendar()">
              ← 返回 {{ formatCalendarMonthTitle(calendarFromMonth) }} 
            </div>
            <div class="note-main-header">
              <div class="note-main-title-wrapper" :class="{ 'note-main-title-wrapper--editorjs': isEditorjsNote }">
                <el-input
                  :value="displayTitle"
                  placeholder="请输入标题"
                  class="note-main-title-input"
                  @input="onTitleInput"
                />
                <div v-if="activeNote.feishuSourceUrl" class="note-main-source">
                  <span>来源：飞书 · </span>
                  <a
                    :href="activeNote.feishuSourceUrl"
                    target="_blank"
                    rel="noopener"
                    class="note-main-source-link"
                  >打开源文档</a>
                </div>
                <div class="note-main-meta">
                  <span>{{ $t('workspace.notes.lastUpdated') }}：{{ formatTime(activeNote.modifiedAt || activeNote.createdAt) }}</span>
                  <span v-if="activeNote.isPublic"> · {{ $t('workspace.notes.public') }}</span>
                  <span v-else> · {{ $t('workspace.notes.private') }}</span>
                  <span v-if="contentTypeLabel"> · {{ contentTypeLabel }}</span>
                  <!-- 临时调试：显示 contentType 值 -->
                  <!-- <span v-if="activeNote.contentType"> · [调试: {{ activeNote.contentType }}, label: {{ contentTypeLabel }}]</span> -->
                  <span v-if="isEditorjsNote" class="save-status-inline">
                    · <i :class="saveStatus.icon"></i> {{ saveStatus.text }}
                </span>
                  <span v-if="isEditorjsNote && wechatPublished" class="wechat-published-inline">
                    ·
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="#07c160" style="vertical-align:-2px;margin-right:2px;"><path d="M8.7 10.6c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zm5.1 0c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zM12 2C6.48 2 2 6.03 2 11c0 2.7 1.24 5.12 3.2 6.8L4 22l4.83-1.61A10.63 10.63 0 0012 21c5.52 0 10-4.03 10-9S17.52 2 12 2z"/></svg>
                    已发布
                  </span>
                </div>
              </div>
              <!-- 选项1：标题和按钮之间的 tag 展示区域（可选，注释掉以使用选项2） -->
              <!--
              <div class="note-main-info-tags">
                <el-tag v-if="activeNote.isPublic" size="mini" type="success">已公开</el-tag>
                <el-tag v-if="contentTypeLabel" size="mini" type="info">{{ contentTypeLabel }}</el-tag>
              </div>
              -->
              <div class="note-main-actions">
                <!-- 项目选择器（EditorJS 类型笔记可编辑） -->
                <el-select
                  v-if="isEditorjsNote"
                  v-model="activeNote.projectId"
                  :placeholder="$t('workspace.notes.selectProject')"
                  clearable
                  size="mini"
                  style="width: 160px; margin-right: 8px;"
                  @change="onProjectChange"
                >
                  <el-option
                    v-for="project in projects"
                    :key="project.id"
                    :label="project.shortName || project.name"
                    :value="project.id"
                  >
                    <span>
                      <i v-if="project.icon" :class="project.icon" style="margin-right: 4px;"></i>
                      {{ project.shortName || project.name }}
                    </span>
                  </el-option>
                </el-select>
                <!-- 项目显示（非 EditorJS 类型笔记只读） -->
                <el-tag
                  v-else-if="activeNote.projectId && activeNote.projectName"
                  size="mini"
                  type="info"
                  effect="plain"
                  style="margin-right: 8px;"
                >
                  <i v-if="getProjectIcon(activeNote.projectId)" :class="getProjectIcon(activeNote.projectId)" style="margin-right: 4px;"></i>
                  {{ activeNote.projectName }}
                </el-tag>
                <el-button size="mini" icon="el-icon-paperclip" @click="goToClips">
                  {{ $t('workspace.notes.references') }}<span v-if="clipCount > 0"> ({{ clipCount }})</span>
                </el-button>
                <el-button v-if="!isEditorjsNote" size="mini" @click="openInEditor">
                  <i class="el-icon-edit"></i> 在编辑器中打开
                </el-button>
                <el-button v-if="!isFullscreen" size="mini" icon="el-icon-full-screen" @click="isFullscreen = true">{{ $t('workspace.notes.fullscreen') }}</el-button>
                <el-button v-if="isFullscreen" size="mini" type="warning" icon="el-icon-close" @click="isFullscreen = false">{{ $t('workspace.notes.exitFullscreen') }}</el-button>
                <button
                  v-if="!isFullscreen"
                  class="panel-toggle-btn"
                  :title="rightPanelCollapsed ? '展开侧栏' : '收起侧栏'"
                  @click="rightPanelCollapsed = !rightPanelCollapsed"
                >
                  <i :class="rightPanelCollapsed ? 'el-icon-d-arrow-left' : 'el-icon-d-arrow-right'"></i>
                </button>
                <el-dropdown @command="(cmd) => handleNoteAction(cmd, activeNote)">
                  <el-button size="mini" icon="el-icon-more"></el-button>
                <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item command="toggle-public">
                      <i :class="activeNote.isPublic ? 'el-icon-lock' : 'el-icon-unlock'"></i>
                      {{ activeNote.isPublic ? $t('workspace.notes.makePrivate') : $t('workspace.notes.makePublic') }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isEditorjsNote" command="translate-english">
                      <i class="el-icon-s-opportunity" style="margin-right:4px;"></i>
                      翻译为英文
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isEditorjsNote" command="publish-wechat">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="#07c160" style="margin-right:4px;vertical-align:-2px;"><path d="M8.7 10.6c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zm5.1 0c-.5 0-.9-.4-.9-.9s.4-.9.9-.9.9.4.9.9-.4.9-.9.9zM12 2C6.48 2 2 6.03 2 11c0 2.7 1.24 5.12 3.2 6.8L4 22l4.83-1.61A10.63 10.63 0 0012 21c5.52 0 10-4.03 10-9S17.52 2 12 2z"/></svg>
                      发布到微信
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isEditorjsNote" command="publish-linkedin">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="#0077B5" style="margin-right:4px;vertical-align:-2px;"><path d="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.32 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.79M6.88 8.56a1.68 1.68 0 0 0 1.68-1.68c0-.93-.75-1.69-1.68-1.69a1.69 1.69 0 0 0-1.69 1.69c0 .93.76 1.68 1.69 1.68m1.39 9.94v-8.37H5.5v8.37h2.77z"/></svg>
                      发布到 LinkedIn
                    </el-dropdown-item>
                    <el-dropdown-item command="export-pdf">
                      <i class="el-icon-document" style="margin-right:4px;"></i>
                      导出为 PDF
                    </el-dropdown-item>
                    <el-dropdown-item command="delete"><i class="el-icon-delete"></i> 删除</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </div>
            </div>
            <!-- 月历多笔记导航条 -->
            <div
              v-if="viewMode === 'calendar' && selectedDateNotes.length > 1"
              class="calendar-note-nav"
              :style="isEditorjsNote ? { marginLeft: '60px' } : {}"
            >
              <button
                class="calendar-nav-btn"
                :disabled="selectedDateNoteIndex === 0"
                @click="handleDateNavigation('prev')"
                title="上一篇 (Ctrl+←)"
              >
                <i class="el-icon-arrow-left"></i>
              </button>
              <span class="calendar-nav-info">
                {{ selectedDate }} · 第 {{ selectedDateNoteIndex + 1 }} / {{ selectedDateNotes.length }} 篇
              </span>
              <button
                class="calendar-nav-btn"
                :disabled="selectedDateNoteIndex === selectedDateNotes.length - 1"
                @click="handleDateNavigation('next')"
                title="下一篇 (Ctrl+→)"
              >
                <i class="el-icon-arrow-right"></i>
              </button>
              <span class="calendar-nav-hint">Ctrl+← → 切换</span>
            </div>
            <!-- EditorJS 类型笔记：可编辑标签选择器 -->
            <div v-if="isEditorjsNote" class="note-main-tags-editable">
              <div class="tag-selector">
                <div class="tag-list">
                  <el-tag
                    v-for="tag in tags"
                    :key="tag.id"
                    :type="getTagType(tag.id)"
                    effect="plain"
                    size="small"
                    class="tag-item"
                    @click.native="toggleNoteTag(tag.id)"
                  >
                    {{ tag.name }}
                  </el-tag>
                </div>
                <!-- <div class="tag-input-wrapper">
                  <el-input
                    v-model="newTagName"
                    placeholder="输入新标签名，按回车创建"
                    size="small"
                    class="tag-input"
                    @keyup.enter.native="createNewTag"
                  />
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="!newTagName || !newTagName.trim()"
                    @click="createNewTag"
                  >
                    创建
                  </el-button>
                </div> -->
              </div>
            </div>
            <!-- 非 EditorJS 类型笔记：只读标签显示 -->
            <div v-else-if="!isEditorjsNote && activeNote.tags && activeNote.tags.length" class="note-main-tags">
              <el-tag v-for="tag in activeNote.tags" :key="tag.id" size="small" type="info" effect="plain" class="tag-item">
                <i class="el-icon-collection-tag"></i> {{ tag.name }}
              </el-tag>
            </div>
            <div v-if="activeNote.summary" class="note-main-summary">
              <div class="summary-header"><i class="el-icon-document"></i><span>摘要</span></div>
              <p class="summary-content">{{ activeNote.summary }}</p>
            </div>
            <div class="note-main-content">
              <!-- EditorJS 格式 -->
              <div v-if="isEditorjsNote" ref="editorContainer" class="editorjs-workspace-container editorjs-container"></div>
              <!-- Markdown 格式 -->
              <div v-else-if="activeNote.contentType === 'markdown'" class="markdown-content-display" v-mermaid v-html="renderMarkdown(activeNote.content)"></div>
              <!-- 富文本格式 -->
              <div v-else-if="activeNote.contentType === 'richtext'" class="wangeditor-content" v-html="activeNote.content"></div>
              <!-- 旧格式多区块 -->
              <template v-else-if="activeNote.sectionContents && activeNote.sectionContents.length">
                <div class="note-main-sections">
                  <div v-for="(section, index) in activeNote.sectionContents" :key="index" class="note-main-section">
                    <div v-if="activeNote.sectionTypes?.[index] === 'markdown'" class="markdown-content-display" v-mermaid v-html="renderMarkdown(section)"></div>
                    <div v-else-if="activeNote.sectionTypes?.[index] === 'richtext'" class="wangeditor-content" v-html="section"></div>
                    <pre v-else-if="activeNote.sectionTypes?.[index] === 'code'" class="code-block-display"><code>{{ section }}</code></pre>
                    <div v-else class="plain-content-display" v-html="section"></div>
                  </div>
                </div>
              </template>
              <!-- 单内容向后兼容 -->
              <div v-else class="note-main-single plain-content-display" v-html="activeNote.content"></div>
            </div>
          </div>
          <div v-else-if="!showMonthCalendar" class="note-main-empty">
            <i class="el-icon-notebook-2 empty-icon"></i>
            <p class="empty-text">选择左侧一条笔记，在这里查看详情</p>
          </div>
      </main>

      <!-- ========== 右侧信息 ========== -->
      <aside v-show="!rightPanelCollapsed" class="workspace-right">
        <!-- 笔记右侧 -->
        <div class="right-panel" v-if="activeNote">
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.notes.outline') }}</h3>
            <ul v-if="outline.length" class="outline-list">
              <li v-for="(item, index) in outline" :key="index" class="outline-item" :style="{ paddingLeft: (item.indent || 0) + 'px' }">
                <span class="outline-text">{{ item.text }}</span>
              </li>
            </ul>
            <p v-else class="right-empty-text">暂无可用大纲</p>
          </div>
          <div class="right-section">
            <h3 class="right-title">{{ $t('workspace.notes.recentNotes') }}</h3>
            <ul class="recent-list">
              <li v-for="note in recentNotes" :key="note.id" class="recent-item" @click="selectNote(note)">
                <div class="recent-title">{{ note.title || '未命名笔记' }}</div>
                <div class="recent-meta">{{ formatTime(note.modifiedAt || note.createdAt) }}</div>
              </li>
            </ul>
          </div>
          <div class="right-section">
            <h3 class="right-title">Reddit</h3>
            <p v-if="!redditStatus.appConfigured" class="right-empty-text">
              服务端未配置 Reddit（REDDIT_CLIENT_ID 等）
            </p>
            <template v-else>
              <p v-if="redditStatus.connected" class="linkedin-status-line">
                已连接 u/{{ redditStatus.redditUsername }}
                <span v-if="redditStatus.tokenExpired" class="linkedin-warn">（建议重新授权）</span>
              </p>
              <p v-else class="right-empty-text">未连接 Reddit 账号</p>
              <div class="linkedin-actions">
                <el-button
                  v-if="!redditStatus.connected"
                  type="primary"
                  size="mini"
                  @click="connectReddit"
                >连接 Reddit</el-button>
                <template v-if="redditStatus.connected">
                  <el-button
                    size="mini"
                    type="primary"
                    plain
                    :disabled="!activeNote"
                    @click="openRedditPublishDialog"
                  >发布当前笔记</el-button>
                  <el-button size="mini" type="text" @click="showRedditLogs">发布记录</el-button>
                  <el-button size="mini" type="text" @click="disconnectReddit">断开</el-button>
                </template>
              </div>
            </template>
          </div>
        </div>

      </aside>
    </div>

    <WechatPublishDialog
      ref="wechatDialog"
      :note-id="activeNote ? Number(activeNote.id) : null"
      :note-title="activeNote ? activeNote.title : ''"
      :note-content="activeNote ? (activeNote.content || '') : ''"
      @published="onWechatPublished"
    />

    <TranslationDialog
      ref="translationDialog"
      :note-id="activeNote ? Number(activeNote.id) : null"
      @close="showTranslationDialog = false"
      @translated="onTranslated"
    />

    <!-- 创建笔记对话框 -->
    <CreateNoteDialog
      v-model="createNoteDialogVisible"
      @select-blank="handleCreateBlankNote"
      @select-feishu="handleImportFeishu"
      @select-markdown="handleImportMarkdown"
    />

    <FeishuCredentialDialog
      v-model="feishuCredentialDialogVisible"
      @submit="saveFeishuCredentials"
    />

    <LinkedinShareDialog
      ref="linkedinShareDialog"
      :note-id="activeNote ? Number(activeNote.id) : null"
    />

    <RedditPublishDialog
      ref="redditPublishDialog"
      :note-id="activeNote ? Number(activeNote.id) : null"
      :note-title="activeNote ? activeNote.title : ''"
      @published="onRedditPublished"
    />

  </div>
</template>

<script>
import { renderMarkdown as renderMd } from '~/utils/markdown'
import { createEditorImageResizer } from '~/utils/editorjsImageResize'

export default {
  name: 'WorkspacePage',
  layout: 'workspace',
  inject: ['getTopbarCollapsed'],
  components: {
    WechatPublishDialog: () => import('~/components/WechatPublishDialog.vue'),
    TranslationDialog: () => import('~/components/TranslationDialog.vue'),
    LinkedinShareDialog: () => import('~/components/LinkedinShareDialog.vue')
  },
  async asyncData({ $axios }) {
    try {
      const [notesRes, recentNotesRes, tagsRes, projectsRes] = await Promise.all([
        $axios.get('/v1/notes', {
          params: { page: 0, size: 10, sortBy: 'createdAt', direction: 'DESC' }
        }),
        $axios.get('/v1/notes', {
          params: { page: 0, size: 5, sortBy: 'modifiedAt', direction: 'DESC' }
        }),
        $axios.get('/v1/tags'),
        $axios.get('/v1/projects/my')
      ])
      return {
        notes: notesRes.data.content || [],
        total: notesRes.data.totalElements || 0,
        recentNotes: recentNotesRes.data.content || [],
        tags: tagsRes.data || [],
        projects: projectsRes.data || []
      }
    } catch (error) {
      return { notes: [], total: 0, recentNotes: [], tags: [], projects: [] }
    }
  },
  data() {
    return {
      // 通用
      loading: false,

      // 笔记
      searchKeyword: '',
      selectedTags: [],
      selectedProjects: [],
      sortBy: 'createdAt', // 左侧列表按创建时间排序
      page: 0,
      pageSize: 10,
      notes: [],
      total: 0,
      recentNotes: [], // 右侧"最近笔记"单独存储，按修改时间排序
      tags: [],
      projects: [],
      activeNoteId: null,
      activeNote: null,
      clipCount: 0,
      outline: [],
      editor: null,
      imageResizer: null,
      saveStatus: { icon: 'el-icon-check', text: '' },
      saveTimeout: null,
      isSaving: false,
      hasUnsavedChanges: false,
      lastSavedSnapshot: null,

      // 侧栏折叠
      tagsCollapsed: false,
      projectsCollapsed: false,

      // 布局控制
      rightPanelCollapsed: false,
      isFullscreen: false,

      // 创建笔记对话框
      createNoteDialogVisible: false,

      // 飞书凭证对话框
      feishuCredentialDialogVisible: false,
      pendingFeishuConnect: false,

      // 标签编辑
      newTagName: '',

      // 微信发布
      wechatPublished: false,
      showTranslationDialog: false,

      // 月历相关
      viewMode: 'list', // 'list' | 'calendar'
      selectedDate: null, // 选中的日期 'YYYY-MM-DD'
      selectedDateNotes: [], // 选中日期的所有笔记
      selectedDateNoteIndex: 0, // 当前显示的笔记索引
      notesByDate: {}, // 日期到笔记数组的映射 { 'YYYY-MM-DD': [note1, note2, ...] }
      allNotesForCalendar: [], // 用于月历的所有笔记
      currentDate: (() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` })(), // 当前日期
      showMonthCalendar: false,
      calendarMonth: '',
      calendarFromMonth: null,
      previousNoteBeforeCalendar: null,

      redditStatus: {
        appConfigured: false,
        connected: false,
        redditUsername: null,
        tokenExpired: false
      }
    }
  },
  computed: {
    workspaceHeight() {
      // 根据顶部栏状态动态计算高度
      // 尝试从父组件获取状态，如果获取不到则使用默认值
      let topbarCollapsed = false
      if (this.getTopbarCollapsed) {
        topbarCollapsed = this.getTopbarCollapsed()
      } else if (this.$parent && this.$parent.topbarCollapsed !== undefined) {
        topbarCollapsed = this.$parent.topbarCollapsed
      }
      // 隐藏时只保留约 20px 的空间（切换按钮 + padding）
      return topbarCollapsed ? 'calc(100vh - 20px)' : 'calc(100vh - 110px)'
    },
    isEditorjsNote() {
      return this.activeNote && this.activeNote.contentType === 'editorjs'
    },
    displayTitle() {
      // 当标题是"未命名笔记"或空时，返回空字符串以显示 placeholder
      if (!this.activeNote || !this.activeNote.title) return ''
      const trimmedTitle = this.activeNote.title.trim()
      // 只有当 trim 后等于"未命名笔记"时才返回空，否则返回原始标题（保留空格）
      return trimmedTitle === '未命名笔记' ? '' : this.activeNote.title
    },
    contentTypeLabel() {
      if (!this.activeNote || !this.activeNote.contentType) return ''
      if (this.activeNote.contentType === 'editorjs') return '' // editorjs 不显示
      const labels = {
        'richtext': '富文本',
        'markdown': 'Markdown',
        'Richtext': '富文本', // 兼容大小写
        'Markdown': 'Markdown', // 兼容大小写
        'RICH TEXT': '富文本', // 兼容其他格式
        'MARKDOWN': 'Markdown' // 兼容其他格式
      }
      const contentType = this.activeNote.contentType.toLowerCase()
      if (contentType === 'richtext' || contentType === 'rich text') return '富文本'
      if (contentType === 'markdown') return 'Markdown'
      return labels[this.activeNote.contentType] || ''
    },
    calendarDays() {
      if (!this.calendarMonth) return []
      const [year, month] = this.calendarMonth.split('-').map(Number)
      const firstDay = new Date(year, month - 1, 1)
      const lastDay = new Date(year, month, 0)
      const days = []
      let startDow = firstDay.getDay()
      startDow = startDow === 0 ? 6 : startDow - 1
      for (let i = 0; i < startDow; i++) days.push(null)
      for (let d = 1; d <= lastDay.getDate(); d++) {
        const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`
        days.push({
          day: d,
          dateStr,
          notes: this.notesByDate[dateStr] || [],
          isToday: dateStr === this.currentDate
        })
      }
      return days
    },
  },
  created() {
    if (process.client) {
      this.tagsCollapsed = localStorage.getItem('sidebar_tags_collapsed') === 'true'
      this.projectsCollapsed = localStorage.getItem('sidebar_projects_collapsed') === 'true'
      this.pageSize = 10 + (this.tagsCollapsed ? 2 : 0) + (this.projectsCollapsed ? 2 : 0)
    }
  },
  mounted() {
    if (this.notes && this.notes.length > 0) {
      this.activeNoteId = this.notes[0].id
      this.loadActiveNote()
    } else {
      this.loadNotes().then(() => {
        if (this.notes.length > 0) {
          this.activeNoteId = this.notes[0].id
          this.loadActiveNote()
        }
      })
    }
    // 如果 recentNotes 为空，单独加载
    if (!this.recentNotes || this.recentNotes.length === 0) {
      this.loadRecentNotes()
    }
    // 监听 layout 触发的创建事件
    this.$nuxt.$on('workspace:create:notes', this.showCreateNoteDialog)
    // 监听顶部栏状态变化事件
    this._onTopbarToggle = () => {
      this.$forceUpdate() // 强制更新以重新计算高度
    }
    this.$nuxt.$on('workspace:topbar:toggle', this._onTopbarToggle)
    // ESC 退出全屏
    this._onEsc = (e) => { if (e.key === 'Escape' && this.isFullscreen) this.isFullscreen = false }
    document.addEventListener('keydown', this._onEsc)
    // Ctrl+→/← 切换同一天的笔记
    this._onDateNav = (e) => {
      if (e.ctrlKey && this.viewMode === 'calendar' && this.selectedDateNotes.length > 1) {
        if (e.key === 'ArrowRight') {
          e.preventDefault()
          this.handleDateNavigation('next')
        } else if (e.key === 'ArrowLeft') {
          e.preventDefault()
          this.handleDateNavigation('prev')
        }
      }
    }
    document.addEventListener('keydown', this._onDateNav)
    this.loadRedditStatus()
    if (this.$route.query.reddit_connected === '1') {
      this.$message.success('Reddit 账号已连接')
      this.$router.replace({ path: this.$route.path, query: {} })
    }
    if (this.$route.query.reddit_error) {
      this.$message.error('Reddit 连接失败：' + decodeURIComponent(this.$route.query.reddit_error))
      this.$router.replace({ path: this.$route.path, query: {} })
    }
    // 月グルーピング用に全ノートデータをバックグラウンドでロード
    this.loadNotesDates()
  },
  beforeDestroy() {
    if (this.saveTimeout) clearTimeout(this.saveTimeout)
    this.destroyEditor()
    // 移除事件监听器
    this.$nuxt.$off('workspace:create:notes', this.showCreateNoteDialog)
    if (this._onTopbarToggle) this.$nuxt.$off('workspace:topbar:toggle', this._onTopbarToggle)
    if (this._onEsc) document.removeEventListener('keydown', this._onEsc)
    if (this._onDateNav) document.removeEventListener('keydown', this._onDateNav)
  },
  methods: {
    // ========== 笔记方法 ==========
    async loadNotes() {
      this.loading = true
      try {
        // 左侧列表：按创建时间排序
        const params = { page: this.page, size: this.pageSize, sortBy: 'createdAt', direction: 'DESC' }
        if (this.searchKeyword) params.keyword = this.searchKeyword
        if (this.selectedTags.length > 0) params.tagIds = this.selectedTags.join(',')
        if (this.selectedProjects.length > 0) params.projectIds = this.selectedProjects.join(',')
        const { data } = await this.$axios.get('/v1/notes', { params })
        this.notes = data.content || []
        this.total = data.totalElements || 0
        
        // 同时加载右侧"最近笔记"：按修改时间排序
        await this.loadRecentNotes()
      } catch (error) {
        this.$message.error('加载笔记失败')
      } finally {
        this.loading = false
      }
    },
    async loadRecentNotes() {
      try {
        // 右侧"最近笔记"：按修改时间排序，取前5条
        const { data } = await this.$axios.get('/v1/notes', {
          params: { page: 0, size: 5, sortBy: 'modifiedAt', direction: 'DESC' }
        })
        this.recentNotes = data.content || []
      } catch (error) {
        // 静默失败，不影响主列表
        console.error('加载最近笔记失败:', error)
      }
    },
    handleSearch() { this.page = 0; this.loadNotes() },
    handleTagFilter() { this.page = 0; this.loadNotes() },
    handleProjectFilter() { this.page = 0; this.loadNotes() },
    handlePageChange(page) { this.page = page - 1; this.loadNotes() },
    async toggleSidebarSection(section) {
      const collapsedKey = section === 'tags' ? 'tagsCollapsed' : 'projectsCollapsed'
      const lsKey = section === 'tags' ? 'sidebar_tags_collapsed' : 'sidebar_projects_collapsed'
      const willCollapse = !this[collapsedKey]
      this[collapsedKey] = willCollapse
      localStorage.setItem(lsKey, willCollapse)

      const oldPageSize = this.pageSize
      const newPageSize = oldPageSize + (willCollapse ? 2 : -2)

      let newPage = 0
      if (this.activeNoteId) {
        const indexInPage = this.notes.findIndex(n => n.id === this.activeNoteId)
        if (indexInPage >= 0) {
          const globalIndex = this.page * oldPageSize + indexInPage
          newPage = Math.floor(globalIndex / newPageSize)
        }
      }

      this.pageSize = newPageSize
      this.page = newPage
      await this.loadNotes()
    },
    toggleTag(tagId) {
      const index = this.selectedTags.indexOf(tagId)
      if (index >= 0) this.selectedTags.splice(index, 1)
      else this.selectedTags.push(tagId)
      this.handleTagFilter()
    },
    toggleProject(projectId) {
      const index = this.selectedProjects.indexOf(projectId)
      if (index >= 0) this.selectedProjects.splice(index, 1)
      else this.selectedProjects.push(projectId)
      this.handleProjectFilter()
    },
    showCreateNoteDialog() {
      this.createNoteDialogVisible = true
    },
    async handleCreateBlankNote() {
      try {
        const noteData = {
          title: '未命名笔记',
          content: JSON.stringify({ time: Date.now(), blocks: [], version: '2.28.2' }),
          contentType: 'editorjs',
          isPublic: false,
          tagIds: [],
          sectionContents: [],
          sectionTypes: []
        }
        const response = await this.$noteService.createNote(noteData)
        await this.loadNotes()
        // loadNotes 内部会调用 loadRecentNotes，所以这里不需要单独调用
        if (this.showMonthCalendar) {
          this.showMonthCalendar = false
          this.calendarFromMonth = null
          this.previousNoteBeforeCalendar = null
          await this.$nextTick()
        }
        this.destroyEditor()
        this.activeNoteId = response.id
        await this.loadActiveNote()
        this.$message.success('笔记已创建')
      } catch (error) {
        this.$message.error('创建笔记失败')
      }
    },
    async handleImportFeishu() {
      // 检查是否已绑定飞书
      try {
        const status = await this.$feishuService.getStatus()
        if (!status.configured) {
          this.pendingFeishuConnect = true
          this.feishuCredentialDialogVisible = true
          return
        }
        if (status.bound) {
          // 已绑定，跳转到飞书导入页面
          this.$router.push('/workspace/feishu-import')
        } else {
          // 未绑定，先获取授权URL
          const res = await this.$feishuService.getOAuthUrl()
          if (res && res.url) {
            window.location.href = res.url
          } else {
            this.$message.error('未获取到授权地址')
          }
        }
      } catch (error) {
        this.$message.error('检查飞书绑定状态失败')
      }
    },


    // ── Reddit ──────────────────────────────────────────────────────────────
    async loadRedditStatus() {
      try {
        const s = await this.$redditService.getStatus()
        this.redditStatus = {
          appConfigured: !!s.appConfigured,
          connected: !!s.connected,
          redditUsername: s.redditUsername || null,
          tokenExpired: !!s.tokenExpired
        }
      } catch (e) {
        this.redditStatus = { appConfigured: false, connected: false, redditUsername: null, tokenExpired: false }
      }
    },
    async connectReddit() {
      try {
        const res = await this.$redditService.getAuthorizeUrl()
        if (res && res.url) {
          window.location.href = res.url
        } else {
          this.$message.error('未获取到授权地址')
        }
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '获取授权地址失败')
      }
    },
    openRedditPublishDialog() {
      if (!this.activeNote) return
      this.$refs.redditPublishDialog && this.$refs.redditPublishDialog.open()
    },
    onRedditPublished() {
      this.loadRedditStatus()
    },
    async disconnectReddit() {
      try {
        await this.$confirm('确定断开 Reddit 连接？', '提示', { type: 'warning' })
      } catch (e) {
        return
      }
      try {
        await this.$redditService.disconnect()
        this.$message.success('已断开')
        await this.loadRedditStatus()
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '断开失败')
      }
    },
    async showRedditLogs() {
      if (!this.activeNote) return
      try {
        const logs = await this.$redditService.getLogs(this.activeNote.id)
        if (!logs || !logs.length) {
          this.$message.info('暂无 Reddit 发布记录')
          return
        }
        const lines = logs.map((l) => {
          const t = l.createdAt ? this.formatTime(l.createdAt) : ''
          const u = l.postUrl ? ` ${l.postUrl}` : ''
          const err = l.errorMessage ? ` ${l.errorMessage}` : ''
          return `[${t}] r/${l.subreddit} ${l.status}${u}${err}`
        })
        await this.$alert(lines.join('\n'), 'Reddit 发布记录', {
          confirmButtonText: '关闭',
          customClass: 'linkedin-logs-alert'
        })
      } catch (e) {
        this.$message.error(e.response?.data?.message || e.message || '加载记录失败')
      }
    },

    async saveFeishuCredentials({ appId, appSecret }) {
      try {
        await this.$feishuService.saveCredentials({ appId, appSecret })
        this.$message.success('飞书配置已保存')
        if (this.pendingFeishuConnect) {
          this.pendingFeishuConnect = false
          // 继续走连接流程
          await this.handleImportFeishu()
        }
      } catch (error) {
        this.pendingFeishuConnect = false
        this.$message.error('保存飞书配置失败：' + (error.response?.data?.message || error.message))
        throw error
      }
    },
    async handleImportMarkdown({ title, content, file }) {
      try {
        const ts = file?.lastModified
          ? new Date(file.lastModified).toISOString().replace('Z', '')
          : undefined
        const noteData = {
          title: title || '导入的笔记',
          content: content,
          contentType: 'markdown',
          isPublic: false,
          tagIds: [],
          sectionContents: [],
          sectionTypes: [],
          createdAt: ts,
          modifiedAt: ts
        }
        const response = await this.$noteService.createNote(noteData)
        await this.loadNotes()
        // loadNotes 内部会调用 loadRecentNotes，所以这里不需要单独调用
        if (this.showMonthCalendar) {
          this.showMonthCalendar = false
          this.calendarFromMonth = null
          this.previousNoteBeforeCalendar = null
          await this.$nextTick()
        }
        this.activeNoteId = response.id
        await this.loadActiveNote()
        this.$message.success('Markdown 文件已导入为笔记')
      } catch (error) {
        this.$message.error('导入 Markdown 文件失败：' + (error.response?.data?.message || error.message))
      }
    },
    async selectNote(note) {
      if (!note || !note.id || note.id === this.activeNoteId) return
      if (this.hasUnsavedChanges && this.editor) await this.saveToBackend()
      // openNoteFromCalendar() は呼び出し前に showMonthCalendar=false をセット済みなので
      // この分岐は月历チップクリック時には実行されず、ブレッドクラムは維持される
      if (this.showMonthCalendar) {
        this.showMonthCalendar = false
        this.calendarFromMonth = null
        this.previousNoteBeforeCalendar = null
        await this.$nextTick()
      }
      this.destroyEditor()
      this.activeNoteId = note.id
      await this.loadActiveNote()
    },
    async loadActiveNote() {
      if (!this.activeNoteId) return
      this.wechatPublished = false
      try {
        const note = await this.$noteService.getNoteById(this.activeNoteId)
        if (note.tags && !Array.isArray(note.tags)) note.tags = Array.from(note.tags || [])
        if (note.sectionContents && !Array.isArray(note.sectionContents)) note.sectionContents = Array.from(note.sectionContents || [])
        if (note.sectionTypes && !Array.isArray(note.sectionTypes)) note.sectionTypes = Array.from(note.sectionTypes || [])
        this.activeNote = note
        this.hasUnsavedChanges = false
        this.updateSaveStatus('saved')
        this.buildOutline()
        this.$clipService.getNoteClipCount(this.activeNoteId)
          .then(count => { this.clipCount = count })
          .catch(() => { this.clipCount = 0 })
        this.$wechatService.getLogs(Number(this.activeNoteId))
          .then(logs => { this.wechatPublished = logs.some(l => l.status === 'SUCCESS') })
          .catch(() => { /* 微信未配置时静默忽略 */ })
        if (note.contentType === 'editorjs') {
          await this.$nextTick()
          let editorData = null
          if (note.content) {
            try { editorData = JSON.parse(note.content) } catch (e) { /* ignore */ }
          }
          await this.initEditor(editorData)
          // 记录初始快照，用于后续比较是否真正变化（包含标签）
          const initialTagIds = (note.tags || []).map(t => t.id).sort()
          this.lastSavedSnapshot = JSON.stringify({ title: note.title, blocks: editorData?.blocks || [], tagIds: initialTagIds })
          this.hasUnsavedChanges = false
          // 清除 EditorJS 初始化过程中 onChange 触发的自动保存定时器
          if (this.saveTimeout) {
            clearTimeout(this.saveTimeout)
            this.saveTimeout = null
          }
          this.updateSaveStatus('saved')
          // 延迟启用 onChange，等 EditorJS 异步 onChange 全部触发完毕
          setTimeout(() => { this._editorReady = true }, 500)
        }
      } catch (error) {
        this.$message.error('加载笔记详情失败')
      }
    },

    // ========== Editor.js ==========
    async initEditor(data = null) {
      if (!process.client) return
      this._editorReady = false
      const container = this.$refs.editorContainer
      if (!container) return
      const holderId = 'editorjs-ws-' + Date.now()
      container.id = holderId
      const [
        { default: EditorJS }, { default: Header }, { default: List },
        { default: CodeTool }, { default: Delimiter }, { default: Quote },
        { default: Table }, { default: InlineCode }, { default: ImageTool },
        { default: Marker }, { default: MarkdownBlock },
        { default: VideoTool }, { default: EmbedVideoTool }, { default: AudioTool }
      ] = await Promise.all([
        import('@editorjs/editorjs'), import('@editorjs/header'), import('@editorjs/list'),
        import('@editorjs/code'), import('@editorjs/delimiter'), import('@editorjs/quote'),
        import('@editorjs/table'), import('@editorjs/inline-code'), import('@editorjs/image'),
        import('@editorjs/marker'), import('~/utils/editorjs-markdown-block'),
        import('~/utils/editorjsVideoTool'), import('~/utils/editorjsEmbedVideoTool'),
        import('~/utils/editorjsAudioTool')
      ])
      const uploadService = this.$uploadService
      const noteId = this.activeNote ? this.activeNote.id : 0
      this.editor = new EditorJS({
        holder: holderId,
        placeholder: '输入 "/" 唤出快捷菜单，或直接开始输入...',
        autofocus: false,
        tools: {
          header: { class: Header, config: { placeholder: '请输入标题', levels: [1, 2, 3], defaultLevel: 2 }, shortcut: 'CMD+SHIFT+H' },
          list: { class: List, inlineToolbar: true, config: { defaultStyle: 'unordered' } },
          code: { class: CodeTool, config: { placeholder: '输入代码...' } },
          delimiter: { class: Delimiter },
          quote: { class: Quote, config: { quotePlaceholder: '输入引用内容...', captionPlaceholder: '引用来源（可选）' }, shortcut: 'CMD+SHIFT+O' },
          table: { class: Table, inlineToolbar: true, config: { rows: 3, cols: 3, withHeadings: true } },
          inlineCode: { class: InlineCode, shortcut: 'CMD+SHIFT+M' },
          image: {
            class: ImageTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return { success: 1, file: { url: result.url || result.fileUrl || result } }
                  } catch (e) {
                    console.error('图片上传失败:', e)
                    return { success: 0 }
                  }
                },
                async uploadByUrl(url) {
                  try {
                    const result = await uploadService.uploadRemote(url, 'note', noteId || 0)
                    return { success: 1, file: { url: result.url || result.fileUrl || result } }
                  } catch (e) { return { success: 0 } }
                }
              }
            }
          },
          marker: { class: Marker },
          markdown: { class: MarkdownBlock, inlineToolbar: false, config: { axiosBaseURL: this.$axios?.defaults?.baseURL || '' } },
          embed: {
            class: EmbedVideoTool
          },
          video: {
            class: VideoTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return { success: 1, file: { url: result.url || result.fileUrl || result } }
                  } catch (e) {
                    console.error('视频上传失败:', e)
                    return { success: 0 }
                  }
                }
              }
            }
          },
          audio: {
            class: AudioTool,
            config: {
              uploader: {
                async uploadByFile(file) {
                  try {
                    const result = await uploadService.uploadLocal(file, 'note', noteId || 0)
                    return { success: 1, file: { url: result.url || result.fileUrl || result } }
                  } catch (e) {
                    console.error('音频上传失败:', e)
                    return { success: 0 }
                  }
                }
              },
              session: {
                async createSession() {
                  return await uploadService.createSession('note', noteId || 0, 'audio/*')
                },
                async getSession(sessionId, token) {
                  return await uploadService.getSession(sessionId, token)
                }
              }
            }
          }
        },
        data: data || undefined,
        onChange: () => {
          if (!this._editorReady) return
          this.hasUnsavedChanges = true
          this.debouncedSave()
        },
        i18n: {
          messages: {
            ui: {
              blockTunes: { toggler: { 'Click to tune': '点击调整', 'or drag to move': '或拖动移动' } },
              inlineToolbar: { converter: { 'Convert to': '转换为' } },
              toolbar: { toolbox: { Add: '添加' } }
            },
            toolNames: {
              Text: '文本', Heading: '标题', List: '列表', Quote: '引用',
              Code: '代码块', Delimiter: '分割线', Table: '表格', Image: '图片',
              InlineCode: '行内代码', Marker: '高亮', Markdown: 'Markdown', Embed: '嵌入视频2', Video: '视频2', Audio: '音频', Bold: '加粗', Italic: '斜体', Link: '链接'
            },
            tools: {
              header: { 'Heading 1': '标题 1', 'Heading 2': '标题 2', 'Heading 3': '标题 3' },
              list: { Ordered: '有序列表', Unordered: '无序列表' },
              quote: { 'Align Left': '左对齐', 'Align Center': '居中' },
              table: { 'With headings': '带表头', 'Without headings': '无表头', 'Add row above': '上方插入行', 'Add row below': '下方插入行', 'Delete row': '删除行', 'Add column to the left': '左侧插入列', 'Add column to the right': '右侧插入列', 'Delete column': '删除列' },
              image: { Caption: '图片说明', 'Select an Image': '选择图片', 'With border': '带边框', 'Stretch image': '拉伸图片', 'With background': '带背景' }
            },
            blockTunes: {
              delete: { Delete: '删除', 'Click to delete': '点击确认删除' },
              moveUp: { 'Move up': '上移' },
              moveDown: { 'Move down': '下移' }
            }
          }
        }
      })
      await this.editor.isReady
      if (!this.imageResizer) {
        this.imageResizer = createEditorImageResizer({
          getEditor: () => this.editor,
          getContainer: () => this.$refs.editorContainer,
          markDirtyAndSave: () => {
            this.hasUnsavedChanges = true
            this.updateSaveStatus('saving')
            this.debouncedSave()
          }
        })
      }
      this.imageResizer.setupImageResize()
      this.setupCodeBlockAutoResize()
      // _editorReady 由 loadActiveNote 在清除 saveTimeout 后设置
    },

    // Code block textarea auto-resize
    setupCodeBlockAutoResize() {
      const container = this.$refs.editorContainer
      if (!container) return

      const autoResize = (textarea) => {
        const lines = (textarea.value || '').split('\n').length
        // font-size: 14px, line-height: 1.6 → 每行 22.4px；padding: 16px × 2 = 32px
        const lineHeight = 14 * 1.6
        const padding = 32
        const minH = 60
        const contentH = Math.ceil(lines * lineHeight + padding)
        textarea.style.height = Math.max(contentH, minH) + 'px'
        // 禁用拼写检查和语法检查，屏蔽蓝色双下划线告警
        textarea.setAttribute('spellcheck', 'false')
        textarea.setAttribute('autocomplete', 'off')
        textarea.setAttribute('autocorrect', 'off')
        textarea.setAttribute('autocapitalize', 'off')
      }

      // 延迟执行，确保 EditorJS 已渲染完内容
      setTimeout(() => {
        container.querySelectorAll('.ce-code__textarea').forEach(autoResize)
      }, 200)

      // 输入时实时调整高度
      container.addEventListener('input', (e) => {
        if (e.target && e.target.classList.contains('ce-code__textarea')) {
          autoResize(e.target)
        }
      })

      // 监听新增代码块
      if (this._codeBlockObserver) {
        this._codeBlockObserver.disconnect()
      }
      this._codeBlockObserver = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          for (const node of mutation.addedNodes) {
            if (node.nodeType !== 1) continue
            const textareas = node.classList && node.classList.contains('ce-code__textarea')
              ? [node]
              : (node.querySelectorAll ? node.querySelectorAll('.ce-code__textarea') : [])
            textareas.forEach((ta) => setTimeout(() => autoResize(ta), 100))
          }
        }
      })
      this._codeBlockObserver.observe(container, { childList: true, subtree: true })
    },

    destroyEditor() {
      if (this._codeBlockObserver) {
        this._codeBlockObserver.disconnect()
        this._codeBlockObserver = null
      }
      if (this.imageResizer) {
        this.imageResizer.destroy()
        this.imageResizer = null
      }
      if (this.editor) {
        try { this.editor.destroy() } catch (e) { /* ignore */ }
        this.editor = null
      }
    },
    onTitleInput(value) {
      // 更新标题，保留空格，仅在值为空时使用占位符
      if (this.activeNote) {
        this.activeNote.title = value || ''
        if (this.isEditorjsNote) {
          this.hasUnsavedChanges = true
          this.updateSaveStatus('saving')
          this.debouncedSave()
        }
      }
    },
    updateSaveStatus(status) {
      const map = {
        saving: { icon: 'el-icon-loading', text: this.$t('workspace.notes.saving') },
        saved: { icon: 'el-icon-check', text: this.$t('workspace.notes.saved') },
        error: { icon: 'el-icon-warning', text: this.$t('workspace.notes.saveFailed') }
      }
      this.saveStatus = map[status] || { icon: 'el-icon-edit', text: this.$t('workspace.notes.saving') }
    },
    debouncedSave() {
      clearTimeout(this.saveTimeout)
      this.saveTimeout = setTimeout(() => { this.saveToBackend() }, 2000)
    },
    async saveToBackend() {
      if (this.isSaving || !this.activeNote) return
      // 如果是 EditorJS 类型但没有 editor 实例，尝试保存标签和标题
      if (this.isEditorjsNote && !this.editor) {
        await this.saveTagsAndTitleOnly()
        return
      }
      // 如果是 EditorJS 类型，需要保存完整内容
      if (this.isEditorjsNote && this.editor) {
        this.isSaving = true
        try {
          const outputData = await this.editor.save()
          if (this.imageResizer) {
            this.imageResizer.syncWidthsToBlocks(outputData)
          }
          const title = (this.activeNote.title || '').trim() || '未命名笔记'
          const tagIds = (this.activeNote.tags || []).map(t => t.id).sort()

          // 比较内容快照，未变化则跳过保存（包含标签）
          const currentSnapshot = JSON.stringify({ title, blocks: outputData.blocks, tagIds })
          if (this.lastSavedSnapshot && currentSnapshot === this.lastSavedSnapshot) {
            this.hasUnsavedChanges = false
            this.updateSaveStatus('saved')
            this.isSaving = false
            return
          }

          this.updateSaveStatus('saving')

          const noteData = {
            title,
            content: JSON.stringify(outputData),
            contentType: 'editorjs',
            isPublic: this.activeNote.isPublic || false,
            tagIds: (this.activeNote.tags || []).map(t => t.id),
            projectId: this.activeNote.projectId || null,
            sectionContents: outputData.blocks.map(b => JSON.stringify(b.data)),
            sectionTypes: outputData.blocks.map(b => b.type)
          }
          await this.$noteService.updateNote(this.activeNote.id, noteData)
          this.hasUnsavedChanges = false
          this.lastSavedSnapshot = currentSnapshot
          this.updateSaveStatus('saved')
          this.buildOutline(outputData.blocks)
          const listNote = this.notes.find(n => n.id === this.activeNote.id)
          if (listNote) {
            listNote.title = title
            listNote.tags = [...this.activeNote.tags]
          }
          // 更新后刷新最近笔记列表
          await this.loadRecentNotes()
        } catch (error) {
          this.updateSaveStatus('error')
        } finally {
          this.isSaving = false
        }
      }
    },
    async saveTagsAndTitleOnly() {
      if (this.isSaving || !this.activeNote || !this.isEditorjsNote) return
      this.isSaving = true
      this.updateSaveStatus('saving')
      try {
        const title = (this.activeNote.title || '').trim() || '未命名笔记'
        // 获取当前笔记内容（如果存在）
        let content = this.activeNote.content
        let sectionContents = this.activeNote.sectionContents || []
        let sectionTypes = this.activeNote.sectionTypes || []
        
        // 如果 editor 存在，获取最新内容
        if (this.editor) {
          try {
            const outputData = await this.editor.save()
            if (this.imageResizer) {
              this.imageResizer.syncWidthsToBlocks(outputData)
            }
            content = JSON.stringify(outputData)
            sectionContents = outputData.blocks.map(b => JSON.stringify(b.data))
            sectionTypes = outputData.blocks.map(b => b.type)
          } catch (e) {
            // 如果保存失败，使用现有内容
          }
        }
        
        const noteData = {
          title,
          content: content || JSON.stringify({ time: Date.now(), blocks: [], version: '2.28.2' }),
          contentType: 'editorjs',
          isPublic: this.activeNote.isPublic || false,
          tagIds: (this.activeNote.tags || []).map(t => t.id),
          projectId: this.activeNote.projectId || null,
          sectionContents,
          sectionTypes
        }
        await this.$noteService.updateNote(this.activeNote.id, noteData)
        this.hasUnsavedChanges = false
        this.updateSaveStatus('saved')
        const listNote = this.notes.find(n => n.id === this.activeNote.id)
        if (listNote) {
          listNote.title = title
          listNote.tags = [...this.activeNote.tags]
        }
        // 更新后刷新最近笔记列表
        await this.loadRecentNotes()
      } catch (error) {
        this.updateSaveStatus('error')
      } finally {
        this.isSaving = false
      }
    },

    // ========== 笔记辅助 ==========
    buildOutline(blocks = null) {
      this.outline = []
      if (!this.activeNote) return
      if (blocks !== null) {
        const filtered = blocks.filter(b => b.type === 'header')
        const minLevel = filtered.length > 0 ? Math.min(...filtered.map(b => b.data.level || 2)) : 1
        this.outline = filtered.map(b => {
          const text = (b.data.text || '').replace(/<[^>]*>/g, '')
          return { text: text.length > 40 ? text.slice(0, 40) + '...' : text, indent: ((b.data.level || 2) - minLevel) * 12 }
        })
        if (this.outline.length === 0) this.outline = [{ text: '（无标题块）', indent: 0 }]
        return
      }
      if (this.activeNote.contentType === 'editorjs' && this.activeNote.content) {
        try {
          const data = JSON.parse(this.activeNote.content)
          if (data.blocks) {
            const filtered = data.blocks.filter(b => b.type === 'header')
            const minLevel = filtered.length > 0 ? Math.min(...filtered.map(b => b.data.level || 2)) : 1
            this.outline = filtered.map(b => {
              const text = (b.data.text || '').replace(/<[^>]*>/g, '')
              return { text: text.length > 40 ? text.slice(0, 40) + '...' : text, indent: ((b.data.level || 2) - minLevel) * 12 }
            })
          }
        } catch (e) { /* ignore */ }
        if (this.outline.length === 0) this.outline = [{ text: '（无标题块）', indent: 0 }]
        return
      }
      if (this.activeNote.contentType === 'markdown' && this.activeNote.content) {
        const headingRegex = /^(#{1,6})\s+(.+)$/gm
        const headings = []
        let match
        while ((match = headingRegex.exec(this.activeNote.content)) !== null) {
          headings.push({ level: match[1].length, text: match[2].trim() })
        }
        if (headings.length > 0) {
          const minLevel = Math.min(...headings.map(h => h.level))
          const minLevelCount = headings.filter(h => h.level === minLevel).length
          const filtered = minLevelCount === 1
            ? headings.filter(h => h.level !== minLevel)
            : headings
          const filteredMinLevel = filtered.length > 0 ? Math.min(...filtered.map(h => h.level)) : 1
          this.outline = filtered.map(h => ({
            text: h.text.length > 40 ? h.text.slice(0, 40) + '...' : h.text,
            indent: (h.level - filteredMinLevel) * 12
          }))
        }
        return
      }
      const sections = this.activeNote.sectionContents || []
      if (sections.length === 0 && this.activeNote.content) {
        this.outline = [{ text: this.extractOutlineTitle(this.activeNote.content), indent: 0 }]
        return
      }
      this.outline = sections.map((section, index) => ({
        text: this.extractOutlineTitle(section) || `区块 ${index + 1}`,
        indent: 0
      }))
    },
    extractOutlineTitle(content) {
      if (!content) return ''
      let text = content.replace(/<[^>]*>/g, '\n').replace(/^#{1,6}\s+/gm, '').replace(/\*\*([^*]+)\*\*/g, '$1').replace(/\*([^*]+)\*/g, '$1').replace(/`([^`]+)`/g, '$1')
      const lines = text.split('\n').map(l => l.trim()).filter(l => l.length > 0)
      if (!lines.length) return ''
      return lines[0].length > 40 ? lines[0].slice(0, 40) + '...' : lines[0]
    },
    getSectionTypeLabel(type) {
      const labels = { 'richtext': '富文本', 'markdown': 'Markdown', 'paragraph': '段落', 'header': '标题', 'quote': '引用', 'code': '代码块', 'inline-code': '行内代码', 'delimiter': '分割线', 'divider': '分割线', 'table': '表格', 'image': '图片', 'list': '列表', 'ai-chat': 'AI 对话', 'iframe': '嵌入页面', 'gallery': '图片库' }
      return labels[type] || '文本'
    },
    getSectionPreview(content) {
      if (!content) return ''
      let text = content.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim()
      return text.length > 200 ? text.slice(0, 200) + '...' : text
    },
    renderMarkdown(markdown) {
      return renderMd(markdown, { axiosBaseURL: this.$axios?.defaults?.baseURL || '' })
    },
    goToClips() {
      if (!this.activeNoteId) return
      this.$router.push(`/workspace/clips?linkTo=${this.activeNoteId}`)
    },
    openInEditor() {
      if (!this.activeNote || !this.activeNote.id) return
      if (this.activeNote.contentType === 'editorjs') this.$router.push(`/workspace/editor?id=${this.activeNote.id}`)
      else this.$router.push(`/workspace/notes/${this.activeNote.id}/edit`)
    },
    handleNoteAction(command, note) {
      if (command === 'toggle-public') this.toggleNotePublic(note)
      else if (command === 'delete') this.deleteNote(note)
      else if (command === 'publish-wechat') this.openWechatPublish(note)
      else if (command === 'translate-english') this.openTranslationDialog(note)
      else if (command === 'export-pdf') this.exportNoteToPdf(note)
      else if (command === 'publish-linkedin') this.openLinkedinShareDialog(note)
    },
    openLinkedinShareDialog(note) {
      if (!note) return
      let blocks = []
      if (note.content) {
        try { blocks = JSON.parse(note.content).blocks || [] } catch (e) { /* ignore */ }
      }
      this.$refs.linkedinShareDialog && this.$refs.linkedinShareDialog.open(
        (note.title || '').trim() || '未命名笔记',
        blocks
      )
    },
    async exportNoteToPdf(note) {
      if (!note) return
      try {
        const contentHtml = await this._buildNoteContentHtml(note)
        const title = (note.title || '').trim() || '未命名笔记'
        const fmt = dt => dt
          ? new Date(dt).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
          : ''
        const createdAt = fmt(note.createdAt)
        const modifiedAt = fmt(note.modifiedAt)
        const tagHtml = (note.tags || []).map(t => `<span class="pdf-tag">${this._escapeHtmlForPdf(t.name)}</span>`).join('')
        const metaParts = []
        if (createdAt) metaParts.push(`创建于 ${createdAt}`)
        if (modifiedAt && modifiedAt !== createdAt) metaParts.push(`最后更新 ${modifiedAt}`)
        if (note.projectName) metaParts.push(`项目：${this._escapeHtmlForPdf(note.projectName)}`)
        const headerHtml = `
          <div class="pdf-header-section">
            <h1 class="pdf-title">${this._escapeHtmlForPdf(title)}</h1>
            ${metaParts.length ? `<div class="pdf-meta">${metaParts.join(' · ')}</div>` : ''}
            ${tagHtml ? `<div class="pdf-tags">${tagHtml}</div>` : ''}
          </div>`
        const { NOTE_PRINT_CSS } = await import('~/utils/note-print-css')
        const fullHtml = this._buildPrintDocument(title, headerHtml, contentHtml, NOTE_PRINT_CSS)
        this._openPrintWindow(fullHtml)
      } catch (e) {
        console.error('[exportNoteToPdf]', e)
        this.$message.error('PDF 导出失败，请重试')
      }
    },
    async _buildNoteContentHtml(note) {
      const { editorjsToHtml } = await import('~/utils/editorjs-to-html')
      if (note.contentType === 'editorjs') {
        let editorData = null
        if (this.editor) {
          try { editorData = await this.editor.save() } catch (e) { /* fall through */ }
        }
        if (!editorData && note.content) {
          try { editorData = JSON.parse(note.content) } catch (e) { /* ignore */ }
        }
        return editorjsToHtml(editorData || { blocks: [] })
      }
      if (note.contentType === 'markdown') {
        return `<div class="pdf-markdown">${this.renderMarkdown(note.content || '')}</div>`
      }
      if (note.contentType === 'richtext') {
        return `<div class="wangeditor-content">${note.content || ''}</div>`
      }
      if (note.sectionContents && note.sectionContents.length) {
        return note.sectionContents.map((section, i) => {
          const type = (note.sectionTypes || [])[i]
          if (type === 'markdown') return `<div class="pdf-markdown">${this.renderMarkdown(section)}</div>`
          if (type === 'richtext') return `<div class="wangeditor-content">${section}</div>`
          if (type === 'code') return `<pre class="pdf-code"><code>${this._escapeHtmlForPdf(section)}</code></pre>`
          return `<div>${section}</div>`
        }).join('<hr class="pdf-delimiter">')
      }
      return `<div>${note.content || ''}</div>`
    },
    _buildPrintDocument(title, headerHtml, contentHtml, css) {
      return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>${this._escapeHtmlForPdf(title.replace(/\./g, '·'))}</title>
  <style>${css}</style>
</head>
<body>
  ${headerHtml}
  <div class="pdf-content">${contentHtml}</div>
</body>
</html>`
    },
    _openPrintWindow(html) {
      const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const win = window.open(url, '_blank', 'width=900,height=700')
      if (!win) {
        URL.revokeObjectURL(url)
        this.$message.warning('请允许弹出窗口以导出 PDF')
        return
      }
      win.addEventListener('load', () => {
        setTimeout(() => {
          win.focus()
          win.print()
          URL.revokeObjectURL(url)
        }, 300)
      })
    },
    _escapeHtmlForPdf(str) {
      if (!str) return ''
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
    },
    openTranslationDialog(note) {
      if (!note || !note.id) {
        this.$message.warning('请先保存笔记后再翻译')
        return
      }
      this.$refs.translationDialog.open()
    },
    onTranslated(newNote) {
      // 将新笔记加入列表，然后跳转到它
      this.notes.unshift(newNote)
      this.activeNoteId = newNote.id
      this.destroyEditor()
      this.loadActiveNote()
      this.$message.success('英文版本已创建')
    },
    openWechatPublish(note) {
      if (!note || !note.id) {
        this.$message.warning('请先保存笔记后再发布')
        return
      }
      this.$refs.wechatDialog.open(this.activeNote ? (this.activeNote.content || '') : '')
    },
    onWechatPublished(result) {
      this.wechatPublished = true
      const modeText = result.mode === 'PUBLISHED' ? '群发成功！' : '草稿已推送至微信公众号后台'
      this.$message.success(modeText)
    },
    async toggleNotePublic(note) {
      if (!note || !note.id) return
      
      try {
        // 对于 EditorJS 类型笔记，如果有未保存的更改，先保存
        if (this.isEditorjsNote && this.hasUnsavedChanges && this.editor) {
          await this.saveToBackend()
        }
        
        // 调用后端 API 切换公开状态
        const response = await this.$axios.patch(`/v1/notes/${note.id}/toggle-public`)
        
        // 从后端响应中获取更新后的状态
        const newIsPublic = response.data?.isPublic ?? !this.activeNote.isPublic
        
        // 更新本地状态
        this.activeNote.isPublic = newIsPublic
        
        // 更新列表中的笔记状态
        const listNote = this.notes.find(n => n.id === note.id)
        if (listNote) {
          listNote.isPublic = newIsPublic
        }
        
        // 刷新最近笔记列表
        await this.loadRecentNotes()
        
        this.$message.success(newIsPublic ? '笔记已公开' : '笔记已关闭')
      } catch (error) {
        this.$message.error('切换公开状态失败：' + (error.response?.data?.message || error.message))
      }
    },
    deleteNote(note) {
      this.$confirm(`确定要删除笔记 "${note.title}" 吗？`, '提示', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
      }).then(async () => {
        try {
          this.destroyEditor()
          await this.$axios.delete(`/v1/notes/${note.id}`)
        this.$message.success('删除成功')
        this.activeNoteId = null
        this.activeNote = null
        await this.loadNotes()
        // loadNotes 内部会调用 loadRecentNotes，所以这里不需要单独调用
        if (this.notes.length > 0) {
          this.activeNoteId = this.notes[0].id
          await this.loadActiveNote()
        }
        } catch (error) { this.$message.error('删除失败') }
      }).catch(() => {})
    },

    // ========== 标签编辑方法 ==========
    getTagType(tagId) {
      if (!this.activeNote || !this.activeNote.tags) return 'info'
      return this.activeNote.tags.some(t => t.id === tagId) ? 'primary' : 'info'
    },
    toggleNoteTag(tagId) {
      if (!this.activeNote || !this.isEditorjsNote) return
      
      // 确保 tags 是数组
      if (!Array.isArray(this.activeNote.tags)) {
        this.activeNote.tags = []
      }
      
      const index = this.activeNote.tags.findIndex(t => t.id === tagId)
      if (index > -1) {
        // 移除标签
        this.activeNote.tags.splice(index, 1)
      } else {
        // 添加标签
        const tag = this.tags.find(t => t.id === tagId)
        if (tag) {
          this.activeNote.tags.push(tag)
        }
      }
      
      // 标记有未保存变更并触发保存
      this.hasUnsavedChanges = true
      this.updateSaveStatus('saving')
      this.debouncedSave()
      
      // 更新左侧列表中的标签显示
      const listNote = this.notes.find(n => n.id === this.activeNote.id)
      if (listNote) {
        listNote.tags = [...this.activeNote.tags]
      }
    },
    async createNewTag() {
      const tagName = this.newTagName?.trim()
      if (!tagName) {
        return
      }

      // 检查标签是否已存在
      const existingTag = this.tags.find(
        (t) => t.name.toLowerCase() === tagName.toLowerCase()
      )
      if (existingTag) {
        // 如果已存在，直接选中
        if (!this.activeNote.tags || !this.activeNote.tags.some(t => t.id === existingTag.id)) {
          if (!Array.isArray(this.activeNote.tags)) {
            this.activeNote.tags = []
          }
          this.activeNote.tags.push(existingTag)
          this.hasUnsavedChanges = true
          this.updateSaveStatus('saving')
          this.debouncedSave()
        }
        this.newTagName = ''
        this.$message.info('标签已存在，已自动选中')
        return
      }

      try {
        const res = await this.$axios.post('/v1/tags', { name: tagName })
        this.tags.push(res.data)
        if (!Array.isArray(this.activeNote.tags)) {
          this.activeNote.tags = []
        }
        this.activeNote.tags.push(res.data)
        this.newTagName = ''
        this.$message.success('标签创建成功')
        
        // 标记有未保存变更并触发保存
        this.hasUnsavedChanges = true
        this.updateSaveStatus('saving')
        this.debouncedSave()
        
        // 更新左侧列表中的标签显示
        const listNote = this.notes.find(n => n.id === this.activeNote.id)
        if (listNote) {
          listNote.tags = [...this.activeNote.tags]
        }
      } catch (error) {
        this.$message.error('创建标签失败：' + (error.response?.data?.message || error.message))
      }
    },

    // ========== 项目相关方法 ==========
    onProjectChange() {
      if (!this.activeNote || !this.isEditorjsNote) return
      
      // 更新项目名称
      if (this.activeNote.projectId) {
        const project = this.projects.find(p => p.id === this.activeNote.projectId)
        if (project) {
          this.activeNote.projectName = project.name
        }
      } else {
        this.activeNote.projectName = null
      }
      
      // 标记有未保存变更并触发保存
      this.hasUnsavedChanges = true
      this.updateSaveStatus('saving')
      this.debouncedSave()
    },
    getProjectIcon(projectId) {
      if (!projectId) return null
      const project = this.projects.find(p => p.id === projectId)
      return project ? project.icon : null
    },

    // ========== 通用方法 ==========
    formatTime(time) {
      if (!time) return ''
      const normalized = typeof time === 'string' && !/Z$|[+-]\d{2}:\d{2}$/.test(time) ? time + 'Z' : time
      const date = new Date(normalized)
      const now = new Date()
      const diff = now - date
      const days = Math.floor(diff / (1000 * 60 * 60 * 24))
      if (days === 0) {
        const hours = Math.floor(diff / (1000 * 60 * 60))
        if (hours === 0) {
          const minutes = Math.floor(diff / (1000 * 60))
          return minutes === 0 ? this.$t('notesPage.time.justNow') : this.$t('notesPage.time.minutesAgo', { n: minutes })
        }
        return this.$t('notesPage.time.hoursAgo', { n: hours })
      } else if (days < 7) {
        return this.$t('notesPage.time.daysAgo', { n: days })
      }
      return date.toLocaleDateString()
    },

    // ========== 月历相关方法 ==========
    switchToListMode() {
      this.viewMode = 'list'
    },
    async switchToCalendarMode() {
      this.viewMode = 'calendar'
      // 如果还没有加载笔记日期数据，则加载
      if (this.allNotesForCalendar.length === 0) {
        await this.loadNotesDates()
      }
      // 滚动到当前月份由月历组件内部处理
    },
    openMonthCalendar(yearMonth) {
      this.calendarMonth = yearMonth
      this.showMonthCalendar = true
      if (this.activeNoteId) {
        const note = this.allNotesForCalendar.find(n => n.id === this.activeNoteId)
        this.previousNoteBeforeCalendar = note || null
      } else {
        this.previousNoteBeforeCalendar = null
      }
    },
    formatCalendarMonthTitle(ym) {
      if (!ym) return ''
      const [y, m] = ym.split('-').map(Number)
      return `${y}年${m}月`
    },
    async closeMonthCalendar() {
      this.showMonthCalendar = false
      this.calendarFromMonth = null
      const noteToRestore = this.previousNoteBeforeCalendar
      this.previousNoteBeforeCalendar = null
      if (noteToRestore && noteToRestore.id !== this.activeNoteId) {
        // 別のノートへ戻る場合は selectNote に任せる
        await this.selectNote(noteToRestore)
      } else if (this.activeNoteId) {
        // 同じノート: selectNote は early return するため、
        // nextTick でエディタ再マウント後に loadActiveNote を直接呼ぶ
        await this.$nextTick()
        await this.loadActiveNote()
      }
    },
    navigateCalendarMonth(delta) {
      const [y, m] = this.calendarMonth.split('-').map(Number)
      const d = new Date(y, m - 1 + delta, 1)
      this.calendarMonth = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    },
    async openNoteFromCalendar(note) {
      this.calendarFromMonth = this.calendarMonth
      this.showMonthCalendar = false
      await this.selectNote(note)
    },
    returnToCalendar() {
      const month = this.calendarFromMonth
      this.calendarFromMonth = null
      this.openMonthCalendar(month)
    },
    async loadNotesDates() {
      try {
        // 获取所有笔记（分页获取全部）
        let allNotes = []
        let page = 0
        const size = 100 // 每页获取更多数据
        let hasMore = true

        while (hasMore) {
          const { data } = await this.$axios.get('/v1/notes', {
            params: { page, size, sortBy: 'createdAt', direction: 'DESC' }
          })
          const notes = data.content || []
          allNotes = allNotes.concat(notes)
          
          if (notes.length < size || allNotes.length >= data.totalElements) {
            hasMore = false
          } else {
            page++
          }
        }

        this.allNotesForCalendar = allNotes
        // 构建日期到笔记的映射
        const notesByDate = {}
        allNotes.forEach(note => {
          if (note.createdAt) {
            const d = new Date(note.createdAt)
            const dateKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
            if (!notesByDate[dateKey]) {
              notesByDate[dateKey] = []
            }
            notesByDate[dateKey].push(note)
          }
        })
        this.notesByDate = notesByDate
      } catch (error) {
        console.error('加载笔记日期数据失败:', error)
        this.$message.error('加载笔记日期数据失败')
      }
    },
    async selectDate(dateStr) {
      // 如果是取消选中
      if (dateStr === null) {
        this.selectedDate = null
        this.selectedDateNotes = []
        return
      }

      this.selectedDate = dateStr
      const notes = this.notesByDate[dateStr] || []
      this.selectedDateNotes = notes
      this.selectedDateNoteIndex = 0

      if (notes.length > 0) {
        // 加载第一个笔记
        await this.selectNote(notes[0])
      }
      // 如果没有笔记，保持当前显示的笔记不变
    },
    handleDateNavigation(direction) {
      if (this.selectedDateNotes.length <= 1) return
      
      if (direction === 'next') {
        this.selectedDateNoteIndex = (this.selectedDateNoteIndex + 1) % this.selectedDateNotes.length
      } else if (direction === 'prev') {
        this.selectedDateNoteIndex = this.selectedDateNoteIndex === 0 
          ? this.selectedDateNotes.length - 1 
          : this.selectedDateNoteIndex - 1
      }
      
      const note = this.selectedDateNotes[this.selectedDateNoteIndex]
      if (note) {
        this.selectNote(note)
      }
    },
  }
}
</script>

<style scoped lang="scss">
.notes-page {
  background: transparent;
}

.workspace-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1.5fr) 260px;
  gap: 16px;
  transition: grid-template-columns 0.3s ease, height 0.3s ease;

  &.right-collapsed {
    grid-template-columns: 280px minmax(0, 1fr);
  }
}

.workspace-sidebar {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 12px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden; // 移除侧边栏整体滚动，让笔记列表区域独立滚动
  height: 100%; // 确保侧边栏占满 grid 容器高度
  max-height: 100%; // 限制最大高度
}

.sidebar-view-toggle {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 0 4px 4px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 4px;
}

.view-toggle-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.2s;
  padding: 0;

  i {
    font-size: 14px;
  }

  &:hover {
    background: var(--bg-secondary);
    color: var(--text-color);
  }

  &.active {
    background: rgba(102, 126, 234, 0.12);
    color: #667eea;
  }
}

.sidebar-calendar {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-section + .sidebar-section {
  border-top: 1px solid var(--border-color);
  padding-top: 8px;
}

.sidebar-search { margin-bottom: 8px; }
.sidebar-filters { margin-bottom: 8px; }

.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.filter-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.filter-toggle {
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 4px;
  &:hover { color: var(--text-color); }
}

.filter-arrow {
  font-size: 12px;
  transition: transform 0.2s;
  transform: rotate(90deg);
  &.filter-arrow--collapsed { transform: rotate(0deg); }
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.filter-tag-item { cursor: pointer; }

.sidebar-section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.section-subtitle {
  font-size: 12px;
  color: var(--text-muted);
}

.sidebar-notes {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0; // 允许 flex 子元素收缩
  overflow: hidden; // 防止整个 section 滚动
  // 确保有明确的高度约束，以便子元素能够正确计算滚动
  height: 0; // 配合 flex: 1 使用，强制计算剩余空间
}

// 列表区域包装器（承载 v-loading，限定加载效果范围）
.note-list-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative; // 使 loading mask 相对于此容器定位
  overflow: hidden;
}

.note-list {
  flex: 1;
  overflow-y: auto; // 垂直滚动
  overflow-x: auto; // 横向滚动
  padding-right: 4px;
  padding-bottom: 4px;
  min-height: 0; // 允许收缩
  // 确保有明确的高度约束才能触发滚动
  height: 100%; // 占满父容器高度
  // 自定义滚动条样式，适用于所有模块的列表区域
  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 3px;
    &:hover {
      background: var(--text-muted);
    }
  }
  // Firefox 滚动条样式
  scrollbar-width: thin;
  scrollbar-color: var(--border-color) transparent;
}

.note-list-item {
  padding: 8px;
  border-radius: 8px;
          cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 4px;
  min-width: fit-content; // 允许内容决定最小宽度
  width: 100%; // 默认占满容器宽度

  &:hover { background: var(--bg-secondary); }
  &.active {
    background: rgba(102, 126, 234, 0.12);
    border: 1px solid #667eea;
  }
}

.note-list-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color);
  margin-bottom: 4px;
  white-space: nowrap; // 不换行
  overflow-x: auto; // 横向滚动
  overflow-y: hidden; // 隐藏垂直滚动
  // 自定义滚动条样式（可选）
  &::-webkit-scrollbar {
    height: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 2px;
          &:hover {
      background: var(--text-muted);
    }
  }
}

.note-list-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-muted);
}

.note-list-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--tag-bg);
  color: var(--tag-color);
}

.sidebar-empty {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 4px;

  .el-button { margin-top: 8px; }
}

.sidebar-pagination {
  margin-top: 8px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;

  ::v-deep .el-pagination.el-pagination--small {
    padding: 0;

    .btn-prev, .btn-next {
      padding: 0 2px;
      min-width: 22px;
    }

    .el-pager {
      li {
        min-width: 22px;
        padding: 0 2px;
        margin: 0 1px;
      }
    }
  }
}

.workspace-main {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 16px 20px;
  overflow-y: auto;
}

.note-main {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-main-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.calendar-note-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  font-size: 12px;
}

.calendar-nav-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--card-bg-color);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  padding: 0;

  &:hover:not(:disabled) {
    border-color: #667eea;
    color: #667eea;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  i { font-size: 12px; }
}

.calendar-nav-info {
  color: var(--text-color);
  font-weight: 500;
}

.calendar-nav-hint {
  color: var(--text-muted);
  margin-left: 4px;
}

.note-main-title-wrapper {
  flex: 1;
  &--editorjs { padding-left: 60px; }
}

.note-main-info-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 12px;
}

.note-main-title-input ::v-deep .el-input__inner {
  font-size: 20px;
  font-weight: 600;
  border: none;
  padding-left: 0;
  color: var(--text-color);
  background: transparent;
}

.note-main-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  .save-status-inline i { font-size: 12px; }
  .wechat-published-inline { color: #07c160; }
  .note-lang-badge {
    display: inline-block;
    background: #e6e8eb;
    color: #606266;
    font-size: 10px;
    border-radius: 3px;
    padding: 0 4px;
    margin-left: 4px;
    vertical-align: middle;
    line-height: 16px;
    font-weight: 500;
  }
}

.note-main-source {
  margin-top: 2px;
  font-size: 13px;
  color: var(--text-muted);

  .note-main-source-link {
    color: #667eea;
    text-decoration: none;
  }

  .note-main-source-link:hover {
    text-decoration: underline;
  }
}

.note-main-actions {
  display: flex;
  align-items: center;
  gap: 8px;

  ::v-deep .el-input__inner::placeholder {
    color: #606266;
  }
}

.note-main-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.note-main-tags-editable {
  margin-bottom: 2px;
  padding-left: 60px;
}

// 标签选择器样式（EditorJS 笔记使用）
.tag-selector {
  width: 100%;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  // margin-bottom: 12px;
  min-height: 24px;
}

.tag-item {
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;

  &:hover {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

.tag-input-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tag-input {
  flex: 1;
}

.note-main-summary {
  background: var(--hero-gradient);
  border-radius: 8px;
  padding: 12px 14px;
  color: var(--text-color);

  .summary-header {
    display: flex;
    align-items: center;
    gap: 6px;
        font-size: 14px;
    font-weight: 600;
    margin-bottom: 6px;
  }
  .summary-content {
    font-size: 13px;
        line-height: 1.6;
    opacity: 0.9;
  }
}

.note-main-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.editorjs-workspace-container { min-height: 300px; }

.note-main-section {
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
  &:last-child { border-bottom: none; }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
          margin-bottom: 4px;
        }

.section-label {
  font-size: 13px;
  font-weight: 500;
  color: #667eea;
}

.section-preview {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.note-main-single {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}

// Markdown 内容显示样式
.markdown-content-display {
  font-size: 15px;
  color: var(--text-color);
  // line-height: 1.8;

  h1, h2, h3, h4 {
    margin: 1.2em 0 0.6em;
    font-weight: 600;
    color: var(--text-color);
    &:first-child { margin-top: 0; }
  }
  h1 { font-size: 1.8em; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3em; }
  h2 { font-size: 1.5em; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3em; }
  h3 { font-size: 1.25em; }
  h4 { font-size: 1.1em; }

  blockquote {
    margin: 1em 0;
    padding: 0.5em 1em;
    border-left: 4px solid var(--primary-color);
    background: var(--bg-secondary);
    color: var(--text-secondary);
  }

  pre.md-code-block {
    margin: 1em 0;
    padding: 1em;
    background: var(--bg-tertiary);
    border-radius: 6px;
    overflow-x: auto;
    code {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      color: var(--text-color);
    }
  }

  code.md-inline-code {
    padding: 0.2em 0.4em;
    background: var(--bg-secondary);
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 0.9em;
    color: var(--primary-color);
  }

  a {
    color: var(--primary-color);
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  img.md-image {
    max-width: 100%;
    height: auto;
    border-radius: 4px;
    margin: 1em 0;
  }

  ul {
    margin: 1em 0;
    padding-left: 2em;
    li { margin: 0.3em 0; }
  }

  hr.md-hr {
    margin: 1.5em 0;
    border: none;
    border-top: 1px solid var(--border-color);
  }

  strong { font-weight: 600; }
  em { font-style: italic; }
}

// 代码块显示样式
.code-block-display {
  margin: 1em 0;
  padding: 1em;
  background: var(--bg-tertiary);
  border-radius: 6px;
  overflow-x: auto;
  code {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    color: var(--text-color);
  }
}

// 纯文本内容显示样式
.plain-content-display {
  font-size: 15px;
  color: var(--text-color);
  line-height: 1.8;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.note-main-empty {
  height: 100%;
        display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;

  .empty-icon {
    font-size: 40px;
    color: var(--text-placeholder);
    margin-bottom: 10px;
  }
  .empty-text {
    font-size: 14px;
    color: var(--text-muted);
  }
}

// 实体表单
.entity-form-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.entity-form-header {
  display: flex;
  align-items: center;
        justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.entity-form-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
}

.entity-form-actions {
  display: flex;
  gap: 8px;
}

.entity-form {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;

  ::v-deep .el-form-item__label {
    font-size: 13px;
    color: var(--text-secondary);
    padding-bottom: 4px;
  }
}

.dynamic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
        align-items: center;

  .el-tag { margin: 0; }
  .tag-input { width: 120px; }
  .tag-add-btn {
    border-style: dashed;
  }
}

// 右侧面板
.workspace-right {
  background: var(--card-bg-color);
  border-radius: 12px;
  border: 1px solid var(--border-color);
  padding: 12px 12px 8px;
  overflow-y: auto;
}

.right-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.right-section {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
  &:last-child { border-bottom: none; }
}

.right-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.outline-list, .recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.outline-item {
  display: flex;
  align-items: flex-start;
  padding: 4px 0;
  font-size: 13px;
  color: var(--text-secondary);
  transition: padding-left 0s;
}

.outline-text { flex: 1; }

.recent-item {
  padding: 4px 0;
  cursor: pointer;
  border-radius: 4px;
  &:hover { background: var(--bg-secondary); }
}

.recent-title {
  font-size: 13px;
  color: var(--text-secondary);
}

.recent-meta {
        font-size: 12px;
  color: var(--text-muted);
}

.right-empty-text {
  font-size: 12px;
  color: var(--text-muted);
}

.linkedin-status-line {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0 0 4px;
}

.linkedin-warn {
  color: #e6a23c;
  margin-left: 4px;
}

.linkedin-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
}

.right-meta-list {
          display: flex;
  flex-direction: column;
  gap: 8px;
}

.right-meta-item {
  display: flex;
  justify-content: space-between;
          align-items: center;
  font-size: 13px;
}

.meta-label {
  color: var(--text-muted);
}

.meta-value {
  color: var(--text-secondary);
  font-weight: 500;
}

.meta-link {
  color: #667eea;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

@media screen and (max-width: 1024px) {
  .workspace-layout {
    grid-template-columns: 260px minmax(0, 1.5fr);
  }
  .workspace-right { display: none; }
}

@media screen and (max-width: 768px) {
  .workspace-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
}

// 折叠/展开按钮
.panel-toggle-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.2s;
  vertical-align: middle;

  i { font-size: 14px; }

  &:hover {
    background: var(--bg-secondary);
    color: var(--text-color);
  }
}

// 全屏模式：让 workspace-main 直接覆盖整个视口
.workspace-main--fullscreen {
  position: fixed !important;
  inset: 0;
  z-index: 2000;
  background: var(--bg-color);
  border-radius: 0 !important;
  border: none !important;
  overflow-y: auto;
  padding: 16px 32px;
}

/* ===== 大月カレンダー main area ===== */
.month-calendar-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.month-calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px 10px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.month-calendar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.cal-month-title {
  font-size: 20px;
  font-weight: 400;
  color: var(--text-color, #e4e4ef);
  letter-spacing: -0.2px;
}
.cal-nav-group {
  display: flex;
  gap: 2px;
}
.cal-nav-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-secondary, #9999b3);
  font-size: 14px;
  transition: background 0.15s;
  outline: none;
}
.cal-nav-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-color, #e4e4ef);
}
.cal-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-secondary, #9999b3);
  font-size: 16px;
  transition: background 0.15s;
  outline: none;
}
.cal-close-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-color, #e4e4ef);
}
.cal-grid-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  flex-shrink: 0;
}
.cal-weekday {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted, #585b70);
  text-align: center;
  padding: 7px 0;
  letter-spacing: 0.5px;
}
.cal-weekday:nth-child(6) { color: #5b8fd9; }
.cal-weekday:nth-child(7) { color: #c65b5b; }
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  grid-auto-rows: 1fr;
  flex: 1;
  min-height: 0;
  border-left: 1px solid rgba(255, 255, 255, 0.07);
  overflow-y: auto;
}
.cal-day {
  border-right: 1px solid rgba(255, 255, 255, 0.07);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  padding: 5px 5px 3px;
  overflow: hidden;
  transition: background 0.1s;
}
.cal-day:hover {
  background: rgba(255, 255, 255, 0.025);
}
.cal-day-empty {
  border-right: 1px solid rgba(255, 255, 255, 0.07);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  background: rgba(0, 0, 0, 0.08);
}
.cal-day-weekend {
  background: rgba(0, 0, 0, 0.04);
}
.cal-day-weekend:hover {
  background: rgba(255, 255, 255, 0.02);
}
.cal-day-header {
  margin-bottom: 3px;
}
.cal-day-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  color: var(--text-secondary, #9999b3);
  border-radius: 50%;
  font-weight: 400;
  line-height: 1;
}
.cal-day-num--today {
  background: var(--el-color-primary, #6c63ff);
  color: #fff !important;
  font-weight: 600;
}
.cal-day-weekend .cal-day-num {
  color: #7a7ab0;
}
.cal-day-notes {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cal-note-chip {
  display: block;
  font-size: 14px;
  line-height: 1.3;
  padding: 2px 6px;
  border-radius: 3px;
  background: rgba(108, 99, 255, 0.15);
  color: var(--text-color);
  border-left: 2px solid var(--el-color-primary, #6c63ff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}
.cal-note-chip:hover {
  background: var(--el-color-primary, #6c63ff);
  color: #fff;
}

/* ===== ブレッドクラム ===== */
.calendar-breadcrumb {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: var(--el-color-primary, #6c63ff);
  cursor: pointer;
  margin-bottom: 12px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
  user-select: none;
}
.calendar-breadcrumb:hover {
  background: rgba(108, 99, 255, 0.1);
  text-decoration: underline;
}
</style>
