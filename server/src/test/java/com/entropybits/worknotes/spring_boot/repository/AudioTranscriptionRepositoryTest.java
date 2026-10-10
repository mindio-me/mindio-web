/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 注解组合沿用 AiChatConversationRepositoryTest（create-drop + H2，不走 Flyway 链路）。 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class AudioTranscriptionRepositoryTest {

    @Autowired private AudioTranscriptionRepository repository;

    @Test
    void savesAndFindsByNoteAndBlock() {
        AudioTranscription saved = repository.save(AudioTranscription.builder()
                .noteId(1L).blockId("blk1")
                .status(AudioTranscription.Status.PENDING)
                .triggerType(AudioTranscription.TriggerType.AUTO)
                .stage(AudioTranscription.Stage.FULL)
                .utterances("[{\"start\":0,\"end\":1,\"text\":\"" + "长".repeat(70000) + "\"}]")
                .build());

        assertThat(repository.findByNoteIdAndBlockId(1L, "blk1")).get()
                .extracting(AudioTranscription::getId).isEqualTo(saved.getId());
        assertThat(repository.findTop10ByStatusOrderByCreatedAtAsc(AudioTranscription.Status.PENDING)).hasSize(1);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();

        repository.deleteByNoteId(1L);
        assertThat(repository.findByNoteId(1L)).isEmpty();
    }
}
