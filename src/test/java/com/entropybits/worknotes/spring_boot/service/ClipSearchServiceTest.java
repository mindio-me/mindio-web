/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.config.AiProperties;
import com.entropybits.worknotes.spring_boot.ai.service.AiTranslationService;
import com.entropybits.worknotes.spring_boot.ai.service.CuratedSearchResult;
import com.entropybits.worknotes.spring_boot.dto.ClipImportUrlRequest;
import com.entropybits.worknotes.spring_boot.dto.ClipSearchMessageResponse;
import com.entropybits.worknotes.spring_boot.dto.SourceClipDraft;
import com.entropybits.worknotes.spring_boot.dto.SourceClipRequest;
import com.entropybits.worknotes.spring_boot.dto.SourceClipResponse;
import com.entropybits.worknotes.spring_boot.entity.ClipSearchMessage;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ClipSearchMessageRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.UserRepository;
import com.entropybits.worknotes.spring_boot.search.SearchResultItem;
import com.entropybits.worknotes.spring_boot.search.WebSearchProvider;
import com.entropybits.worknotes.spring_boot.search.WebSearchProviderResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClipSearchServiceTest {

    @Mock ClipSearchMessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock NoteRepository noteRepository;
    @Mock AiProperties aiProperties;
    @Mock AiTranslationService anthropicService;
    @Mock WebSearchProviderResolver searchProviderResolver;
    @Mock WebSearchProvider searchProvider;
    @Mock ClipImportService clipImportService;
    @Mock SourceClipService sourceClipService;
    @Mock NoteClipRefService noteClipRefService;

    private ClipSearchService service;
    private final User user = User.builder().id(1L).username("alice").build();
    private final Note note = Note.builder().id(10L).build();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(aiProperties.getProvider()).thenReturn("anthropic");
        when(messageRepository.save(any(ClipSearchMessage.class)))
                .thenAnswer(inv -> {
                    ClipSearchMessage m = inv.getArgument(0);
                    if (m.getId() == null) m.setId((long) (Math.random() * 100000));
                    return m;
                });
        service = new ClipSearchService(messageRepository, userRepository, aiProperties,
                anthropicService, anthropicService, anthropicService, anthropicService,
                searchProviderResolver, objectMapper, clipImportService, sourceClipService,
                noteRepository, noteClipRefService);
    }

    @Test
    void sendMessage_runsNewSearchWhenPlannerReturnsQuery() throws Exception {
        when(messageRepository.findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note)).thenReturn(List.of());
        when(anthropicService.planSearchQuery(anyList(), eq("帮我找找AI监管的报道")))
                .thenReturn("AI 监管");
        when(searchProviderResolver.resolve()).thenReturn(searchProvider);
        when(searchProvider.search(eq("AI 监管"), eq(10)))
                .thenReturn(List.of(new SearchResultItem("标题一", "https://a.example.com", "")));
        when(anthropicService.curateSearchResults(eq("帮我找找AI监管的报道"), anyList()))
                .thenReturn(new CuratedSearchResult("为你找到1篇报道：",
                        List.of(new SearchResultItem("标题一", "https://a.example.com", "摘要一"))));

        List<ClipSearchMessageResponse> result = service.sendMessage("alice", "帮我找找AI监管的报道", 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo("USER");
        assertThat(result.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(result.get(1).getResults()).hasSize(1);
        assertThat(result.get(1).getResults().get(0).title()).isEqualTo("标题一");
        verify(searchProvider).search("AI 监管", 10);
    }

    @Test
    void sendMessage_reusesPreviousCandidatePoolWhenPlannerReturnsNull() throws Exception {
        ClipSearchMessage priorAssistant = ClipSearchMessage.builder()
                .id(2L).owner(user).note(note).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                .resultsJson("[{\"title\":\"标题一\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        ClipSearchMessage priorUser = ClipSearchMessage.builder()
                .id(1L).owner(user).note(note).role(ClipSearchMessage.Role.USER).content("帮我找找AI监管的报道").build();
        when(messageRepository.findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note))
                .thenReturn(List.of(priorAssistant, priorUser)); // desc order 由 service 负责反转
        when(anthropicService.planSearchQuery(anyList(), eq("只看2024年以后的")))
                .thenReturn(null);
        when(anthropicService.curateSearchResults(eq("只看2024年以后的"), anyList()))
                .thenReturn(new CuratedSearchResult("已经帮你收窄：",
                        List.of(new SearchResultItem("标题一", "https://a.example.com", "摘要一"))));

        service.sendMessage("alice", "只看2024年以后的", 10L);

        verify(searchProviderResolver, never()).resolve();
        verify(anthropicService).curateSearchResults(eq("只看2024年以后的"),
                eq(List.of(new SearchResultItem("标题一", "https://a.example.com", "摘要一"))));
    }

    @Test
    void sendMessage_fallsBackToFriendlyMessageWhenPipelineThrows() throws Exception {
        when(messageRepository.findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note)).thenReturn(List.of());
        when(anthropicService.planSearchQuery(anyList(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        List<ClipSearchMessageResponse> result = service.sendMessage("alice", "帮我找找AI监管的报道", 10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).getRole()).isEqualTo("ASSISTANT");
        assertThat(result.get(1).getContent()).contains("换个说法");
        assertThat(result.get(1).getResults()).isNull();
    }

    @Test
    void sendMessage_filtersCuratedResultsToOnlyRealCandidateUrls() throws Exception {
        when(messageRepository.findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note)).thenReturn(List.of());
        when(anthropicService.planSearchQuery(anyList(), eq("帮我找找AI监管的报道")))
                .thenReturn("AI 监管");
        when(searchProviderResolver.resolve()).thenReturn(searchProvider);
        when(searchProvider.search(eq("AI 监管"), eq(10)))
                .thenReturn(List.of(new SearchResultItem("标题一", "https://a.example.com", "")));
        // AI 幻觉：多返回了一条不在候选池里的链接
        when(anthropicService.curateSearchResults(eq("帮我找找AI监管的报道"), anyList()))
                .thenReturn(new CuratedSearchResult("为你找到2篇报道：",
                        List.of(
                                new SearchResultItem("标题一", "https://a.example.com", "摘要一"),
                                new SearchResultItem("编造的标题", "https://fake.example.com", "编造的摘要"))));

        List<ClipSearchMessageResponse> result = service.sendMessage("alice", "帮我找找AI监管的报道", 10L);

        assertThat(result.get(1).getResults()).hasSize(1);
        assertThat(result.get(1).getResults().get(0).url()).isEqualTo("https://a.example.com");
    }

    @Test
    void listHistory_returnsMessagesScopedToNote() {
        ClipSearchMessage m = ClipSearchMessage.builder()
                .id(1L).owner(user).note(note).role(ClipSearchMessage.Role.USER).content("hi").build();
        when(messageRepository.findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note))
                .thenReturn(List.of(m));

        List<ClipSearchMessageResponse> result = service.listHistory("alice", 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("hi");
        verify(messageRepository).findTop50ByOwnerAndNoteOrderByCreatedAtDesc(user, note);
    }

    @Test
    void saveResult_createsClipAndWritesBackSourceClipId() throws Exception {
        ClipSearchMessage message = ClipSearchMessage.builder()
                .id(5L).owner(user).note(note).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                .resultsJson("[{\"title\":\"标题一\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        SourceClipDraft draft = SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://a.example.com")
                .suggestedTitle("标题一")
                .fetchSuccess(true)
                .build();
        when(clipImportService.fetchFromUrl(any(ClipImportUrlRequest.class))).thenReturn(draft);
        SourceClipResponse created = SourceClipResponse.builder().id(99L).build();
        when(sourceClipService.createClip(any(SourceClipRequest.class), eq("alice"))).thenReturn(created);

        Long sourceClipId = service.saveResult(5L, 0, "alice");

        assertThat(sourceClipId).isEqualTo(99L);
        verify(messageRepository).save(argThat(m -> m.getResultsJson().contains("\"sourceClipId\":99")));
    }

    @Test
    void saveResult_linksCreatedClipToMessagesNote() throws Exception {
        ClipSearchMessage message = ClipSearchMessage.builder()
                .id(5L).owner(user).note(note).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                .resultsJson("[{\"title\":\"标题一\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(clipImportService.fetchFromUrl(any(ClipImportUrlRequest.class))).thenReturn(SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://a.example.com")
                .suggestedTitle("标题一")
                .fetchSuccess(true)
                .build());
        when(sourceClipService.createClip(any(SourceClipRequest.class), eq("alice")))
                .thenReturn(SourceClipResponse.builder().id(99L).build());

        service.saveResult(5L, 0, "alice");

        verify(noteClipRefService).linkClipToNote(eq(10L), eq(99L), isNull());
    }

    @Test
    void saveResult_skipsAutoLinkWhenMessageHasNoNote() throws Exception {
        ClipSearchMessage message = ClipSearchMessage.builder()
                .id(7L).owner(user).note(null).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                .resultsJson("[{\"title\":\"标题一\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        when(messageRepository.findById(7L)).thenReturn(Optional.of(message));
        when(clipImportService.fetchFromUrl(any(ClipImportUrlRequest.class))).thenReturn(SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://a.example.com")
                .suggestedTitle("标题一")
                .fetchSuccess(true)
                .build());
        when(sourceClipService.createClip(any(SourceClipRequest.class), eq("alice")))
                .thenReturn(SourceClipResponse.builder().id(99L).build());

        service.saveResult(7L, 0, "alice");

        verifyNoInteractions(noteClipRefService);
    }

    @Test
    void saveResult_truncatesOverlongSuggestedTitleTo200Chars() throws Exception {
        String longTitle = "标".repeat(260);
        ClipSearchMessage message = ClipSearchMessage.builder()
                .id(5L).owner(user).note(note).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                .resultsJson("[{\"title\":\"标题一\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
        when(clipImportService.fetchFromUrl(any(ClipImportUrlRequest.class))).thenReturn(SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://a.example.com")
                .suggestedTitle(longTitle)
                .fetchSuccess(true)
                .build());
        when(sourceClipService.createClip(any(SourceClipRequest.class), eq("alice")))
                .thenReturn(SourceClipResponse.builder().id(99L).build());

        service.saveResult(5L, 0, "alice");

        ArgumentCaptor<SourceClipRequest> captor = ArgumentCaptor.forClass(SourceClipRequest.class);
        verify(sourceClipService).createClip(captor.capture(), eq("alice"));
        assertThat(captor.getValue().getTitle()).hasSize(200);
        assertThat(captor.getValue().getTitle()).isEqualTo(longTitle.substring(0, 200));
    }

    @Test
    void saveResult_fallsBackToItemTitleThenUrlWhenSuggestedTitleBlank() throws Exception {
        ClipSearchMessage message = ClipSearchMessage.builder()
                .id(6L).owner(user).note(note).role(ClipSearchMessage.Role.ASSISTANT)
                .content("为你找到1篇报道：")
                // AI 写出来的 title 是空串
                .resultsJson("[{\"title\":\"\",\"url\":\"https://a.example.com\",\"excerpt\":\"摘要一\",\"sourceClipId\":null}]")
                .build();
        when(messageRepository.findById(6L)).thenReturn(Optional.of(message));
        when(clipImportService.fetchFromUrl(any(ClipImportUrlRequest.class))).thenReturn(SourceClipDraft.builder()
                .sourceType(SourceClip.SourceType.WEBPAGE)
                .sourceUrl("https://a.example.com")
                .suggestedTitle("   ")
                .fetchSuccess(true)
                .build());
        when(sourceClipService.createClip(any(SourceClipRequest.class), eq("alice")))
                .thenReturn(SourceClipResponse.builder().id(99L).build());

        service.saveResult(6L, 0, "alice");

        ArgumentCaptor<SourceClipRequest> captor = ArgumentCaptor.forClass(SourceClipRequest.class);
        verify(sourceClipService).createClip(captor.capture(), eq("alice"));
        assertThat(captor.getValue().getTitle()).isEqualTo("https://a.example.com");
    }
}
