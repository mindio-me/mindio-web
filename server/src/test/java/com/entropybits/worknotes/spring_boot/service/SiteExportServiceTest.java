/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.dto.AchievementResponse;
import com.entropybits.worknotes.spring_boot.dto.SiteExportResult;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Profile;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.integration.wechat.EditorJsToHtmlConverter;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SiteExportServiceTest {

    private static final String UPLOAD_PREFIX = "http://127.0.0.1:8080/api";
    private static final String TEMPLATE_ID = "editorial";

    @Mock NoteRepository noteRepository;
    @Mock ProjectService projectService;
    @Mock ProfileRepository profileRepository;
    @Mock AchievementService achievementService;
    @Mock UploadPathConfig uploadPathConfig;

    @TempDir Path uploadRoot;
    @TempDir Path templatesRootDir;
    @TempDir Path exportTarget;

    private SiteExportService service;
    private User owner;

    @BeforeEach
    void setUp() throws IOException {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        when(uploadPathConfig.getUploadPath()).thenReturn(uploadRoot.toString());
        when(profileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(projectService.getAllPublicProjects()).thenReturn(List.of());
        when(achievementService.getAllActiveAchievements()).thenReturn(List.of());
        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc()).thenReturn(List.of());

        // 模拟真实的 site-templates 结构：templatesRoot 下按 templateId 分子目录，
        // package.json 带识别标记 name，public/index.html 静态引用 site-data.js，
        // src/ 下放一个哨兵文件，用来验证"重复导出不覆盖 src/"这个行为。
        Path templateDir = templatesRootDir.resolve(TEMPLATE_ID);
        Files.createDirectories(templateDir);
        Files.writeString(templateDir.resolve("package.json"),
                "{\"name\":\"mindio-user-site\",\"version\":\"1.0.0\"}");
        Files.createDirectories(templateDir.resolve("public"));
        Files.writeString(templateDir.resolve("public/index.html"),
                "<html><body><script src=\"./site-data.js\"></script></body></html>");
        Files.createDirectories(templateDir.resolve("src"));
        Files.writeString(templateDir.resolve("src/marker.txt"), "original-template-source");

        service = new SiteExportService(
                noteRepository,
                projectService,
                profileRepository,
                achievementService,
                uploadPathConfig,
                new EditorJsToHtmlConverter(new ObjectMapper()),
                new com.entropybits.worknotes.spring_boot.integration.wechat.MarkdownToHtmlConverter(),
                new ObjectMapper()
        );
        service.setUploadUrlPrefix(UPLOAD_PREFIX);
        service.setTemplatesRoot(templatesRootDir.toString());
    }

    private Note noteWithContent(String contentType, String content) {
        Note note = Note.builder()
                .id(1L)
                .title("Hello")
                .content(content)
                .contentType(contentType)
                .owner(owner)
                .isPublic(true)
                .tags(Set.of())
                .build();
        note.setCreatedAt(LocalDateTime.now());
        note.setModifiedAt(LocalDateTime.now());
        return note;
    }

    @Test
    void scaffoldsFullSourceProjectOnFirstExport() throws IOException {
        SiteExportResult result = service.export(exportTarget.toString(), TEMPLATE_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(exportTarget.resolve("package.json")).exists();
        assertThat(exportTarget.resolve("src/marker.txt")).exists();
        assertThat(exportTarget.resolve("public/index.html")).exists();
        assertThat(exportTarget.resolve("public/site-data.js")).exists();
    }

    @Test
    void rejectsUnknownTemplateId() {
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> service.export(exportTarget.toString(), "does-not-exist"));
    }

    @Test
    void preservesSrcOnRepeatExportButRefreshesSiteData() throws IOException {
        service.export(exportTarget.toString(), TEMPLATE_ID);

        // 模拟用户/AI 改过导出目录里的源码
        Files.writeString(exportTarget.resolve("src/marker.txt"), "edited-by-ai");

        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc())
                .thenReturn(List.of(noteWithContent("richtext", "<p>second export</p>")));
        service.export(exportTarget.toString(), TEMPLATE_ID);

        assertThat(Files.readString(exportTarget.resolve("src/marker.txt"))).isEqualTo("edited-by-ai");
        assertThat(Files.readString(exportTarget.resolve("public/site-data.js"))).contains("second export");
    }

    @Test
    void repeatExportIgnoresTemplateIdEvenIfInvalid() throws IOException {
        service.export(exportTarget.toString(), TEMPLATE_ID);

        // 项目已经存在了，即使传一个不存在的 templateId 也不该报错，
        // 因为重复导出根本不应该再去解析模板目录。
        SiteExportResult result = service.export(exportTarget.toString(), "does-not-exist");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void exportsSuccessfullyWhenOwnerHasNeverSavedAProfile() {
        // Regression test: profileRepository.findFirstByOrderByIdAsc() returning
        // empty (no profile row yet, the common case for a fresh install) must
        // not fail the export.
        when(profileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        SiteExportResult result = service.export(exportTarget.toString(), TEMPLATE_ID);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void includesOwnerProfileFieldsWhenProfileExists() throws IOException {
        Profile profile = Profile.builder()
                .id(1L)
                .user(owner)
                .fullName("Jane Doe")
                .bio("Building things.")
                .build();
        when(profileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(profile));

        service.export(exportTarget.toString(), TEMPLATE_ID);

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("Jane Doe").contains("Building things.");
    }

    @Test
    void writesSiteDataAsPlainJsFileWithoutTouchingIndexHtml() throws IOException {
        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc())
                .thenReturn(List.of(noteWithContent("richtext", "<p>hello file protocol</p>")));

        service.export(exportTarget.toString(), TEMPLATE_ID);

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).startsWith("window.__SITE_DATA__=");
        assertThat(siteData).contains("hello file protocol");

        String indexHtml = Files.readString(exportTarget.resolve("public/index.html"));
        assertThat(indexHtml).contains("<script src=\"./site-data.js\"></script>");
    }

    @Test
    void rewritesRichtextImageAndCopiesFile() throws IOException {
        Files.createDirectories(uploadRoot.resolve("worknotesimage/public/notes"));
        Files.writeString(uploadRoot.resolve("worknotesimage/public/notes/a.png"), "fake-image-bytes");

        String html = "<p>pic</p><img src=\"" + UPLOAD_PREFIX + "/uploads/worknotesimage/public/notes/a.png\">";
        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc())
                .thenReturn(List.of(noteWithContent("richtext", html)));

        service.export(exportTarget.toString(), TEMPLATE_ID);

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("assets/uploads/worknotesimage/public/notes/a.png");
        assertThat(exportTarget.resolve("public/assets/uploads/worknotesimage/public/notes/a.png")).exists();
    }

    @Test
    void skipsMissingImageWithWarningInsteadOfFailing() {
        String html = "<img src=\"" + UPLOAD_PREFIX + "/uploads/worknotesimage/public/notes/missing.png\">";
        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc())
                .thenReturn(List.of(noteWithContent("richtext", html)));

        SiteExportResult result = service.export(exportTarget.toString(), TEMPLATE_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSkippedImageCount()).isEqualTo(1);
        assertThat(exportTarget.resolve("public/assets/uploads/worknotesimage/public/notes/missing.png")).doesNotExist();
    }

    @Test
    void reExportRemovesContentThatIsNoLongerPublic() throws IOException {
        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc())
                .thenReturn(List.of(noteWithContent("richtext", "<p>first export</p>")));
        service.export(exportTarget.toString(), TEMPLATE_ID);
        assertThat(Files.readString(exportTarget.resolve("public/site-data.js"))).contains("first export");

        when(noteRepository.findByIsPublicTrueOrderByModifiedAtDesc()).thenReturn(List.of());
        service.export(exportTarget.toString(), TEMPLATE_ID);

        assertThat(Files.readString(exportTarget.resolve("public/site-data.js"))).doesNotContain("first export");
    }

    @Test
    void rejectsDriveRootAsTargetPath() {
        Path root = exportTarget.getRoot();
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> service.export(root.toString(), TEMPLATE_ID));
    }

    @Test
    void rejectsBlankTargetPath() {
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> service.export("  ", TEMPLATE_ID));
    }

    @Test
    void includesActiveAchievementsInExportedSiteData() throws IOException {
        Files.createDirectories(uploadRoot.resolve("worknotesimage/public/achievements"));
        Files.writeString(uploadRoot.resolve("worknotesimage/public/achievements/a.png"), "fake-image-bytes");

        AchievementResponse achievement = new AchievementResponse();
        achievement.setId(1L);
        achievement.setTitle("Notecast");
        achievement.setSubtitle("自托管笔记系统");
        achievement.setType("产品");
        achievement.setStatus("active");
        achievement.setIcon("el-icon-trophy");
        achievement.setIconVariant("purple");
        achievement.setTechnologies("Vue, Spring Boot");
        achievement.setDescription("<p>自托管、单用户笔记管理系统</p><img src=\"" + UPLOAD_PREFIX
                + "/uploads/worknotesimage/public/achievements/a.png\">");
        achievement.setDisplayOrder(1);
        when(achievementService.getAllActiveAchievements()).thenReturn(List.of(achievement));

        service.export(exportTarget.toString(), TEMPLATE_ID, "zh-CN");

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("Notecast").contains("自托管、单用户笔记管理系统");
        assertThat(siteData).contains("assets/uploads/worknotesimage/public/achievements/a.png");
        assertThat(exportTarget.resolve("public/assets/uploads/worknotesimage/public/achievements/a.png")).exists();
    }

    @Test
    void defaultsLocaleToZhCnWhenTwoArgOverloadUsed() throws IOException {
        service.export(exportTarget.toString(), TEMPLATE_ID);

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("\"locale\":\"zh-CN\"");
    }

    @Test
    void passesThroughProvidedLocale() throws IOException {
        service.export(exportTarget.toString(), TEMPLATE_ID, "en");

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("\"locale\":\"en\"");
    }

    @Test
    void includesProjectIconInExportedSiteData() throws IOException {
        Project project = Project.builder()
                .id(1L)
                .name("IMS")
                .icon("el-icon-data-analysis")
                .isPublic(true)
                .isFeatured(false)
                .displayOrder(1)
                .owner(owner)
                .build();
        when(projectService.getAllPublicProjects())
                .thenReturn(List.of(com.entropybits.worknotes.spring_boot.dto.ProjectResponse.fromEntity(project)));

        service.export(exportTarget.toString(), TEMPLATE_ID, "zh-CN");

        String siteData = Files.readString(exportTarget.resolve("public/site-data.js"));
        assertThat(siteData).contains("el-icon-data-analysis");
    }
}
