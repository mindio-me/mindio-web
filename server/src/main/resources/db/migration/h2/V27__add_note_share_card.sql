-- spring-boot/src/main/resources/db/migration/h2/V27__add_note_share_card.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 笔记分享卡片（网站卡片）：卡片图 URL、背景图 URL、背景来源（share_card_source，纯字符串）、
-- 渐变序号、AI 生图提示词、卡片标题、是否叠加遮罩。
-- 详见 docs/superpowers/specs/2026-10-20-note-share-card-design.md。
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_image_url VARCHAR(500);
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_bg_url VARCHAR(500);
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_source VARCHAR(10);
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_gradient INT;
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_ai_prompt TEXT;
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_title VARCHAR(200);
ALTER TABLE notes ADD COLUMN IF NOT EXISTS share_card_overlay BOOLEAN;
