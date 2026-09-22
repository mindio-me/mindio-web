/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.entity.*;
import com.entropybits.worknotes.spring_boot.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class BookmarkAgentService {

    /** 进度阶段标识，原样透传给前端翻译成对应的阶段说明文案 */
    static final String PHASE_CLASSIFYING = "CLASSIFYING";
    static final String PHASE_SUMMARIZING = "SUMMARIZING";

    private final BookmarkAgentJobRepository jobRepository;
    private final SourceClipRepository clipRepository;
    private final NoteRepository noteRepository;
    private final NoteClipRefRepository noteClipRefRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ClipTagLinkRepository clipTagLinkRepository;
    private final AiProperties aiProperties;
    private final AiTranslationService anthropicService;
    private final AiTranslationService openAiService;
    private final AiTranslationService deepseekService;
    private final AiTranslationService doubaoService;
    private final ContentIndexingService contentIndexingService;
    private final NoteImageRefRepository noteImageRefRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Autowired
    @Lazy
    private BookmarkAgentService self;

    public BookmarkAgentService(
            BookmarkAgentJobRepository jobRepository,
            SourceClipRepository clipRepository,
            NoteRepository noteRepository,
            NoteClipRefRepository noteClipRefRepository,
            UserRepository userRepository,
            TagRepository tagRepository,
            ClipTagLinkRepository clipTagLinkRepository,
            AiProperties aiProperties,
            @Qualifier("anthropicTranslationService") AiTranslationService anthropicService,
            @Qualifier("openAiTranslationService") AiTranslationService openAiService,
            @Qualifier("deepseekTranslationService") AiTranslationService deepseekService,
            @Qualifier("doubaoTranslationService") AiTranslationService doubaoService,
            ContentIndexingService contentIndexingService,
            NoteImageRefRepository noteImageRefRepository) {
        this.jobRepository = jobRepository;
        this.clipRepository = clipRepository;
        this.noteRepository = noteRepository;
        this.noteClipRefRepository = noteClipRefRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.clipTagLinkRepository = clipTagLinkRepository;
        this.aiProperties = aiProperties;
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.deepseekService = deepseekService;
        this.doubaoService = doubaoService;
        this.contentIndexingService = contentIndexingService;
        this.noteImageRefRepository = noteImageRefRepository;
    }

    /** 把 clips 按每批 size 条切分，用于分批调用 classifyTopics */
    static List<List<SourceClip>> partition(List<SourceClip> list, int size) {
        List<List<SourceClip>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    /**
     * 每个 clip 取候选主题词列表的第一个作为主题词，按主题词精确字符串匹配分组；
     * 成员数 < 2 的分组（含没有候选主题词的 clip）一律并入统一的"其他"分组。
     */
    static LinkedHashMap<String, List<SourceClip>> groupByTopic(List<SourceClip> clips, List<List<String>> topicsPerClip) {
        LinkedHashMap<String, List<SourceClip>> byTopic = new LinkedHashMap<>();
        for (int i = 0; i < clips.size(); i++) {
            List<String> topics = topicsPerClip.get(i);
            String topic = (topics != null && !topics.isEmpty()) ? topics.get(0).trim() : "";
            if (topic.isEmpty()) topic = "其他";
            byTopic.computeIfAbsent(topic, k -> new ArrayList<>()).add(clips.get(i));
        }

        LinkedHashMap<String, List<SourceClip>> result = new LinkedHashMap<>();
        List<SourceClip> misc = new ArrayList<>();
        for (Map.Entry<String, List<SourceClip>> entry : byTopic.entrySet()) {
            if ("其他".equals(entry.getKey()) || entry.getValue().size() < 2) {
                misc.addAll(entry.getValue());
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        if (!misc.isEmpty()) {
            result.put("其他", misc);
        }
        return result;
    }

    /** 按 originalBookmarkedAt（为空则退回 createdAt）的年份分桶，TreeMap 天然按年份升序 */
    static Map<Integer, List<SourceClip>> groupByYear(List<SourceClip> clips) {
        Map<Integer, List<SourceClip>> byYear = new TreeMap<>();
        for (SourceClip clip : clips) {
            LocalDateTime dt = clip.getOriginalBookmarkedAt() != null ? clip.getOriginalBookmarkedAt() : clip.getCreatedAt();
            int year = dt.getYear();
            byYear.computeIfAbsent(year, k -> new ArrayList<>()).add(clip);
        }
        return byYear;
    }

    /**
     * 用新生成的内容覆盖用户当前该类型的有效结果：先删除旧 Note 并 flush，再插入新的。
     * 必须先 flush 删除——Hibernate 默认 flush 顺序是先 INSERT 后 DELETE，如果不先 flush，
     * 新 Note 的 INSERT 会在旧 Note 的 DELETE 之前执行，触发 (owner_id, generated_type) 唯一索引冲突。
     * 必须通过 self 代理调用（而不是 this. 直接调用）——@Transactional 依赖 Spring 的代理式 AOP，
     * 只拦截"经过代理"的外部调用，同一个 bean 内部的 this. 自调用会绕过代理，导致注解静默失效。
     */
    @Transactional
    public Note replaceGeneratedNote(User owner, Note.GeneratedType type, String title, String markdownContent,
                                      List<SourceClip> refClips) {
        noteRepository.findByOwnerAndGeneratedType(owner, type).ifPresent(old -> {
            // 必须先清掉旧笔记的语义索引分块再删笔记，否则 content_chunks 里会留下孤儿行，
            // 而 RetrievalService 不校验来源笔记是否还存在，已删除内容会被无限期当作 RAG 上下文召回。
            // note_image_refs 同理必须先清：外键没有 ON DELETE CASCADE，用户如果手动编辑过
            // 这篇生成笔记并贴了图，这里不清就会在下面 delete 时直接撞外键约束。
            contentIndexingService.deleteChunksFor(ContentChunk.SourceType.NOTE, old.getId());
            noteImageRefRepository.deleteByNote(old);
            noteRepository.delete(old);
            noteRepository.flush();
        });

        Note note = noteRepository.save(Note.builder()
                .title(title)
                .content(markdownContent)
                .contentType("markdown")
                .owner(owner)
                .generatedType(type)
                .build());
        reindexNoteAfterCommit(note.getId());

        int order = 0;
        for (SourceClip clip : refClips) {
            noteClipRefRepository.save(NoteClipRef.builder()
                    .note(note)
                    .clip(clip)
                    .sortOrder(order++)
                    .build());
        }
        return note;
    }

    /**
     * 把异步重索引推迟到当前事务真正提交之后再触发。
     * reindexNote 是 @Async + @Transactional：它跑在另一个线程、另一个连接、另一个事务里，
     * 看不到 replaceGeneratedNote 还没提交的新笔记行，直接内联调用会静默不索引。
     * 没有事务上下文时（如单元测试直接 new 出服务）回退到立即调用。
     */
    private void reindexNoteAfterCommit(Long noteId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    contentIndexingService.reindexNote(noteId);
                }
            });
        } else {
            contentIndexingService.reindexNote(noteId);
        }
    }

    /** 把收藏列表拼成 markdown 链接列表，每条一行，供 runCluster/runTimeline 拼进生成结果 */
    static String buildLinkListMarkdown(List<SourceClip> clips) {
        StringBuilder sb = new StringBuilder();
        for (SourceClip clip : clips) {
            sb.append("- [").append(sanitizeLinkText(clip.getTitle()))
                    .append("](").append(clip.getSourceUrl()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * 去掉标题里会破坏 [标题](链接) 语法的字符——渲染端 renderMarkdown 是简单正则实现
     * （\[([^\]]+)\]\(([^)]+)\)），标题原样带 ] 会把链接边界截断。
     */
    private static String sanitizeLinkText(String text) {
        if (text == null) return "";
        return text.replace("[", "(").replace("]", ")").replace("\n", " ").replace("\r", " ").trim();
    }

    Note runCluster(Long jobId, User owner, List<SourceClip> clips) throws Exception {
        AiTranslationService ai = resolveService();

        List<SourceClip> toClassify = clips.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getTagsManuallyAdjusted()))
                .toList();
        List<List<SourceClip>> batches = partition(toClassify, 50);
        jobRepository.startPhase(jobId, batches.size(), PHASE_CLASSIFYING);

        List<String> existingTopics = tagRepository.findByOwnerAndUsedByClipsTrue(owner).stream()
                .map(Tag::getName)
                .distinct()
                .sorted()
                .limit(200)
                .toList();

        Map<Long, List<String>> topicsByClipId = new HashMap<>();
        for (List<SourceClip> batch : batches) {
            List<String> titles = batch.stream().map(SourceClip::getTitle).toList();
            List<List<String>> batchTopics = ai.classifyTopics(titles, existingTopics);
            for (int i = 0; i < batch.size(); i++) {
                topicsByClipId.put(batch.get(i).getId(), batchTopics.get(i));
            }
            jobRepository.incrementCompletedSteps(jobId);
        }

        List<List<String>> topicsPerClip = new ArrayList<>();
        for (SourceClip clip : clips) {
            topicsPerClip.add(topicsByClipId.containsKey(clip.getId())
                    ? topicsByClipId.get(clip.getId())
                    : manualTopicFor(clip));
        }

        LinkedHashMap<String, List<SourceClip>> groups = groupByTopic(clips, topicsPerClip);
        jobRepository.startPhase(jobId, groups.size(), PHASE_SUMMARIZING);

        // 标签落库必须用 groupByTopic 合并后的最终分组名（成员数 < 2 的已并入"其他"），
        // 而不是 AI 原始候选词，否则左侧标签列表会比知识地图标题多出大量只关联 1 条收藏的零散标签
        Map<Long, String> finalTopicByClipId = new HashMap<>();
        for (Map.Entry<String, List<SourceClip>> entry : groups.entrySet()) {
            for (SourceClip clip : entry.getValue()) {
                finalTopicByClipId.put(clip.getId(), entry.getKey());
            }
        }
        upsertClipTags(toClassify, finalTopicByClipId);

        StringBuilder markdown = new StringBuilder();
        List<SourceClip> orderedRefs = new ArrayList<>();
        for (Map.Entry<String, List<SourceClip>> entry : groups.entrySet()) {
            List<String> titles = entry.getValue().stream().map(SourceClip::getTitle).toList();
            String summary = ai.summarizeCluster(entry.getKey(), titles);
            markdown.append("## ").append(entry.getKey()).append("\n\n")
                    .append(summary).append("\n\n")
                    .append(buildLinkListMarkdown(entry.getValue())).append("\n");
            orderedRefs.addAll(entry.getValue());
            jobRepository.incrementCompletedSteps(jobId);
        }

        return self.replaceGeneratedNote(owner, Note.GeneratedType.CLUSTER, "知识地图", markdown.toString(), orderedRefs);
    }

    /** 手动调整过标签的 clip 不参与 AI 分类，文档分组时退回它自己当前的人工标签（没有则归入"其他"） */
    private List<String> manualTopicFor(SourceClip clip) {
        return clipTagLinkRepository.findByClip(clip).stream()
                .filter(l -> Boolean.TRUE.equals(l.getManuallyAdded()))
                .map(l -> l.getTag().getName())
                .findFirst()
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * 把该 clip 在 groupByTopic 合并后最终所在的分组名写成 aiSuggested=true 的 ClipTagLink
     * （而不是 AI 原始候选词），确保标签列表与知识地图标题严格一致。
     * 上一轮生成过、这一轮没再命中同一个词的旧 aiSuggested 关联会被回收：manuallyAdded 仍为 true 的只清掉
     * aiSuggested 标记，否则整行删除。
     */
    private void upsertClipTags(List<SourceClip> clips, Map<Long, String> finalTopicByClipId) {
        for (SourceClip clip : clips) {
            String topicWord = finalTopicByClipId.get(clip.getId());

            List<ClipTagLink> existingLinks = clipTagLinkRepository.findByClip(clip);
            for (ClipTagLink link : existingLinks) {
                boolean isStaleAiLink = Boolean.TRUE.equals(link.getAiSuggested())
                        && (topicWord == null || !link.getTag().getName().equals(topicWord));
                if (!isStaleAiLink) continue;
                if (Boolean.TRUE.equals(link.getManuallyAdded())) {
                    link.setAiSuggested(false);
                    clipTagLinkRepository.save(link);
                } else {
                    clipTagLinkRepository.delete(link);
                    reconcileUsedByClips(link.getTag());
                }
            }

            if (topicWord == null || topicWord.isEmpty()) continue;

            Tag tag = tagRepository.findByNameAndOwner(topicWord, clip.getOwner()).orElse(null);
            if (tag == null) {
                tag = tagRepository.save(Tag.builder()
                        .name(topicWord).owner(clip.getOwner()).usedByClips(true).build());
            } else if (!Boolean.TRUE.equals(tag.getUsedByClips())) {
                tag.setUsedByClips(true);
                tagRepository.save(tag);
            }

            ClipTagLink link = clipTagLinkRepository.findByClipAndTag(clip, tag).orElse(null);
            if (link == null) {
                link = ClipTagLink.builder().clip(clip).tag(tag).aiSuggested(true).build();
            } else {
                link.setAiSuggested(true);
            }
            clipTagLinkRepository.save(link);
        }
    }

    /** 标签最后一条生效的 ClipTagLink 被删除后，把 usedByClips 重置为 false，避免僵尸标签常驻标签列表 */
    private void reconcileUsedByClips(Tag tag) {
        if (Boolean.TRUE.equals(tag.getUsedByClips()) && !clipTagLinkRepository.existsByTag(tag)) {
            tag.setUsedByClips(false);
            tagRepository.save(tag);
        }
    }

    Note runTimeline(Long jobId, User owner, List<SourceClip> clips) throws Exception {
        AiTranslationService ai = resolveService();
        Map<Integer, List<SourceClip>> byYear = groupByYear(clips);
        jobRepository.startPhase(jobId, byYear.size(), PHASE_SUMMARIZING);

        StringBuilder markdown = new StringBuilder();
        List<SourceClip> orderedRefs = new ArrayList<>();
        for (Map.Entry<Integer, List<SourceClip>> entry : byYear.entrySet()) {
            List<String> titles = entry.getValue().stream().map(SourceClip::getTitle).toList();
            String bucketLabel = entry.getKey() + " 年";
            String narrative = ai.summarizeTimelineBucket(bucketLabel, titles);
            markdown.append("## ").append(bucketLabel).append("\n\n")
                    .append(narrative).append("\n\n")
                    .append(buildLinkListMarkdown(entry.getValue())).append("\n");
            orderedRefs.addAll(entry.getValue());
            jobRepository.incrementCompletedSteps(jobId);
        }

        return self.replaceGeneratedNote(owner, Note.GeneratedType.TIMELINE, "时间线回顾", markdown.toString(), orderedRefs);
    }

    /** 若该用户该类型已有 RUNNING 的任务直接复用，否则建新任务并提交异步生成 */
    public BookmarkAgentJob generate(BookmarkAgentJob.Type type, User user) {
        Optional<BookmarkAgentJob> latest = jobRepository.findTopByOwnerAndTypeOrderByCreatedAtDesc(user, type);
        if (latest.isPresent() && latest.get().getStatus() == BookmarkAgentJob.Status.RUNNING) {
            return latest.get();
        }

        BookmarkAgentJob job = jobRepository.save(BookmarkAgentJob.builder()
                .owner(user)
                .type(type)
                .status(BookmarkAgentJob.Status.RUNNING)
                .build());

        Long jobId = job.getId();
        Long ownerId = user.getId();
        executor.submit(() -> runGenerate(jobId, type, ownerId));
        return job;
    }

    void runGenerate(Long jobId, BookmarkAgentJob.Type type, Long ownerId) {
        try {
            User owner = userRepository.findById(ownerId).orElseThrow();
            List<SourceClip> clips = clipRepository
                    .findByOwner(owner, Pageable.unpaged())
                    .getContent();
            Note note = type == BookmarkAgentJob.Type.CLUSTER
                    ? runCluster(jobId, owner, clips)
                    : runTimeline(jobId, owner, clips);
            self.completeJob(jobId, note.getId());
        } catch (Exception e) {
            log.error("BookmarkAgentJob {} failed", jobId, e);
            self.failJob(jobId, e.getMessage());
        }
    }

    // NOTE: 这两个方法必须是 public（Spring 代理式 AOP 只拦截 public 方法），
    // 且调用方必须通过 self 代理调用而不是 this. 直接调用（同一 bean 内部自调用会绕过代理），
    // 两个条件同时满足 @Transactional 才会真正生效——参见 replaceGeneratedNote 上的注释。
    @Transactional
    public void failJob(Long jobId, String message) {
        BookmarkAgentJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(BookmarkAgentJob.Status.FAILED);
        job.setErrorMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    @Transactional
    public void completeJob(Long jobId, Long noteId) {
        BookmarkAgentJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(BookmarkAgentJob.Status.DONE);
        job.setResultNoteId(noteId);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private AiTranslationService resolveService() {
        return switch (aiProperties.getProvider().toLowerCase()) {
            case "openai" -> openAiService;
            case "deepseek" -> deepseekService;
            case "doubao" -> doubaoService;
            default -> anthropicService;
        };
    }
}
