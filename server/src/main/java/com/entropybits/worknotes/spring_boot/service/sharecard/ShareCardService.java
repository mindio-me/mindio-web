/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingKeys;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.dto.ShareCardResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 笔记分享卡片（网站卡片）：读取当前卡片与 AI 生图可用性。
 */
@Service
public class ShareCardService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AiSettingsService aiSettingsService;
    private final boolean aiEnabled;

    public ShareCardService(NoteRepository noteRepository,
                            UserRepository userRepository,
                            AiSettingsService aiSettingsService,
                            @Value("${worknotes.share-card.ai-enabled:true}") boolean aiEnabled) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.aiSettingsService = aiSettingsService;
        this.aiEnabled = aiEnabled;
    }

    @Transactional(readOnly = true)
    public ShareCardResponse getCard(Long noteId, String username) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记", "id", noteId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户", "username", username));
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException("无权限查看此笔记的分享卡片");
        }
        return new ShareCardResponse(note.getId(), Boolean.TRUE.equals(note.getIsPublic()),
                note.getShareCardSource(), note.getShareCardImageUrl(), note.getShareCardBgUrl(),
                note.getShareCardGradient(), note.getShareCardAiPrompt(), note.getShareCardTitle(),
                note.getShareCardOverlay(), aiAvailability());
    }

    /** available | disabled（部署开关关闭）| not_configured（没选生图服务商或没有对应 Key） */
    public String aiAvailability() {
        if (!aiEnabled) {
            return "disabled";
        }
        String provider = aiSettingsService.imageConfig().get(AiSettingKeys.IMAGE_PROVIDER);
        if (provider == null || provider.isBlank()) {
            return "not_configured";
        }
        AiProperties cfg = aiSettingsService.current();
        String apiKey = switch (provider) {
            case "openai" -> cfg.getOpenai().getApiKey();
            case "doubao" -> cfg.getDoubao().getApiKey();
            default -> null;
        };
        return apiKey == null || apiKey.isBlank() ? "not_configured" : "available";
    }
}
