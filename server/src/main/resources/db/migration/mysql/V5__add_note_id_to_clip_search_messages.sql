-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件
-- （h2/V5__add_note_id_to_clip_search_messages.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- AI 搜索 chatbox 从"收藏夹页面的全局对话"改为"按笔记独立对话"：每篇笔记是一个专题，
-- 搜索历史和保存下来的素材都应该归属到这篇笔记，而不是混在一个全局列表里。
-- clip_search_messages 需要加一个可空的 note_id 外键。
--
-- 按 V4 已验证过的方案处理 ddl-auto:update 抢跑的风险：这一列在实体里加上之后，
-- dev 环境的 ddl-auto:update 可能已经在某台开发机的本地库上悄悄建出来了，
-- 之后这份迁移在同一台机器（或任何 baseline-on-migrate 的存量库）上跑，
-- 如果不做存在性判断会直接报 duplicate column / duplicate key 失败，永久阻断启动。
-- 真实 MySQL（8.0，本项目自建部署目标）不支持 `ADD COLUMN/CONSTRAINT IF NOT EXISTS`，
-- 用 INFORMATION_SCHEMA 判断 + PREPARE/EXECUTE 动态 SQL 实现等价的幂等操作。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clip_search_messages' AND COLUMN_NAME = 'note_id') = 0,
    'ALTER TABLE clip_search_messages ADD COLUMN note_id BIGINT NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;

SET @fk_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'clip_search_messages'
       AND CONSTRAINT_NAME = 'fk_clip_search_messages_note_id') = 0,
    'ALTER TABLE clip_search_messages ADD CONSTRAINT fk_clip_search_messages_note_id FOREIGN KEY (note_id) REFERENCES notes (id) ON DELETE CASCADE',
    'SELECT 1'
  )
);
PREPARE fk_stmt FROM @fk_stmt;
EXECUTE fk_stmt;
DEALLOCATE PREPARE fk_stmt;
