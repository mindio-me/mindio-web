-- spring-boot/src/main/resources/db/migration/mysql/V8__add_attachments_to_ai_chat_messages.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 给全局AI助手的消息表加一个可选的附件引用字段（图片/文档的URL+文件名，不含原始数据），
-- 详见 docs/superpowers/specs/2026-09-02-global-chat-multimodal-input-design.md
--
-- 真实 MySQL（8.0，本项目自建部署目标）不支持 `ADD COLUMN IF NOT EXISTS`（V5 已踩过这个坑），
-- 用 INFORMATION_SCHEMA 判断 + PREPARE/EXECUTE 动态 SQL 实现等价的幂等操作。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages' AND COLUMN_NAME = 'attachments_json') = 0,
    'ALTER TABLE ai_chat_messages ADD COLUMN attachments_json TEXT',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;
