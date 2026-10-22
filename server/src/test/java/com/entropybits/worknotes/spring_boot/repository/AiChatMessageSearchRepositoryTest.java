/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.AiChatMessage;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 聊天记录搜索（AiChatMessageRepository#searchByOwner）的 JPQL：大小写、归属、通配符转义、排序 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class AiChatMessageSearchRepositoryTest {

    @Autowired private AiChatConversationRepository conversationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AiChatMessageRepository chatMessageRepository;

    private AiChatMessage message(AiChatConversation c, String content, String createdAt) {
        return chatMessageRepository.save(AiChatMessage.builder().conversation(c)
                .role(AiChatMessage.Role.USER).content(content).createdAt(Instant.parse(createdAt)).build());
    }

    private List<String> search(User owner, String pattern) {
        return chatMessageRepository.searchByOwner(owner, pattern, PageRequest.of(0, 50))
                .stream().map(AiChatMessage::getContent).toList();
    }

    @Test
    void searchesOnlyOwnMessagesCaseInsensitivelyNewestFirst() {
        User alice = userRepository.save(User.builder().username("search-alice").password("x").role("USER").build());
        User bob = userRepository.save(User.builder().username("search-bob").password("x").role("USER").build());
        AiChatConversation a = conversationRepository.save(AiChatConversation.builder().owner(alice).build());
        AiChatConversation b = conversationRepository.save(AiChatConversation.builder().owner(bob).build());
        message(a, "older Docker note", "2026-10-01T00:00:00Z");
        message(a, "newer DOCKER compose", "2026-10-02T00:00:00Z");
        message(a, "unrelated", "2026-10-03T00:00:00Z");
        message(b, "bob's docker", "2026-10-04T00:00:00Z");

        assertThat(search(alice, "%docker%")).containsExactly("newer DOCKER compose", "older Docker note");
    }

    @Test
    void escapedWildcardsMatchLiterally() {
        User alice = userRepository.save(User.builder().username("search-escape").password("x").role("USER").build());
        AiChatConversation a = conversationRepository.save(AiChatConversation.builder().owner(alice).build());
        message(a, "完成率 100% 了", "2026-10-01T00:00:00Z");
        message(a, "完成率 1000 了", "2026-10-02T00:00:00Z");
        message(a, "snake_case", "2026-10-03T00:00:00Z");
        message(a, "snakeXcase", "2026-10-04T00:00:00Z");

        assertThat(search(alice, "%100!%%")).containsExactly("完成率 100% 了");
        assertThat(search(alice, "%e!_c%")).containsExactly("snake_case");
    }
}
