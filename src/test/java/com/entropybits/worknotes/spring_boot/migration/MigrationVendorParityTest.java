/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * db/migration/h2/ 和 db/migration/mysql/ 是两份并行维护、内容近似的 vendor 专属迁移文件
 * （参见两个目录下每个文件开头的兄弟文件说明）。这个测试把"给一个 vendor 加了新版本号的
 * 迁移文件，却忘了给另一个 vendor 也加"这种疏忽变成一个明确、快速失败的单元测试，而不是
 * 某个 vendor 启动时才出现的、含糊的 Flyway 运行时校验错误。
 *
 * <p>已知的、刻意为之的例外：h2/V3__revert_notes_longtext_clob_widening.sql 只撤销
 * H2LongTextFixConfig（一个只在 desktop/H2 profile 生效的历史遗留 CommandLineRunner，
 * 已删除）留下的问题——MySQL 自建部署从未跑过这个 runner，不受影响，因此故意没有对应的
 * mysql/V3。除了下面这个已登记的例外，两边版本号集合必须完全一致；新增任何其他
 * 单个 vendor 独有的版本号都会让这个测试失败。
 */
class MigrationVendorParityTest {

    private static final Pattern VERSION_PREFIX = Pattern.compile("^(V\\d+)__.*\\.sql$");

    /** 已知且有文档说明的、只属于 h2/ 的版本号（见类注释）。 */
    private static final Set<String> KNOWN_H2_ONLY_VERSIONS = Set.of("V3");

    /** 目前没有已知的、只属于 mysql/ 的版本号；预留位置以保持两边检查方式对称。 */
    private static final Set<String> KNOWN_MYSQL_ONLY_VERSIONS = Set.of();

    @Test
    void h2AndMysqlMigrationFoldersHaveTheSameSetOfVersionsModuloKnownExceptions() throws Exception {
        Set<String> h2Versions = migrationVersions("db/migration/h2");
        Set<String> mysqlVersions = migrationVersions("db/migration/mysql");

        assertThat(h2Versions).isNotEmpty();
        assertThat(mysqlVersions).isNotEmpty();

        Set<String> h2VersionsExcludingKnownExceptions = new TreeSet<>(h2Versions);
        h2VersionsExcludingKnownExceptions.removeAll(KNOWN_H2_ONLY_VERSIONS);

        Set<String> mysqlVersionsExcludingKnownExceptions = new TreeSet<>(mysqlVersions);
        mysqlVersionsExcludingKnownExceptions.removeAll(KNOWN_MYSQL_ONLY_VERSIONS);

        assertThat(h2VersionsExcludingKnownExceptions)
                .as("h2/ and mysql/ versions, after removing documented single-vendor exceptions, must match")
                .isEqualTo(mysqlVersionsExcludingKnownExceptions);
    }

    private static Set<String> migrationVersions(String classpathFolder) throws Exception {
        URL folderUrl = Thread.currentThread().getContextClassLoader().getResource(classpathFolder);
        assertThat(folderUrl).as("classpath folder " + classpathFolder).isNotNull();
        File folder = new File(folderUrl.toURI());
        String[] fileNames = folder.list();
        assertThat(fileNames).as("files under " + classpathFolder).isNotNull();

        Set<String> versions = new TreeSet<>();
        for (String fileName : fileNames) {
            Matcher matcher = VERSION_PREFIX.matcher(fileName);
            if (matcher.matches()) {
                versions.add(matcher.group(1));
            }
        }
        return versions;
    }
}
