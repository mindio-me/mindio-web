-- spring-boot/src/main/resources/db/migration/mysql/V10__add_note_id_to_ai_chat_messages.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 给 ai_chat_messages 加一个可空的 note_id 列。dev 环境 ddl-auto:update 可能已经在某台
-- 开发机的本地库上抢先建过这一列，之后本迁移在同机（或任何 baseline-on-migrate 的存量库）
-- 上跑会因 duplicate column 失败。真实 MySQL（5.7/8.0，本项目自建部署目标）不支持
-- `ADD COLUMN IF NOT EXISTS`（MariaDB 专有），用 INFORMATION_SCHEMA 判断 + PREPARE/EXECUTE
-- 实现等价的幂等 ADD COLUMN（照 V5/V8/V12 已验证的写法）。h2/V10 用原生 IF NOT EXISTS。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages' AND COLUMN_NAME = 'note_id') = 0,
    'ALTER TABLE ai_chat_messages ADD COLUMN note_id BIGINT NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;
