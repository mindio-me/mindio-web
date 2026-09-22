/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.SiteExportResult;
import com.entropybits.worknotes.spring_boot.service.SiteExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteExportControllerTest {

    @Mock SiteExportService siteExportService;

    private SiteExportController controller;

    @BeforeEach
    void setUp() {
        controller = new SiteExportController(siteExportService);
    }

    @Test
    void delegatesToServiceWithTargetPathAndReturnsResult() {
        SiteExportResult expected = SiteExportResult.builder()
                .success(true)
                .targetPath("C:\\Users\\demo\\mindio-site")
                .exportedNoteCount(3)
                .exportedProjectCount(1)
                .skippedImageCount(0)
                .warnings(List.of())
                .build();
        when(siteExportService.export("C:\\Users\\demo\\mindio-site", "editorial", null)).thenReturn(expected);

        com.entropybits.worknotes.spring_boot.dto.SiteExportRequest request =
                new com.entropybits.worknotes.spring_boot.dto.SiteExportRequest();
        request.setTargetPath("C:\\Users\\demo\\mindio-site");
        request.setTemplateId("editorial");

        var response = controller.exportSite(request);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(siteExportService).export("C:\\Users\\demo\\mindio-site", "editorial", null);
    }

    @Test
    void passesRequestLocaleThroughToService() {
        SiteExportResult expected = SiteExportResult.builder()
                .success(true)
                .targetPath("C:\\Users\\demo\\mindio-site")
                .exportedNoteCount(0)
                .exportedProjectCount(0)
                .skippedImageCount(0)
                .warnings(List.of())
                .build();
        when(siteExportService.export("C:\\Users\\demo\\mindio-site", "editorial", "en")).thenReturn(expected);

        com.entropybits.worknotes.spring_boot.dto.SiteExportRequest request =
                new com.entropybits.worknotes.spring_boot.dto.SiteExportRequest();
        request.setTargetPath("C:\\Users\\demo\\mindio-site");
        request.setTemplateId("editorial");
        request.setLocale("en");

        var response = controller.exportSite(request);

        assertThat(response.getBody()).isEqualTo(expected);
        verify(siteExportService).export("C:\\Users\\demo\\mindio-site", "editorial", "en");
    }
}
