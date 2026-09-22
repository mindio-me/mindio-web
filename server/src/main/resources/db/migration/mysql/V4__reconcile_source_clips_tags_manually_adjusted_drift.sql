-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件
-- （h2/V4__reconcile_source_clips_tags_manually_adjusted_drift.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 背景同 h2/V4 文件头注释：SourceClip.tagsManuallyAdjusted 字段可能在存量库上从未被
-- ddl-auto:update 真正建出来，baseline-on-migrate 之后也不会被 V1 补上。
--
-- 注意：真实 MySQL（8.0，本项目自建部署目标）不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
-- ——那是 MariaDB 专有扩展，MySQL 上会直接报 SQL 语法错误，导致 Flyway 把这次迁移记为失败，
-- 永久阻断后续所有启动。这里沿用 V2 已验证过的方案：用 INFORMATION_SCHEMA.COLUMNS 判断列是否
-- 已存在，配合 PREPARE/EXECUTE 动态 SQL 实现等价的幂等 ADD COLUMN：列已存在则执行 SELECT 1
-- （no-op），列缺失则执行真正的 ADD COLUMN。
SET @stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'source_clips' AND COLUMN_NAME = 'tags_manually_adjusted') = 0,
    'ALTER TABLE source_clips ADD COLUMN tags_manually_adjusted BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
  )
);
PREPARE stmt FROM @stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
