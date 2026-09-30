/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V20 把存量的"单一连续会话"消息回填成一条 ai_chat_conversations 记录。用一个独立的
 * Flyway 实例（不经过 SpringBootTest/JPA）先迁到 V19，手工插入旧格式的 ai_chat_messages
 * 行（只有 owner_id，没有 conversation_id——这正是 V20 之前所有真实存量库的样子），
 * 再迁到最新版本，断言回填结果。做法参照 RealMysqlFreshInstallMigrationTest 里
 * Flyway.configure()...load() 的独立实例用法。
 */
class ConversationBackfillMigrationTest {

    @TempDir
    Path tempDir;

    private String jdbcUrl() {
        return "jdbc:h2:file:" + tempDir.resolve("conversation-backfill")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
    }

    private Flyway flywayToVersion(String version) {
        return Flyway.configure()
                .dataSource(jdbcUrl(), "sa", "")
                .locations("classpath:db/migration/h2")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    @Test
    void legacyMessagesAreBackfilledIntoOneConversationPerOwner() throws Exception {
        flywayToVersion("19").migrate();

        long aliceId;
        long bobId;
        try (Connection c = DriverManager.getConnection(jdbcUrl(), "sa", "");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (username, password, role, created_at) "
                    + "VALUES ('alice', 'hash', 'USER', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO users (username, password, role, created_at) "
                    + "VALUES ('bob', 'hash', 'USER', CURRENT_TIMESTAMP)");
            try (ResultSet rs = s.executeQuery("SELECT id FROM users WHERE username = 'alice'")) {
                rs.next();
                aliceId = rs.getLong(1);
            }
            try (ResultSet rs = s.executeQuery("SELECT id FROM users WHERE username = 'bob'")) {
                rs.next();
                bobId = rs.getLong(1);
            }
            s.execute("INSERT INTO ai_chat_messages (owner_id, role, content, created_at) "
                    + "VALUES (" + aliceId + ", 'USER', '第一条', '2026-01-01 10:00:00')");
            s.execute("INSERT INTO ai_chat_messages (owner_id, role, content, created_at) "
                    + "VALUES (" + aliceId + ", 'ASSISTANT', '第一条回复', '2026-01-01 10:00:05')");
            s.execute("INSERT INTO ai_chat_messages (owner_id, role, content, created_at) "
                    + "VALUES (" + bobId + ", 'USER', 'bob的消息', '2026-02-01 09:00:00')");
        }

        flywayToVersion("latest").migrate();

        try (Connection c = DriverManager.getConnection(jdbcUrl(), "sa", "");
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM ai_chat_conversations WHERE owner_id = " + aliceId)) {
                rs.next();
                assertThat(rs.getInt(1)).as("alice恰好一条迁移出来的会话").isEqualTo(1);
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM ai_chat_messages m "
                    + "JOIN ai_chat_conversations c ON m.conversation_id = c.id "
                    + "WHERE c.owner_id = " + aliceId)) {
                rs.next();
                assertThat(rs.getInt(1)).as("alice的2条消息都挂到了同一条会话下").isEqualTo(2);
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM ai_chat_messages m "
                    + "JOIN ai_chat_conversations c ON m.conversation_id = c.id "
                    + "WHERE c.owner_id = " + bobId)) {
                rs.next();
                assertThat(rs.getInt(1)).as("bob的1条消息挂到了他自己的会话下").isEqualTo(1);
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM ai_chat_messages WHERE conversation_id IS NULL")) {
                rs.next();
                assertThat(rs.getInt(1)).as("没有消息漏回填").isEqualTo(0);
            }
        }
    }
}
