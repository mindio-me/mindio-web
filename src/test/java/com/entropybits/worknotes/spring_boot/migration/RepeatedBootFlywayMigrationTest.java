/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import com.entropybits.worknotes.spring_boot.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 覆盖"全新安装库连续启动两次"这一种场景：两次都必须成功。
 *
 * <p>注意：全新安装库从 V1 开始 notes.content / notes.summary 就是 varchar，从未经过旧
 * {@code H2LongTextFixConfig}（desktop-only CommandLineRunner，已删除）改出来的 CLOB 状态，
 * 所以这个测试本身并不能验证 V3 迁移（把 CLOB 改回 varchar）是否生效——即使删掉 V3 文件，
 * 这个测试依然会通过。真正复现 C1 报告的故障（存量库的 notes 列已经是 CLOB，与
 * {@code ddl-auto: validate} 冲突导致启动失败）、并验证 V3 修复了它的测试在
 * {@link LegacyClobNotesMigrationTest} 里。这两个测试合起来才完整覆盖 C1。
 */
class RepeatedBootFlywayMigrationTest {

    @Test
    void desktopProfileBootsSuccessfullyTwiceInARowAgainstTheSameDatabaseFile() throws IOException {
        Path tempDir = Files.createTempDirectory("repeated-boot-flyway-test");
        Path dbPath = tempDir.resolve("mindio-app");
        String jdbcUrl = "jdbc:h2:file:" + dbPath
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

        try {
            ConfigurableApplicationContext firstBoot = bootDesktopProfile(jdbcUrl);
            try {
                Integer notesTableExists = firstBoot.getBean(JdbcTemplate.class).queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'NOTES'",
                        Integer.class);
                assertThat(notesTableExists).isEqualTo(1);
            } finally {
                firstBoot.close();
            }

            assertThatCode(() -> bootDesktopProfile(jdbcUrl).close())
                    .doesNotThrowAnyException();
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private ConfigurableApplicationContext bootDesktopProfile(String jdbcUrl) {
        // Must be passed as command-line-style args (highest-precedence property source),
        // not via .properties(...) (lowest-precedence "defaultProperties") — otherwise
        // application-desktop.yml's own spring.datasource.url wins and the test silently
        // boots against the real ./desktop-data/mindio-app instead of the isolated temp dir.
        return new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .profiles("desktop")
                .run(
                        "--spring.datasource.url=" + jdbcUrl,
                        "--worknotes.desktop.license.enforcement-enabled=false");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
