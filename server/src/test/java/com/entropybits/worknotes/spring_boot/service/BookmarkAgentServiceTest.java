/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.ai.service.ClipContent;
import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.*;
import com.entropybits.worknotes.spring_boot.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @Mock ContentChunkRepository contentChunkRepository;
    @Mock EmbeddingService embeddingService;

    // 真实 ObjectMapper 而不是 mock：只是用来解析测试里自己拼的 embeddingJson 字符串，
    // mock 出来还要逐条 stub 每个 chunk 的解析结果，不如直接用真的省事又不失真
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BookmarkAgentService service;

    private void setUp() {
        service = new BookmarkAgentService(jobRepository, clipRepository, noteRepository, noteClipRefRepository,
                userRepository, tagRepository, clipTagLinkRepository, aiProperties,
                anthropicService, openAiService, deepseekService, doubaoService, contentIndexingService,
                noteImageRefRepository, contentChunkRepository, embeddingService, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    }

    private SourceClip clip(long id, String title) {
        return SourceClip.builder().id(id).title(title).sourceType(SourceClip.SourceType.WEBPAGE).build();
    }

    /** 内容分块，embeddingJson 直接传字符串形式的向量数组，比如 "[1.0,0.0]" */
    private ContentChunk chunk(long sourceId, String embeddingJson) {
        return ContentChunk.builder().sourceType(ContentChunk.SourceType.CLIP).sourceId(sourceId)
                .chunkIndex(0).chunkText("x").contentHash("h").embeddingJson(embeddingJson).build();
    }

    /**
     * 只关心正文文本和顺序的分块（摘要相关测试用）；embeddingJson 固定给 2 维向量，跟其他测试里
     * chunk() 用的维度一致——同一个 clip 的多个分块会被 averageVectors 按元素相加，维度不一致会
     * 直接数组越界
     */
    private ContentChunk textChunk(long sourceId, int chunkIndex, String chunkText) {
        return ContentChunk.builder().sourceType(ContentChunk.SourceType.CLIP).sourceId(sourceId)
                .chunkIndex(chunkIndex).chunkText(chunkText).contentHash("h").embeddingJson("[0.0,0.0]").build();
    }

    @Test
    void averageVectors_averagesElementwiseAcrossAllVectors() {
        float[] result = BookmarkAgentService.averageVectors(List.of(
                new float[]{1f, 3f}, new float[]{3f, 5f}));

        assertThat(result).containsExactly(2f, 4f);
    }

    @Test
    void updateCentroid_computesTrueRunningMeanNotJustPairwiseAverage() {
        // 簇里已有 2 个成员、质心是 [2,2]（比如 [1,1] 和 [3,3] 的均值），加入第 3 个成员 [4,4]：
        // 三者真实均值是 (1+3+4)/3=8/3；如果简单跟新向量取一次 pairwise 平均 (2+4)/2=3 就算错了，
        // 必须用 existingCount 加权
        float[] updated = BookmarkAgentService.updateCentroid(new float[]{2f, 2f}, 2, new float[]{4f, 4f});

        assertThat(updated[0]).isCloseTo(8f / 3f, org.assertj.core.data.Offset.offset(0.0001f));
        assertThat(updated[1]).isCloseTo(8f / 3f, org.assertj.core.data.Offset.offset(0.0001f));
    }

    @Test
    void clusterBySimilarity_groupsClipsWithVectorsAboveThresholdAndSeparatesTheRest() {
        SourceClip c1 = clip(1, "a");
        SourceClip c2 = clip(2, "b");
        SourceClip c3 = clip(3, "c");
        Map<Long, float[]> vectors = Map.of(
                1L, new float[]{1f, 0f},
                2L, new float[]{1f, 0f},  // 跟 c1 完全同向，相似度 1.0，应该分到一簇
                3L, new float[]{0f, 1f}); // 跟前两者正交，相似度 0.0，应该单独成簇

        List<List<SourceClip>> clusters = BookmarkAgentService.clusterBySimilarity(List.of(c1, c2, c3), vectors, 0.7);

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).containsExactly(c1, c2);
        assertThat(clusters.get(1)).containsExactly(c3);
    }

    @Test
    void clusterBySimilarity_clipWithoutVectorFormsItsOwnCluster() {
        SourceClip c1 = clip(1, "a");
        SourceClip c2 = clip(2, "b"); // 没有向量：computeClipVectors 里 embedding 彻底失败的兜底情况

        List<List<SourceClip>> clusters = BookmarkAgentService.clusterBySimilarity(
                List.of(c1, c2), Map.of(1L, new float[]{1f, 0f}), 0.7);

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).containsExactly(c1);
        assertThat(clusters.get(1)).containsExactly(c2);
    }

    @Test
    void foldSingletonClusters_foldsSizeOneClustersIntoOtherKeepsRestAsNamable() {
        SourceClip c1 = clip(1, "a"), c2 = clip(2, "b"), c3 = clip(3, "c");
        List<List<SourceClip>> clusters = List.of(List.of(c1, c2), List.of(c3));

        BookmarkAgentService.FoldedClusters folded = BookmarkAgentService.foldSingletonClusters(clusters);

        assertThat(folded.namable()).containsExactly(List.of(c1, c2));
        assertThat(folded.other()).containsExactly(c3);
    }

    @Test
    void pickClusterLabel_picksMostFrequentTopCandidateAcrossCluster() {
        String label = BookmarkAgentService.pickClusterLabel(List.of(
                List.of("AI", "编程"), List.of("AI"), List.of("旅行")));

        assertThat(label).isEqualTo("AI");
    }

    @Test
    void pickClusterLabel_fallsBackToOtherWhenAllCandidatesEmpty() {
        String label = BookmarkAgentService.pickClusterLabel(List.of(List.of(), List.of()));

        assertThat(label).isEqualTo("其他");
    }

    @Test
    void foldSmallNamedGroups_foldsGroupsSmallerThanTwoIntoOtherAndKeepsExistingOther() {
        SourceClip c1 = clip(1, "a"), c2 = clip(2, "b"), c3 = clip(3, "c"), c4 = clip(4, "d");
        LinkedHashMap<String, List<SourceClip>> groups = new LinkedHashMap<>();
        groups.put("AI", new ArrayList<>(List.of(c1, c2)));
        groups.put("旅行", new ArrayList<>(List.of(c3))); // 只有 1 条，该被折叠
        groups.put("其他", new ArrayList<>(List.of(c4))); // 已有的"其他"要保留并合并

        LinkedHashMap<String, List<SourceClip>> result = BookmarkAgentService.foldSmallNamedGroups(groups);

        assertThat(result).containsKey("AI");
        assertThat(result).doesNotContainKey("旅行");
        assertThat(result.get("其他")).containsExactlyInAnyOrder(c3, c4);
    }

    @Test
    void buildClipContents_joinsChunksInIndexOrderAndTruncatesLongExcerpts() {
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceAuthor("张三");
        // 分块乱序传入，拼接结果必须按 chunkIndex 排序而不是传入顺序；第一块本身就超过截断上限，
        // 用来确认截断确实发生在"按顺序拼接之后"而不是丢弃了排在后面的块
        Map<Long, List<ContentChunk>> chunksByClipId = Map.of(1L, List.of(
                textChunk(1, 1, "b".repeat(400)),
                textChunk(1, 0, "a".repeat(700))));

        List<ClipContent> contents = BookmarkAgentService.buildClipContents(List.of(c1), chunksByClipId);

        assertThat(contents).hasSize(1);
        ClipContent content = contents.get(0);
        assertThat(content.title()).isEqualTo("标题一");
        assertThat(content.author()).isEqualTo("张三");
        // a 块在前（index 0），拼接后超过 600 字上限被截断并加省略号，不会把两块全量塞进 prompt
        assertThat(content.excerpt()).startsWith("a".repeat(600));
        assertThat(content.excerpt()).endsWith("...");
        assertThat(content.excerpt()).hasSize(600 + 3);
    }

    @Test
    void buildClipContents_treatsChunkZeroEqualToTitleAsNoRealExcerpt() {
        // ContentChunkingService.chunkClip 不管有没有正文都会把标题单独存成第 0 块（标题检索不被
        // 正文稀释）；仅链接/抓取失败的收藏因此也会有"1 条分块"，但那条分块除了标题本身没有任何
        // 新信息。回填这类收藏后 excerpt 不该变成"标题的复读"，得继续判定为没有正文
        SourceClip c1 = clip(1, "标题一");
        Map<Long, List<ContentChunk>> chunksByClipId = Map.of(1L, List.of(textChunk(1, 0, "标题一")));

        List<ClipContent> contents = BookmarkAgentService.buildClipContents(List.of(c1), chunksByClipId);

        assertThat(contents.get(0).excerpt()).isEmpty();
    }

    @Test
    void buildClipContents_keepsChunkZeroWhenItDiffersFromTitle() {
        // 只有"第 0 块文本 == 标题"才特殊处理；万一某条 clip 第 0 块碰巧不是标题重复（理论上
        // chunkClip 不会这样，但摘录构建逻辑本身不应该对此有隐藏假设），不该被误判掉
        SourceClip c1 = clip(1, "标题一");
        Map<Long, List<ContentChunk>> chunksByClipId = Map.of(1L, List.of(textChunk(1, 0, "这不是标题")));

        List<ClipContent> contents = BookmarkAgentService.buildClipContents(List.of(c1), chunksByClipId);

        assertThat(contents.get(0).excerpt()).isEqualTo("这不是标题");
    }

    @Test
    void buildClipContents_emptyExcerptWhenClipHasNoChunks() {
        // 仅链接/抓取失败的收藏：chunksByClipId 里没有它的分块，摘录应为空字符串，
        // 让 prompt 只能靠标题推断，而不是抛异常或塞 null
        SourceClip c1 = clip(1, "标题一");

        List<ClipContent> contents = BookmarkAgentService.buildClipContents(List.of(c1), Map.of());

        assertThat(contents.get(0).excerpt()).isEmpty();
    }

    @Test
    void groupByYear_bucketsClipsByOriginalBookmarkedAtYearAscending() {
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
        SourceClip clip = clip(1, "没有原始收藏时间");
        clip.setCreatedAt(LocalDateTime.of(2021, 6, 1, 0, 0));

        Map<Integer, List<SourceClip>> byYear = BookmarkAgentService.groupByYear(List.of(clip));

        assertThat(byYear).containsOnlyKeys(2021);
    }

    @Test
    void runCluster_semanticClustersSimilarClipsAndReplacesNote() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        List<SourceClip> clips = List.of(c1, c2);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        // c1、c2 内容向量相同，语义聚类阶段会把它们分进同一簇
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(anthropicService.summarizeCluster(eq("AI"), any()))
                .thenReturn("这是一组 AI 相关收藏。");
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
        verify(jobRepository).startPhase(1L, 1, "CLASSIFYING"); // 语义聚类阶段固定 1 步
        verify(jobRepository).startPhase(1L, 1, "SUMMARIZING"); // 1 个最终分组
        verify(jobRepository, times(2)).incrementCompletedSteps(1L); // 聚类 1 步 + 1 个分组
        verify(noteClipRefRepository, times(2)).save(any());
    }

    @Test
    void runCluster_passesRealClipExcerptsNotJustTitlesToSummarizeCluster() throws Exception {
        // 回归测试：摘要这一步曾经只拿标题编一句话，完全不看正文。这里验证正文分块的真实文本
        // 确实传到了 summarizeCluster，而不是空摘录或者干脆没传
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        List<SourceClip> clips = List.of(c1, c2);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(
                        chunk(1, "[1.0,0.0]"), textChunk(1, 1, "第一条收藏的真实正文"),
                        chunk(2, "[1.0,0.0]"), textChunk(2, 1, "第二条收藏的真实正文")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(107L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(anthropicService).summarizeCluster(eq("AI"), argThat(contents ->
                contents.stream().anyMatch(c -> c.excerpt().contains("第一条收藏的真实正文"))
                        && contents.stream().anyMatch(c -> c.excerpt().contains("第二条收藏的真实正文"))));
    }

    @Test
    void runCluster_singletonClusterSkipsNamingCallAndFoldsDirectlyIntoOtherTag() throws Exception {
        // 只有 1 条待分类的收藏，语义聚类阶段必然是单独一簇，够不上命名门槛（<2 个成员），
        // 直接并入"其他"——不该为它单独花一次 AI 调用去起名字
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        List<SourceClip> clips = List.of(c1);
        Tag otherTag = Tag.builder().id(11L).name("其他").owner(owner).usedByClips(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(anthropicService.summarizeCluster(eq("其他"), any())).thenReturn("摘要。");
        when(tagRepository.findByNameAndOwner("其他", owner)).thenReturn(java.util.Optional.of(otherTag));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(105L);
            return n;
        });

        service.runCluster(1L, owner, clips);

        verify(anthropicService, org.mockito.Mockito.never()).classifyTopics(any(), any());
        verify(clipTagLinkRepository).save(argThat(l ->
                l.getClip() == c1 && l.getTag() == otherTag && Boolean.TRUE.equals(l.getAiSuggested())));
    }

    @Test
    void runCluster_mergesTwoSeparateEmbeddingClustersWhenAiGivesThemTheSameLabel() throws Exception {
        // 语义聚类只保证"向量够相似的分到一起"，不保证同义表述一定会被分到同一簇（比如两组内容
        // 各自相似、但彼此向量正交）；命名阶段如果两个簇被独立起了同一个名字，应该直接合并成一个
        // 分组输出，而不是各自单独出一节——这是这次改造要解决的"同义词碎片化"问题的核心验证
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一"); c1.setSourceUrl("https://example.com/a");
        SourceClip c2 = clip(2, "标题二"); c2.setSourceUrl("https://example.com/b");
        SourceClip c3 = clip(3, "标题三"); c3.setSourceUrl("https://example.com/c");
        SourceClip c4 = clip(4, "标题四"); c4.setSourceUrl("https://example.com/d");
        List<SourceClip> clips = List.of(c1, c2, c3, c4);

        when(aiProperties.getProvider()).thenReturn("anthropic");
        // c1、c2 向量相同分一簇；c3、c4 向量相同分另一簇；两簇彼此正交，向量层面绝不会合并到一起
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(
                        chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]"),
                        chunk(3, "[0.0,1.0]"), chunk(4, "[0.0,1.0]")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.classifyTopics(List.of("标题三", "标题四"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
        when(noteRepository.save(any())).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(106L);
            return n;
        });

        Note result = service.runCluster(1L, owner, clips);

        assertThat(result.getContent()).containsOnlyOnce("## AI");
        verify(jobRepository).startPhase(1L, 1, "SUMMARIZING"); // 合并后只剩 1 个分组
        verify(noteClipRefRepository, times(4)).save(any());
    }

    @Test
    void runCluster_skipsClassificationForClipsWithManuallyAdjustedTags() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
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
        // 只有 auto1、auto2（id=3,4）会被传去查向量：手动调整过标签的 clip 完全不参与语义聚类
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(3, "[1.0,0.0]"), chunk(4, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(List.of("标题三", "标题四"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("人工分类"), any())).thenReturn("人工分类摘要。");
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("AI 摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
    void runCluster_upsertsAiSuggestedTagFromClusterLabel() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag aiTag = Tag.builder().id(7L).name("AI").owner(owner).usedByClips(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("AI"), List.of("AI")));
        when(anthropicService.summarizeCluster(eq("AI"), any())).thenReturn("摘要。");
        when(tagRepository.findByNameAndOwner("AI", owner)).thenReturn(java.util.Optional.of(aiTag));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
    void runCluster_demotesStaleAiSuggestedLinkWhenManuallyAddedTooInsteadOfDeleting() throws Exception {
        setUp();
        User owner = User.builder().id(1L).build();
        SourceClip c1 = clip(1, "标题一");
        c1.setSourceUrl("https://example.com/a");
        c1.setOwner(owner);
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag oldTag = Tag.builder().id(8L).name("旧标签").owner(owner).usedByClips(true).build();
        // 上一轮生成的 aiSuggested 关联，同时被用户手动确认过（manuallyAdded=true），
        // 这一轮命中了不同的簇名，旧关联应当被"降级"而不是删除
        ClipTagLink staleLink = ClipTagLink.builder().clip(c1).tag(oldTag).aiSuggested(true).manuallyAdded(true).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(clipTagLinkRepository.findByClip(c1)).thenReturn(List.of(staleLink));
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("新标签"), List.of("新标签")));
        when(anthropicService.summarizeCluster(any(), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
        SourceClip c2 = clip(2, "标题二");
        c2.setSourceUrl("https://example.com/b");
        c2.setOwner(owner);
        List<SourceClip> clips = List.of(c1, c2);
        Tag oldTag = Tag.builder().id(8L).name("旧标签").owner(owner).usedByClips(true).build();
        // 上一轮纯 AI 建议、用户从未手动确认过（manuallyAdded=false），这一轮没再命中同一个簇名，
        // 旧关联应当被直接删除而不是保留降级
        ClipTagLink staleLink = ClipTagLink.builder().clip(c1).tag(oldTag).aiSuggested(true).manuallyAdded(false).build();

        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(clipTagLinkRepository.findByClip(c1)).thenReturn(List.of(staleLink));
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(List.of("标题一", "标题二"), List.of()))
                .thenReturn(List.of(List.of("新标签"), List.of("新标签")));
        when(anthropicService.summarizeCluster(any(), any())).thenReturn("摘要。");
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clipTagLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 旧标签删完这条链接后不再有任何生效链接 —— 不应留成僵尸标签
        when(clipTagLinkRepository.existsByTag(oldTag)).thenReturn(false);
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.TIMELINE))
                .thenReturn(List.of());
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
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of());
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
        SourceClip c1 = clip(1, "标题一");
        SourceClip c2 = clip(2, "标题二");
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(owner));
        when(clipRepository.findByOwner(eq(owner), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(c1, c2)));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        // c1、c2 向量相同才会真的进到 nameClusters 触发 classifyTopics 调用
        when(contentChunkRepository.findBySourceTypeAndSourceIdIn(eq(ContentChunk.SourceType.CLIP), any()))
                .thenReturn(List.of(chunk(1, "[1.0,0.0]"), chunk(2, "[1.0,0.0]")));
        when(anthropicService.classifyTopics(any(), any())).thenThrow(new RuntimeException("AI 调用失败"));
        when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(
                BookmarkAgentJob.builder().id(1L).status(BookmarkAgentJob.Status.RUNNING).build()));

        service.runGenerate(1L, BookmarkAgentJob.Type.CLUSTER, 1L);

        verify(jobRepository).save(argThat(j ->
                j.getStatus() == BookmarkAgentJob.Status.FAILED && "AI 调用失败".equals(j.getErrorMessage())));
    }

    @Test
    void replaceGeneratedNote_deletesNoteImageRefsAndClipRefsBeforeDeletingOldNote() {
        // note_image_refs.note_id 和 note_clip_refs.note_id 的外键都没有 ON DELETE CASCADE。
        // note_clip_refs 这条是这个方法自己在插入新笔记时写入的（见下面的 refClips 循环）——
        // 第一次生成时还没有旧记录不会触发，但只要用户点第二次"重新生成"，旧笔记就带着上一轮
        // 写的 note_clip_refs，不先清掉这里 delete 就会直接撞外键约束，炸掉整个生成任务
        // （这个 bug 真实发生过：第二次生成知识地图时报 FK 约束错误）。
        setUp();
        User owner = User.builder().id(1L).build();
        Note old = Note.builder().id(99L).owner(owner).generatedType(Note.GeneratedType.CLUSTER).build();
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of(old));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replaceGeneratedNote(owner, Note.GeneratedType.CLUSTER, "知识地图", "内容", List.of());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(noteImageRefRepository, noteClipRefRepository, noteRepository);
        order.verify(noteImageRefRepository).deleteByNote(old);
        order.verify(noteClipRefRepository).deleteByNote(old);
        order.verify(noteRepository).delete(old);
    }

    @Test
    void replaceGeneratedNote_deletesAllStaleRows_whenMultipleExistFromDataCorruption() {
        // 数据库并没有真正的 (owner_id, generated_type) 唯一索引兜底"每种类型最多一条"这个假设，
        // 历史数据修复/迁移曾实际残留过同一用户同一类型的多条旧记录（例如一次崩库抢救后
        // owner 3 的 CLUSTER 笔记残留了 9 条），此前用 Optional 单条查询会直接抛
        // IncorrectResultSizeDataAccessException 炸掉整个生成任务。这里验证改为 List 后
        // 每一条旧记录都会被清理，而不是假设至多一条。
        setUp();
        User owner = User.builder().id(1L).build();
        Note old1 = Note.builder().id(1L).owner(owner).generatedType(Note.GeneratedType.CLUSTER).build();
        Note old2 = Note.builder().id(2L).owner(owner).generatedType(Note.GeneratedType.CLUSTER).build();
        Note old3 = Note.builder().id(3L).owner(owner).generatedType(Note.GeneratedType.CLUSTER).build();
        when(noteRepository.findAllByOwnerAndGeneratedType(owner, Note.GeneratedType.CLUSTER))
                .thenReturn(List.of(old1, old2, old3));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replaceGeneratedNote(owner, Note.GeneratedType.CLUSTER, "知识地图", "内容", List.of());

        for (Note old : List.of(old1, old2, old3)) {
            verify(contentIndexingService).deleteChunksFor(ContentChunk.SourceType.NOTE, old.getId());
            verify(noteImageRefRepository).deleteByNote(old);
            verify(noteRepository).delete(old);
        }
        verify(noteRepository).flush();
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
