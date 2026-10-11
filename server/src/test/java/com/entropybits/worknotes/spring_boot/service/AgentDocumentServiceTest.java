/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.AgentDocumentContent;
import com.entropybits.worknotes.spring_boot.dto.AgentDocumentSummary;
import com.entropybits.worknotes.spring_boot.dto.AgentImageRef;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDocumentServiceTest {

    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository clipRepository;
    @Mock ContentChunkingService chunkingService;
    @Mock MediaBlockService mediaBlockService;

    AgentDocumentService service;
    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        service = new AgentDocumentService(userRepository, noteRepository, clipRepository, chunkingService,
                mediaBlockService, new ObjectMapper());
        alice = User.builder().id(1L).username("alice").build();
        bob = User.builder().id(2L).username("bob").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
    }

    @Test
    void findNotesByTitle_returnsOwnerScopedSummaries() {
        Note n = Note.builder().id(10L).title("2026-10-05-sms-gateway-design").owner(alice).build();
        when(noteRepository.findByOwnerAndTitleContaining(eq(alice), eq("sms-gateway"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        List<AgentDocumentSummary> result = service.findNotesByTitle("alice", "sms-gateway");

        assertThat(result).containsExactly(new AgentDocumentSummary("note", 10L, "2026-10-05-sms-gateway-design"));
    }

    @Test
    void readNote_returnsFullBodyWithoutTruncation() {
        Note n = Note.builder().id(10L).title("设计").owner(alice).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));
        String longParagraph = "长".repeat(9000);
        when(chunkingService.chunkNote(n)).thenReturn(List.of("设计", longParagraph));

        AgentDocumentContent result = service.readNote("alice", 10L);

        assertThat(result.title()).isEqualTo("设计");
        // 第一块是 prependTitleChunk 插进来的标题，正文里不该重复
        assertThat(result.bodyText()).isEqualTo(longParagraph);
    }

    @Test
    void readNote_otherUsersNote_throwsNotFound() {
        Note n = Note.builder().id(11L).title("bob的笔记").owner(bob).build();
        when(noteRepository.findById(11L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.readNote("alice", 11L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void readClip_otherUsersClip_throwsNotFound() {
        SourceClip c = SourceClip.builder().id(20L).title("bob的收藏").owner(bob).build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.readClip("alice", 20L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void readClip_returnsBody() {
        SourceClip c = SourceClip.builder().id(20L).title("收藏").owner(alice).build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(c));
        when(chunkingService.chunkClip(c)).thenReturn(List.of("收藏", "第一段", "第二段"));

        AgentDocumentContent result = service.readClip("alice", 20L);

        assertThat(result).isEqualTo(new AgentDocumentContent("clip", 20L, "收藏", "第一段\n第二段", 0));
    }

    @Test
    void listNoteImages_editorjs_collectsImageBlocksAndInlineImagesInOrder() {
        String content = """
                {"blocks":[
                  {"id":"a","type":"image","data":{"file":{"url":"http://localhost:8080/api/uploads/x/1.png"},"caption":"<b>架构图</b>"}},
                  {"id":"b","type":"paragraph","data":{"text":"见下图"}},
                  {"id":"c","type":"markdown","data":{"markdown":"说明 ![流程](/api/uploads/x/2.jpg) 结束"}},
                  {"id":"d","type":"paragraph","data":{"text":"<img src=\\"https://example.com/3.webp\\">"}}
                ]}""";
        Note n = Note.builder().id(10L).title("t").owner(alice).contentType("editorjs").content(content).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));

        List<AgentImageRef> images = service.listNoteImages("alice", 10L);

        assertThat(images).containsExactly(
                new AgentImageRef(1, "http://localhost:8080/api/uploads/x/1.png", "架构图"),
                new AgentImageRef(2, "/api/uploads/x/2.jpg", "流程"),
                new AgentImageRef(3, "https://example.com/3.webp", ""));
    }

    @Test
    void listClipImages_html_prefersDataSrcAndSkipsUnusableUrls() {
        String content = "<p>a</p><img class=\"x\" data-src=\"https://mmbiz.qpic.cn/1.png\" src=\"data:image/svg+xml,placeholder\">"
                + "<img src=\"about:blank\"><img alt=\"二\" src=\"https://example.com/2.png\">";
        SourceClip c = SourceClip.builder().id(20L).title("t").owner(alice).contentFormat("html").content(content).build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(c));

        List<AgentImageRef> images = service.listClipImages("alice", 20L);

        assertThat(images).extracting(AgentImageRef::url)
                .containsExactly("https://mmbiz.qpic.cn/1.png", "https://example.com/2.png");
    }

    @Test
    void listNoteImages_markdown_skipsUnresolvedFeishuPlaceholders() {
        String content = "![](http://localhost:8080/api/uploads/f/1.png)\n![](feishu-image:IZD7b9)\n![x](about:blank)";
        Note n = Note.builder().id(10L).title("t").owner(alice).contentType("markdown").content(content).build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));

        assertThat(service.listNoteImages("alice", 10L)).extracting(AgentImageRef::url)
                .containsExactly("http://localhost:8080/api/uploads/f/1.png");
    }

    @Test
    void listNoteImages_otherUsersNote_throwsNotFound() {
        Note n = Note.builder().id(11L).title("t").owner(bob).contentType("markdown").content("![](/uploads/1.png)").build();
        when(noteRepository.findById(11L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.listNoteImages("alice", 11L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void loadNoteImage_localUpload_returnsBase64(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("1.png");
        Files.write(file, new byte[]{1, 2, 3});
        Note n = Note.builder().id(10L).title("t").owner(alice).contentType("markdown")
                .content("![](http://localhost:8080/api/uploads/x/1.png)").build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));
        when(mediaBlockService.resolveLocalFilePath("http://localhost:8080/api/uploads/x/1.png")).thenReturn(file);

        Map<String, Object> media = service.loadNoteImage("alice", 10L, 1);

        assertThat(media).containsEntry("mimeType", "image/png").containsEntry("base64Data", "AQID");
    }

    @Test
    void loadClipImage_externalUrl_returnsUrlForModelToFetch() {
        SourceClip c = SourceClip.builder().id(20L).title("t").owner(alice).contentFormat("markdown")
                .content("![](https://example.com/wp-content/uploads/2.png)").build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(c));
        when(mediaBlockService.resolveLocalFilePath(any())).thenReturn(Path.of("does-not-exist.png"));

        Map<String, Object> media = service.loadClipImage("alice", 20L, 1);

        assertThat(media).containsExactly(Map.entry("url", "https://example.com/wp-content/uploads/2.png"));
    }

    @Test
    void loadClipImage_externalUrlThatIsAnInvalidLocalPath_returnsUrlInsteadOf500() {
        String wp = "https://example.com/wp-content/uploads/2.png?w=1024";
        SourceClip c = SourceClip.builder().id(20L).title("t").owner(alice).contentFormat("markdown")
                .content("![](" + wp + ")").build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(c));
        when(mediaBlockService.resolveLocalFilePath(wp)).thenThrow(new java.nio.file.InvalidPathException(wp, "Illegal char <?>"));

        Map<String, Object> media = service.loadClipImage("alice", 20L, 1);

        assertThat(media).containsExactly(Map.entry("url", wp));
    }

    @Test
    void loadNoteImage_indexOutOfRange_throwsBadRequest() {
        Note n = Note.builder().id(10L).title("t").owner(alice).contentType("markdown").content("![](/uploads/1.png)").build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.loadNoteImage("alice", 10L, 2))
                .isInstanceOf(com.entropybits.worknotes.spring_boot.exception.BadRequestException.class);
    }

    @Test
    void readNote_reportsImageCount() {
        Note n = Note.builder().id(10L).title("t").owner(alice).contentType("markdown")
                .content("![](/uploads/1.png) ![](/uploads/2.png)").build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(n));
        when(chunkingService.chunkNote(n)).thenReturn(List.of("t", "正文"));

        assertThat(service.readNote("alice", 10L).imageCount()).isEqualTo(2);
    }
}
