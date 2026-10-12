/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.repository;

import com.entropybits.worknotes.spring_boot.entity.Note;
import com.entropybits.worknotes.spring_boot.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** uid 的条件写入和 modifiedAt 覆盖是手写 JPQL 批量更新，绕开了 @PreUpdate，必须在真库上验证。 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
class NoteUidRepositoryTest {

    @Autowired private NoteRepository noteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private User alice;
    private User bob;
    private final LocalDateTime old = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder().username("alice").password("x").role("USER").build());
        bob = userRepository.save(User.builder().username("bob").password("x").role("USER").build());
    }

    private Note note(User owner, String uid) {
        return noteRepository.saveAndFlush(Note.builder().title("t").contentType("editorjs").owner(owner)
                .uid(uid).createdAt(old).modifiedAt(old).build());
    }

    @Test
    void assignsUidOnlyWhenAbsentAndKeepsModifiedAt() {
        Note n = note(alice, null);
        em.clear();

        assertThat(noteRepository.assignUidIfAbsent(n.getId(), "11111111-1111-1111-1111-111111111111")).isEqualTo(1);
        assertThat(noteRepository.assignUidIfAbsent(n.getId(), "22222222-2222-2222-2222-222222222222")).isZero();
        em.clear();

        assertThat(noteRepository.findUidById(n.getId())).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(noteRepository.findById(n.getId()).orElseThrow().getModifiedAt()).isEqualTo(old);
    }

    @Test
    void findsByOwnerAndUidOnly() {
        note(alice, "aaaaaaaa-0000-0000-0000-000000000001");
        note(bob, "aaaaaaaa-0000-0000-0000-000000000001");

        List<Note> found = noteRepository.findByOwnerAndUidIn(alice, List.of("aaaaaaaa-0000-0000-0000-000000000001"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getOwner().getId()).isEqualTo(alice.getId());
    }

    @Test
    void sameUidTwiceForOneOwnerIsRejectedButNullsAreFine() {
        note(alice, null);
        note(alice, null);
        note(alice, "aaaaaaaa-0000-0000-0000-000000000002");
        assertThatThrownBy(() -> note(alice, "aaaaaaaa-0000-0000-0000-000000000002"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void overwriteModifiedAtBypassesPreUpdate() {
        Note n = note(alice, null);
        LocalDateTime target = LocalDateTime.of(2025, 5, 6, 7, 8, 9);
        em.clear();

        assertThat(noteRepository.overwriteModifiedAt(n.getId(), target)).isEqualTo(1);
        em.clear();

        assertThat(noteRepository.findById(n.getId()).orElseThrow().getModifiedAt()).isEqualTo(target);
    }

    @Test
    void listsOnlyOwnersEditorjsNotes() {
        note(alice, null);
        noteRepository.saveAndFlush(Note.builder().title("md").contentType("markdown").owner(alice).build());
        note(bob, null);

        assertThat(noteRepository.findByOwnerAndContentTypeOrderByIdAsc(alice, "editorjs")).hasSize(1);
    }
}
