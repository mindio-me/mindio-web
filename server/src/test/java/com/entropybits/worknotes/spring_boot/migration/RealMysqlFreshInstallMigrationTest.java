/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 把整条 db/migration/mysql/ 链在一个<b>真实 MySQL</b> 实例上从空库跑一遍——这是
 * {@link MysqlMigrationSyntaxLintTest}（静态检查）之外，唯一能真正证明「自建 MySQL 部署
 * 能起来」的测试。CI / 开发机默认没有 MySQL，所以默认跳过；需要时这样跑：
 *
 * <pre>
 *   # 用一个一次性 MySQL 5.7（Docker）：
 *   docker run --rm -d --name mindio-mig-test -e MYSQL_ROOT_PASSWORD=root \
 *       -e MYSQL_DATABASE=mindio_migtest -p 3307:3306 mysql:5.7
 *
 *   ./mvnw -pl spring-boot test -Dtest=RealMysqlFreshInstallMigrationTest \
 *       -Dmysql.migration.jdbc-url='jdbc:mysql://127.0.0.1:3307/mindio_migtest?useSSL=false&allowPublicKeyRetrieval=true' \
 *       -Dmysql.migration.user=root -Dmysql.migration.password=root
 *
 *   docker rm -f mindio-mig-test
 * </pre>
 *
 * <p><b>目标 schema 会被 {@code flyway.clean()} 清空</b>，只指向一次性库。
 */
@EnabledIfSystemProperty(named = "mysql.migration.jdbc-url", matches = ".+")
class RealMysqlFreshInstallMigrationTest {

    private static final String URL = System.getProperty("mysql.migration.jdbc-url");
    private static final String USER = System.getProperty("mysql.migration.user", "root");
    private static final String PASSWORD = System.getProperty("mysql.migration.password", "");

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(false)
                .cleanDisabled(false)
                .load();
    }

    @Test
    void wholeMysqlChainAppliesCleanlyFromAnEmptyDatabase() throws SQLException {
        Flyway flyway = flyway();
        flyway.clean();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).as("flyway migrate() success").isTrue();
        assertThat(result.migrationsExecuted).as("至少跑了 V1").isGreaterThanOrEqualTo(1);
        assertThat(flyway.info().pending()).as("没有 pending 迁移").isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful)
                .as("Flyway validate 通过").isTrue();
    }

    @Test
    void localDirUniquenessIsEnforcedViaGeneratedHashColumn() throws SQLException {
        Flyway flyway = flyway();
        flyway.clean();
        flyway.migrate();

        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement s = c.createStatement()) {

            // 生成列存在，且唯一约束建在 hash 列上（不是原始的 dir_path 整列）
            assertThat(columnExists(s, "local_doc_directories", "dir_path_hash")).isTrue();
            assertThat(columnExists(s, "local_media_directories", "dir_path_hash")).isTrue();
            assertThat(uniqueIndexColumns(s, "local_doc_directories", "UKg0bmgl8xct5r0s6ykt5lcvs00"))
                    .containsExactlyInAnyOrder("owner_id", "dir_path_hash");

            // 功能校验：同一 owner 下相同 dir_path 的第二条插入必须被唯一约束拦下
            s.executeUpdate("INSERT INTO users (username, password, role, created_at) "
                    + "VALUES ('mig-test-admin', 'hash', 'ADMIN', NOW(6))");
            long ownerId;
            try (ResultSet rs = s.executeQuery("SELECT id FROM users WHERE username = 'mig-test-admin'")) {
                rs.next();
                ownerId = rs.getLong(1);
            }
            String insertDir = "INSERT INTO local_doc_directories "
                    + "(owner_id, dir_path, scan_status, document_count, created_at) "
                    + "VALUES (" + ownerId + ", '/data/very/long/path', 'IDLE', 0, NOW(6))";
            s.executeUpdate(insertDir);
            assertThatThrownBy(() -> s.executeUpdate(insertDir))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static boolean columnExists(Statement s, String table, String column) throws SQLException {
        try (ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                        + "AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private static java.util.List<String> uniqueIndexColumns(Statement s, String table, String indexName)
            throws SQLException {
        java.util.List<String> cols = new java.util.ArrayList<>();
        try (ResultSet rs = s.executeQuery(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                        + "AND index_name = '" + indexName + "' ORDER BY seq_in_index")) {
            while (rs.next()) {
                cols.add(rs.getString(1));
            }
        }
        return cols;
    }
}
