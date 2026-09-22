/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.h2.tools.RunScript;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 复现 V4 要修的存量场景：source_clips.tags_manually_adjusted 在 SourceClip 实体里加入的时间
 * （00bfb65，2026-08-14）早于 V1 基线脚本生成（ff1819c，2026-08-20），但存量库在
 * baseline-on-migrate 之后从未真正跑过 V1 的 CREATE TABLE，这列就永远不会被建出来——
 * 和 {@link LegacyInstallFlywayMigrationTest} 修复 tags.used_by_notes/used_by_clips
 * 是同一类事故，只是发生在另一张表的另一列上。
 *
 * <p>这里手动建出完整 33 张表（用 h2/V1__init_schema.sql）后删掉这一列，模拟真实存量库的样子，
 * 再走真正的 Flyway baseline-on-migrate 流程，断言：应用能启动、V4 把列补回来、且原有数据
 * 一字不差地保留。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class LegacySourceClipsTagsAdjustedMigrationTest {

    @TempDir
    static Path tempDir;

    private static String jdbcUrl(Path dir) {
        return "jdbc:h2:file:" + dir.resolve("legacy-source-clips")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl(tempDir));
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    @BeforeAll
    static void seedLegacySchemaBeforeSpringBoots() throws Exception {
        String url = jdbcUrl(tempDir);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (InputStream schemaStream = LegacySourceClipsTagsAdjustedMigrationTest.class
                    .getResourceAsStream("/db/migration/h2/V1__init_schema.sql")) {
                RunScript.execute(connection, new InputStreamReader(schemaStream, StandardCharsets.UTF_8));
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO users (username, password, role, created_at) "
                                + "VALUES ('legacy-admin', 'hash', 'ADMIN', CURRENT_TIMESTAMP)");
                statement.execute(
                        "INSERT INTO source_clips (owner_id, title, extraction_mode, source_type, "
                                + "manually_confirmed_alive, tags_manually_adjusted, was_detected_dead_link, "
                                + "created_at, modified_at) VALUES ("
                                + "1, 'legacy clip', 'FULL', 'WEBPAGE', false, true, false, "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                // 模拟存量库从未真正建过这一列（baseline-on-migrate 跳过了 V1 的 CREATE TABLE）。
                statement.execute("ALTER TABLE source_clips DROP COLUMN tags_manually_adjusted");
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacySourceClipsRowSurvivesWithColumnBackfilled() {
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM source_clips WHERE owner_id = 1", String.class);
        Boolean tagsManuallyAdjusted = jdbcTemplate.queryForObject(
                "SELECT tags_manually_adjusted FROM source_clips WHERE owner_id = 1", Boolean.class);

        assertThat(title).isEqualTo("legacy clip");
        assertThat(tagsManuallyAdjusted).isFalse();
    }

    @Test
    void v4RanAndWasNotSkipped() {
        String v4Type = jdbcTemplate.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '4'", String.class);

        assertThat(v4Type).isEqualTo("SQL");
    }
}
