-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件
-- （mysql/V5__add_note_id_to_clip_search_messages.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 同 mysql/V5 文件头注释：AI 搜索 chatbox 改为按笔记独立对话，需要给 clip_search_messages
-- 加一个可空的 note_id 外键。桌面版用的是持久化的 H2 文件库（非临时内存库），存量安装同样
-- 可能被 ddl-auto:update 抢先建过这一列，所以也要用 IF NOT EXISTS 保持幂等——H2 原生支持
-- ADD COLUMN / ADD CONSTRAINT 的 IF NOT EXISTS 语法，不需要 mysql 那套动态 SQL 判断。
ALTER TABLE clip_search_messages ADD COLUMN IF NOT EXISTS note_id BIGINT;
ALTER TABLE clip_search_messages ADD CONSTRAINT IF NOT EXISTS fk_clip_search_messages_note_id FOREIGN KEY (note_id) REFERENCES notes (id) ON DELETE CASCADE;
