/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.entity.*;
import com.entropybits.worknotes.spring_boot.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookmarkAgentServiceTest {

    @Mock BookmarkAgentJobRepository jobRepository;
    @Mock SourceClipRepository clipRepository;
    @Mock NoteRepository noteRepository;
    @Mock NoteClipRefRepository noteClipRefRepository;
    @Mock UserRepository userRepository;
    @Mock TagRepository tagRepository;
    @Mock ClipTagLinkRepository clipTagLinkRepository;
    @Mock AiProperties aiProperties;
    @Mock AiTranslationService anthropicService;
    @Mock AiTranslationService openAiService;
    @Mock AiTranslationService deepseekService;
    @Mock AiTranslationService doubaoService;
    @Mock ContentIndexingService contentIndexingService;
    @Mock NoteImageRefRepository noteImageRefRepository;

    private BookmarkAgentService service;

    private void setUp() {
        service = new BookmarkAgentService(jobRepository, clipRepository, noteRepository, noteClipRefRepository,
                userRepository, tagRepository, clipTagLinkRepository, aiProperties,
                anthropicService, openAiService, deepseekService, doubaoService, contentIndexingService,
                noteImageRefRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    }

    private SourceClip clip(long id, String title) {
        return SourceClip.builder().id(id).title(title).sourceType(SourceClip.SourceType.WEBPAGE).build();
    }

    @Test
    void partition_splitsListIntoChunksOfGivenSize() {
        setUp();
        List<SourceClip> clips = List.of(clip(1, "a"), clip(2, "b"), clip(3, "c"));

        List<List<SourceClip>> result = BookmarkAgentService.partition(clips, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).extracting(SourceClip::getId).containsExactly(1L, 2L);
        assertThat(result.get(1)).extracting(SourceClip::getId).containsExactly(3L);
    }

    @Test
    void groupByTopic_groupsClipsByFirstCandidateTopic() {
        setUp();
        SourceClip c1 = clip(1, "标题一");
        SourceClip c2 = clip(2, "标题二");
        SourceClip c3 = clip(3, "标题三");
        List<SourceClip> clips = List.of(c1, c2, c3);
        List<List<String>> topics = List.of(List.of("AI", "编程"), List.of("AI"), List.of("旅行"));

        LinkedHashMap<String, List<SourceClip>> groups = BookmarkAgentService.groupByTopic(clips, topics);

        assertThat(groups.get("AI")).containsExactly(c1, c2);
    }

    @Test
    void groupByTopic_mergesGroupsWithFewerThanTwoMembersIntoOther() {
        setUp();
        SourceClip c1 = clip(1, "标题一");
        SourceClip c2 = clip(2, "标题二");
        SourceClip c3 = clip(3, "标题三");
        SourceClip c4 = clip(4, "标题四");
        // "AI" 有 2 条；"旅行"、"美食" 各只有 1 条 —— 两个 singleton 分组都应该并入同一个"其他"
        List<SourceClip> clips = List.of(c1, c2, c3, c4);
        List<List<String>> topics = List.of(List.of("AI"), List.of("AI"), List.of("旅行"), List.of("美食"));

        LinkedHashMap<String, List<SourceClip>> groups = BookmarkAgentService.groupByTopic(clips, topics);

        assertThat(groups).containsKey("AI");
        assertThat(groups).doesNotContainKey("旅行");
        assertThat(groups).doesNotContainKey("美食");
        assertThat(groups.get("其他")).containsExactlyInAnyOrder(c3, c4);
    }

    @Test
    void groupByTopic_treatsClipWithNoTopicsAsOther() {
        setUp();
        SourceClip c1 = clip(1, "标题一");
        List<SourceClip> clips = List.of(c1);
        List<List<String>> topics = List.of(List.of());

        LinkedHashMap<String, List<SourceClip>> groups = BookmarkAgentService.groupByTopic(clips, topics);

        assertThat(groups.get("其他")).containsExactly(c1);
    }

    @Test
    void groupByYear_bucketsClipsByOriginalBookmarkedAtYearAscending() {
        setUp();
        SourceClip c2019 = clip(1, "2019年的收藏");
        c2019.setOriginalBookmarkedAt(LocalDateTime.of(2019, 5, 1, 0, 0));
        SourceClip c2023 = clip(2, "2023年的收藏");
        c2023.setOriginalBookmarkedAt(LocalDateTime.of(2023, 1, 1, 0, 0));
        List<SourceClip> clips = List.of(c2023, c2019); // 故意乱序输入

        Map<Integer, List<SourceClip>> byYear = BookmarkAgentService.groupByYear(clips);

        assertThat(byYear.keySet()).containsExactly(2019, 2023); // TreeMap 保证升序
        assertThat(byYear.get(2019)).containsExactly(c2019);
    }

    @Test
    void groupByYear_fallsBackToCreatedAtWhenOriginalBookmarkedAtIsNull() {
        setUp();
        SourceClip clip = clip(1, "旧数据");
        clip.setOriginalBookmarkedAt(null);
        clip.setCreatedAt(LocalDateTime.of(2021, 6, 1, 0, 0));

        Map<Integer, List<SourceClip>> byYear = BookmarkAgentService.groupByYear(List.of(clip));

        assertThat(byYear).containsOnlyKeys(2021);
    }

    @Test
    void runCluster_classifiesGroupsSummarizesAndReplacesNote() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        List<SourceClip> clips = List.of(c1, c2);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(anthropicService.summarizeCluster(eq("AI"), any()))
                .thenReturn("这是一组 AI 相关收藏。");
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });

        Note result = service.runCluster(1L, owner, clips);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getContentType()).isEqualTo("markdown");
        assertThat(result.getContent())
                .contains("## AI")
                .contains("这是一组 AI 相关收藏。")
                .contains("- [标题一](https://example.com/a)")
                .contains("- [标题二](https://example.com/b)");
        verify(jobRepository).startPhase(1L, 1, "CLASSIFYING"); // 1 个批次
        verify(jobRepository).startPhase(1L, 1, "SUMMARIZING"); // 1 个分组，各阶段单独计数
        verify(jobRepository, times(2)).incrementCompletedSteps(1L); // 1 个批次 + 1 个分组
        verify(noteClipRefRepository, times(2)).save(any());
    }

    @Test
    void runCluster_skipsClassificationForClipsWithManuallyAdjustedTags() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        // groupByTopic 会把成员数 < 2 的分组并入"其他"，所以人工组和 AI 组都各造 2 条才能各自成组，
        // 从而验证"人工分类"这个分组名确实来自 manualTopicFor 而不是被合并掉
        SourceClip manual1 = clip(1, "标题一");
        manual1.setSourceUrl("https://example.com/a1");
        manual1.setTagsManuallyAdjusted(true);
        SourceClip manual2 = clip(2, "标题二");
        manual2.setSourceUrl("https://example.com/a2");
        manual2.setTagsManuallyAdjusted(true);
        Tag manualTag = Tag.builder().id(9L).name("人工分类").owner(owner).build();
        ClipTagLink manualLink1 = ClipTagLink.builder().clip(manual1).tag(manualTag).manuallyAdded(true).build();
        ClipTagLink manualLink2 = ClipTagLink.builder().clip(manual2).tag(manualTag).manuallyAdded(true).build();
        SourceClip auto1 = clip(3, "标题三");
        auto1.setSourceUrl("https://example.com/b1");
        SourceClip auto2 = clip(4, "标题四");
        auto2.setSourceUrl("https://example.com/b2");
        List<SourceClip> clips = List.of(manual1, manual2, auto1, auto2);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(clipTagLinkRepository.findByClip(manual1)).thenReturn(List.of(manualLink1));
        when(clipTagLinkRepository.findByClip(manual2)).thenReturn(List.of(manualLink2));
        when(anthropicService.classifyTopics(List.of("标题三", "标题四"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("人工分类"), any())).thenReturn("人工分类摘要。");
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("AI 摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(101L);
            return n;
        });

        Note result = service.runCluster(1L, owner, clips);

        verify(anthropicService, org.mockito.Mockito.never())
                .classifyTopics(argThat(l -> l.contains("标题一") || l.contains("标题二")), any());
        assertThat(result.getContent()).contains("## 人工分类").contains("## AI");
    }

    @Test
    void runCluster_upsertsAiSuggestedTagFromFirstCandidateTopic() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        // groupByTopic 会把成员数 < 2 的分组并入"其他"，两条都归到 AI 才能让 AI 分组本身不被合并掉
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag aiTag = Tag.builder().id(7L).name("AI").owner(owner).usedByClips(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("摘要。");
        when(tagRepository.findByNameAndOwner("AI", owner)).thenReturn(java.util.Optional.of(aiTag));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(102L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(clipTagLinkRepository).save(argThat(l ->
                l.getClip() == c1 && l.getTag() == aiTag && Boolean.TRUE.equals(l.getAiSuggested())));
        verify(clipTagLinkRepository).save(argThat(l ->
                l.getClip() == c2 && l.getTag() == aiTag && Boolean.TRUE.equals(l.getAiSuggested())));
        verify(tagRepository).save(argThat(t -> t == aiTag && Boolean.TRUE.equals(t.getUsedByClips())));
    }

    @Test
    void runCluster_foldsSingletonTopicIntoOtherTagToMatchKnowledgeMapDoc() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        List<SourceClip> clips = List.of(c1);
        // 单独一条 clip 命中的主题词在 groupByTopic 里成员数 < 2，会被并入"其他"；
        // 标签落库必须用这个合并后的最终分组名，而不是 AI 原始候选词，否则左侧标签列表会和知识地图标题不一致
        Tag otherTag = Tag.builder().id(11L).name("其他").owner(owner).usedByClips(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.classifyTopics(List.of("标题一"), List.of()))
                .thenReturn(List.of(List.of("小众话题")));
        when(anthropicService.summarizeCluster(eq("其他"), any())).thenReturn("摘要。");
        when(tagRepository.findByNameAndOwner("其他", owner)).thenReturn(java.util.Optional.of(otherTag));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(105L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(clipTagLinkRepository).save(argThat(l ->
                l.getClip() == c1 && l.getTag() == otherTag && Boolean.TRUE.equals(l.getAiSuggested())));
        verify(tagRepository, org.mockito.Mockito.never()).findByNameAndOwner(eq("小众话题"), any());
    }

    @Test
    void runCluster_demotesStaleAiSuggestedLinkWhenManuallyAddedTooInsteadOfDeleting() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        // groupByTopic 会把成员数 < 2 的分组并入"其他"，加一条同样落在"新标签"的 clip 让该分组不被合并掉
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag oldTag = Tag.builder().id(8L).name("旧标签").owner(owner).usedByClips(true).build();
        // 上一轮生成的 aiSuggested 关联，同时被用户手动确认过（manuallyAdded=true），
        // 这一轮 classifyTopics 命中了不同的词，旧关联应当被"降级"而不是删除
        ClipTagLink staleLink = ClipTagLink.builder().clip(c1).tag(oldTag).aiSuggested(true).manuallyAdded(true).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(clipTagLinkRepository.findByClip(c1)).thenReturn(List.of(staleLink));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("新标签"), List.of("新标签")));
        when(anthropicService.summarizeCluster(any(), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(103L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(clipTagLinkRepository).save(argThat(l ->
                l == staleLink && Boolean.FALSE.equals(l.getAiSuggested()) && Boolean.TRUE.equals(l.getManuallyAdded())));
        verify(clipTagLinkRepository, org.mockito.Mockito.never()).delete(staleLink);
    }

    @Test
    void runCluster_deletesStaleAiSuggestedLinkWhenNotManuallyAdded() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        // groupByTopic 会把成员数 < 2 的分组并入"其他"，加一条同样落在"新标签"的 clip 让该分组不被合并掉
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag oldTag = Tag.builder().id(8L).name("旧标签").owner(owner).usedByClips(true).build();
        // 上一轮纯 AI 建议、用户从未手动确认过（manuallyAdded=false），这一轮没再命中同一个词，
        // 旧关联应当被直接删除而不是保留降级
        ClipTagLink staleLink = ClipTagLink.builder().clip(c1).tag(oldTag).aiSuggested(true).manuallyAdded(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(clipTagLinkRepository.findByClip(c1)).thenReturn(List.of(staleLink));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("新标签"), List.of("新标签")));
        when(anthropicService.summarizeCluster(any(), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 旧标签删完这条链接后不再有任何生效链接 —— 不应留成僵尸标签
        when(clipTagLinkRepository.existsByTag(oldTag)).thenReturn(false);
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(104L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(clipTagLinkRepository).delete(staleLink);
        verify(clipTagLinkRepository, org.mockito.Mockito.never()).save(argThat(l -> l == staleLink));
        assertThat(oldTag.getUsedByClips()).isFalse();
        verify(tagRepository).save(argThat(t -> t == oldTag && Boolean.FALSE.equals(t.getUsedByClips())));
    }

    @Test
    void runTimeline_bucketsByYearSummarizesAndReplacesNote() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c2023 = clip(1, "标题A");
        c2023.setOriginalBookmarkedAt(LocalDateTime.of(2023, 1, 1, 0, 0));
        c2023.setSourceUrl("https://example.com/a2023");
        List<SourceClip> clips = List.of(c2023);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.summarizeTimelineBucket(eq("2023 年"), any()))
                .thenReturn("这一年收藏了不少内容。");
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.TIMELINE))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(200L);
            return n;
        });

        Note result = service.runTimeline(1L, owner, clips);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getContent())
                .contains("## 2023 年")
                .contains("这一年收藏了不少内容")
                .contains("- [标题A](https://example.com/a2023)");
        verify(jobRepository).startPhase(1L, 1, "SUMMARIZING");
        verify(jobRepository, times(1)).incrementCompletedSteps(1L);
    }

    @Test
    void generate_returnsExistingRunningJobInsteadOfCreatingNew() {
        setUp();
        User owner = User.builder().id(1L).build();
        BookmarkAgentJob running = BookmarkAgentJob.builder().id(9L).owner(owner)
                .type(BookmarkAgentJob.Type.CLUSTER).status(BookmarkAgentJob.Status.RUNNING).build();
        when(jobRepository.findTopByOwnerAndTypeOrderByCreatedAtDesc(owner, BookmarkAgentJob.Type.CLUSTER))
                .thenReturn(java.util.Optional.of(running));

        BookmarkAgentJob result = service.generate(BookmarkAgentJob.Type.CLUSTER, owner);

        assertThat(result).isSameAs(running);
        verify(jobRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void generate_createsNewRunningJobWhenNonePending() {
        setUp();
        User owner = User.builder().id(1L).build();
        when(jobRepository.findTopByOwnerAndTypeOrderByCreatedAtDesc(owner, BookmarkAgentJob.Type.TIMELINE))
                .thenReturn(java.util.Optional.empty());
        when(jobRepository.save(any())).thenAnswer(inv -> {
            BookmarkAgentJob j = inv.getArgument(0);
            j.setId(10L);
            return j;
        });

        BookmarkAgentJob result = service.generate(BookmarkAgentJob.Type.TIMELINE, owner);

        assertThat(result.getStatus()).isEqualTo(BookmarkAgentJob.Status.RUNNING);
        assertThat(result.getType()).isEqualTo(BookmarkAgentJob.Type.TIMELINE);
    }

    @Test
    void failJob_marksJobFailedWithTruncatedErrorMessage() {
        setUp();
        BookmarkAgentJob job = BookmarkAgentJob.builder().id(1L).status(BookmarkAgentJob.Status.RUNNING).build();
        when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.failJob(1L, "boom");

        verify(jobRepository).save(argThat(j ->
                j.getStatus() == BookmarkAgentJob.Status.FAILED && "boom".equals(j.getErrorMessage())));
    }

    @Test
    void completeJob_marksJobDoneWithResultNoteId() {
        setUp();
        BookmarkAgentJob job = BookmarkAgentJob.builder().id(1L).status(BookmarkAgentJob.Status.RUNNING).build();
        when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeJob(1L, 77L);

        verify(jobRepository).save(argThat(j ->
                j.getStatus() == BookmarkAgentJob.Status.DONE && Long.valueOf(77L).equals(j.getResultNoteId())));
    }

    @Test
    void runGenerate_dispatchesToRunClusterAndCompletesJobOnSuccess() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(owner));
        when(clipRepository.findByOwner(eq(owner), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.empty());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(300L);
            return n;
        });
        when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(
                BookmarkAgentJob.builder().id(1L).status(BookmarkAgentJob.Status.RUNNING).build()));

        service.runGenerate(1L, BookmarkAgentJob.Type.CLUSTER, 1L);

        verify(jobRepository).save(argThat(j ->
                j.getStatus() == BookmarkAgentJob.Status.DONE && Long.valueOf(300L).equals(j.getResultNoteId())));
    }

    @Test
    void runGenerate_catchesExceptionAndCallsFailJob() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(owner));
        when(clipRepository.findByOwner(eq(owner), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(clip(1, "标题一"))));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.classifyTopics(any(), any())).thenThrow(new RuntimeException("AI 调用失败"));
        when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(
                BookmarkAgentJob.builder().id(1L).status(BookmarkAgentJob.Status.RUNNING).build()));

        service.runGenerate(1L, BookmarkAgentJob.Type.CLUSTER, 1L);

        verify(jobRepository).save(argThat(j ->
                j.getStatus() == BookmarkAgentJob.Status.FAILED && "AI 调用失败".equals(j.getErrorMessage())));
    }

    @Test
    void replaceGeneratedNote_deletesNoteImageRefsBeforeDeletingOldNote() {
        // note_image_refs.note_id的外键没有ON DELETE CASCADE（不同于V5给clip_search_messages
        // 加的先例），如果用户手动编辑过一篇自动生成的笔记并贴了张已完成OCR的图，下次
        // 自动重新生成时如果不先清note_image_refs，这里的delete会直接撞外键约束抛异常，
        // 炸掉收藏摘要的定时生成任务。必须和NoteService.deleteNote一样先清再删。
        setUp();
        User owner = User.builder().id(1L).build();
        Note old = Note.builder().id(99L).owner(owner).generatedType(Note.GeneratedType.CLUSTER).build();
        when(noteRepository.findByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(java.util.Optional.of(old));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replaceGeneratedNote(owner, Note.GeneratedType.CLUSTER, "知识地图", "内容", List.of());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(noteImageRefRepository, noteRepository);
        order.verify(noteImageRefRepository).deleteByNote(old);
        order.verify(noteRepository).delete(old);
    }

    @Test
    void buildLinkListMarkdown_buildsOneBulletLinkPerClip() {
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");

        String markdown = BookmarkAgentService.buildLinkListMarkdown(List.of(c1, c2));

        assertThat(markdown).isEqualTo(
                "- [标题一](https://example.com/a)\n"
                + "- [标题二](https://example.com/b)\n");
    }

    @Test
    void buildLinkListMarkdown_sanitizesTitleCharactersThatBreakLinkSyntax() {
        SourceClip c1 = clip(1, "标题[带方括号]和\n换行");
        c1.setSourceUrl("https://example.com/a");

        String markdown = BookmarkAgentService.buildLinkListMarkdown(List.of(c1));

        assertThat(markdown).isEqualTo("- [标题(带方括号)和 换行](https://example.com/a)\n");
    }
}
