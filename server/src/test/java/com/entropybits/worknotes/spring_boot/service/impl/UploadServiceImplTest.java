/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.impl;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.repository.AttachmentRepository;
import com.entropybits.worknotes.spring_boot.service.LocalFileExtractionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadServiceImplTest {

    @Mock AttachmentRepository attachmentRepository;
    @Mock LocalFileExtractionService extractionService;

    @TempDir Path tempDir;

    private UploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UploadServiceImpl(attachmentRepository, extractionService);

        UploadPathConfig uploadPathConfig = mock(UploadPathConfig.class);
        when(uploadPathConfig.getUploadPath()).thenReturn(tempDir.toString());
        ReflectionTestUtils.setField(service, "uploadPathConfig", uploadPathConfig);
        ReflectionTestUtils.setField(service, "uploadUrlPrefix", "");

        when(extractionService.registerImageForOcr(any())).thenReturn(
                LocalFileExtraction.builder().contentHash("stub-hash").status(LocalFileExtraction.Status.PENDING).build());
    }

    @Test
    void imageUpload_registersContentHashOnAttachment() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake bytes".getBytes());

        service.imageUpload(file, "note", 0, 1L);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHash()).isEqualTo("stub-hash");
        verify(extractionService).registerImageForOcr(any());
    }

    @Test
    void imageUpload_returnsRootRelativeApiUrlWhenNoPrefixConfigured() {
        // uploadUrlPrefix 为空（同源自用部署）：正文里应存根相对地址 /api/uploads/...，不写死环境。
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake bytes".getBytes());

        var result = service.imageUpload(file, "note", 0, 1L);

        assertThat(result.getUrl()).startsWith("/api/uploads/").endsWith(".jpg");
    }

    @Test
    void imageUpload_prependsConfiguredPrefixWhenSet() {
        ReflectionTestUtils.setField(service, "uploadUrlPrefix", "https://www.entropybits.com/api");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake bytes".getBytes());

        var result = service.imageUpload(file, "note", 0, 1L);

        assertThat(result.getUrl()).startsWith("https://www.entropybits.com/api/uploads/");
    }

    @Test
    void fileUpload_doesNotRegisterContentHashForNonImageFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "fake bytes".getBytes());

        service.fileUpload(file, "note", 0, 1L);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHash()).isNull();
        verify(extractionService, never()).registerImageForOcr(any());
    }

    @Test
    void base64Upload_registersContentHashOnAttachment() {
        String base64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("fake bytes".getBytes());

        service.base64Upload(base64, "note", 0);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHash()).isEqualTo("stub-hash");
    }

    @Test
    void remoteUpload_registersContentHashOnAttachment() throws Exception {
        byte[] imageBytes = "fake remote image bytes".getBytes();
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/photo.jpg", ex -> {
            ex.getResponseHeaders().add("Content-Type", "image/jpeg");
            ex.sendResponseHeaders(200, imageBytes.length);
            ex.getResponseBody().write(imageBytes);
            ex.close();
        });
        httpServer.start();
        try {
            String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/photo.jpg";

            service.remoteUpload(url, "note", 0, 1L);

            ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
            verify(attachmentRepository).save(captor.capture());
            assertThat(captor.getValue().getContentHash()).isEqualTo("stub-hash");
        } finally {
            httpServer.stop(0);
        }
    }

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private HttpServer serve(String path, String contentType, byte[] body) throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, ex -> {
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        httpServer.start();
        return httpServer;
    }

    @Test
    void remoteUpload_infersImageTypeWhenPathLooksLikeScript() throws Exception {
        // 路径里的 ".php" 不是媒体扩展名，应按 Content-Type 存成 png，而不是存成 .php
        HttpServer httpServer = serve("/img/view.php", "image/png", PNG_HEAD);
        try {
            String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/img/view.php?id=1";

            var result = service.remoteUpload(url, "note", 0, 1L);

            assertThat(result.getUrl()).endsWith(".png");
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void remoteUpload_sniffsImageWhenNoExtensionAndGenericContentType() throws Exception {
        HttpServer httpServer = serve("/media/abc123", "application/octet-stream", PNG_HEAD);
        try {
            String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/media/abc123";

            var result = service.remoteUpload(url, "note", 0, 1L);

            assertThat(result.getUrl()).endsWith(".png");
            ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
            verify(attachmentRepository).save(captor.capture());
            assertThat(captor.getValue().getAttType()).isEqualTo("png");
            assertThat(captor.getValue().getContentHash()).isEqualTo("stub-hash");
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void remoteUpload_rejectsHtmlPageBehindScriptPath() throws Exception {
        HttpServer httpServer = serve("/article.php", "text/html; charset=utf-8", "<html></html>".getBytes());
        try {
            String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/article.php";

            assertThatThrownBy(() -> service.remoteUpload(url, "note", 0, 1L))
                    .isInstanceOf(BadRequestException.class);
            verify(attachmentRepository, never()).save(any());
        } finally {
            httpServer.stop(0);
        }
    }
}
