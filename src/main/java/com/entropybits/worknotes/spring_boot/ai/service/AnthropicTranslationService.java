/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("anthropicTranslationService")
@RequiredArgsConstructor
public class AnthropicTranslationService implements AiTranslationService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    public List<String> translateTexts(List<String> texts, String targetLanguage) throws Exception {
        if (texts.isEmpty()) return List.of();

        String inputJson = objectMapper.writeValueAsString(texts);
        String prompt = "Translate each element of the following JSON string array from Chinese to " + targetLanguage + ".\n"
            + "Return ONLY a valid JSON array with exactly " + texts.size() + " string elements, in the same order.\n"
            + "No explanation, no markdown, no extra text outside the JSON array.\n"
            + inputJson;

        String result = callClaude(prompt).trim();

        // Strip markdown code fences if model wrapped the JSON
        if (result.startsWith("```")) {
            result = result.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        List<String> translated = objectMapper.readValue(result, new TypeReference<List<String>>() {});

        // Pad or truncate to match input size as safety net
        if (translated.size() < texts.size()) {
            for (int i = translated.size(); i < texts.size(); i++) translated.add(texts.get(i));
        } else if (translated.size() > texts.size()) {
            translated = translated.subList(0, texts.size());
        }
        return translated;
    }

    @Override
    public String rewriteForLinkedIn(String title, String bodyText) throws Exception {
        String prompt = "Based on the following Chinese article, write an engaging LinkedIn post in English.\n"
            + "Requirements: strong opening hook, professional yet personal tone, short paragraphs,\n"
            + "key insights highlighted, end with a question or call-to-action. Length: 200-400 words.\n"
            + "Return only the LinkedIn post text, no title.\n\n"
            + "Article title: " + title + "\n\n"
            + "Article content:\n" + bodyText;

        return callClaude(prompt).trim();
    }

    @Override
    public List<String> generateLinkedInHashtags(String title, String bodyText) throws Exception {
        String prompt = "Based on the following English article, generate 3 to 5 relevant LinkedIn hashtags.\n"
            + "Return ONLY a valid JSON array of strings without the '#' symbol.\n"
            + "Example: [\"AI\",\"SaaS\",\"Productivity\"]\n"
            + "No explanation, no markdown, no extra text outside the JSON array.\n\n"
            + "Title: " + title + "\n\n"
            + "Content:\n" + bodyText;

        String result = callClaude(prompt).trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        List<String> tags = objectMapper.readValue(result, new TypeReference<List<String>>() {});
        if (tags.size() > 5) tags = tags.subList(0, 5);
        return tags;
    }

    @Override
    public List<List<String>> classifyTopics(List<String> titles, List<String> existingTopics) throws Exception {
        if (titles.isEmpty()) return List.of();

        String inputJson = objectMapper.writeValueAsString(titles);
        StringBuilder prompt = new StringBuilder();
        prompt.append("For each title in the following JSON string array (bookmark/webpage titles, mostly Chinese), ")
                .append("suggest 1 to 3 short topic keywords (2-6 Chinese characters each) that categorize it, ")
                .append("ordered from most general/reusable to most specific — the FIRST keyword is the one ")
                .append("actually used as this bookmark's category tag, so put your best broad-category choice there.\n")
                .append("Return ONLY a valid JSON array with exactly ").append(titles.size())
                .append(" elements, in the same order, ")
                .append("where each element is itself a JSON array of topic keyword strings.\n")
                .append("Example: [[\"人工智能\",\"GPT-4\"],[\"旅行\"]]\n");
        if (existingTopics != null && !existingTopics.isEmpty()) {
            prompt.append("如果下面「已有分类」列表中有词条能描述某个标题的第一关键词，必须原样复用该词条")
                    .append("（不要因为想更精确而改写、加前后缀，或换用近义词）；分类颗粒度要偏粗——同一批书签整体上")
                    .append("应尽量归入较少的分类数，只有确实没有合适的已有分类时才提出新词，且新词也要选宽泛、")
                    .append("能覆盖未来同类内容的说法，不要为单条书签量身定做过窄的分类。已有分类：")
                    .append(objectMapper.writeValueAsString(existingTopics)).append("\n");
        }
        prompt.append("No explanation, no markdown, no extra text outside the JSON array.\n")
                .append(inputJson);

        String result = callClaude(prompt.toString()).trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }

