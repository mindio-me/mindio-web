/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageRequest;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchResultSaveResponse;
import com.entropybits.worknotes.spring_boot.service.ClipSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClipSearchControllerTest {

    @Mock ClipSearchService clipSearchService;

    private final UserDetails principal =
            org.springframework.security.core.userdetails.User.withUsername("alice").password("x").authorities("USER").build();

    @Test
    void listMessages_delegatesToServiceWithUsername() {
        ClipSearchController controller = new ClipSearchController(clipSearchService);
        List<ClipSearchMessageResponse> history = List.of(ClipSearchMessageResponse.builder().id(1L).build());
        when(clipSearchService.listHistory("alice")).thenReturn(history);

        ResponseEntity<List<ClipSearchMessageResponse>> response = controller.listMessages(principal);

        assertThat(response.getBody()).isEqualTo(history);
    }

    @Test
    void sendMessage_delegatesToServiceWithContentAndUsername() {
        ClipSearchController controller = new ClipSearchController(clipSearchService);
        ClipSearchMessageRequest request = new ClipSearchMessageRequest();
        request.setContent("帮我找找AI监管的报道");
        List<ClipSearchMessageResponse> reply = List.of(
                ClipSearchMessageResponse.builder().id(1L).role("USER").build(),
                ClipSearchMessageResponse.builder().id(2L).role("ASSISTANT").build());
        when(clipSearchService.sendMessage("alice", "帮我找找AI监管的报道")).thenReturn(reply);

        ResponseEntity<List<ClipSearchMessageResponse>> response = controller.sendMessage(request, principal);

        assertThat(response.getBody()).isEqualTo(reply);
    }

    @Test
    void saveResult_wrapsSourceClipIdInResponse() {
        ClipSearchController controller = new ClipSearchController(clipSearchService);
        when(clipSearchService.saveResult(5L, 0, "alice")).thenReturn(99L);

        ResponseEntity<ClipSearchResultSaveResponse> response = controller.saveResult(5L, 0, principal);

        assertThat(response.getBody()).isEqualTo(new ClipSearchResultSaveResponse(99L));
    }
}
