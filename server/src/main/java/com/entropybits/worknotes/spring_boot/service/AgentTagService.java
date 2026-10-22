/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.dto.ClipTagResponse;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.dto.TagRequest;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.entropybits.worknotes.spring_boot.exception.ResourceNotFoundException;
import com.entropybits.worknotes.spring_boot.repository.ClipTagLinkRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.TagRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 助手管理标签用（list_tags / create_tag / delete_tag / tag_note / tag_clip 工具）：agent 只知道
 * 标签名，这里按名字找标签、缺的自动建，再复用 TagService / SourceClipService 的既有规则
 * （归属校验、usedByNotes/usedByClips 标记、删标签先清关联）。
 */
@Service
@RequiredArgsConstructor
public class AgentTagService {

    private static final int TAG_NAME_MAX_CHARS = 50;

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final ClipTagLinkRepository clipTagLinkRepository;
    private final TagService tagService;
    private final SourceClipService sourceClipService;

    /** 全部标签及各自被多少篇笔记 / 多少条收藏使用，按名字排序 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTags(String username) {
        User user = getUser(username);
        Map<Long, Long> clipCounts = clipTagLinkRepository.countActiveLinksByOwnerGroupedByTag(user).stream()
                .collect(Collectors.toMap(ClipTagLinkRepository.TagClipCount::getTagId,
                        ClipTagLinkRepository.TagClipCount::getClipCount));
        return tagRepository.findByOwner(user).stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("noteCount", t.getNotes().size());
                    m.put("clipCount", clipCounts.getOrDefault(t.getId(), 0L));
                    return m;
                }).toList();
    }

    /** 新建标签；scope 为 "clip" 时出现在收藏的标签列表里，否则出现在笔记的标签列表里。已存在时不重复建 */
    @Transactional
    public Map<String, Object> createTag(String username, String name, String scope) {
        String n = normalizeName(name);
        User user = getUser(username);
        Tag existing = tagRepository.findByNameAndOwner(n, user).orElse(null);
        if (existing != null) {
            return Map.of("id", existing.getId(), "name", existing.getName(), "created", false);
        }
        TagRequest request = new TagRequest();
        request.setName(n);
        request.setScope("clip".equals(scope) ? "clip" : "note");
        Long id = tagService.createTag(request, username).getId();
        return Map.of("id", id, "name", n, "created", true);
    }

    /** 删除标签：笔记和收藏上的这个标签会一并去掉 */
    @Transactional
    public Map<String, Object> deleteTag(String username, String name) {
        Tag tag = findOwnedTag(getUser(username), normalizeName(name));
        int notes = tag.getNotes().size();
        tagService.deleteTag(tag.getId(), username);
        return Map.of("name", tag.getName(), "removedFromNotes", notes);
    }

    /** 给笔记加 / 去标签（按名字；加的标签不存在就新建），返回笔记现在的全部标签名 */
    @Transactional
    public Map<String, Object> updateNoteTags(String username, Long noteId, List<String> add, List<String> remove) {
        User user = getUser(username);
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("笔记", "id", noteId));
        if (!note.getOwner().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("笔记", "id", noteId);
        }
        for (String raw : safe(remove)) {
            String n = normalizeName(raw);
            note.getTags().removeIf(t -> t.getName().equals(n));
        }
        for (String raw : safe(add)) {
            Tag tag = findOrCreate(user, normalizeName(raw), "note");
            if (!Boolean.TRUE.equals(tag.getUsedByNotes())) {
                tag.setUsedByNotes(true);
                tagRepository.save(tag);
            }
            note.getTags().add(tag);
        }
        noteRepository.save(note);
        List<String> names = note.getTags().stream().map(Tag::getName).sorted().toList();
        return Map.of("noteId", noteId, "tags", names);
    }

    /** 给收藏加 / 去标签（按名字；加的标签不存在就新建），返回收藏现在的全部标签名 */
    @Transactional
    public Map<String, Object> updateClipTags(String username, Long clipId, List<String> add, List<String> remove) {
        User user = getUser(username);
        SourceClipResponse clip = null;
        for (String raw : safe(remove)) {
            Tag tag = tagRepository.findByNameAndOwner(normalizeName(raw), user).orElse(null);
            if (tag != null) clip = sourceClipService.removeTag(clipId, tag.getId(), username);
        }
        for (String raw : safe(add)) {
            Tag tag = findOrCreate(user, normalizeName(raw), "clip");
            clip = sourceClipService.addTag(clipId, tag.getId(), username);
        }
        if (clip == null) throw new BadRequestException("没有要添加或去掉的标签");
        List<String> names = clip.getTags() == null ? List.of()
                : clip.getTags().stream().map(ClipTagResponse::getName).sorted().toList();
        return Map.of("clipId", clipId, "tags", names);
    }

    private Tag findOrCreate(User user, String name, String scope) {
        return tagRepository.findByNameAndOwner(name, user).orElseGet(() -> {
            TagRequest request = new TagRequest();
            request.setName(name);
            request.setScope(scope);
            Long id = tagService.createTag(request, user.getUsername()).getId();
            return tagRepository.findById(id).orElseThrow();
        });
    }

    private Tag findOwnedTag(User user, String name) {
        return tagRepository.findByNameAndOwner(name, user)
                .orElseThrow(() -> new ResourceNotFoundException("没有名为「" + name + "」的标签"));
    }

    private static String normalizeName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new BadRequestException("标签名称不能为空");
        if (n.length() > TAG_NAME_MAX_CHARS) throw new BadRequestException("标签名称长度不能超过50个字符");
        return n;
    }

    private static List<String> safe(List<String> names) {
        return names == null ? List.of() : names;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }
}
