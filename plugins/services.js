/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import ClipService from '~/services/clipService'
import LocalDocService from '~/services/localDocService'
import LocalMediaService from '~/services/localMediaService'
import ProjectService from '~/services/projectService'
import AchievementService from '~/services/achievementService'
import ResourceService from '~/services/resourceService'
import NoteService from '~/services/noteService'
import TagService from '~/services/tagService'
import UploadService from '~/services/uploadService'
import ProfileService from '~/services/profileService'
import FeishuService from '~/services/feishuService'
import WechatService from '~/services/wechatService'
import WechatBindingService from '~/services/wechatBindingService'
import AiService from '~/services/aiService'
import RedditService from '~/services/redditService'
import BookmarkImportService from '~/services/bookmarkImportService'
import BookmarkAgentService from '~/services/bookmarkAgentService'

export default ({ $axios }, inject) => {
  inject('projectService', new ProjectService($axios))
  inject('achievementService', new AchievementService($axios))
  inject('resourceService', new ResourceService($axios))
  inject('noteService', new NoteService($axios))
  inject('tagService', new TagService($axios))
  inject('uploadService', new UploadService($axios))
  inject('profileService', new ProfileService($axios))
  inject('feishuService', new FeishuService($axios))
  inject('wechatService', new WechatService($axios))
  inject('wechatBindingService', new WechatBindingService($axios))
  inject('aiService', new AiService($axios))
  inject('redditService', new RedditService($axios))
  inject('clipService', new ClipService($axios))
  inject('bookmarkImportService', new BookmarkImportService($axios))
  inject('bookmarkAgentService', new BookmarkAgentService($axios))
  inject('localDocService', new LocalDocService($axios))
  inject('localMediaService', new LocalMediaService($axios))
}
