/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AgentStateResponse;
import com.entropybits.worknotes.spring_boot.dto.InternalRetrieveRequest;
import com.entropybits.worknotes.spring_boot.dto.PutAgentStateRequest;
import com.entropybits.worknotes.spring_boot.entity.AgentConversationState;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AgentConversationStateRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.RetrievalService;
import com.entropybits.worknotes.spring_boot.service.RetrievedChunk;
import com.entropybits.worknotes.spring_boot.service.TopicBlockService;
import com.entropybits.worknotes.spring_boot.service.MediaBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 只给独立的Python/LangGraph Agent服务回调用的内部接口（鉴权见 InternalServiceAuthFilter，
 * 不是给终端用户/前端直接调用的）。详见
 * docs/superpowers/specs/2026-09-04-agent-service-langgraph-design.md。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalAgentController {

    private final RetrievalService retrievalService;
    private final UserRepository userRepository;
    private final AgentConversationStateRepository stateRepository;
    private final TopicBlockService topicBlockService;
    private final MediaBlockService mediaBlockService;

    @PostMapping("/retrieve")
    public List<RetrievedChunk> retrieve(@RequestBody InternalRetrieveRequest request) throws Exception {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        return retrievalService.retrieve(user, request.query(), request.topK());
    }

    @GetMapping("/agent-state/{conversationId}")
    public ResponseEntity<AgentStateResponse> getState(@PathVariable String conversationId) {
        return stateRepository.findByConversationId(conversationId)
                .map(s -> ResponseEntity.ok(new AgentStateResponse(s.getConversationId(), s.getStateBlob(), s.getUpdatedAt())))
                .orElseGet(() -> ResponseEntity.ok(new AgentStateResponse(conversationId, null, null)));
    }

    @PutMapping("/agent-state/{conversationId}")
    public void putState(@PathVariable String conversationId, @RequestBody PutAgentStateRequest request) {
        AgentConversationState state = stateRepository.findByConversationId(conversationId)
                .orElseGet(() -> AgentConversationState.builder().conversationId(conversationId).build());
        state.setStateBlob(request.stateBlob());
        stateRepository.save(state);
    }

    @PostMapping("/notes/{noteId}/topic-blocks/{blockType}/items")
    public List<Map<String, Object>> appendBlockItem(
            @PathVariable Long noteId,
            @PathVariable String blockType,
            @RequestBody Map<String, Object> item) {
        return topicBlockService.appendItem(noteId, blockType, item);
    }

    @GetMapping("/notes/{noteId}/media-blocks")
    public List<Map<String, Object>> mediaBlocks(@PathVariable Long noteId) {
        return mediaBlockService.listMediaBlocks(noteId);
    }

    @GetMapping("/notes/{noteId}/blocks/{blockId}/content")
    public Map<String, Object> blockContent(@PathVariable Long noteId, @PathVariable String blockId) {
        return mediaBlockService.getBlockFile(noteId, blockId);
    }

    @PatchMapping("/notes/{noteId}/blocks/{blockId}")
    public Map<String, Object> patchBlock(
            @PathVariable Long noteId,
            @PathVariable String blockId,
            @RequestBody Map<String, Object> fields) {
        return mediaBlockService.patchBlock(noteId, blockId, fields);
    }
}
