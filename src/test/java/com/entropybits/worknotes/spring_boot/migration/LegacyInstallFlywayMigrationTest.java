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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class LegacyInstallFlywayMigrationTest {

    @TempDir
    static Path tempDir;

    private static String jdbcUrl(Path dir) {
        return "jdbc:h2:file:" + dir.resolve("legacy-install")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl(tempDir));
        registry.add("worknotes.desktop.license.enforcement-enabled", () -> "false");
    }

    @BeforeAll
    static void seedLegacySchemaBeforeSpringBoots() throws Exception {
        // 必须先建出完整的 39 张表(用 h2/V1__init_schema.sql),再手动删掉 tags 的那两列,
        // 而不是只建 users/tags 两张表——ddl-auto:validate 现在会检查每一张实体对应的表
        // 是否存在,只有 2 张表的库在 baseline 之后会因为"缺 37 张表"报错,这不是真实场景。
        // 真实的存量库是长期用 ddl-auto:update 跑出来的,其他表早就有了,只有 tags 这两列
        // 因为"给已有数据的表加 NOT NULL 列"这个模式失败过。
        String url = jdbcUrl(tempDir);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (InputStream schemaStream = LegacyInstallFlywayMigrationTest.class
                    .getResourceAsStream("/db/migration/h2/V1__init_schema.sql")) {
                RunScript.execute(connection, new InputStreamReader(schemaStream, StandardCharsets.UTF_8));
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE tags DROP COLUMN used_by_notes");
                statement.execute("ALTER TABLE tags DROP COLUMN used_by_clips");
                statement.execute(
                        "INSERT INTO users (username, password, role, created_at) "
                                + "VALUES ('legacy-admin', 'hash', 'ADMIN', CURRENT_TIMESTAMP)");
                statement.execute(
                        "INSERT INTO tags (name, owner_id) VALUES ('legacy-tag', 1)");
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacyInstallGetsBaselinedAndReconciledWithoutLosingData() {
        String tagName = jdbcTemplate.queryForObject(
                "SELECT name FROM tags WHERE owner_id = 1", String.class);
        assertThat(tagName).isEqualTo("legacy-tag");

        Boolean usedByNotes = jdbcTemplate.queryForObject(
                "SELECT used_by_notes FROM tags WHERE owner_id = 1", Boolean.class);
        Boolean usedByClips = jdbcTemplate.queryForObject(
                "SELECT used_by_clips FROM tags WHERE owner_id = 1", Boolean.class);

        assertThat(usedByNotes).isFalse();
        assertThat(usedByClips).isFalse();
    }

    @Test
    void v1WasBaselinedNotReExecuted() {
        String v1Type = jdbcTemplate.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '1'", String.class);

        assertThat(v1Type).isEqualTo("BASELINE");
    }
}
