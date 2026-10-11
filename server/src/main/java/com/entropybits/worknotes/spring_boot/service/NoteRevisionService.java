/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.EditedNoteRef;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.AiChatConversationRepository;
import com.entropybits.worknotes.spring_boot.repository.AiChatMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRevisionRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 助手改笔记的快照与撤销。一轮对话（turnId）里某篇笔记第一次被改动之前存一份快照，
 * 撤销时把这一轮改过的笔记全部恢复。判断"用户之后又改过"用正文内容比较而不是修改时间：
 * 打开着的编辑器在同步 agent 的改动后会自动保存一次，修改时间必然变化，按时间判断会每次误报。
 */
@Service
public class NoteRevisionService {

    public record UndoResult(List<EditedNoteRef> conflicts, List<Long> restoredNoteIds) {}

    private final NoteRevisionRepository revisionRepository;
    private final AiChatMessageRepository chatMessageRepository;
    private final AiChatConversationRepository conversationRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final ContentIndexingService contentIndexingService;
    private final ObjectMapper objectMapper;

    public NoteRevisionService(NoteRevisionRepository revisionRepository, AiChatMessageRepository chatMessageRepository,
                               AiChatConversationRepository conversationRepository, NoteRepository noteRepository,
                               UserRepository userRepository, ContentIndexingService contentIndexingService,
                               ObjectMapper objectMapper) {
        this.revisionRepository = revisionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.contentIndexingService = contentIndexingService;
        this.objectMapper = objectMapper;
    }

    /** agent 写入所属的一轮 = 该会话最新一条用户消息的 id；拿不到时返回 null（这次改动不存快照） */
    public String resolveAgentTurn(User user, String conversationId) {
        if (conversationId == null || !conversationId.matches("\\d+")) return null;
        return conversationRepository.findById(Long.parseLong(conversationId))
                .filter(c -> c.getOwner().getId().equals(user.getId()))
                .flatMap(c -> chatMessageRepository.findFirstByConversationAndRoleOrderByIdDesc(c, AiChatMessage.Role.USER))
                .map(m -> String.valueOf(m.getId()))
                .orElse(null);
    }

    public void snapshotBeforeEdit(Note note, String turnId, NoteRevision.Source source) {
        if (turnId == null) return;
        if (revisionRepository.findByTurnIdAndNoteId(turnId, note.getId()).isPresent()) return;
        revisionRepository.save(NoteRevision.builder()
                .noteId(note.getId()).ownerId(note.getOwner().getId()).turnId(turnId).source(source)
                .contentType(note.getContentType()).content(note.getContent())
                .build());
    }

    public void recordAfter(String turnId, Long noteId, String contentAfter) {
        if (turnId == null) return;
        revisionRepository.findByTurnIdAndNoteId(turnId, noteId).ifPresent(r -> {
            r.setContentAfter(contentAfter);
            revisionRepository.save(r);
        });
    }

    public List<EditedNoteRef> editedNotes(String turnId) {
        List<EditedNoteRef> result = new ArrayList<>();
        for (NoteRevision r : revisionRepository.findByTurnIdOrderByIdAsc(turnId)) {
            noteRepository.findById(r.getNoteId()).ifPresent(n -> result.add(toRef(n, r)));
        }
        return result;
    }

    public List<EditedNoteRef> listTurn(String username, String turnId) {
        User user = getUser(username);
        List<EditedNoteRef> result = new ArrayList<>();
        for (NoteRevision r : revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc(turnId, user.getId())) {
            ownedNote(r, user).ifPresent(n -> result.add(toRef(n, r)));
        }
        return result;
    }

    public UndoResult undo(String username, String turnId, boolean force) {
        User user = getUser(username);
        List<NoteRevision> revisions = revisionRepository.findByTurnIdAndOwnerIdOrderByIdAsc(turnId, user.getId());
        if (revisions.isEmpty()) throw new ResourceNotFoundException("没有可撤销的修改");

        List<EditedNoteRef> conflicts = new ArrayList<>();
        for (NoteRevision r : revisions) {
            ownedNote(r, user).map(n -> toRef(n, r)).filter(EditedNoteRef::modifiedSince).ifPresent(conflicts::add);
        }
        if (!conflicts.isEmpty() && !force) return new UndoResult(conflicts, List.of());

        List<Long> restored = new ArrayList<>();
        for (NoteRevision r : revisions) {
            ownedNote(r, user).ifPresent(note -> {
                note.setContent(r.getContent());
                note.setContentType(r.getContentType());
                noteRepository.save(note);
                contentIndexingService.reindexNote(note.getId());
                restored.add(note.getId());
            });
        }
        revisionRepository.deleteAll(revisions);
        return new UndoResult(List.of(), restored);
    }

    private EditedNoteRef toRef(Note note, NoteRevision r) {
        boolean modified = r.getContentAfter() != null
                && !sameContent(note.getContentType(), note.getContent(), r.getContentAfter(), objectMapper);
        return new EditedNoteRef(note.getId(), note.getTitle(), modified);
    }

    private Optional<Note> ownedNote(NoteRevision r, User user) {
        return noteRepository.findById(r.getNoteId()).filter(n -> n.getOwner().getId().equals(user.getId()));
    }

    /**
     * Editor.js 按块的"类型 + Markdown 文本"比较，不比原始 JSON：编辑器同步 agent 的改动后自动保存
     * 会重新序列化（刷新 time/version、给代码块加 tunes、把 &amp;quot; 存回成 "、给图片加宽度），
     * 原始 JSON 几乎每次都不同，按它比较会让撤销每次都误报"之后又改过"。不可编辑的特殊块比 hash。
     * 其他格式按去尾空白后的文本比。
     */
    static boolean sameContent(String contentType, String a, String b, ObjectMapper om) {
        if (a == null || b == null) return a == b;
        if ("editorjs".equals(contentType)) {
            return blockSignature(a, om).equals(blockSignature(b, om));
        }
        return a.stripTrailing().equals(b.stripTrailing());
    }

    private static List<String> blockSignature(String content, ObjectMapper om) {
        List<String> result = new ArrayList<>();
        for (AgentNoteBlocks.Block b : AgentNoteBlocks.editorjsBlocks(content, om)) {
            result.add(b.type() + "|" + (b.editable() ? b.text() : b.hash()));
        }
        return result;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
