/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.dto.AchievementResponse;
import com.entropybits.worknotes.spring_boot.dto.ProfileResponse;
import com.entropybits.worknotes.spring_boot.dto.ProjectResponse;
import com.entropybits.worknotes.spring_boot.dto.SiteExportResult;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.integration.wechat.EditorJsToHtmlConverter;
import com.entropybits.worknotes.spring_boot.integration.wechat.MarkdownToHtmlConverter;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 桌面版「导出网站」：首次导出按 templateId 从 templatesRoot 下对应的
 * 模板目录，把可编辑的 Vue 源码工程（含 node_modules）拷贝到目标目录；
 * 重复导出保留已有源码改动，只刷新笔记/项目/站主资料数据和引用到的
 * 本地图片，此时 templateId 不生效。实际的 Vite 构建由 Electron 主
 * 进程负责，见 desktop-electron/electron/main.js 的
 * mindio:site-export:build。
 */
@Service
public class SiteExportService {

    private static final Logger logger = LoggerFactory.getLogger(SiteExportService.class);

    private final NoteRepository noteRepository;
    private final ProjectService projectService;
    private final ProfileRepository profileRepository;
    private final AchievementService achievementService;
    private final UploadPathConfig uploadPathConfig;
    private final EditorJsToHtmlConverter editorJsToHtmlConverter;
    private final MarkdownToHtmlConverter markdownToHtmlConverter;
    private final ObjectMapper objectMapper;

    @Value("${worknotes.upload.url-prefix:}")
    private String uploadUrlPrefix;

    @Value("${worknotes.site-export.templates-root:}")
    private String templatesRoot;

    public SiteExportService(
            NoteRepository noteRepository,
            ProjectService projectService,
            ProfileRepository profileRepository,
            AchievementService achievementService,
            UploadPathConfig uploadPathConfig,
            EditorJsToHtmlConverter editorJsToHtmlConverter,
            MarkdownToHtmlConverter markdownToHtmlConverter,
            ObjectMapper objectMapper
    ) {
        this.noteRepository = noteRepository;
        this.projectService = projectService;
        this.profileRepository = profileRepository;
        this.achievementService = achievementService;
        this.uploadPathConfig = uploadPathConfig;
        this.editorJsToHtmlConverter = editorJsToHtmlConverter;
        this.markdownToHtmlConverter = markdownToHtmlConverter;
        this.objectMapper = objectMapper;
    }

    // 供测试直接设置 @Value 字段（测试不跑 Spring 容器）
    void setUploadUrlPrefix(String uploadUrlPrefix) {
        this.uploadUrlPrefix = uploadUrlPrefix;
    }

    void setTemplatesRoot(String templatesRoot) {
        this.templatesRoot = templatesRoot;
    }

    private static final String DEFAULT_LOCALE = "zh-CN";

    @Transactional(readOnly = true)
    public SiteExportResult export(String targetPathRaw, String templateId) {
        return export(targetPathRaw, templateId, DEFAULT_LOCALE);
    }

