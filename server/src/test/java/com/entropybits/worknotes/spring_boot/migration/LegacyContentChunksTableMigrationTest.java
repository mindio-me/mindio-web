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
 * 复现"ddl-auto:update 抢先建出 content_chunks 表"的存量场景：本地开发库在 V6 迁移文件
 * 出现之前，Hibernate 已经因为实体类存在而自动建出了这张表。V6 用 CREATE TABLE IF NOT EXISTS，
 * 再跑一次应该是 no-op，不应该报 "table already exists" 而启动失败。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("desktop")
class LegacyContentChunksTableMigrationTest {

    @TempDir
    static Path tempDir;

    private static String jdbcUrl(Path dir) {
        return "jdbc:h2:file:" + dir.resolve("legacy-content-chunks")
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
            try (InputStream schemaStream = LegacyContentChunksTableMigrationTest.class
                    .getResourceAsStream("/db/migration/h2/V1__init_schema.sql")) {
                RunScript.execute(connection, new InputStreamReader(schemaStream, StandardCharsets.UTF_8));
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO users (username, password, role, created_at) "
                                + "VALUES ('legacy-admin', 'hash', 'ADMIN', CURRENT_TIMESTAMP)");
                // 模拟 ddl-auto:update 在 V6 迁移文件出现之前，已经悄悄建出了这张表
                statement.execute(
                        "CREATE TABLE content_chunks ("
                                + "id BIGINT NOT NULL AUTO_INCREMENT, owner_id BIGINT NOT NULL, "
                                + "source_type VARCHAR(20) NOT NULL, source_id BIGINT NOT NULL, "
                                + "chunk_index INT NOT NULL, chunk_text TEXT NOT NULL, "
                                + "content_hash VARCHAR(64) NOT NULL, embedding_json TEXT NOT NULL, "
                                + "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, "
                                + "PRIMARY KEY (id))");
                statement.execute(
                        "INSERT INTO content_chunks (owner_id, source_type, source_id, chunk_index, "
                                + "chunk_text, content_hash, embedding_json, created_at, updated_at) VALUES ("
                                + "1, 'NOTE', 1, 0, 'legacy chunk', 'abc123', '[0.1,0.2]', "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legacyContentChunksRowSurvivesAppStartup() {
        String chunkText = jdbcTemplate.queryForObject(
                "SELECT chunk_text FROM content_chunks WHERE source_id = 1", String.class);

        assertThat(chunkText).isEqualTo("legacy chunk");
    }

    @Test
    void v6RanAndWasNotSkipped() {
        String v6Type = jdbcTemplate.queryForObject(
                "SELECT type FROM flyway_schema_history WHERE version = '6'", String.class);

        assertThat(v6Type).isEqualTo("SQL");
    }
}
