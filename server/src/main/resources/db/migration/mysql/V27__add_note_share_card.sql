-- spring-boot/src/main/resources/db/migration/mysql/V27__add_note_share_card.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 笔记分享卡片（网站卡片）：卡片图 URL、背景图 URL、背景来源（share_card_source，纯字符串）、
-- 渐变序号、AI 生图提示词、卡片标题、是否叠加遮罩。
-- 详见 docs/superpowers/specs/2026-10-20-note-share-card-design.md。
--
-- 幂等：dev 环境 ddl-auto:update 可能已建过这几列；真实 MySQL 不支持 ADD COLUMN IF NOT EXISTS，
-- 照 V26 用 INFORMATION_SCHEMA + PREPARE/EXECUTE。七列总是一起出现，以 share_card_source 列为准判断。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notes' AND COLUMN_NAME = 'share_card_source') = 0,
    'ALTER TABLE notes ADD COLUMN share_card_image_url VARCHAR(500) NULL, ADD COLUMN share_card_bg_url VARCHAR(500) NULL, ADD COLUMN share_card_source VARCHAR(10) NULL, ADD COLUMN share_card_gradient INT NULL, ADD COLUMN share_card_ai_prompt TEXT NULL, ADD COLUMN share_card_title VARCHAR(200) NULL, ADD COLUMN share_card_overlay BOOLEAN NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;