    @Transactional(readOnly = true)
    public SiteExportResult export(String targetPathRaw, String templateId, String locale) {
        Path target = validateTargetPath(targetPathRaw);
        SiteExportImageRewriter rewriter =
                new SiteExportImageRewriter(uploadUrlPrefix, Path.of(uploadPathConfig.getUploadPath()));

        List<SiteExportImageRewriter.Match> allMatches = new ArrayList<>();

        ProfileResponse ownerProfile = loadOwnerProfile();
        ObjectNode profileJson = buildProfileJson(ownerProfile, rewriter, allMatches);

        List<Note> publicNotes = noteRepository.findByIsPublicTrueOrderByModifiedAtDesc();
        ArrayNode notesJson = objectMapper.createArrayNode();
        for (Note note : publicNotes) {
            notesJson.add(buildNoteJson(note, rewriter, allMatches));
        }

        List<ProjectResponse> publicProjects = projectService.getAllPublicProjects();
        ArrayNode projectsJson = objectMapper.createArrayNode();
        for (ProjectResponse project : publicProjects) {
            projectsJson.add(buildProjectJson(project, rewriter, allMatches));
        }

        List<AchievementResponse> activeAchievements = achievementService.getAllActiveAchievements();
        ArrayNode achievementsJson = objectMapper.createArrayNode();
        for (AchievementResponse achievement : activeAchievements) {
            achievementsJson.add(buildAchievementJson(achievement, rewriter, allMatches));
        }

        String resolvedLocale = (locale == null || locale.isBlank()) ? DEFAULT_LOCALE : locale;

        List<String> warnings = new ArrayList<>();
        try {
            if (!isExistingProject(target)) {
                Path templateDir = resolveTemplateDir(templateId);
                scaffoldSourceProject(target, templateDir);
            }
            resetGeneratedAssetDirectories(target);
            writeSiteDataScript(target, profileJson, notesJson, projectsJson, achievementsJson, resolvedLocale);
            copyMatchedAssets(target, allMatches, warnings);
        } catch (IOException e) {
            logger.error("站点导出失败", e);
            throw new BadRequestException("导出失败: " + e.getMessage());
        }

        return SiteExportResult.builder()
                .success(true)
                .targetPath(target.toString())
                .exportedNoteCount(publicNotes.size())
                .exportedProjectCount(publicProjects.size())
                .skippedImageCount(warnings.size())
                .warnings(warnings)
                .build();
    }

    private static final String PROJECT_MARKER_NAME = "mindio-user-site";

    /**
     * 判断目标目录是不是已经存在一个由本功能生成过的项目（package.json
     * 的 name 字段等于约定标记）。是的话，重复导出要跳过源码脚手架，
     * 保留用户/AI 对 src/ 等文件的改动。
     */
    private boolean isExistingProject(Path target) {
        Path packageJsonFile = target.resolve("package.json");
        if (!Files.isRegularFile(packageJsonFile)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(packageJsonFile.toFile());
            return PROJECT_MARKER_NAME.equals(node.path("name").asText(null));
        } catch (IOException e) {
            logger.warn("读取已存在的 package.json 失败，按全新项目处理: {}", packageJsonFile, e);
            return false;
        }
    }

    /**
     * 首次导出时，把请求里的 templateId 解析成 templatesRoot 下对应的
     * 模板源目录。重复导出不会走到这里（见 export() 里的判断）。
     */
    private Path resolveTemplateDir(String templateId) {
        if (templatesRoot == null || templatesRoot.isBlank()) {
            throw new BadRequestException("未配置站点模板根目录 (worknotes.site-export.templates-root)");
        }
        if (templateId == null || templateId.isBlank()) {
            throw new BadRequestException("未指定站点模板 templateId");
        }
        Path dir = Path.of(templatesRoot).resolve(templateId);
        if (!Files.isDirectory(dir)) {
            throw new BadRequestException("站点模板不存在: " + templateId);
        }
        return dir;
    }

    private ProfileResponse loadOwnerProfile() {
        // Query the repository directly instead of going through
        // ProfileService.getOwnerProfile(), which throws when no profile
        // row exists yet. That method is itself @Transactional; letting its
        // exception cross that boundary marks the transaction this method
        // shares (default propagation) as rollback-only, so even catching
        // it here still fails the whole export with
        // UnexpectedRollbackException once export() tries to commit --
        // after all the file writes have already happened.
        return profileRepository.findFirstByOrderByIdAsc()
                .map(ProfileResponse::fromEntity)
                .orElseGet(ProfileResponse::new);
    }

    private Path validateTargetPath(String targetPathRaw) {
        if (targetPathRaw == null || targetPathRaw.isBlank()) {
            throw new BadRequestException("导出目录不能为空");
        }

        Path target = Path.of(targetPathRaw).toAbsolutePath().normalize();
        Path root = target.getRoot();

        if (root != null && target.equals(root)) {
            throw new BadRequestException("不能导出到磁盘根目录");
        }

        if (root != null) {
            List<Path> forbidden = List.of(root.resolve("Windows"), root.resolve("Program Files"),
                    root.resolve("Program Files (x86)"));
            for (Path dir : forbidden) {
                if (target.startsWith(dir)) {
                    throw new BadRequestException("不能导出到系统目录: " + dir);
                }
            }
        }

        return target;
    }

