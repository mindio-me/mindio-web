/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ProjectNoteSummaryResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目页 AI 助手"读关联笔记起草文案"（Spec B）专用：把 Note.project 外键关联的笔记
 * 转成模型可读的纯文本摘要。单独成服务而不是塞进 NoteService/ProjectService，是因为
 * 这是"AI 工具用的数据整形"职责，和 chunkNote 在 GlobalChatService.currentNoteBodyText
 * 里的既有用法同类，不是笔记/项目本身的 CRUD 职责。
 */
@Service
public class ProjectNoteContextService {

    // 项目关联笔记可能不止一条，单条上限比"当前笔记"场景（4000字，见
    // GlobalChatService.CURRENT_NOTE_BODY_CHAR_CAP）更小，控制多条笔记合计塞进
    // 系统提示的总大小。
    private static final int NOTE_BODY_CHAR_CAP = 1500;

    private final NoteRepository noteRepository;
    private final ContentChunkingService chunkingService;

    public ProjectNoteContextService(NoteRepository noteRepository, ContentChunkingService chunkingService) {
        this.noteRepository = noteRepository;
        this.chunkingService = chunkingService;
    }

    @Transactional(readOnly = true)
    public List<ProjectNoteSummaryResponse> getNoteSummaries(Long projectId) {
        return noteRepository.findByProjectId(projectId).stream()
                .map(this::toSummary)
                .toList();
    }

    private ProjectNoteSummaryResponse toSummary(Note note) {
        String bodyText = String.join("\n", chunkingService.chunkNote(note));
        if (bodyText.length() > NOTE_BODY_CHAR_CAP) {
            bodyText = bodyText.substring(0, NOTE_BODY_CHAR_CAP) + "…（内容过长，已截断）";
        }
        return new ProjectNoteSummaryResponse(note.getId(), note.getTitle(), bodyText);
    }
}
