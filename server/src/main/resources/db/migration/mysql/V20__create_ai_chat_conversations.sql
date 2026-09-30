-- spring-boot/src/main/resources/db/migration/mysql/V20__create_ai_chat_conversations.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 全局AI助手多会话第一步：新建 ai_chat_conversations 表 + 给 ai_chat_messages 加可空的
-- conversation_id 列，并把存量消息回填成"每个有历史的owner一条会话"。详见
-- docs/superpowers/specs/2026-09-30-multi-conversation-chat-design.md。
-- 这一步只做加法（不删 owner_id/note_id、不加NOT NULL/外键约束），破坏性的收尾在V21。
CREATE TABLE IF NOT EXISTS ai_chat_conversations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  title TEXT,
  created_at DATETIME(6) NOT NULL,
  last_message_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_chat_conversations_owner (owner_id),
  CONSTRAINT fk_ai_chat_conversations_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;

SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages' AND COLUMN_NAME = 'conversation_id') = 0,
    'ALTER TABLE ai_chat_messages ADD COLUMN conversation_id BIGINT NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;

-- 回填：每个在 ai_chat_messages 里有历史记录的 owner_id 建一条会话（title留空，列表接口
-- 用第一条消息截断展示兜底，不在这里猜标题），会话的 created_at/last_message_at 分别取
-- 该owner名下消息的最早/最晚 created_at。
INSERT INTO ai_chat_conversations (owner_id, title, created_at, last_message_at)
SELECT owner_id, NULL, MIN(created_at), MAX(created_at)
FROM ai_chat_messages
WHERE owner_id NOT IN (SELECT owner_id FROM ai_chat_conversations)
GROUP BY owner_id;

UPDATE ai_chat_messages m
JOIN ai_chat_conversations c ON c.owner_id = m.owner_id
SET m.conversation_id = c.id
WHERE m.conversation_id IS NULL;
