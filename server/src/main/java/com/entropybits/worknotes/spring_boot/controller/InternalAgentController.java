/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.controller;

import com.entropybits.worknotes.spring_boot.dto.AgentStateResponse;
import com.entropybits.worknotes.spring_boot.dto.AudioTranscriptionResponse;
import com.entropybits.worknotes.spring_boot.dto.InternalRetrieveRequest;
import com.entropybits.worknotes.spring_boot.dto.ProjectNoteSummaryResponse;
import com.entropybits.worknotes.spring_boot.dto.ProjectResponse;
import com.entropybits.worknotes.spring_boot.dto.PutAgentStateRequest;
import com.entropybits.worknotes.spring_boot.entity.AgentConversationState;
import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AgentConversationStateRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.service.AudioTranscriptionService;
import com.entropybits.worknotes.spring_boot.service.ProjectNoteContextService;
import com.entropybits.worknotes.spring_boot.service.ProjectService;
import com.entropybits.worknotes.spring_boot.service.RetrievalService;
import com.entropybits.worknotes.spring_boot.service.RetrievedChunk;
import com.entropybits.worknotes.spring_boot.service.TopicBlockService;
import com.entropybits.worknotes.spring_boot.service.MediaBlockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
    private final ProjectNoteContextService projectNoteContextService;
    private final ProjectService projectService;
    private final AudioTranscriptionService audioTranscriptionService;
    private final ObjectMapper objectMapper;

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

    /** agent 的 transcribe_audio 工具：手动触发一次完整转写，立即返回，不等结果 */
    @PostMapping("/notes/{noteId}/audio-transcriptions/{blockId}")
    public AudioTranscriptionResponse triggerAudioTranscription(@PathVariable Long noteId, @PathVariable String blockId) {
        return AudioTranscriptionResponse.from(audioTranscriptionService.triggerFromAgent(noteId, blockId), objectMapper);
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

    /** 音频原始字节（整段或第 segment 段）。长录音转写用，体积可能上百 MB，不能像图片那样塞进 JSON */
    @GetMapping("/notes/{noteId}/blocks/{blockId}/audio")
    public ResponseEntity<Resource> blockAudio(
            @PathVariable Long noteId,
            @PathVariable String blockId,
            @RequestParam(required = false) Integer segment) {
        MediaBlockService.AudioFile audio = mediaBlockService.resolveAudioFile(noteId, blockId, segment);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mimeType()))
                .body(new FileSystemResource(audio.path()));
    }

    @PatchMapping("/notes/{noteId}/blocks/{blockId}")
    public Map<String, Object> patchBlock(
            @PathVariable Long noteId,
            @PathVariable String blockId,
            @RequestBody Map<String, Object> fields) {
        return mediaBlockService.patchBlock(noteId, blockId, fields);
    }

    @GetMapping("/projects/{projectId}/notes")
    public List<ProjectNoteSummaryResponse> projectNotes(@PathVariable Long projectId) {
        return projectNoteContextService.getNoteSummaries(projectId);
    }

    @PatchMapping("/projects/{projectId}/fields")
    public ProjectResponse patchProjectField(@PathVariable Long projectId, @RequestBody Map<String, String> fields) {
        return projectService.updateProjectField(projectId, fields.get("field"), fields.get("value"));
    }
}
