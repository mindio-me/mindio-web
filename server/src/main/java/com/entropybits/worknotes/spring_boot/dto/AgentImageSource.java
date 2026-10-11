/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

/**
 * agent 插入图片的来源。type：note/clip（配合 id + index，引用已有笔记/收藏里的第 index 张图）、
 * attachment（url 是聊天附件已上传的本地地址）、url（网上的图片，下载到本地后插入）。
 */
public record AgentImageSource(String type, Long id, Integer index, String url) {}
