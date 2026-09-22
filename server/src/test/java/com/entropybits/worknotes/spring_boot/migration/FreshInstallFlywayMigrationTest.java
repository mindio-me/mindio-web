/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class FreshInstallFlywayMigrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbPath = tempDir.resolve("fresh-install");
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshInstallAppliesV1AndV2AndTagsHasUsageColumns() {
        Integer usedByNotesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'TAGS' AND column_name = 'USED_BY_NOTES'",
                Integer.class);
        Integer usedByClipsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'TAGS' AND column_name = 'USED_BY_CLIPS'",
                Integer.class);

        assertThat(usedByNotesCount).isEqualTo(1);
        assertThat(usedByClipsCount).isEqualTo(1);
    }

    @Test
    void flywayHistoryShowsBothMigrationsAppliedSuccessfully() {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IN ('1', '2')",
                Integer.class);

        assertThat(appliedCount).isEqualTo(2);
    }

    @Test
    void freshInstallCreatesContentChunksTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name = 'CONTENT_CHUNKS'",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void freshInstallCreatesAiChatMessagesTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_name = 'AI_CHAT_MESSAGES'",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void freshInstallAddsAttachmentsJsonColumnToAiChatMessages() {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'AI_CHAT_MESSAGES' AND column_name = 'ATTACHMENTS_JSON'",
                Integer.class);

        assertThat(columnCount).isEqualTo(1);
    }
}
