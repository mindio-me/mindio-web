/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.ClipTagLink;
import com.entropybits.worknotes.spring_boot.entity.SourceClip;
import com.entropybits.worknotes.spring_boot.entity.Tag;
import com.entropybits.worknotes.spring_boot.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** filterForAgent 是手写 JPQL，mock 掉 Repository 的 Service 单测验证不到查询本身，这里在 H2 上真跑一遍。 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class SourceClipRepositoryTest {

    @Autowired private SourceClipRepository clipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private ClipTagLinkRepository clipTagLinkRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder().username("alice").password("x").role("USER").build());
    }

    private SourceClip clip(User owner, String title, String url, LocalDateTime bookmarkedAt) {
        return clipRepository.save(SourceClip.builder().sourceType(SourceClip.SourceType.WEBPAGE)
                .title(title).sourceUrl(url).originalBookmarkedAt(bookmarkedAt).owner(owner).build());
    }

    private Page<SourceClip> byHost(String host, String keyword, String tag) {
        return clipRepository.filterForAgent(alice,
                host == null ? null : "%://" + host + "/%", host == null ? null : "%://" + host,
                host == null ? null : "%://%." + host + "/%", host == null ? null : "%://%." + host,
                keyword, tag, PageRequest.of(0, 10));
    }

    @Test
    void filterByHost_matchesDomainAndSubdomainsButNotLookalikesOrOtherOwners() {
        User bob = userRepository.save(User.builder().username("bob").password("x").role("USER").build());
        clip(alice, "core", "https://github.com/vuejs/core", LocalDateTime.of(2020, 1, 1, 0, 0));
        clip(alice, "gist", "https://gist.github.com/abc", LocalDateTime.of(2021, 1, 1, 0, 0));
        clip(alice, "root", "https://github.com", LocalDateTime.of(2019, 1, 1, 0, 0));
        clip(alice, "lookalike", "https://notgithub.com/x", null);
        clip(alice, "other", "https://gitee.com/x", null);
        clip(bob, "bob's", "https://github.com/bob/repo", null);

        Page<SourceClip> page = byHost("github.com", null, null);

        assertThat(page.getTotalElements()).isEqualTo(3);
        // 按收藏时间（书签原始时间优先）倒序
        assertThat(page.getContent()).extracting(SourceClip::getTitle).containsExactly("gist", "core", "root");
    }

    @Test
    void filterByTagAndKeyword_ignoresRemovedTagLinksAndMatchesUrlText() {
        Tag openSource = tagRepository.save(Tag.builder().name("开源").owner(alice).build());
        SourceClip tagged = clip(alice, "Vue", "https://github.com/vuejs/core", null);
        SourceClip removed = clip(alice, "React", "https://github.com/facebook/react", null);
        clipTagLinkRepository.save(ClipTagLink.builder().clip(tagged).tag(openSource).manuallyAdded(true).build());
        clipTagLinkRepository.save(ClipTagLink.builder().clip(removed).tag(openSource).build());

        assertThat(byHost(null, null, "开源").getContent()).extracting(SourceClip::getTitle).containsExactly("Vue");
        assertThat(byHost(null, "facebook", null).getContent()).extracting(SourceClip::getTitle).containsExactly("React");
    }
}
