/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleTranslationServiceTest {

    private HttpServer httpServer;
    private OpenAiCompatibleTranslationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setUp(String modelReplyContent) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/chat/completions", ex -> {
            String body = "{\"choices\":[{\"message\":{\"content\":"
                + objectMapper.writeValueAsString(modelReplyContent) + "}}]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        httpServer.start();

        AiProperties.ProviderConfig config = new AiProperties.ProviderConfig();
        config.setApiKey("test-key");
        config.setModel("test-model");
        config.setBaseUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        config.setChatPath("/v1/chat/completions");

        service = new OpenAiCompatibleTranslationService(config, "TestProvider", objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) httpServer.stop(0);
    }

    @Test
    void classifyTopics_parsesNestedJsonArrayResponseInOrder() throws Exception {
        setUp("[[\"AI\",\"编程\"],[\"旅行\"]]");

        List<List<String>> result = service.classifyTopics(List.of("标题一", "标题二"), List.of());

        assertThat(result).containsExactly(List.of("AI", "编程"), List.of("旅行"));
    }

    @Test
    void classifyTopics_padsMissingEntriesWithEmptyListWhenModelReturnsFewer() throws Exception {
        setUp("[[\"AI\"]]");

        List<List<String>> result = service.classifyTopics(List.of("标题一", "标题二"), List.of());

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly("AI");
        assertThat(result.get(1)).isEmpty();
    }

    @Test
    void summarizeCluster_returnsRawMarkdownText() throws Exception {
        setUp("这是一组关于 AI 的收藏。\n- 标题一\n- 标题二");

        String result = service.summarizeCluster("AI", List.of("标题一", "标题二"));

        assertThat(result).isEqualTo("这是一组关于 AI 的收藏。\n- 标题一\n- 标题二");
    }

    @Test
    void summarizeTimelineBucket_returnsRawParagraphText() throws Exception {
        setUp("这一年主要收藏了关于人工智能的内容。");

        String result = service.summarizeTimelineBucket("2023 年", List.of("标题一"));

        assertThat(result).isEqualTo("这一年主要收藏了关于人工智能的内容。");
    }

    @Test
    void planSearchQuery_returnsNullWhenModelRespondsNoSearch() throws Exception {
        setUp("NO_SEARCH");

        String query = service.planSearchQuery(List.of("用户: 帮我找找AI监管的报道", "助手: 已找到3篇"), "只看2024年以后的");

        assertThat(query).isNull();
    }

    @Test
    void planSearchQuery_returnsTrimmedQueryWhenModelRequestsNewSearch() throws Exception {
        setUp("AI 监管 深度报道");

        String query = service.planSearchQuery(List.of(), "帮我整理关于AI监管的深度报道");

        assertThat(query).isEqualTo("AI 监管 深度报道");
    }

    @Test
    void curateSearchResults_resolvesModelIndexesBackToOriginalCandidates() throws Exception {
        // 模型只回下标+摘要，title/url 必须由候选池原样解析出来（而不是模型复述）
        setUp("{\"reply\":\"为你找到2篇相关报道：\",\"results\":["
                + "{\"index\":1,\"excerpt\":\"摘要二\"},"
                + "{\"index\":0,\"excerpt\":\"摘要一\"}]}");

        List<com.entropybits.worknotes.spring_boot.search.SearchResultItem> candidates = List.of(
                new com.entropybits.worknotes.spring_boot.search.SearchResultItem("标题一", "https://a.example.com/CBM1a2c", ""),
                new com.entropybits.worknotes.spring_boot.search.SearchResultItem("标题二", "https://b.example.com/CBM9z8y", ""));

        com.entropybits.worknotes.spring_boot.ai.service.CuratedSearchResult result =
                service.curateSearchResults("帮我整理关于AI监管的深度报道", candidates);

        assertThat(result.reply()).isEqualTo("为你找到2篇相关报道：");
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).title()).isEqualTo("标题二");
        assertThat(result.results().get(0).url()).isEqualTo("https://b.example.com/CBM9z8y");
        assertThat(result.results().get(0).excerpt()).isEqualTo("摘要二");
        assertThat(result.results().get(1).title()).isEqualTo("标题一");
        assertThat(result.results().get(1).url()).isEqualTo("https://a.example.com/CBM1a2c");
        assertThat(result.results().get(1).excerpt()).isEqualTo("摘要一");
    }

    @Test
    void curateSearchResults_dropsOutOfRangeAndNegativeIndexes() throws Exception {
        setUp("{\"reply\":\"为你找到3篇相关报道：\",\"results\":["
                + "{\"index\":99,\"excerpt\":\"编造的摘要\"},"
                + "{\"index\":-1,\"excerpt\":\"另一个编造的摘要\"},"
                + "{\"index\":0,\"excerpt\":\"摘要一\"}]}");

        com.entropybits.worknotes.spring_boot.ai.service.CuratedSearchResult result = service.curateSearchResults(
                "帮我整理关于AI监管的深度报道",
                List.of(new com.entropybits.worknotes.spring_boot.search.SearchResultItem("标题一", "https://a.example.com", ""),
                        new com.entropybits.worknotes.spring_boot.search.SearchResultItem("标题二", "https://b.example.com", "")));

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("标题一");
        assertThat(result.results().get(0).url()).isEqualTo("https://a.example.com");
    }
}
