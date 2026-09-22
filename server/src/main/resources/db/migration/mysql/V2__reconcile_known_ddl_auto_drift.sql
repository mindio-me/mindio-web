-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V2__reconcile_known_ddl_auto_drift.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
-- 补齐 ddl-auto:update 曾经静默失败、没能加到已有 tags 表上的两个 NOT NULL 列。
-- 对全新安装（V1 已经建好这两列）和老库（V1 被 baseline 跳过，从未真正建过这两列）都要安全。
--
-- 注意：真实 MySQL（8.0，本项目自建部署目标）不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
-- ——那是 MariaDB 专有扩展，MySQL 上会直接报 SQL 语法错误，导致 Flyway 把这次迁移记为失败，
-- 永久阻断后续所有启动。这里改用 INFORMATION_SCHEMA.COLUMNS 判断列是否已存在，
-- 配合 PREPARE/EXECUTE 动态 SQL 实现等价的幂等 ADD COLUMN：列已存在则执行 SELECT 1（no-op），
-- 列缺失则执行真正的 ADD COLUMN。
SET @stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tags' AND COLUMN_NAME = 'used_by_notes') = 0,
    'ALTER TABLE tags ADD COLUMN used_by_notes BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
  )
);
PREPARE stmt FROM @stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tags' AND COLUMN_NAME = 'used_by_clips') = 0,
    'ALTER TABLE tags ADD COLUMN used_by_clips BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
  )
);
PREPARE stmt FROM @stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
