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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * agent 的 read_note_blocks / edit_note，以及回复插入按钮：按块原子地改 Editor.js / Markdown 笔记。
 * 执行分两步——先按读取时的文档一次性解析所有块引用并校验 hash（任何一处不对整批拒绝，
 * 不覆盖用户在读取之后的输入），再在内存副本上按顺序执行；操作之间只通过块对象引用，不依赖
 * 位置，所以 Markdown 那种按位置生成的 id 不会因前面的插入/删除而错位。
 * 详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md。
 */
@Service
public class AgentNoteEditService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final NoteRevisionService revisionService;
    private final AgentImageEditService imageEditService;
    private final ContentIndexingService contentIndexingService;
    private final ObjectMapper om;
    private final MarkdownToEditorjsConverter converter;

    public AgentNoteEditService(UserRepository userRepository, NoteRepository noteRepository,
                                NoteRevisionService revisionService, AgentImageEditService imageEditService,
                                ContentIndexingService contentIndexingService, ObjectMapper om) {
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.revisionService = revisionService;
        this.imageEditService = imageEditService;
        this.contentIndexingService = contentIndexingService;
        this.om = om;
        this.converter = new MarkdownToEditorjsConverter(om);
    }

    public Map<String, Object> readBlocks(String username, Long noteId) {
        Note note = loadOwnedNote(getUser(username), noteId);
        String contentType = editableContentType(note);
        List<AgentNoteBlocks.Block> blocks = "editorjs".equals(contentType)
                ? AgentNoteBlocks.editorjsBlocks(note.getContent(), om)
                : AgentNoteBlocks.markdownBlocks(note.getContent());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("noteId", note.getId());
        view.put("contentType", contentType);
        view.put("title", note.getTitle());
        view.put("blocks", blocks);
        return view;
    }

    public Map<String, Object> applyAsAgent(String username, Long noteId, String conversationId,
                                            List<Map<String, Object>> operations) {
        User user = getUser(username);
        Note note = loadOwnedNote(user, noteId);
        return apply(user, note, revisionService.resolveAgentTurn(user, conversationId), NoteRevision.Source.AGENT,
                operations);
    }

    public Map<String, Object> applyAsReplyInsert(String username, Long noteId, String markdown, String after) {
        User user = getUser(username);
        Note note = loadOwnedNote(user, noteId);
        Map<String, Object> insert = new HashMap<>();
        insert.put("op", "insert");
        insert.put("after", after == null || after.isBlank() ? "end" : after);
        insert.put("markdown", markdown);
        return apply(user, note, "reply-" + UUID.randomUUID(), NoteRevision.Source.REPLY_INSERT, List.of(insert));
    }

    private Map<String, Object> apply(User user, Note note, String turnId, NoteRevision.Source source,
                                      List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) throw new BadRequestException("operations 不能为空");
        String contentType = editableContentType(note);
        List<Map<String, Object>> ops = new ArrayList<>();
        String newContent = "editorjs".equals(contentType)
                ? applyEditorjs(user, note.getContent(), operations, ops)
                : applyMarkdown(note.getContent(), operations);

        revisionService.snapshotBeforeEdit(note, turnId, source);
        note.setContent(newContent);
        noteRepository.save(note);
        contentIndexingService.reindexNote(note.getId());
        revisionService.recordAfter(turnId, note.getId(), newContent);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noteId", note.getId());
        result.put("contentType", contentType);
        result.put("turnId", turnId);
        result.put("ops", ops);
        return result;
    }

    // ---------- Editor.js ----------

    private String applyEditorjs(User user, String content, List<Map<String, Object>> operations,
                                 List<Map<String, Object>> ops) {
        ObjectNode root = parseRoot(content);
        ArrayNode blocksNode = root.has("blocks") && root.get("blocks").isArray()
                ? (ArrayNode) root.get("blocks") : root.putArray("blocks");
        List<ObjectNode> list = new ArrayList<>();
        blocksNode.forEach(b -> list.add((ObjectNode) b));

        Map<String, ObjectNode> byId = new HashMap<>();
        Map<String, AgentNoteBlocks.Block> viewById = new HashMap<>();
        List<AgentNoteBlocks.Block> view = AgentNoteBlocks.editorjsBlocks(content, om);
        for (int i = 0; i < list.size(); i++) {
            byId.put(view.get(i).id(), list.get(i));
            viewById.put(view.get(i).id(), view.get(i));
        }

        // 第一步：解析与校验（不改动任何东西）
        Set<String> referenced = new HashSet<>();
        for (Map<String, Object> op : operations) {
            switch (opName(op)) {
                case "insert" -> {
                    requireMarkdown(op);
                    requireAnchor(str(op, "after", "end"), viewById);
                }
                case "replace" -> {
                    AgentNoteBlocks.Block b = requireBlock(op, str(op, "blockId", null), str(op, "hash", null), viewById, referenced);
                    if (!b.editable()) throw new BadRequestException("块 " + b.id() + " 是音频/附件等特殊块，只能删除或移动");
                    requireMarkdown(op);
                }
                case "delete" -> {
                    List<String> ids = strList(op, "blockIds");
                    List<String> hashes = strList(op, "hashes");
                    if (ids.isEmpty() || ids.size() != hashes.size()) {
                        throw new BadRequestException("delete 需要等长的 blockIds 和 hashes");
                    }
                    for (int i = 0; i < ids.size(); i++) requireBlock(op, ids.get(i), hashes.get(i), viewById, referenced);
                }
                case "move" -> {
                    requireBlock(op, str(op, "blockId", null), str(op, "hash", null), viewById, referenced);
                    String after = str(op, "after", "end");
                    requireAnchor(after, viewById);
                    if (after.equals(str(op, "blockId", null))) throw new BadRequestException("不能把块移动到它自己之后");
                }
                default -> throw new BadRequestException("不支持的操作: " + opName(op));
            }
        }

        // 第二步：在内存副本上按顺序执行
        for (Map<String, Object> op : operations) {
            switch (opName(op)) {
                case "insert" -> {
                    int at = insertIndex(list, str(op, "after", "end"), byId);
                    insertBlocks(user, list, at, convert(op), ops);
                }
                case "replace" -> {
                    ObjectNode target = byId.get(str(op, "blockId", null));
                    int at = indexOf(list, target);
                    insertBlocks(user, list, at + 1, convert(op), ops);
                    list.remove(at);
                    ops.add(Map.of("op", "delete", "blockId", target.path("id").asText()));
                }
                case "delete" -> {
                    for (String id : strList(op, "blockIds")) {
                        ObjectNode target = byId.get(id);
                        list.remove(indexOf(list, target));
                        ops.add(Map.of("op", "delete", "blockId", target.path("id").asText()));
                    }
                }
                case "move" -> {
                    ObjectNode target = byId.get(str(op, "blockId", null));
                    list.remove(indexOf(list, target));
                    int at = insertIndex(list, str(op, "after", "end"), byId);
                    list.add(at, target);
                    Map<String, Object> move = new HashMap<>();
                    move.put("op", "move");
                    move.put("blockId", target.path("id").asText());
                    move.put("afterBlockId", at == 0 ? null : list.get(at - 1).path("id").asText());
                    ops.add(move);
                }
                default -> throw new IllegalStateException();
            }
        }

        blocksNode.removeAll();
        list.forEach(blocksNode::add);
        try {
            return om.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void insertBlocks(User user, List<ObjectNode> list, int at, List<ObjectNode> newBlocks,
                              List<Map<String, Object>> ops) {
        for (ObjectNode block : newBlocks) {
            if ("image".equals(block.path("type").asText())) {
                ObjectNode file = (ObjectNode) block.path("data").path("file");
                file.put("url", imageEditService.localize(user, file.path("url").asText()));
            }
            Map<String, Object> insert = new HashMap<>();
            insert.put("op", "insert");
            insert.put("afterBlockId", at == 0 ? null : list.get(at - 1).path("id").asText());
            insert.put("block", om.convertValue(block, MAP_TYPE));
            ops.add(insert);
            list.add(at++, block);
        }
    }

    private List<ObjectNode> convert(Map<String, Object> op) {
        List<ObjectNode> blocks = converter.convert(str(op, "markdown", ""));
        if (blocks.isEmpty()) throw new BadRequestException("markdown 内容为空");
        return blocks;
    }

    /** after 引用的块在本批前面的操作里被删掉时，这里找不到，整批报错而不是悄悄插到末尾 */
    private static int insertIndex(List<ObjectNode> list, String after, Map<String, ObjectNode> byId) {
        if ("start".equals(after)) return 0;
        if ("end".equals(after)) return list.size();
        int i = indexOf(list, byId.get(after));
        if (i < 0) throw new BadRequestException("块 " + after + " 已在本批操作中被删除，不能作为位置参照");
        return i + 1;
    }

    private static int indexOf(List<ObjectNode> list, ObjectNode target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) return i;
        }
        return -1;
    }

    // ---------- Markdown ----------

    private static final class Segment {
        String text;
        Segment(String text) { this.text = text; }
    }

    private String applyMarkdown(String content, List<Map<String, Object>> operations) {
        String text = content == null ? "" : content;
        List<AgentNoteBlocks.Block> view = AgentNoteBlocks.markdownBlocks(text);
        List<Segment> list = new ArrayList<>();
        Map<String, Segment> byId = new HashMap<>();
        Map<String, AgentNoteBlocks.Block> viewById = new HashMap<>();
        for (AgentNoteBlocks.Block b : view) {
            Segment s = new Segment(b.text());
            list.add(s);
            byId.put(b.id(), s);
            viewById.put(b.id(), b);
        }

        Set<String> referenced = new HashSet<>();
        for (Map<String, Object> op : operations) {
            switch (opName(op)) {
                case "insert" -> {
                    requireMarkdown(op);
                    requireAnchor(str(op, "after", "end"), viewById);
                }
                case "replace" -> {
                    requireBlock(op, str(op, "blockId", null), str(op, "hash", null), viewById, referenced);
                    requireMarkdown(op);
                }
                case "delete" -> {
                    List<String> ids = strList(op, "blockIds");
                    List<String> hashes = strList(op, "hashes");
                    if (ids.isEmpty() || ids.size() != hashes.size()) {
                        throw new BadRequestException("delete 需要等长的 blockIds 和 hashes");
                    }
                    for (int i = 0; i < ids.size(); i++) requireBlock(op, ids.get(i), hashes.get(i), viewById, referenced);
                }
                case "move" -> {
                    requireBlock(op, str(op, "blockId", null), str(op, "hash", null), viewById, referenced);
                    String after = str(op, "after", "end");
                    requireAnchor(after, viewById);
                    if (after.equals(str(op, "blockId", null))) throw new BadRequestException("不能把块移动到它自己之后");
                }
                default -> throw new BadRequestException("不支持的操作: " + opName(op));
            }
        }

        for (Map<String, Object> op : operations) {
            switch (opName(op)) {
                case "insert" -> list.add(segmentInsertIndex(list, str(op, "after", "end"), byId),
                        new Segment(str(op, "markdown", "").strip()));
                case "replace" -> byId.get(str(op, "blockId", null)).text = str(op, "markdown", "").strip();
                case "delete" -> strList(op, "blockIds").forEach(id -> list.remove(byId.get(id)));
                case "move" -> {
                    Segment target = byId.get(str(op, "blockId", null));
                    list.remove(target);
                    list.add(segmentInsertIndex(list, str(op, "after", "end"), byId), target);
                }
                default -> throw new IllegalStateException();
            }
        }

        List<String> parts = new ArrayList<>();
        list.forEach(s -> parts.add(s.text));
        return String.join("\n\n", parts) + (text.endsWith("\n") ? "\n" : "");
    }

    private static int segmentInsertIndex(List<Segment> list, String after, Map<String, Segment> byId) {
        if ("start".equals(after)) return 0;
        if ("end".equals(after)) return list.size();
        int i = list.indexOf(byId.get(after));
        if (i < 0) throw new BadRequestException("块 " + after + " 已在本批操作中被删除，不能作为位置参照");
        return i + 1;
    }

    // ---------- 共用 ----------

    private static AgentNoteBlocks.Block requireBlock(Map<String, Object> op, String id, String hash,
                                                      Map<String, AgentNoteBlocks.Block> viewById, Set<String> referenced) {
        if (id == null) throw new BadRequestException(opName(op) + " 缺少 blockId");
        AgentNoteBlocks.Block b = viewById.get(id);
        if (b == null || !b.hash().equals(hash)) {
            throw new BadRequestException("文档在你读取后被修改过（块 " + id + "），请重新 read_note_blocks 后再改");
        }
        if (!referenced.add(id)) throw new BadRequestException("块 " + id + " 在同一批操作里被引用了不止一次");
        return b;
    }

    private static void requireAnchor(String after, Map<String, AgentNoteBlocks.Block> viewById) {
        if (!"start".equals(after) && !"end".equals(after) && !viewById.containsKey(after)) {
            throw new BadRequestException("找不到块 " + after + "，请重新 read_note_blocks");
        }
    }

    private static void requireMarkdown(Map<String, Object> op) {
        if (str(op, "markdown", "").isBlank()) throw new BadRequestException(opName(op) + " 缺少 markdown 内容");
    }

    private static String opName(Map<String, Object> op) {
        return str(op, "op", "");
    }

    private static String str(Map<String, Object> op, String key, String def) {
        Object v = op.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static List<String> strList(Map<String, Object> op, String key) {
        Object v = op.get(key);
        List<String> result = new ArrayList<>();
        if (v instanceof List<?> l) l.forEach(x -> result.add(String.valueOf(x)));
        return result;
    }

    private ObjectNode parseRoot(String content) {
        try {
            return (ObjectNode) om.readTree(content == null || content.isBlank() ? "{\"blocks\":[]}" : content);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("笔记内容不是合法的 EditorJS JSON");
        }
    }

    private static String editableContentType(Note note) {
        String ct = note.getContentType() == null ? "richtext" : note.getContentType();
        if (!"editorjs".equals(ct) && !"markdown".equals(ct)) {
            throw new BadRequestException("这篇是富文本笔记，暂不支持 AI 直接编辑");
        }
        return ct;
    }

    private Note loadOwnedNote(User user, Long noteId) {
        return noteRepository.findById(noteId)
                .filter(n -> n.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("笔记不存在"));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
