/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingKeys;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import com.entropybits.worknotes.spring_boot.dto.FileResultVo;
import com.entropybits.worknotes.spring_boot.dto.ShareCardBackgroundResponse;
import com.entropybits.worknotes.spring_boot.dto.ShareCardResponse;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.exception.UnauthorizedException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.AgentCallException;
import com.entropybits.worknotes.spring_boot.service.AgentServiceClient;
import com.entropybits.worknotes.spring_boot.service.AgentUnavailableException;
import com.entropybits.worknotes.spring_boot.service.UploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 笔记分享卡片（网站卡片）：读取当前卡片与 AI 生图可用性。
 */
@Service
public class ShareCardService {

    private static final int TEXT_MAX_CHARS = 1500;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AiSettingsService aiSettingsService;
    private final AgentServiceClient agentServiceClient;
    private final UploadService uploadService;
    private final TransactionTemplate readTx;
    private final boolean aiEnabled;

    public ShareCardService(NoteRepository noteRepository,
                            UserRepository userRepository,
                            AiSettingsService aiSettingsService,
                            AgentServiceClient agentServiceClient,
                            UploadService uploadService,
                            PlatformTransactionManager transactionManager,
                            @Value("${worknotes.share-card.ai-enabled:true}") boolean aiEnabled) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.aiSettingsService = aiSettingsService;
        this.agentServiceClient = agentServiceClient;
        this.uploadService = uploadService;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.aiEnabled = aiEnabled;
    }

    /** 调 agents 生成背景图并存进上传目录；只返回 URL，不改动笔记（保存由 PUT 完成） */
    public ShareCardBackgroundResponse generateBackground(Long noteId, String username, String prompt) {
        // 数据库读取放进短事务（owner 是懒加载）；120 秒的生图调用和上传都不占着连接
        String[] titleAndText = readTx.execute(status -> {
            Note note = noteRepository.findById(noteId)
                    .orElseThrow(() -> new ResourceNotFoundException("笔记", "id", noteId));
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("用户", "username", username));
            if (!note.getOwner().getId().equals(user.getId())) {
                throw new UnauthorizedException("无权限为此笔记生成分享卡片");
            }
            return new String[]{note.getTitle(), NotePlainText.extract(note, TEXT_MAX_CHARS)};
        });
        Map<String, String> imageConfig = effectiveImageConfig();
        String availability = availabilityOf(imageConfig);
        if ("disabled".equals(availability)) {
            throw new BadRequestException("AI 生成背景功能已被关闭");
        }
        if ("not_configured".equals(availability)) {
            throw new BadRequestException("尚未配置生图服务商或 API Key，请先在设置里配置");
        }
        Map<String, Object> result;
        try {
            result = agentServiceClient.shareCardBackground(titleAndText[0], titleAndText[1], prompt, imageConfig);
        } catch (AgentCallException | AgentUnavailableException e) {
            throw new BadRequestException(e.getMessage());
        }
        Object image = result.get("imageBase64");
        if (!(image instanceof String b64) || b64.isBlank()) {
            throw new BadRequestException("AI 服务没有返回图片");
        }
        Object mime = result.get("mimeType");
        String mimeType = mime instanceof String m && !m.isBlank() ? m : "image/png";
        FileResultVo saved = uploadService.base64Upload("data:" + mimeType + ";base64," + b64, "share-card", 3);
        Object usedPrompt = result.get("prompt");
        return new ShareCardBackgroundResponse(usedPrompt == null ? null : usedPrompt.toString(), saved.getUrl());
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
        return availabilityOf(effectiveImageConfig());
    }

    private String availabilityOf(Map<String, String> imageConfig) {
        if (!aiEnabled) {
            return "disabled";
        }
        String provider = imageConfig.get("provider");
        if (provider == null || provider.isBlank()) {
            return "not_configured";
        }
        String apiKey = imageConfig.get("apiKey");
        return apiKey == null || apiKey.isBlank() ? "not_configured" : "available";
    }

    /**
     * 生图的生效配置（后端是唯一来源）：服务商/模型来自设置，Key 和地址取该服务商在 env+库 合并后的值。
     * 判断可用性和实际调用都用它，并随请求下发给 agents，避免两边读到不同的配置。
     * 值可能为 null（未配置）。
     */
    private Map<String, String> effectiveImageConfig() {
        Map<String, String> settings = aiSettingsService.imageConfig();
        String provider = settings.get(AiSettingKeys.IMAGE_PROVIDER);
        Map<String, String> out = new java.util.HashMap<>();
        if (provider == null || provider.isBlank()) {
            return out;
        }
        out.put("provider", provider);
        AiProperties cfg = aiSettingsService.current();
        AiProperties.ProviderConfig pc = switch (provider) {
            case "openai" -> cfg.getOpenai();
            case "doubao" -> cfg.getDoubao();
            default -> null;
        };
        if (pc != null) {
            putIfPresent(out, "apiKey", pc.getApiKey());
            putIfPresent(out, "baseUrl", pc.getBaseUrl());
        }
        putIfPresent(out, "model", settings.get(AiSettingKeys.IMAGE_MODEL));
        return out;
    }

    private static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) {
            m.put(k, v);
        }
    }
}
