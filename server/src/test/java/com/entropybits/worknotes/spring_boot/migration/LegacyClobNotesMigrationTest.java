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
 * 复现 V3（Fix 1 / C1）真正要修的存量场景：数据库在旧 {@code H2LongTextFixConfig}
 * （desktop CommandLineRunner，已删除）跑过之后，notes.content / notes.summary 已经是真正的
 * CLOB 类型，而不只是"全新安装库连续启动两次"（那种库从来不会经过 CLOB 状态，
 * {@code RepeatedBootFlywayMigrationTest} 对 V3 是否生效其实是空转的，不能作为 V3 的回归保护）。
 *
 * <p>这里手动模拟旧 runner 已经跑过的效果（ALTER 成 CLOB 并插入含多字节内容的一行），
 * 再走真正的 Flyway baseline-on-migrate 流程，断言：应用能启动、V3 把列改回
 * varchar、且原有数据一字不差地保留。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class LegacyClobNotesMigrationTest {

    @TempDir
    static Path tempDir;

    private static final String UNICODE_CONTENT = "legacy CLOB content with unicode 你好世界";
    private static final String UNICODE_SUMMARY = "legacy CLOB summary 摘要";

    private static String jdbcUrl(Path dir) {
        return "jdbc:h2:file:" + dir.resolve("legacy-clob-notes")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl(tempDir));
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    @BeforeAll
    static void seedLegacyClobSchemaBeforeSpringBoots() throws Exception {
        String url = jdbcUrl(tempDir);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (InputStream schemaStream = LegacyClobNotesMigrationTest.class
                    .getResourceAsStream("/db/migration/h2/V1__init_schema.sql")) {
                RunScript.execute(connection, new InputStreamReader(schemaStream, StandardCharsets.UTF_8));
            }
            try (Statement statement = connection.createStatement()) {
                // 模拟旧 H2LongTextFixConfig 在此前某次启动后留下的效果：这两列此刻是真正的 CLOB。
                statement.execute("ALTER TABLE notes ALTER COLUMN content CLOB");
                statement.execute("ALTER TABLE notes ALTER COLUMN summary CLOB");
                statement.execute(
                        "INSERT INTO users (username, password, role, created_at) "
                                + "VALUES ('legacy-admin', 'hash', 'ADMIN', CURRENT_TIMESTAMP)");
                statement.execute(
                        "INSERT INTO notes (title, content, content_type, owner_id, is_public, "
                                + "view_count, summary, created_at, modified_at) VALUES ("
                                + "'legacy note', '" + UNICODE_CONTENT + "', 'richtext', 1, false, "
                                + "0, '" + UNICODE_SUMMARY + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacyClobColumnsAreRevertedToVarcharWithoutLosingData() {
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM notes WHERE title = 'legacy note'", String.class);
        String summary = jdbcTemplate.queryForObject(
                "SELECT summary FROM notes WHERE title = 'legacy note'", String.class);

        assertThat(content).isEqualTo(UNICODE_CONTENT);
        assertThat(summary).isEqualTo(UNICODE_SUMMARY);
    }

    @Test
    void notesColumnsAreNoLongerClobAfterV3() {
        String contentType = jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'NOTES' AND COLUMN_NAME = 'CONTENT'", String.class);
        String summaryType = jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'NOTES' AND COLUMN_NAME = 'SUMMARY'", String.class);

        assertThat(contentType).isNotEqualToIgnoringCase("CHARACTER LARGE OBJECT");
        assertThat(summaryType).isNotEqualToIgnoringCase("CHARACTER LARGE OBJECT");
    }

    @Test
    void v3RanAndWasNotSkipped() {
        String v3Type = jdbcTemplate.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '3'", String.class);

        assertThat(v3Type).isEqualTo("SQL");
    }
}