    private ObjectNode buildProfileJson(
            ProfileResponse profile,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fullName", profile.getFullName());
        node.put("title", profile.getTitle());
        node.put("bio", profile.getBio());
        node.put("avatarUrl", rewriteSingleUrl(profile.getAvatarUrl(), rewriter, allMatches));
        node.put("location", profile.getLocation());
        node.put("website", profile.getWebsite());
        node.put("github", profile.getGithub());
        node.put("linkedin", profile.getLinkedin());
        node.put("twitter", profile.getTwitter());
        node.put("wechat", profile.getWechat());
        node.put("wechatQrUrl", rewriteSingleUrl(profile.getWechatQrUrl(), rewriter, allMatches));
        node.put("email", profile.getEmail());
        return node;
    }

    private ObjectNode buildNoteJson(
            Note note,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", note.getId());
        node.put("title", note.getTitle());
        node.put("summary", note.getSummary());
        node.put("contentHtml", renderContentHtml(note.getContent(), note.getContentType(), rewriter, allMatches));
        node.put("createdAt", note.getCreatedAt() != null ? note.getCreatedAt().toString() : null);
        node.put("modifiedAt", note.getModifiedAt() != null ? note.getModifiedAt().toString() : null);

        ArrayNode tags = objectMapper.createArrayNode();
        for (Tag tag : note.getTags()) {
            tags.add(tag.getName());
        }
        node.set("tags", tags);

        return node;
    }

    private ObjectNode buildProjectJson(
            ProjectResponse project,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", project.getId());
        node.put("name", project.getName());
        node.put("subtitle", project.getSubtitle());
        node.put("description", project.getDescription());
        node.put("imageUrl", rewriteSingleUrl(project.getImageUrl(), rewriter, allMatches));
        node.put("projectUrl", project.getProjectUrl());
        node.put("githubUrl", project.getGithubUrl());
        node.put("category", project.getCategory());
        node.put("icon", project.getIcon());
        node.put("technologies", project.getTechnologies());
        node.put("contentHtml", renderContentHtml(project.getContent(), project.getContentType(), rewriter, allMatches));
        node.put("isFeatured", Boolean.TRUE.equals(project.getIsFeatured()));
        node.put("displayOrder", project.getDisplayOrder());
        return node;
    }

    private ObjectNode buildAchievementJson(
            AchievementResponse achievement,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", achievement.getId());
        node.put("title", achievement.getTitle());
        node.put("subtitle", achievement.getSubtitle());
        node.put("type", achievement.getType());
        node.put("status", achievement.getStatus());
        node.put("icon", achievement.getIcon());
        node.put("iconVariant", achievement.getIconVariant());
        node.put("technologies", achievement.getTechnologies());
        node.put("displayOrder", achievement.getDisplayOrder());

        String description = achievement.getDescription();
        if (description != null && !description.isBlank()) {
            SiteExportImageRewriter.RewriteResult rewritten = rewriter.rewriteText(description);
            allMatches.addAll(rewritten.matches());
            node.put("description", rewritten.text());
        } else {
            node.put("description", "");
        }

        return node;
    }

    private String rewriteSingleUrl(
            String url,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        if (url == null || url.isBlank()) return url;
        return rewriter.matchSingleUrl(url)
                .map(match -> {
                    allMatches.add(match);
                    return match.relativeAssetPath();
                })
                .orElse(url);
    }

