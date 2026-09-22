/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * db/migration/mysql/ 里的迁移脚本必须能在<b>真实 MySQL</b>（5.7 / 8.0，本项目自建部署目标）
 * 上执行。开发环境的 XAMPP「MySQL」其实是 MariaDB，H2 的迁移测试跑在 {@code MODE=MySQL}
 * 兼容模式下——两者都会悄悄接受一批 MariaDB 专有、但真实 MySQL 会直接报语法错误的写法
 * （最典型的是 {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS}）。这类错误只有在 MySQL
 * 上第一次跑 Flyway 时才炸，而且会把迁移记为失败、永久阻断后续所有启动。
 *
 * <p>这个测试把「往 mysql/ 里写了 MariaDB 专有语法」变成一个不需要数据库、秒级失败的静态检查。
 * 幂等操作在 mysql/ 侧的正确写法是 {@code INFORMATION_SCHEMA} 判断 + {@code PREPARE/EXECUTE}
 * 动态 SQL（见 V4 / V5 / V8 / V12）。真正把整条 mysql/ 链在 MySQL 上跑一遍的端到端校验在
 * {@link RealMysqlFreshInstallMigrationTest}（需要一个 MySQL 实例，默认跳过）。
 */
class MysqlMigrationSyntaxLintTest {

    /**
     * 每条规则：一个匹配「MariaDB 专有 / 真实 MySQL 不支持」写法的正则，加一句人类可读的说明。
     * 注意：{@code CREATE TABLE IF NOT EXISTS} 和 {@code DROP TABLE IF EXISTS} 是合法 MySQL，
     * 不在拦截范围内。
     */
    private record Rule(Pattern pattern, String why) {
        Rule(String regex, String why) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), why);
        }
    }

    private static final List<Rule> FORBIDDEN = List.of(
            new Rule("\\bADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS",
                    "ADD COLUMN IF NOT EXISTS 是 MariaDB 扩展；MySQL 用 INFORMATION_SCHEMA + PREPARE/EXECUTE 判断"),
            new Rule("\\bADD\\s+(CONSTRAINT|INDEX|KEY|UNIQUE|FOREIGN\\s+KEY)\\s+IF\\s+NOT\\s+EXISTS",
                    "ADD <约束/索引> IF NOT EXISTS 是 MariaDB 扩展；MySQL 用 INFORMATION_SCHEMA 判断后再 ADD"),
            new Rule("\\bDROP\\s+(COLUMN|CONSTRAINT|INDEX|KEY|FOREIGN\\s+KEY|PRIMARY\\s+KEY)\\s+IF\\s+EXISTS",
                    "DROP <列/约束/索引> IF EXISTS 是 MariaDB 扩展；MySQL 用 INFORMATION_SCHEMA 判断后再 DROP"),
            new Rule("\\b(CHANGE|MODIFY)\\s+(COLUMN\\s+)?IF\\s+EXISTS",
                    "CHANGE/MODIFY ... IF EXISTS 是 MariaDB 扩展"),
            new Rule("\\bALTER\\s+TABLE\\s+IF\\s+EXISTS\\b",
                    "ALTER TABLE IF EXISTS 是 MariaDB 扩展；MySQL 不支持"),
            new Rule("\\bCREATE\\s+(UNIQUE\\s+)?INDEX\\s+IF\\s+NOT\\s+EXISTS",
                    "CREATE INDEX IF NOT EXISTS 是 MariaDB 扩展；MySQL 不支持"),
            new Rule("\\bRENAME\\s+(COLUMN|INDEX|KEY)\\s+IF\\s+EXISTS",
                    "RENAME ... IF EXISTS 是 MariaDB 扩展"));

    @Test
    void mysqlMigrationsUseNoMariaDbOnlySyntax() throws Exception {
        URL folderUrl = Thread.currentThread().getContextClassLoader().getResource("db/migration/mysql");
        assertThat(folderUrl).as("classpath folder db/migration/mysql").isNotNull();
        File folder = new File(folderUrl.toURI());
        File[] sqlFiles = folder.listFiles((dir, name) -> name.endsWith(".sql"));
        assertThat(sqlFiles).as("*.sql under db/migration/mysql").isNotNull().isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (File sqlFile : sqlFiles) {
            String sql = Files.readString(sqlFile.toPath(), StandardCharsets.UTF_8);
            String withoutComments = stripSqlComments(sql);
            for (Rule rule : FORBIDDEN) {
                Matcher matcher = rule.pattern().matcher(withoutComments);
                while (matcher.find()) {
                    violations.add(sqlFile.getName() + ": 命中「" + matcher.group().replaceAll("\\s+", " ")
                            + "」——" + rule.why());
                }
            }
        }

        assertThat(violations)
                .as("db/migration/mysql/ 出现真实 MySQL 不支持的 MariaDB 专有语法")
                .isEmpty();
    }

    /** 去掉 {@code --} 行注释，避免注释里举例说明的坏写法把测试自己绊倒。 */
    private static String stripSqlComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\n", -1)) {
            int dash = line.indexOf("--");
            out.append(dash >= 0 ? line.substring(0, dash) : line).append('\n');
        }
        return out.toString();
    }
}