        List<List<String>> topics = objectMapper.readValue(result, new TypeReference<List<List<String>>>() {});
        if (topics.size() < titles.size()) {
            for (int i = topics.size(); i < titles.size(); i++) topics.add(List.of());
        } else if (topics.size() > titles.size()) {
            topics = topics.subList(0, titles.size());
        }
        return topics;
    }

    @Override
    public String summarizeCluster(String topicLabel, List<String> titlesInCluster) throws Exception {
        String inputJson = objectMapper.writeValueAsString(titlesInCluster);
        String prompt = "The following is a JSON array of webpage/bookmark titles that all belong to the topic \""
            + topicLabel + "\".\n"
            + "Write ONE summary sentence in Chinese describing what this group of bookmarks is about.\n"
            + "Return ONLY that single sentence, no bullet list, no markdown formatting, no extra commentary.\n\n"
            + inputJson;

        return callClaude(prompt).trim();
    }

    @Override
    public String summarizeTimelineBucket(String bucketLabel, List<String> titlesInBucket) throws Exception {
        String inputJson = objectMapper.writeValueAsString(titlesInBucket);
        String prompt = "The following is a JSON array of webpage/bookmark titles collected during \""
            + bucketLabel + "\".\n"
            + "Write one short narrative paragraph in Chinese (3-5 sentences) describing what the person seemed "
            + "to be interested in or working on during this period, based on these titles.\n"
            + "Return ONLY the paragraph text, no markdown, no code fences, no extra commentary.\n\n"
            + inputJson;

        return callClaude(prompt).trim();
    }

    @Override
    public String planSearchQuery(List<String> recentHistoryLines, String userMessage) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are helping a user search the web for articles. Below is the recent conversation ")
              .append("history (oldest first), followed by the user's newest message.\n");
        if (recentHistoryLines != null && !recentHistoryLines.isEmpty()) {
            prompt.append("History:\n");
            for (String line : recentHistoryLines) prompt.append(line).append("\n");
        }
        prompt.append("Newest message: ").append(userMessage).append("\n\n")
              .append("Decide: does answering the newest message require running a NEW web search ")
              .append("(a new topic, or a request to broaden/change the search), or can it be answered by only ")
              .append("filtering/re-ranking the articles already found in this conversation ")
              .append("(e.g. \"only show me the ones after 2024\", \"give me more detail on the third one\")?\n")
              .append("If a new search is needed, respond with ONLY the search query in Chinese, on a single line, ")
              .append("no explanation, no quotes.\n")
              .append("If no new search is needed, respond with exactly: NO_SEARCH\n");

        String result = callClaude(prompt.toString()).trim();
        return "NO_SEARCH".equals(result) ? null : result;
    }

    /** 模型只回引用候选池下标，不回 url——内部线格式，不属于对外接口。 */
    private record CurationResultRef(int index, String excerpt) {}

    private record CurationResponse(String reply, List<CurationResultRef> results) {}

    @Override
    public CuratedSearchResult curateSearchResults(String userMessage,
                                                     List<com.entropybits.worknotes.spring_boot.search.SearchResultItem> candidates) throws Exception {
        List<Map<String, Object>> indexed = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            com.entropybits.worknotes.spring_boot.search.SearchResultItem c = candidates.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("title", c.title());
            entry.put("url", c.url());
            entry.put("excerpt", c.excerpt());
            indexed.add(entry);
        }
        String candidatesJson = objectMapper.writeValueAsString(indexed);
        String prompt = "The user asked: \"" + userMessage + "\"\n\n"
            + "Here is a JSON array of candidate articles found from a real web/news search, each with an index:\n"
            + candidatesJson + "\n\n"
            + "Select the articles (by their index) that best match what the user is looking for "
            + "(you may omit irrelevant ones, but only reference an index that appears in the array above — "
            + "never invent a new article or a url). For each kept article, write a one-sentence excerpt in "
            + "Chinese summarizing why it's relevant, ordered by relevance. Also write a short one-sentence "
            + "reply in Chinese to show the user (e.g. \"为你找到3篇相关报道：\").\n"
            + "Return ONLY a valid JSON object of the exact shape: "
            + "{\"reply\": \"...\", \"results\": [{\"index\": 0, \"excerpt\": \"...\"}]}\n"
            + "No markdown, no code fences, no extra text outside the JSON object.";

        String result = callClaude(prompt).trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        CurationResponse parsed = objectMapper.readValue(result, CurationResponse.class);
        if (parsed.results() == null) {
            return new CuratedSearchResult(parsed.reply(), List.of());
        }
        List<com.entropybits.worknotes.spring_boot.search.SearchResultItem> resolved = parsed.results().stream()
            .filter(r -> r.index() >= 0 && r.index() < candidates.size())
            .map(r -> {
                com.entropybits.worknotes.spring_boot.search.SearchResultItem c = candidates.get(r.index());
                return new com.entropybits.worknotes.spring_boot.search.SearchResultItem(c.title(), c.url(), r.excerpt());
            })
            .toList();
        return new CuratedSearchResult(parsed.reply(), resolved);
    }

    private String callClaude(String prompt) throws Exception {
        AiProperties.ProviderConfig cfg = aiProperties.getAnthropic();
        String url = cfg.getBaseUrl() + "/v1/messages";

        Map<String, Object> body = Map.of(
            "model", cfg.getModel(),
            "max_tokens", 4096,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String json = objectMapper.writeValueAsString(body);
        log.debug("Anthropic request: model={}", cfg.getModel());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-api-key", cfg.getApiKey())
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(120))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.debug("Anthropic response status={}", response.statusCode());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Anthropic API error: HTTP " + response.statusCode() + " " + response.body());
        }

        Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) parsed.get("content");
        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Anthropic API returned empty content");
        }
        return (String) content.get(0).get("text");
    }
}
