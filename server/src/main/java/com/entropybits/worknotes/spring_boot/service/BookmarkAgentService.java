/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.*;
import com.entropybits.worknotes.spring_boot.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookmarkAgentService {

    /** 进度阶段标识，原样透传给前端翻译成对应的阶段说明文案 */
    static final String PHASE_CLASSIFYING = "CLASSIFYING";
    static final String PHASE_SUMMARIZING = "SUMMARIZING";

    /** 语义聚类相似度阈值，跟 RetrievalService 检索用的阈值口径一致，需要时独立调 */
    private static final double CLUSTER_SIMILARITY_THRESHOLD = 0.7;

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
    private final ContentChunkRepository contentChunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

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
            NoteImageRefRepository noteImageRefRepository,
            ContentChunkRepository contentChunkRepository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper) {
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
        this.contentChunkRepository = contentChunkRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 把一条 clip 的多个分块向量平均成一个代表向量，反映全文的语义重心
     * （比只取第一块更能代表长文章，短文里"标题+开场白"占比过大的问题也被稀释掉）。
     */
    static float[] averageVectors(List<float[]> vectors) {
        int dim = vectors.get(0).length;
        float[] result = new float[dim];
        for (float[] v : vectors) {
            for (int i = 0; i < dim; i++) result[i] += v[i];
        }
        for (int i = 0; i < dim; i++) result[i] /= vectors.size();
        return result;
    }

    /** 簇质心的增量更新：加入一个新成员后的真实算术平均（existingCount 是加入前的成员数） */
    static float[] updateCentroid(float[] centroid, int existingCount, float[] newVector) {
        float[] result = new float[centroid.length];
        for (int i = 0; i < centroid.length; i++) {
            result[i] = (centroid[i] * existingCount + newVector[i]) / (existingCount + 1);
        }
        return result;
    }

    /**
     * 贪心阈值聚类：按输入顺序把每条 clip 归入跟它相似度最高、且达到阈值的已有簇（比较簇质心），
     * 否则新开一簇。没有向量的 clip（embedding 计算失败）单独成一簇，交给调用方按成员数兜底处理。
     * 不在这里做"成员数 < 2 并入其他"的折叠——折叠前还要给簇命名，命名可能让原本独立的两个簇同名
     * 合并到 >= 2 个成员，提前折叠会把这种后续可能达标的簇过早误判成孤例。
     */
    static List<List<SourceClip>> clusterBySimilarity(List<SourceClip> clips, Map<Long, float[]> vectorsByClipId,
                                                        double threshold) {
        List<List<SourceClip>> clusters = new ArrayList<>();
        List<float[]> centroids = new ArrayList<>();
        for (SourceClip clip : clips) {
            float[] vector = vectorsByClipId.get(clip.getId());
            if (vector == null) {
                clusters.add(new ArrayList<>(List.of(clip)));
                centroids.add(null);
                continue;
            }
            int bestIdx = -1;
            double bestScore = threshold;
            for (int i = 0; i < centroids.size(); i++) {
                float[] centroid = centroids.get(i);
                if (centroid == null) continue;
                double score = RetrievalService.cosineSimilarity(vector, centroid);
                if (score >= bestScore) {
                    bestScore = score;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                List<SourceClip> cluster = clusters.get(bestIdx);
                centroids.set(bestIdx, updateCentroid(centroids.get(bestIdx), cluster.size(), vector));
                cluster.add(clip);
            } else {
                clusters.add(new ArrayList<>(List.of(clip)));
                centroids.add(vector);
            }
        }
        return clusters;
    }

    /** clusterBySimilarity 的折叠结果：namable 是够格命名的簇（成员数 >= 2），other 是待并入"其他"的散件 */
    record FoldedClusters(List<List<SourceClip>> namable, List<SourceClip> other) {}

    /** 成员数 < 2 的簇（含没有向量、单独成簇的）一律并入统一的"其他"，规则跟旧版分组逻辑一致 */
    static FoldedClusters foldSingletonClusters(List<List<SourceClip>> clusters) {
        List<List<SourceClip>> namable = new ArrayList<>();
        List<SourceClip> other = new ArrayList<>();
        for (List<SourceClip> cluster : clusters) {
            if (cluster.size() < 2) other.addAll(cluster);
            else namable.add(cluster);
        }
        return new FoldedClusters(namable, other);
    }

    /** 一簇标题里出现次数最多的候选主题词当簇名；全空则退回"其他" */
    static String pickClusterLabel(List<List<String>> candidatesPerTitle) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> candidates : candidatesPerTitle) {
            if (candidates == null || candidates.isEmpty()) continue;
            String top = candidates.get(0).trim();
            if (!top.isEmpty()) counts.merge(top, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("其他");
    }

    /** 不管来源是 AI 语义簇还是人工标签，成员数 < 2 且不是"其他"本身的一律并入"其他"（最终安全网） */
    static LinkedHashMap<String, List<SourceClip>> foldSmallNamedGroups(LinkedHashMap<String, List<SourceClip>> groups) {
        LinkedHashMap<String, List<SourceClip>> result = new LinkedHashMap<>();
        List<SourceClip> misc = new ArrayList<>(groups.getOrDefault("其他", List.of()));
        for (Map.Entry<String, List<SourceClip>> entry : groups.entrySet()) {
            if ("其他".equals(entry.getKey())) continue;
            if (entry.getValue().size() < 2) misc.addAll(entry.getValue());
            else result.put(entry.getKey(), entry.getValue());
        }
        if (!misc.isEmpty()) result.put("其他", misc);
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
     * 用新生成的内容覆盖用户当前该类型的有效结果：先删除所有旧 Note 并 flush，再插入新的。
     * 按 findAll 而非假设唯一：数据库并没有 (owner_id, generated_type) 唯一索引真正兜底这个不变式，
     * 一旦历史数据修复/迁移残留了多条（曾实际发生过），这里必须能兜底全部清掉而不是让
     * IncorrectResultSizeDataAccessException 直接炸掉整个生成任务。
     * 必须先 flush 删除——Hibernate 默认 flush 顺序是先 INSERT 后 DELETE，如果不先 flush，
     * 新 Note 的 INSERT 会在旧 Note 的 DELETE 之前执行。
     * 必须通过 self 代理调用（而不是 this. 直接调用）——@Transactional 依赖 Spring 的代理式 AOP，
     * 只拦截"经过代理"的外部调用，同一个 bean 内部的 this. 自调用会绕过代理，导致注解静默失效。
     */
    @Transactional
    public Note replaceGeneratedNote(User owner, Note.GeneratedType type, String title, String markdownContent,
                                      List<SourceClip> refClips) {
        List<Note> olds = noteRepository.findAllByOwnerAndGeneratedType(owner, type);
        for (Note old : olds) {
            // 必须先清掉旧笔记的语义索引分块再删笔记，否则 content_chunks 里会留下孤儿行，
            // 而 RetrievalService 不校验来源笔记是否还存在，已删除内容会被无限期当作 RAG 上下文召回。
            // note_image_refs、note_clip_refs 同理必须先清：两边外键都没有 ON DELETE CASCADE，
            // 不清就会在下面 delete 时直接撞外键约束——note_clip_refs 这条是这个方法自己在下面
            // insert 新笔记时写入的，第一次生成时还没有旧记录不会触发，第二次往后重新生成必炸。
            contentIndexingService.deleteChunksFor(ContentChunk.SourceType.NOTE, old.getId());
            noteImageRefRepository.deleteByNote(old);
            noteClipRefRepository.deleteByNote(old);
            noteRepository.delete(old);
        }
        if (!olds.isEmpty()) noteRepository.flush();

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

        jobRepository.startPhase(jobId, 1, PHASE_CLASSIFYING);
        Map<Long, float[]> vectors = computeClipVectors(toClassify);
        List<List<SourceClip>> rawClusters = clusterBySimilarity(toClassify, vectors, CLUSTER_SIMILARITY_THRESHOLD);
        jobRepository.incrementCompletedSteps(jobId);

        List<String> existingTopics = tagRepository.findByOwnerAndUsedByClipsTrue(owner).stream()
                .map(Tag::getName)
                .distinct()
                .sorted()
                .limit(200)
                .toList();

        FoldedClusters folded = foldSingletonClusters(rawClusters);
        LinkedHashMap<String, List<SourceClip>> groups = nameClusters(folded.namable(), existingTopics, ai);
        if (!folded.other().isEmpty()) {
            groups.merge("其他", folded.other(), (existing, toAdd) -> { existing.addAll(toAdd); return existing; });
        }

        // 手动调整过标签的 clip 不参与语义聚类，按它自己当前的人工标签归组，跟 AI 语义簇一起走最终折叠
        for (SourceClip clip : clips) {
            if (!Boolean.TRUE.equals(clip.getTagsManuallyAdjusted())) continue;
            String manual = manualTopicFor(clip).stream().findFirst().orElse("其他");
            groups.merge(manual, new ArrayList<>(List.of(clip)), (existing, toAdd) -> { existing.addAll(toAdd); return existing; });
        }
        groups = foldSmallNamedGroups(groups);

        jobRepository.startPhase(jobId, groups.size(), PHASE_SUMMARIZING);

        // 标签落库必须用最终折叠合并后的分组名（成员数 < 2 的已并入"其他"），
        // 而不是语义簇命名前的候选词，否则左侧标签列表会比知识地图标题多出大量只关联 1 条收藏的零散标签
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

    /**
     * 给每条待分类的收藏算一个代表向量：有正文分块（reindexClip 已生成的 ContentChunk）就取全部
     * 分块向量的平均；没有正文（仅链接/抓取失败）或分块向量解析失败，现算一次标题 embedding 兜底。
     * 标题 embedding 也失败的极端情况：不放进结果 map，clusterBySimilarity 会把它当缺失向量单独
     * 成簇，最终被并入"其他"，不会让整个生成任务失败。
     */
    private Map<Long, float[]> computeClipVectors(List<SourceClip> clips) {
        List<Long> clipIds = clips.stream().map(SourceClip::getId).toList();
        Map<Long, List<ContentChunk>> chunksByClipId = contentChunkRepository
                .findBySourceTypeAndSourceIdIn(ContentChunk.SourceType.CLIP, clipIds).stream()
                .collect(Collectors.groupingBy(ContentChunk::getSourceId));

        Map<Long, float[]> vectors = new HashMap<>();
        for (SourceClip clip : clips) {
            List<float[]> chunkVectors = chunksByClipId.getOrDefault(clip.getId(), List.of()).stream()
                    .map(this::parseEmbedding)
                    .filter(v -> v.length > 0)
                    .toList();
            if (!chunkVectors.isEmpty()) {
                vectors.put(clip.getId(), averageVectors(chunkVectors));
                continue;
            }
            try {
                vectors.put(clip.getId(), embeddingService.embed(clip.getTitle()));
            } catch (Exception e) {
                log.warn("为收藏 {} 计算标题 embedding 失败，聚类时归入待并入其他的组: {}", clip.getId(), e.getMessage());
            }
        }
        return vectors;
    }

    private float[] parseEmbedding(ContentChunk chunk) {
        try {
            return objectMapper.readValue(chunk.getEmbeddingJson(), float[].class);
        } catch (Exception e) {
            return new float[0]; // 脏数据：这一块不参与平均，不影响整体向量计算
        }
    }

    /**
     * 给每个语义簇起名字：复用 classifyTopics（同一批标题、同一份已有标签词表），取簇内出现次数
     * 最多的候选词当簇名。两个不同的语义簇如果被独立起了同一个名字，直接合并——这是符合直觉的
     * 结果（说明它们在标签词表的粒度上其实是一类），不算异常。
     */
    private LinkedHashMap<String, List<SourceClip>> nameClusters(List<List<SourceClip>> clusters,
                                                                   List<String> existingTopics,
                                                                   AiTranslationService ai) throws Exception {
        LinkedHashMap<String, List<SourceClip>> named = new LinkedHashMap<>();
        for (List<SourceClip> cluster : clusters) {
            List<String> titles = cluster.stream().map(SourceClip::getTitle).toList();
            String label = pickClusterLabel(ai.classifyTopics(titles, existingTopics));
            named.merge(label, new ArrayList<>(cluster), (existing, toAdd) -> { existing.addAll(toAdd); return existing; });
        }
        return named;
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
