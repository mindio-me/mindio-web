/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.ai.dto.AiSettingsResponse;
import com.entropybits.worknotes.spring_boot.ai.dto.InternalAiConfigResponse;
import com.entropybits.worknotes.spring_boot.ai.settings.AiSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsControllerTest {

    @Mock AiSettingsService service;

    @Test
    void getAndPutDelegateToService() {
        AiSettingsController controller = new AiSettingsController(service);
        AiSettingsResponse view = new AiSettingsResponse(List.of(), true);
        when(service.view()).thenReturn(view);

        assertThat(controller.get()).isSameAs(view);
        ResponseEntity<Void> put = controller.update(Map.of("ai.openai.model", "gpt-x"));

        assertThat(put.getStatusCode().value()).isEqualTo(204);
        verify(service).update(Map.of("ai.openai.model", "gpt-x"));
    }

    @Test
    void bothAdminEndpointsRequireAdmin() throws Exception {
        for (String method : List.of("get", "update")) {
            var m = method.equals("get")
                    ? AiSettingsController.class.getMethod("get")
                    : AiSettingsController.class.getMethod("update", Map.class);
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        }
    }

    @Test
    void internalConfigLivesUnderInternalPathSoTheSharedTokenFilterGuardsIt() {
        assertThat(InternalAiConfigController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/internal");
        InternalAiConfigResponse cfg = new InternalAiConfigResponse("openai", Map.of(), null, null);
        when(service.internalConfig()).thenReturn(cfg);
        assertThat(new InternalAiConfigController(service).aiConfig()).isSameAs(cfg);
    }
}
