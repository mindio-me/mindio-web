/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.ai.service.EmbeddingService;
import com.entropybits.worknotes.spring_boot.entity.ContentChunk;
import com.entropybits.worknotes.spring_boot.entity.LocalFileExtraction;
import com.entropybits.worknotes.spring_boot.entity.LocalMediaFile;
import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.NoteImageRef;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.User;
import com.entropybits.worknotes.spring_boot.repository.ContentChunkRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalFileExtractionRepository;
import com.entropybits.worknotes.spring_boot.repository.LocalMediaFileRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteImageRefRepository;
import com.entropybits.worknotes.spring_boot.repository.NoteRepository;
import com.entropybits.worknotes.spring_boot.repository.SourceClipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentIndexingServiceTest {

    @Mock ContentChunkRepository chunkRepository;
    @Mock NoteRepository noteRepository;
    @Mock SourceClipRepository clipRepository;
    @Mock LocalFileExtractionRepository extractionRepository;
    @Mock LocalMediaFileRepository localMediaFileRepository;
    @Mock ContentChunkingService chunkingService;
    @Mock EmbeddingService embeddingService;
    @Mock LocalFileExtractionService extractionService;
    @Mock NoteImageRefRepository noteImageRefRepository;

    private ContentIndexingService service;
    private final User user = User.builder().id(1L).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ContentIndexingService(chunkRepository, noteRepository, clipRepository,
                chunkingService, embeddingService, objectMapper, extractionRepository, localMediaFileRepository,
                extractionService, noteImageRefRepository);
    }

    @Test
    void reindexNote_skipsEmbeddingWhenChunkHashUnchanged() throws Exception {
        Note note = Note.builder().id(10L).owner(user).contentType("markdown").content("同样的内容").build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("同样的内容"));

        String existingHash = sha256("同样的内容");
        ContentChunk existingChunk = ContentChunk.builder()
                .id(100L).owner(user).sourceType(ContentChunk.SourceType.NOTE).sourceId(10L)
                .chunkIndex(0).chunkText("同样的内容").contentHash(existingHash)
                .embeddingJson("[0.1,0.2]").build();
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 10L))
                .thenReturn(List.of(existingChunk));

        service.reindexNote(10L);

        verify(embeddingService, never()).embed(anyString());
        verify(chunkRepository, never()).save(any());
    }

    @Test
    void reindexNote_reEmbedsOnlyChangedChunkAndUpdatesRow() throws Exception {
        Note note = Note.builder().id(10L).owner(user).contentType("markdown").content("新内容").build();
        when(noteRepository.findById(10L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("新内容"));

        ContentChunk existingChunk = ContentChunk.builder()
                .id(100L).owner(user).sourceType(ContentChunk.SourceType.NOTE).sourceId(10L)
                .chunkIndex(0).chunkText("旧内容").contentHash(sha256("旧内容"))
                .embeddingJson("[0.1,0.2]").build();
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 10L))
                .thenReturn(List.of(existingChunk));
        when(embeddingService.embed("新内容")).thenReturn(new float[]{0.5f, 0.6f});

        service.reindexNote(10L);

        ArgumentCaptor<ContentChunk> captor = ArgumentCaptor.forClass(ContentChunk.class);
        verify(chunkRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(100L); // 更新已有行，不是新建
        assertThat(captor.getValue().getChunkText()).isEqualTo("新内容");
        assertThat(captor.getValue().getContentHash()).isEqualTo(sha256("新内容"));
    }

    @Test
    void reindexNote_correctsStaleChunkIndexOnUpdate() throws Exception {
        // 上一轮某个分块 embedding 失败被跳过，已落库的 chunk_index 序列出现空洞，
        // 存活的行带着过期的 index（这里是 2）却排在列表第 0 位。更新分支若不写回 chunkIndex，
        // 后续 INSERT 会撞上 (source_type, source_id, chunk_index) 唯一约束导致整个事务永久回滚。
        Note note = Note.builder().id(13L).owner(user).contentType("markdown").content("新内容").build();
        when(noteRepository.findById(13L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("新内容"));

        ContentChunk staleIndexedChunk = ContentChunk.builder()
                .id(300L).owner(user).sourceType(ContentChunk.SourceType.NOTE).sourceId(13L)
                .chunkIndex(2) // 与它在列表中的实际位置 0 不一致
                .chunkText("旧内容").contentHash(sha256("旧内容")).embeddingJson("[0.1]").build();
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 13L))
                .thenReturn(List.of(staleIndexedChunk));
        when(embeddingService.embed("新内容")).thenReturn(new float[]{0.7f});

        service.reindexNote(13L);

        ArgumentCaptor<ContentChunk> captor = ArgumentCaptor.forClass(ContentChunk.class);
        verify(chunkRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(300L);
        assertThat(captor.getValue().getChunkIndex()).isEqualTo(0); // 已自愈到真实位置，而不是残留的 2
    }

    @Test
    void reindexNote_createsNewRowsWhenChunkCountIncreases() throws Exception {
        Note note = Note.builder().id(11L).owner(user).contentType("markdown").content("段落一\n\n段落二").build();
        when(noteRepository.findById(11L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("段落一", "段落二"));
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 11L))
                .thenReturn(List.of());
        when(embeddingService.embed("段落一")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("段落二")).thenReturn(new float[]{0.2f});

        service.reindexNote(11L);

        // 新行不再在循环里逐条 save（IDENTITY 主键会让 INSERT 立刻打到库里、抢在同批 UPDATE 之前），
        // 而是攒起来等 flush 之后统一 saveAll
        verify(chunkRepository, never()).save(any());
        ArgumentCaptor<List<ContentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ContentChunk::getChunkIndex).containsExactly(0, 1);
        assertThat(captor.getValue()).extracting(ContentChunk::getChunkText).containsExactly("段落一", "段落二");
    }

    @Test
    void reindexNote_deletesTrailingRowsWhenChunkCountDecreases() throws Exception {
        Note note = Note.builder().id(12L).owner(user).contentType("markdown").content("只剩一段").build();
        when(noteRepository.findById(12L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("只剩一段"));

        ContentChunk chunk0 = ContentChunk.builder().id(1L).owner(user).sourceType(ContentChunk.SourceType.NOTE)
                .sourceId(12L).chunkIndex(0).chunkText("只剩一段").contentHash(sha256("只剩一段")).embeddingJson("[0.1]").build();
        ContentChunk chunk1 = ContentChunk.builder().id(2L).owner(user).sourceType(ContentChunk.SourceType.NOTE)
                .sourceId(12L).chunkIndex(1).chunkText("被删掉的第二段").contentHash("oldhash").embeddingJson("[0.2]").build();
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 12L))
                .thenReturn(List.of(chunk0, chunk1));

        service.reindexNote(12L);

        verify(chunkRepository).deleteAll(List.of(chunk1));
    }

    @Test
    void reindexNote_doesNothingWhenNoteAlreadyDeleted() throws Exception {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        service.reindexNote(99L);

        verifyNoInteractions(chunkingService, embeddingService);
        verify(chunkRepository, never()).save(any());
    }

    @Test
    void reindexNote_replacesImageRefsWithCurrentlyReferencedHashes() throws Exception {
        Note note = Note.builder().id(15L).owner(user).contentType("richtext")
                .content("<img src=\"/uploads/a.png\">").build();
        when(noteRepository.findById(15L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("正文"));
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 15L))
                .thenReturn(List.of());
        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});
        when(chunkingService.extractImageUrls(note)).thenReturn(List.of("/uploads/a.png"));
        when(extractionService.resolveContentHashForImageUrl("/uploads/a.png")).thenReturn("hashA");

        service.reindexNote(15L);

        verify(noteImageRefRepository).deleteByNote(note);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteImageRef>> captor = ArgumentCaptor.forClass(List.class);
        verify(noteImageRefRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(NoteImageRef::getContentHash).containsExactly("hashA");
        assertThat(captor.getValue()).extracting(NoteImageRef::getNote).containsExactly(note);
    }

    @Test
    void reindexNote_doesNotSaveImageRefsWhenNoteReferencesNoImages() throws Exception {
        Note note = Note.builder().id(16L).owner(user).contentType("markdown").content("纯文字笔记").build();
        when(noteRepository.findById(16L)).thenReturn(Optional.of(note));
        when(chunkingService.chunkNote(note)).thenReturn(List.of("纯文字笔记"));
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.NOTE, 16L))
                .thenReturn(List.of());
        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});
        when(chunkingService.extractImageUrls(note)).thenReturn(List.of());

        service.reindexNote(16L);

        verify(noteImageRefRepository).deleteByNote(note);
        verify(noteImageRefRepository, never()).saveAll(any());
    }

    @Test
    void deleteChunksFor_delegatesToRepository() {
        service.deleteChunksFor(ContentChunk.SourceType.CLIP, 5L);

        verify(chunkRepository).deleteBySourceTypeAndSourceId(ContentChunk.SourceType.CLIP, 5L);
    }

    @Test
    void reindexClip_skipsEmbeddingWhenChunkHashUnchanged() throws Exception {
        SourceClip clip = SourceClip.builder().id(20L).owner(user).contentFormat("html").content("<p>同样的内容</p>").build();
        when(clipRepository.findById(20L)).thenReturn(Optional.of(clip));
        when(chunkingService.chunkClip(clip)).thenReturn(List.of("同样的内容"));

        String existingHash = sha256("同样的内容");
        ContentChunk existingChunk = ContentChunk.builder()
                .id(200L).owner(user).sourceType(ContentChunk.SourceType.CLIP).sourceId(20L)
                .chunkIndex(0).chunkText("同样的内容").contentHash(existingHash)
                .embeddingJson("[0.3,0.4]").build();
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.CLIP, 20L))
                .thenReturn(List.of(existingChunk));

        service.reindexClip(20L);

        verify(embeddingService, never()).embed(anyString());
        verify(chunkRepository, never()).save(any());
    }

    @Test
    void reindexClip_doesNothingWhenClipAlreadyDeleted() throws Exception {
        when(clipRepository.findById(98L)).thenReturn(Optional.empty());

        service.reindexClip(98L);

        verifyNoInteractions(chunkingService, embeddingService);
        verify(chunkRepository, never()).save(any());
    }

    @Test
    void reindexLocalMediaExtraction_indexesUnderLocalMediaSourceTypeUsingMatchingMediaFileOwner() throws Exception {
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(5L).contentHash("hash5").status(LocalFileExtraction.Status.SUCCESS)
                .extractedText("图片里的文字").build();
        when(extractionRepository.findById(5L)).thenReturn(Optional.of(extraction));
        LocalMediaFile mediaFile = LocalMediaFile.builder().owner(user).contentHash("hash5").build();
        when(localMediaFileRepository.findFirstByContentHash("hash5")).thenReturn(Optional.of(mediaFile));
        when(chunkingService.chunkPlainText("图片里的文字")).thenReturn(List.of("图片里的文字"));
        when(chunkRepository.findBySourceTypeAndSourceIdOrderByChunkIndexAsc(ContentChunk.SourceType.LOCAL_MEDIA, 5L))
                .thenReturn(List.of());
        when(embeddingService.embed(any())).thenReturn(new float[]{0.1f});

        service.reindexLocalMediaExtraction(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSourceType()).isEqualTo(ContentChunk.SourceType.LOCAL_MEDIA);
        assertThat(captor.getValue().get(0).getOwner()).isEqualTo(user);
    }

    @Test
    void reindexLocalMediaExtraction_skipsWhenNoLocalMediaFileMatchesHash() {
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(6L).contentHash("hash6").status(LocalFileExtraction.Status.SUCCESS)
                .extractedText("文字").build();
        when(extractionRepository.findById(6L)).thenReturn(Optional.of(extraction));
        when(localMediaFileRepository.findFirstByContentHash("hash6")).thenReturn(Optional.empty());

        service.reindexLocalMediaExtraction(6L);

        verify(chunkRepository, never()).saveAll(any());
    }

    @Test
    void reindexLocalMediaExtraction_skipsWhenExtractionNotYetSuccess() {
        LocalFileExtraction extraction = LocalFileExtraction.builder()
                .id(7L).contentHash("hash7").status(LocalFileExtraction.Status.PENDING).build();
        when(extractionRepository.findById(7L)).thenReturn(Optional.of(extraction));

        service.reindexLocalMediaExtraction(7L);

        verify(chunkRepository, never()).saveAll(any());
        verifyNoInteractions(localMediaFileRepository);
    }

    private static String sha256(String text) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
