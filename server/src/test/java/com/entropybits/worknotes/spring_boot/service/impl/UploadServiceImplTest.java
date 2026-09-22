/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.impl;

import com.entropybits.worknotes.spring_boot.config.UploadPathConfig;
import com.entropybits.worknotes.spring_boot.entity.Attachment;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
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
}
