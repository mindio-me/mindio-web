/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.AiChatConversation;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 这是仓库里第一个 repository 层测试，仓库其它测试要么是mock Repository的Service单测，
 * 要么是走完整Flyway链路的迁移测试（{@code FreshInstallFlywayMigrationTest} 那一批）。
 * 这里只验证JPA查询/排序逻辑本身，不需要真实迁移链路——@DataJpaTest默认会自动配置一个
 * 内嵌H2 + create-drop建表，显式声明ddl-auto覆盖掉主配置里各profile的设置，避免这个
 * 测试的行为随"当前哪个profile是默认"漂移。
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class AiChatConversationRepositoryTest {

    @Autowired private AiChatConversationRepository conversationRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void findByOwnerOrderByLastMessageAtDesc_returnsNewestFirstAndOnlyOwnersOwn() {
        User alice = userRepository.save(User.builder().username("alice").password("x").role("USER").build());
        User bob = userRepository.save(User.builder().username("bob").password("x").role("USER").build());

        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
        conversationRepository.save(AiChatConversation.builder().owner(alice).title("旧会话")
                .createdAt(t1).lastMessageAt(t1).build());
        conversationRepository.save(AiChatConversation.builder().owner(alice).title("新会话")
                .createdAt(t2).lastMessageAt(t2).build());
        conversationRepository.save(AiChatConversation.builder().owner(bob).title("bob的会话")
                .createdAt(t2).lastMessageAt(t2).build());

        List<AiChatConversation> result = conversationRepository.findByOwnerOrderByLastMessageAtDesc(alice);

        assertThat(result).extracting(AiChatConversation::getTitle).containsExactly("新会话", "旧会话");
    }
}
