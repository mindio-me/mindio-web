/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteRevision;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentNoteEditServiceTest {

    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock NoteRevisionService revisionService;
    @Mock AgentImageEditService imageEditService;
    @Mock ContentIndexingService contentIndexingService;

    final ObjectMapper om = new ObjectMapper();
    AgentNoteEditService service;
    User alice = User.builder().id(1L).username("alice").build();
    User bob = User.builder().id(2L).username("bob").build();

    static final String DOC = """
            {"time":1,"blocks":[
              {"id":"h","type":"header","data":{"text":"方案","level":2}},
              {"id":"p1","type":"paragraph","data":{"text":"旧段落"}},
              {"id":"p2","type":"paragraph","data":{"text":"第二段"}},
              {"id":"a","type":"audioRecord","data":{"url":"/u.webm"}}
            ],"version":"2.31.6"}""";

    @BeforeEach
    void setUp() {
        service = new AgentNoteEditService(userRepository, noteRepository, revisionService, imageEditService,
                contentIndexingService, om);
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        lenient().when(revisionService.resolveAgentTurn(alice, "5")).thenReturn("77");
        lenient().when(imageEditService.localize(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    private Note editorjsNote() {
        return Note.builder().id(9L).title("设计").owner(alice).contentType("editorjs").content(DOC).build();
    }

    private String hashOf(String content, String id) {
        return AgentNoteBlocks.editorjsBlocks(content, om).stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow().hash();
    }

    private List<String> blockIds(String content) throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        om.readTree(content).path("blocks").forEach(b -> ids.add(b.path("id").asText()));
        return ids;
    }

    @Test
    void readBlocks_returnsBlockView() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(editorjsNote()));

        Map<String, Object> view = service.readBlocks("alice", 9L);

        assertThat(view).containsEntry("contentType", "editorjs").containsEntry("title", "设计");
        assertThat((List<?>) view.get("blocks")).hasSize(4);
    }

    @Test
    void editorjs_replaceInsertDeleteMove_inOneBatch() throws Exception {
        Note note = editorjsNote();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        Map<String, Object> result = service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "replace", "blockId", "p1", "hash", hashOf(DOC, "p1"), "markdown", "新段落\n\n- 要点"),
                Map.of("op", "insert", "after", "end", "markdown", "## 结论"),
                Map.of("op", "move", "blockId", "p2", "hash", hashOf(DOC, "p2"), "after", "start"),
                Map.of("op", "delete", "blockIds", List.of("a"), "hashes", List.of(hashOf(DOC, "a")))));

        JsonNode blocks = om.readTree(note.getContent()).path("blocks");
        assertThat(blocks).extracting(b -> b.path("type").asText())
                .containsExactly("paragraph", "header", "paragraph", "list", "header");
        assertThat(blocks.get(0).path("id").asText()).isEqualTo("p2");
        assertThat(blocks.get(2).path("data").path("text").asText()).isEqualTo("新段落");
        assertThat(blockIds(note.getContent())).doesNotContain("p1", "a");
        assertThat(om.readTree(note.getContent()).path("version").asText()).isEqualTo("2.31.6");
        assertThat(result).containsEntry("turnId", "77").containsEntry("contentType", "editorjs");
        List<Map<String, Object>> ops = (List<Map<String, Object>>) result.get("ops");
        assertThat(ops).extracting(o -> o.get("op"))
                .containsExactly("insert", "insert", "delete", "insert", "move", "delete");
        verify(revisionService).snapshotBeforeEdit(any(), eq("77"), eq(NoteRevision.Source.AGENT));
        verify(revisionService).recordAfter("77", 9L, note.getContent());
        verify(noteRepository).save(note);
        verify(contentIndexingService).reindexNote(9L);
    }

    @Test
    void editorjs_staleHash_rejectsWholeBatchAndLeavesContentUntouched() {
        Note note = editorjsNote();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "insert", "after", "end", "markdown", "新增"),
                Map.of("op", "replace", "blockId", "p1", "hash", "deadbeef", "markdown", "x"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("p1").hasMessageContaining("read_note_blocks");
        assertThat(note.getContent()).isEqualTo(DOC);
        verify(noteRepository, never()).save(any());
        verify(revisionService, never()).snapshotBeforeEdit(any(), any(), any());
    }

    @Test
    void editorjs_replaceSpecialBlock_rejected() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(editorjsNote()));

        assertThatThrownBy(() -> service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "replace", "blockId", "a", "hash", hashOf(DOC, "a"), "markdown", "x"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("只能删除或移动");
    }

    @Test
    void editorjs_moveAfterBlockDeletedInSameBatch_rejected() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(editorjsNote()));

        assertThatThrownBy(() -> service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "delete", "blockIds", List.of("p1"), "hashes", List.of(hashOf(DOC, "p1"))),
                Map.of("op", "move", "blockId", "p2", "hash", hashOf(DOC, "p2"), "after", "p1"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("p1");
    }

    @Test
    void editorjs_sameBlockReferencedTwice_rejected() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(editorjsNote()));

        assertThatThrownBy(() -> service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "replace", "blockId", "p1", "hash", hashOf(DOC, "p1"), "markdown", "x"),
                Map.of("op", "delete", "blockIds", List.of("p1"), "hashes", List.of(hashOf(DOC, "p1"))))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("p1");
    }

    @Test
    void editorjs_insertedImageUrlIsLocalized() throws Exception {
        Note note = editorjsNote();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));
        when(imageEditService.localize(alice, "https://x.com/a.png")).thenReturn("/api/uploads/agent_image/a.png");

        service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "insert", "after", "start", "markdown", "![图](https://x.com/a.png)")));

        JsonNode first = om.readTree(note.getContent()).path("blocks").get(0);
        assertThat(first.path("data").path("file").path("url").asText()).isEqualTo("/api/uploads/agent_image/a.png");
    }

    @Test
    void markdown_replaceAndInsert_rejoinsWithBlankLines() {
        Note note = Note.builder().id(9L).title("t").owner(alice).contentType("markdown")
                .content("# 标题\n\n旧段落\n\n结尾\n").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));
        List<AgentNoteBlocks.Block> blocks = AgentNoteBlocks.markdownBlocks(note.getContent());

        Map<String, Object> result = service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "replace", "blockId", blocks.get(1).id(), "hash", blocks.get(1).hash(), "markdown", "新段落"),
                Map.of("op", "insert", "after", blocks.get(0).id(), "markdown", "插入的段落")));

        assertThat(note.getContent()).isEqualTo("# 标题\n\n插入的段落\n\n新段落\n\n结尾\n");
        assertThat((List<?>) result.get("ops")).isEmpty();
    }

    @Test
    void richtext_rejected() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(
                Note.builder().id(9L).owner(alice).contentType("richtext").content("<p>x</p>").build()));

        assertThatThrownBy(() -> service.applyAsAgent("alice", 9L, "5",
                List.of(Map.of("op", "insert", "after", "end", "markdown", "x"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("富文本");
    }

    @Test
    void otherUsersNote_notFound() {
        when(noteRepository.findById(9L)).thenReturn(Optional.of(
                Note.builder().id(9L).owner(bob).contentType("markdown").content("x").build()));

        assertThatThrownBy(() -> service.readBlocks("alice", 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replyInsert_usesReplyTurnAndReplyInsertSource() {
        Note note = editorjsNote();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        Map<String, Object> result = service.applyAsReplyInsert("alice", 9L, "回复内容", "p1");

        assertThat((String) result.get("turnId")).startsWith("reply-");
        verify(revisionService).snapshotBeforeEdit(any(), eq((String) result.get("turnId")),
                eq(NoteRevision.Source.REPLY_INSERT));
    }

    @Test
    void editorjs_blocksWithoutIds_getIdsAndClientIsToldToReload() throws Exception {
        String doc = "{\"blocks\":[{\"type\":\"paragraph\",\"data\":{\"text\":\"一\"}},"
                + "{\"type\":\"paragraph\",\"data\":{\"text\":\"二\"}}]}";
        Note note = Note.builder().id(9L).title("t").owner(alice).contentType("editorjs").content(doc).build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(note));

        Map<String, Object> result = service.applyAsAgent("alice", 9L, "5", List.of(
                Map.of("op", "delete", "blockIds", List.of("b0"), "hashes", List.of(hashOf(doc, "b0")))));

        assertThat(blockIds(note.getContent())).hasSize(1).allMatch(id -> !id.isBlank());
        assertThat(result).containsEntry("reload", true);
        assertThat((List<?>) result.get("ops")).isEmpty();
    }
}
