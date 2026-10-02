/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.dto.AttachmentPayload;
import com.entropybits.worknotes.spring_boot.dto.ChatAttachmentRef;
import com.entropybits.worknotes.spring_boot.dto.ChatCitation;
import com.entropybits.worknotes.spring_boot.dto.ChatMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.ChatStreamEvent;
import com.entropybits.worknotes.spring_boot.dto.ConversationResponse;
import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Project;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AgentConversationStateRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatConversationRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.ProjectRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GlobalChatService {

    private static final int FALLBACK_TITLE_CHAR_CAP = 20;

    private final AiChatMessageRepository chatMessageRepository;
    private final AiChatConversationRepository conversationRepository;
    private final AgentConversationStateRepository agentConversationStateRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final ProjectRepository projectRepository;
    private final SourceClipRepository sourceClipRepository;
    private final ContentChunkingService chunkingService;
    private final AgentServiceClient agentServiceClient;
    private final LocalFileExtractionService extractionService;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final AiTranslationService anthropicService;
    private final AiTranslationService openAiService;
    private final AiTranslationService deepseekService;
    private final AiTranslationService doubaoService;

    public GlobalChatService(AiChatMessageRepository chatMessageRepository,
                              AiChatConversationRepository conversationRepository,
                              AgentConversationStateRepository agentConversationStateRepository,
                              UserRepository userRepository,
                              NoteRepository noteRepository,
                              ProjectRepository projectRepository,
                              SourceClipRepository sourceClipRepository,
                              ContentChunkingService chunkingService,
                              AgentServiceClient agentServiceClient,
                              LocalFileExtractionService extractionService,
                              ObjectMapper objectMapper,
                              AiProperties aiProperties,
                              @Qualifier("anthropicTranslationService") AiTranslationService anthropicService,
                              @Qualifier("openAiTranslationService") AiTranslationService openAiService,
                              @Qualifier("deepseekTranslationService") AiTranslationService deepseekService,
                              @Qualifier("doubaoTranslationService") AiTranslationService doubaoService) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.agentConversationStateRepository = agentConversationStateRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.projectRepository = projectRepository;
        this.sourceClipRepository = sourceClipRepository;
        this.chunkingService = chunkingService;
        this.agentServiceClient = agentServiceClient;
        this.extractionService = extractionService;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.deepseekService = deepseekService;
        this.doubaoService = doubaoService;
    }

    // agent推理逻辑整体在独立的Python/LangGraph服务（AgentServiceClient），这里只做：
    // 解析/懒创建会话 -> 持久化用户消息 -> 调用Agent服务并把事件原样转发给前端 -> 持久化
    // 最终回复 -> 首轮问答后异步生成标题。conversationId为空表示"新建会话"，在这次请求里
    // 和第一条用户消息一起创建，不单独开一个"创建会话"的接口（懒创建，见设计文档）。
    public void sendMessageStream(String username, String content, Long conversationId, Long currentNoteId,
                                   Long currentProjectId, List<AttachmentPayload> attachments, SseEmitter emitter) {
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            User user = getUser(username);
            AiChatConversation conversation = conversationId == null
                    ? conversationRepository.save(AiChatConversation.builder().owner(user).build())
                    : loadOwnedConversationOrThrow(conversationId, user);

            List<ChatAttachmentRef> attachmentRefs = attachments == null ? List.of() : attachments.stream()
                    .map(a -> new ChatAttachmentRef(a.type(), a.url(), a.fileName()))
                    .toList();
            String attachmentsJson = attachmentRefs.isEmpty() ? null : writeJson(attachmentRefs);

            Note currentNote = loadOwnedNoteOrNull(currentNoteId, user);
            // currentNoteId本身不代表调用者拥有这条笔记（可能是别人的笔记ID、已删除的ID，或压根没传）。
            // 下游（agent服务的note-references工具）一旦拿到这个ID就会按它去查内容，所以只有在
            // currentNote非空（即真正属于user）时才允许把ID继续往下传，否则一律传null。
            Long ownedNoteId = currentNote == null ? null : currentNoteId;

            Project currentProject = loadOwnedProjectOrNull(currentProjectId, user);
            // 和 ownedNoteId 同样的道理：currentProjectId 不代表调用者拥有这个项目，
            // 只有真正属于 user 的才允许继续往 agent 服务传，否则一律传 null——防止
            // 伪造 projectId 让 AI 读写别人的项目。
            Long ownedProjectId = currentProject == null ? null : currentProjectId;
            String currentProjectContext = currentProject == null ? null : buildProjectContext(currentProject);

            AiChatMessage userMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .conversation(conversation).role(AiChatMessage.Role.USER).content(content)
                    .attachmentsJson(attachmentsJson).build());
            sendEvent(emitter, ChatStreamEvent.userMessage(toResponse(userMessage), conversation.getId()), disconnected);

            String currentNoteContext = currentNote == null ? null
                    : "标题：" + currentNote.getTitle() + "\n正文：\n" + currentNoteBodyText(currentNote);

            StringBuilder finalReplyText = new StringBuilder();
            List<ChatCitation>[] resolvedCitations = new List[]{List.of()};
            boolean[] chatSucceeded = {false};
            boolean[] awaitingConfirm = {false};
            String conversationIdStr = String.valueOf(conversation.getId());

            try {
                agentServiceClient.streamChat(username, content, conversationIdStr, currentNoteContext,
                        currentProjectContext, attachments, ownedNoteId, ownedProjectId,
                        new AgentServiceClient.StreamListener() {
                            @Override
                            public void onTextDelta(String text) {
                                finalReplyText.append(text);
                                sendEvent(emitter, ChatStreamEvent.textDelta(text), disconnected);
                            }

                            @Override
                            public void onToolCall(String query) {
                                sendEvent(emitter, ChatStreamEvent.toolCall(query == null ? "" : query), disconnected);
                            }

                            @Override
                            public void onDone(String finalContent, List<ChatCitation> citations) {
                                finalReplyText.setLength(0);
                                finalReplyText.append(finalContent);
                                resolvedCitations[0] = resolveCitationTitles(citations);
                                chatSucceeded[0] = true;
                            }

                            @Override
                            public void onError(String message) {
                                if (finalReplyText.length() == 0) {
                                    finalReplyText.append(message);
                                } else {
                                    finalReplyText.append("\n\n（生成中断，请重新提问）");
                                }
                            }

                            @Override
                            public void onConfirmRequest(String proposalId, String blockType, Long noteId, Map<String, Object> preview) {
                                awaitingConfirm[0] = true;
                                sendEvent(emitter, ChatStreamEvent.confirmRequest(proposalId, blockType, noteId, preview), disconnected);
                            }

                            @Override
                            public void onBlockUpdated(Long noteId, String blockId, String blockType, List<Map<String, Object>> items) {
                                sendEvent(emitter, ChatStreamEvent.blockUpdated(noteId, blockId, blockType, items), disconnected);
                            }

                            @Override
                            public void onMediaBlockUpdated(Long noteId, String blockId, String blockType, Map<String, Object> data) {
                                sendEvent(emitter, ChatStreamEvent.mediaBlockUpdated(noteId, blockId, blockType, data), disconnected);
                            }
                        });
            } catch (Exception e) {
                log.error("agent service call failed for user {}", username, e);
                if (finalReplyText.length() == 0) {
                    finalReplyText.append("抱歉，这次没能回复，换个说法试试？");
                } else {
                    finalReplyText.append("\n\n（生成中断，请重新提问）");
                }
            }

            if (awaitingConfirm[0]) {
                // 等用户在聊天面板确认——这一轮不持久化assistant消息、不发done，
                // 真正的回复要等 resumeStream 那一轮才会来
                emitter.complete();
                return;
            }

            String reply = finalReplyText.length() > 0 ? finalReplyText.toString() : "抱歉，这次没能回复，换个说法试试？";
            String citationsJson = chatSucceeded[0] && !resolvedCitations[0].isEmpty()
                    ? writeJson(resolvedCitations[0]) : null;

            AiChatMessage assistantMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .conversation(conversation).role(AiChatMessage.Role.ASSISTANT).content(reply)
                    .citationsJson(citationsJson).build());

            touchConversation(conversation);
            // 门槛是"title是否还是null"，不是"是不是这次请求里新建的会话"——V20迁移出来的
            // 存量会话conversationId永远不为null，如果只在conversationId==null时触发，这些
            // 会话就永远生不出标题；同理上一次生成失败（title仍是null）也要在下一轮问答后重试。
            if (conversation.getTitle() == null) {
                triggerTitleGeneration(conversation.getId(), content, reply);
            }

            sendEvent(emitter, ChatStreamEvent.done(toResponse(assistantMessage)), disconnected);
            emitter.complete();
        } catch (ResourceNotFoundException e) {
            // 会话归属校验失败（不存在/不属于当前用户）——按现有其他owner校验的写法，应该是
            // 一个可观察到的404级错误，不能被下面的通用catch吞掉变成一条"抱歉没能回复"的
            // 普通对话错误。emitter仍然要收尾（发error帧+completeWithError），但异常本身要
            // 继续往外抛，调用方（含单测）能感知到这是一次无效请求而不是一次对话失败。
            log.warn("conversation lookup failed for user {}", username, e);
            sendEvent(emitter, ChatStreamEvent.error("抱歉，这次没能回复，换个说法试试？"), disconnected);
            emitter.completeWithError(e);
            throw e;
        } catch (Exception e) {
            log.error("unexpected error in sendMessageStream for user {}", username, e);
            sendEvent(emitter, ChatStreamEvent.error("抱歉，这次没能回复，换个说法试试？"), disconnected);
            emitter.completeWithError(e);
        }
    }

    public void resumeStream(String username, Long conversationId, String proposalId, String decision, SseEmitter emitter) {
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            User user = getUser(username);
            AiChatConversation conversation = loadOwnedConversationOrThrow(conversationId, user);

            StringBuilder finalReplyText = new StringBuilder();
            List<ChatCitation>[] resolvedCitations = new List[]{List.of()};
            boolean[] chatSucceeded = {false};
            boolean[] awaitingConfirm = {false};

            try {
                agentServiceClient.resumeChat(String.valueOf(conversation.getId()), proposalId, decision,
                        new AgentServiceClient.StreamListener() {
                            @Override
                            public void onTextDelta(String text) {
                                finalReplyText.append(text);
                                sendEvent(emitter, ChatStreamEvent.textDelta(text), disconnected);
                            }

                            @Override
                            public void onToolCall(String query) {
                                sendEvent(emitter, ChatStreamEvent.toolCall(query == null ? "" : query), disconnected);
                            }

                            @Override
                            public void onDone(String finalContent, List<ChatCitation> citations) {
                                finalReplyText.setLength(0);
                                finalReplyText.append(finalContent);
                                resolvedCitations[0] = resolveCitationTitles(citations);
                                chatSucceeded[0] = true;
                            }

                            @Override
                            public void onError(String message) {
                                if (finalReplyText.length() == 0) finalReplyText.append(message);
                                else finalReplyText.append("\n\n（生成中断，请重新提问）");
                            }

                            @Override
                            public void onConfirmRequest(String proposalId2, String blockType, Long noteId, Map<String, Object> preview) {
                                awaitingConfirm[0] = true;
                                sendEvent(emitter, ChatStreamEvent.confirmRequest(proposalId2, blockType, noteId, preview), disconnected);
                            }

                            @Override
                            public void onBlockUpdated(Long noteId, String blockId, String blockType, List<Map<String, Object>> items) {
                                sendEvent(emitter, ChatStreamEvent.blockUpdated(noteId, blockId, blockType, items), disconnected);
                            }

                            @Override
                            public void onMediaBlockUpdated(Long noteId, String blockId, String blockType, Map<String, Object> data) {
                                sendEvent(emitter, ChatStreamEvent.mediaBlockUpdated(noteId, blockId, blockType, data), disconnected);
                            }
                        });
            } catch (Exception e) {
                log.error("agent service resume call failed for user {}", username, e);
                if (finalReplyText.length() == 0) finalReplyText.append("抱歉，这次没能回复，换个说法试试？");
                else finalReplyText.append("\n\n（生成中断，请重新提问）");
            }

            if (awaitingConfirm[0]) {
                emitter.complete();
                return;
            }

            String reply = finalReplyText.length() > 0 ? finalReplyText.toString() : "抱歉，这次没能回复，换个说法试试？";
            String citationsJson = chatSucceeded[0] && !resolvedCitations[0].isEmpty()
                    ? writeJson(resolvedCitations[0]) : null;

            AiChatMessage assistantMessage = chatMessageRepository.save(AiChatMessage.builder()
                    .conversation(conversation).role(AiChatMessage.Role.ASSISTANT).content(reply)
                    .citationsJson(citationsJson).build());

            touchConversation(conversation);
            // 门槛同sendMessageStream：title是否还是null，而不是"这轮是不是结构上的第一轮"——
            // 旧的"没有ASSISTANT消息"判断对title已非null的旧会话会漏触发重试。resume场景下
            // 这轮请求本身没有新的用户文本（只有proposalId/decision），仍然用会话最早一条
            // 用户消息作为生成标题的素材。
            if (conversation.getTitle() == null) {
                String firstUserMessage = chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)
                        .stream().filter(m -> m.getRole() == AiChatMessage.Role.USER)
                        .findFirst().map(AiChatMessage::getContent).orElse("");
                triggerTitleGeneration(conversation.getId(), firstUserMessage, reply);
            }

            sendEvent(emitter, ChatStreamEvent.done(toResponse(assistantMessage)), disconnected);
            emitter.complete();
        } catch (ResourceNotFoundException e) {
            // 理由同 sendMessageStream 里的同名catch：会话归属校验失败要能被调用方感知到，
            // 不能被下面的通用catch吞掉。
            log.warn("conversation lookup failed for user {}", username, e);
            sendEvent(emitter, ChatStreamEvent.error("抱歉，这次没能回复，换个说法试试？"), disconnected);
            emitter.completeWithError(e);
            throw e;
        } catch (Exception e) {
            log.error("unexpected error in resumeStream for user {}", username, e);
            sendEvent(emitter, ChatStreamEvent.error("抱歉，这次没能回复，换个说法试试？"), disconnected);
            emitter.completeWithError(e);
        }
    }

    // 只更新lastMessageAt这一列（定向update），不走conversationRepository.save(conversation)
    // 整体落盘——调用方手里的conversation对象是这次请求一开始加载/创建的，如果同一会话的
    // 异步标题生成线程（triggerTitleGeneration）在此期间已经把title写进了数据库，save()整个
    // 实体会把内存里还是null的title覆盖回去，把刚生成好的标题静默冲掉。定向update从根上避免
    // 这个字段被牵连。
    private void touchConversation(AiChatConversation conversation) {
        Instant now = Instant.now();
        conversation.setLastMessageAt(now);
        conversationRepository.updateLastMessageAt(conversation.getId(), now);
    }

    // 标题生成失败不能影响主对话流程已经返回给用户——用独立线程异步跑，风格对齐
    // GlobalChatController里"new Thread(...).start()"驱动SSE流的既有写法，不引入新的
    // 线程池/@Async机制。竞态：如果用户在标题生成完成前又极快发了第二轮消息，两轮都可能
    // 判断出"title还是null"各自触发一次生成——无害，以后写入的为准，不做互斥。
    private void triggerTitleGeneration(Long conversationId, String firstUserMessage, String firstAssistantReply) {
        new Thread(() -> {
            try {
                AiTranslationService ai = resolveService();
                String title = ai.generateConversationTitle(firstUserMessage, firstAssistantReply);
                conversationRepository.findById(conversationId).ifPresent(c -> {
                    c.setTitle(title);
                    conversationRepository.save(c);
                });
            } catch (Exception e) {
                log.warn("title generation failed for conversation {}, will keep title null", conversationId, e);
            }
        }).start();
    }

    private AiTranslationService resolveService() {
        return switch (aiProperties.getProvider().toLowerCase()) {
            case "openai" -> openAiService;
            case "deepseek" -> deepseekService;
            case "doubao" -> doubaoService;
            default -> anthropicService;
        };
    }

    // Agent服务只知道sourceType/sourceId，人类可读的标题按ID反查（复用现有lookupTitle逻辑）。
    // WEB类型是例外——Python已经直接给出title，不需要反查，见下面的WEB分支。
    private List<ChatCitation> resolveCitationTitles(List<ChatCitation> citations) {
        Map<String, String> titleCache = new LinkedHashMap<>();
        return citations.stream()
                .map(c -> {
                    if ("WEB".equals(c.sourceType())) {
                        return c;
                    }
                    ContentChunk.SourceType sourceType = ContentChunk.SourceType.valueOf(c.sourceType());
                    String key = titleKey(sourceType, c.sourceId());
                    String title = titleCache.computeIfAbsent(key, k -> lookupTitle(sourceType, c.sourceId()));
                    return new ChatCitation(c.sourceType(), c.sourceId(), title, c.sourceUrl());
                })
                .toList();
    }

    private void sendEvent(SseEmitter emitter, ChatStreamEvent event, java.util.concurrent.atomic.AtomicBoolean disconnected) {
        if (disconnected.get()) return;
        try {
            emitter.send(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            disconnected.set(true);
            log.warn("SSE send failed (client likely disconnected), continuing pipeline without further sends", e);
        }
    }

    public List<ConversationResponse> listConversations(String username) {
        User user = getUser(username);
        return conversationRepository.findByOwnerOrderByLastMessageAtDesc(user).stream()
                .map(this::toConversationResponse).toList();
    }

    public List<ChatMessageResponse> getConversationMessages(String username, Long conversationId) {
        User user = getUser(username);
        AiChatConversation conversation = loadOwnedConversationOrThrow(conversationId, user);
        return chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation)
                .stream().map(this::toResponse).toList();
    }

    public ConversationResponse renameConversation(String username, Long conversationId, String title) {
        User user = getUser(username);
        AiChatConversation conversation = loadOwnedConversationOrThrow(conversationId, user);
        conversation.setTitle(title);
        conversationRepository.save(conversation);
        return toConversationResponse(conversation);
    }

    public void deleteConversation(String username, Long conversationId) {
        User user = getUser(username);
        AiChatConversation conversation = loadOwnedConversationOrThrow(conversationId, user);
        conversationRepository.delete(conversation);
        // agent_conversation_state不受AiChatConversation的FK级联影响（按conversationId字符串
        // 独立存取，见AgentConversationState类注释），LangGraph checkpointer的序列化状态里
        // 复制了一份对话内容，不清掉的话用户删会话后这份内容副本会一直留在这张表里。
        agentConversationStateRepository.deleteByConversationId(String.valueOf(conversationId));
    }

    private ConversationResponse toConversationResponse(AiChatConversation c) {
        String displayTitle = c.getTitle() != null ? c.getTitle() : fallbackTitle(c);
        return ConversationResponse.builder().id(c.getId()).title(displayTitle).lastMessageAt(c.getLastMessageAt()).build();
    }

    // title为空时（还没生成完，或V20迁移出来的存量会话本来就没有title）用第一条用户消息
    // 截断展示——纯展示层兜底，不回写数据库，真正的title只在首轮问答后异步生成一次。
    private String fallbackTitle(AiChatConversation c) {
        String firstContent = chatMessageRepository.findFirstByConversationOrderByCreatedAtAsc(c)
                .map(AiChatMessage::getContent).orElse(null);
        if (firstContent == null || firstContent.isBlank()) return "新对话";
        return firstContent.length() > FALLBACK_TITLE_CHAR_CAP
                ? firstContent.substring(0, FALLBACK_TITLE_CHAR_CAP) + "…"
                : firstContent;
    }

    AiChatConversation loadOwnedConversationOrThrow(Long conversationId, User user) {
        AiChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在"));
        if (!conversation.getOwner().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("会话不存在");
        }
        return conversation;
    }

    private Note loadOwnedNoteOrNull(Long noteId, User user) {
        if (noteId == null) return null;
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElse(null);
    }

    private Project loadOwnedProjectOrNull(Long projectId, User user) {
        if (projectId == null) return null;
        return projectRepository.findById(projectId)
                .filter(p -> p.getOwner().getId().equals(user.getId()))
                .orElse(null);
    }

    private String buildProjectContext(Project project) {
        StringBuilder sb = new StringBuilder();
        sb.append("名称：").append(project.getName());
        if (project.getCategory() != null) sb.append("\n分类：").append(project.getCategory());
        if (project.getTechnologies() != null && !project.getTechnologies().isBlank()) {
            // Project.technologies 本身就是逗号分隔的单个字符串（不是 List），直接拼接。
            sb.append("\n技术栈：").append(project.getTechnologies());
        }
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append("\n现有描述（英文）：").append(project.getDescription());
        }
        if (project.getDescriptionZh() != null && !project.getDescriptionZh().isBlank()) {
            sb.append("\n现有描述（中文）：").append(project.getDescriptionZh());
        }
        return sb.toString();
    }

    private static final int CURRENT_NOTE_BODY_CHAR_CAP = 4000;

    private String currentNoteBodyText(Note note) {
        String text = String.join("\n", chunkingService.chunkNote(note));
        return text.length() > CURRENT_NOTE_BODY_CHAR_CAP
                ? text.substring(0, CURRENT_NOTE_BODY_CHAR_CAP) + "…（内容过长，已截断）"
                : text;
    }

    private String titleKey(ContentChunk.SourceType sourceType, Long sourceId) {
        return sourceType + ":" + sourceId;
    }

    private String lookupTitle(ContentChunk.SourceType sourceType, Long sourceId) {
        if (sourceType == ContentChunk.SourceType.NOTE) {
            return noteRepository.findById(sourceId).map(Note::getTitle).orElse("（已删除的笔记）");
        }
        if (sourceType == ContentChunk.SourceType.LOCAL_MEDIA) {
            return extractionService.findDisplayNameForExtraction(sourceId);
        }
        return sourceClipRepository.findById(sourceId).map(SourceClip::getTitle).orElse("（已删除的收藏）");
    }

    private ChatMessageResponse toResponse(AiChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .role(m.getRole().name())
                .content(m.getContent())
                .citations(readCitations(m.getCitationsJson()))
                .attachments(readAttachments(m.getAttachmentsJson()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private List<ChatCitation> readCitations(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatCitation>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ChatAttachmentRef> readAttachments(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatAttachmentRef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
