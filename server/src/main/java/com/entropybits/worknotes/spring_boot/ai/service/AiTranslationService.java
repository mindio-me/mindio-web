/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.ai.service;

import java.util.List;

public interface AiTranslationService {

    /**
     * 批量翻译文本片段（忠实翻译）。
     * 返回列表长度与输入相同，顺序一一对应。
     */
    List<String> translateTexts(List<String> texts, String targetLanguage) throws Exception;

    /**
     * 以原文为素材，改写为 LinkedIn 风格英文文章。
     * 返回改写后的正文文本（不含标题）。
     */
    String rewriteForLinkedIn(String title, String bodyText) throws Exception;

    /**
     * Generate 3–5 LinkedIn hashtags for the given English article.
     * Returns tag words without the '#' prefix, e.g. ["AI","SaaS","Dev"].
     */
    List<String> generateLinkedInHashtags(String title, String bodyText) throws Exception;

    /**
     * 给每个标题（书签/网页标题，多为中文）推荐 2~4 个候选主题词，用于聚类分组。
     * 返回列表长度、顺序与输入 titles 一致；每个元素是该标题的候选主题词列表。
     *
     * existingTopics 是用户当前已有的收藏分类词（去重排序），为空时表示用户第一次生成，行为与不带这个参数时一致。
     * 非空时会在 prompt 里要求 AI 优先复用已有词条，让分类随时间趋于稳定。
     */
    List<List<String>> classifyTopics(List<String> titles, List<String> existingTopics) throws Exception;

    /**
     * 给定一个主题下的标题列表，只返回一句话中文归纳（不含链接列表——链接列表由调用方
     * BookmarkAgentService.buildLinkListMarkdown 根据真实 SourceClip 数据确定性拼接）。
     */
    String summarizeCluster(String topicLabel, List<String> titlesInCluster) throws Exception;

    /**
     * 给定一个时间段（如"2023 年"）内的标题列表，返回一段叙述性文字，描述这段时间收藏的内容。
     */
    String summarizeTimelineBucket(String bucketLabel, List<String> titlesInBucket) throws Exception;

    /**
     * 根据最近对话历史（旧→新，格式为"用户: ..."/"助手: ..."）和用户最新一句话，
     * 判断本轮是否需要发起新的网络搜索。
     * 需要时返回搜索关键词（中文，单行，无解释）；不需要时返回 null，
     * 表示只对已有候选池做二次筛选/收窄即可。
     */
    String planSearchQuery(java.util.List<String> recentHistoryLines, String userMessage) throws Exception;

    /**
     * 给定用户意图和候选文章（真实搜索/RSS 得到的 title+url，excerpt 可能为空），
     * 整理成最终结果列表（可丢弃不相关的，但不能编造新文章或改动 url/title）+ 一句自然语言回应。
     */
    CuratedSearchResult curateSearchResults(String userMessage, java.util.List<com.entropybits.worknotes.spring_boot.search.SearchResultItem> candidates) throws Exception;
}
