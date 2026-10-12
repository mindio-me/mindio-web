-- spring-boot/src/main/resources/db/migration/mysql/V25__add_note_uid.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 笔记的跨安装身份，供 .mindio 包导出/导入识别"同一篇笔记"。按需分配，不回填已有笔记。
-- 多用户服务器上同一个包可能被不同用户导入，所以唯一性按 (owner_id, uid)。
-- 详见 docs/superpowers/specs/2026-10-12-editorjs-note-export-import-design.md。
--
-- 幂等：dev 环境 ddl-auto:update 可能已在本地库上建过 uid 列和 uk_notes_owner_uid，之后
-- 本迁移在该库（或任何 baseline-on-migrate 的存量库）上跑会因 duplicate column / duplicate key 失败。
-- 真实 MySQL 不支持 IF NOT EXISTS 形式的 ADD COLUMN，照 V10 用 INFORMATION_SCHEMA + PREPARE/EXECUTE。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notes' AND COLUMN_NAME = 'uid') = 0,
    'ALTER TABLE notes ADD COLUMN uid VARCHAR(36) NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;

SET @uk_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notes' AND INDEX_NAME = 'uk_notes_owner_uid') = 0,
    'ALTER TABLE notes ADD CONSTRAINT uk_notes_owner_uid UNIQUE (owner_id, uid)',
    'SELECT 1'
  )
);
PREPARE uk_stmt FROM @uk_stmt;
EXECUTE uk_stmt;
DEALLOCATE PREPARE uk_stmt;