    private String renderContentHtml(
            String content,
            String contentType,
            SiteExportImageRewriter rewriter,
            List<SiteExportImageRewriter.Match> allMatches
    ) {
        if (content == null || content.isBlank()) return "";

        if ("editorjs".equals(contentType)) {
            try {
                Map<String, String> urlMap = new HashMap<>();
                for (String url : editorJsToHtmlConverter.extractImageUrls(content)) {
                    rewriter.matchSingleUrl(url).ifPresent(match -> {
                        urlMap.put(url, match.relativeAssetPath());
                        allMatches.add(match);
                    });
                }
                return editorJsToHtmlConverter.convert(content, urlMap);
            } catch (Exception e) {
                logger.warn("EditorJS 内容转换失败，导出时跳过该笔记正文: {}", e.getMessage());
                return "";
            }
        }

        if ("markdown".equals(contentType)) {
            SiteExportImageRewriter.RewriteResult rewritten = rewriter.rewriteText(content);
            allMatches.addAll(rewritten.matches());
            return markdownToHtmlConverter.convert(rewritten.text());
        }

        // richtext 或未指定：内容本身已经是 HTML
        SiteExportImageRewriter.RewriteResult rewritten = rewriter.rewriteText(content);
        allMatches.addAll(rewritten.matches());
        return rewritten.text();
    }

    /**
     * 首次导出：把源码模板（src/、public/、vite.config.js、package.json、
     * node_modules/）整棵树拷贝到目标目录。这份源码之后可以被人工/AI
     * 持续编辑，重复导出不会再碰它（见 isExistingProject）。
     */
    private void scaffoldSourceProject(Path target, Path source) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path relative = source.relativize(src);
                if (relative.toString().isEmpty()) continue;
                Path dest = target.resolve(relative.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.warn("清理导出目录时删除失败: {}", path, e);
                }
            });
        }
    }

    /**
     * 图片资源目录每次导出都重新生成（笔记/项目引用的图片集合可能变化）。
     * 注意落在 public/ 下面：Vite 构建时会把 public/ 整个目录原样拷进
     * dist/ 根目录，笔记正文里写的相对路径 "assets/uploads/xxx.png" 才能
     * 在构建产物里解析到同一份文件。
     */
    private void resetGeneratedAssetDirectories(Path target) throws IOException {
        Path uploadsDir = target.resolve("public").resolve("assets").resolve("uploads");
        deleteRecursively(uploadsDir);
        Files.createDirectories(uploadsDir);
    }

    /**
     * 把站点数据写成一个独立的 JS 文件（public/site-data.js），由
     * public/index.html 用普通 <script src> 静态引用，而不是运行时
     * fetch data/*.json（file:// 协议下会被浏览器 CORS 策略拦截），
     * 也不是每次改写 index.html 本身（index.html 属于会被人工/AI 持续
     * 编辑的源码，导出流程不应该碰它）。这个文件每次导出都会被整个重写。
     */
    private void writeSiteDataScript(
            Path target,
            ObjectNode profileJson,
            ArrayNode notesJson,
            ArrayNode projectsJson,
            ArrayNode achievementsJson,
            String locale
    ) throws IOException {
        ObjectNode siteData = objectMapper.createObjectNode();
        siteData.set("profile", profileJson);
        siteData.set("notes", notesJson);
        siteData.set("projects", projectsJson);
        siteData.set("achievements", achievementsJson);
        siteData.put("locale", locale);

        String json = objectMapper.writeValueAsString(siteData);
        String script = "window.__SITE_DATA__=" + json + ";";

        Path dataScriptFile = target.resolve("public").resolve("site-data.js");
        Files.createDirectories(dataScriptFile.getParent());
        Files.writeString(dataScriptFile, script);
    }

    private void copyMatchedAssets(
            Path target,
            List<SiteExportImageRewriter.Match> matches,
            List<String> warnings
    ) throws IOException {
        Set<String> seen = new HashSet<>();
        for (SiteExportImageRewriter.Match match : matches) {
            if (!seen.add(match.relativeAssetPath())) continue;

            if (!Files.isRegularFile(match.sourceFile())) {
                warnings.add("图片未找到，已跳过: " + match.originalUrl());
                continue;
            }

            Path dest = target.resolve("public").resolve(match.relativeAssetPath());
            Files.createDirectories(dest.getParent());
            Files.copy(match.sourceFile(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
