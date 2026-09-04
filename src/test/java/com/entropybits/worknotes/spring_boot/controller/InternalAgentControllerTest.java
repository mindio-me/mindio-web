/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AgentStateResponse;
import com.entropybits.worknotes.spring_boot.dto.InternalRetrieveRequest;
import com.entropybits.worknotes.spring_boot.dto.PutAgentStateRequest;
import com.entropybits.worknotes.spring_boot.entity.AgentConversationState;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AgentConversationStateRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.RetrievalService;
import com.entropybits.worknotes.spring_boot.service.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAgentControllerTest {

    @Mock RetrievalService retrievalService;
    @Mock UserRepository userRepository;
    @Mock AgentConversationStateRepository stateRepository;

    InternalAgentController controller() {
        return new InternalAgentController(retrievalService, userRepository, stateRepository);
    }

    @Test
    void retrieve_resolvesUserAndDelegatesToRetrievalService() throws Exception {
        User alice = User.builder().username("alice").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        List<RetrievedChunk> expected = List.of(new RetrievedChunk(ContentChunk.SourceType.NOTE, 1L, "text", 0.9));
        when(retrievalService.retrieve(alice, "query", 5)).thenReturn(expected);

        List<RetrievedChunk> result = controller().retrieve(new InternalRetrieveRequest("alice", "query", 5));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void retrieve_unknownUser_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().retrieve(new InternalRetrieveRequest("ghost", "query", 5)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getState_existingConversation_returnsStoredBlob() {
        AgentConversationState state = AgentConversationState.builder()
                .conversationId("alice").stateBlob("{\"todos\":[]}").build();
        when(stateRepository.findByConversationId("alice")).thenReturn(Optional.of(state));

        ResponseEntity<AgentStateResponse> response = controller().getState("alice");

        assertThat(response.getBody().stateBlob()).isEqualTo("{\"todos\":[]}");
    }

    @Test
    void getState_unknownConversation_returnsNullBlobNotError() {
        when(stateRepository.findByConversationId("bob")).thenReturn(Optional.empty());

        ResponseEntity<AgentStateResponse> response = controller().getState("bob");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().stateBlob()).isNull();
        assertThat(response.getBody().conversationId()).isEqualTo("bob");
    }

    @Test
    void putState_newConversation_createsRow() {
        when(stateRepository.findByConversationId("alice")).thenReturn(Optional.empty());

        controller().putState("alice", new PutAgentStateRequest("{\"todos\":[]}"));

        ArgumentCaptor<AgentConversationState> captor = ArgumentCaptor.forClass(AgentConversationState.class);
        verify(stateRepository).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isEqualTo("alice");
        assertThat(captor.getValue().getStateBlob()).isEqualTo("{\"todos\":[]}");
    }

    @Test
    void putState_existingConversation_updatesInPlace() {
        AgentConversationState existing = AgentConversationState.builder()
                .conversationId("alice").stateBlob("old").build();
        when(stateRepository.findByConversationId("alice")).thenReturn(Optional.of(existing));

        controller().putState("alice", new PutAgentStateRequest("new"));

        verify(stateRepository).save(existing);
        assertThat(existing.getStateBlob()).isEqualTo("new");
    }
}
