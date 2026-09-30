-- spring-boot/src/main/resources/db/migration/mysql/V21__cutover_ai_chat_messages_to_conversations.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- V20 的收尾：这时候所有存量消息都已经有 conversation_id 了（V20回填 + 应用代码此后只按
-- 会话写消息），把列收紧成 NOT NULL + 外键（ON DELETE CASCADE，删除会话时消息由数据库级联
-- 删除），并删掉不再使用的 owner_id / note_id 列（note_id 是V10加的，随"按笔记过滤历史"
-- 这个机制一起下线，见 docs/superpowers/specs/2026-09-30-multi-conversation-chat-design.md）。
ALTER TABLE ai_chat_messages MODIFY COLUMN conversation_id BIGINT NOT NULL;

SET @fk_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages'
       AND CONSTRAINT_NAME = 'fk_ai_chat_messages_conversation') = 0,
    'ALTER TABLE ai_chat_messages ADD CONSTRAINT fk_ai_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_chat_conversations (id) ON DELETE CASCADE',
    'SELECT 1'
  )
);
PREPARE fk_stmt FROM @fk_stmt;
EXECUTE fk_stmt;
DEALLOCATE PREPARE fk_stmt;

SET @drop_owner_fk := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages'
       AND CONSTRAINT_NAME = 'fk_ai_chat_messages_owner') = 0,
    'SELECT 1',
    'ALTER TABLE ai_chat_messages DROP FOREIGN KEY fk_ai_chat_messages_owner'
  )
);
PREPARE drop_owner_fk FROM @drop_owner_fk;
EXECUTE drop_owner_fk;
DEALLOCATE PREPARE drop_owner_fk;

SET @drop_owner_idx := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages'
       AND INDEX_NAME = 'idx_ai_chat_messages_owner') = 0,
    'SELECT 1',
    'ALTER TABLE ai_chat_messages DROP INDEX idx_ai_chat_messages_owner'
  )
);
PREPARE drop_owner_idx FROM @drop_owner_idx;
EXECUTE drop_owner_idx;
DEALLOCATE PREPARE drop_owner_idx;

SET @drop_owner_col := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages' AND COLUMN_NAME = 'owner_id') = 0,
    'SELECT 1',
    'ALTER TABLE ai_chat_messages DROP COLUMN owner_id'
  )
);
PREPARE drop_owner_col FROM @drop_owner_col;
EXECUTE drop_owner_col;
DEALLOCATE PREPARE drop_owner_col;

SET @drop_note_col := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages' AND COLUMN_NAME = 'note_id') = 0,
    'SELECT 1',
    'ALTER TABLE ai_chat_messages DROP COLUMN note_id'
  )
);
PREPARE drop_note_col FROM @drop_note_col;
EXECUTE drop_note_col;
DEALLOCATE PREPARE drop_note_col;
