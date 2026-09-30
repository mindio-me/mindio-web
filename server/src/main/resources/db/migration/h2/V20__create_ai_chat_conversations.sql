-- spring-boot/src/main/resources/db/migration/h2/V20__create_ai_chat_conversations.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
CREATE TABLE IF NOT EXISTS ai_chat_conversations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  title TEXT,
  created_at DATETIME(6) NOT NULL,
  last_message_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE ai_chat_conversations ADD CONSTRAINT IF NOT EXISTS fk_ai_chat_conversations_owner
  FOREIGN KEY (owner_id) REFERENCES users (id);
ALTER TABLE ai_chat_messages ADD COLUMN IF NOT EXISTS conversation_id BIGINT;

INSERT INTO ai_chat_conversations (owner_id, title, created_at, last_message_at)
SELECT owner_id, NULL, MIN(created_at), MAX(created_at)
FROM ai_chat_messages
WHERE owner_id NOT IN (SELECT owner_id FROM ai_chat_conversations)
GROUP BY owner_id;

UPDATE ai_chat_messages m
SET conversation_id = (SELECT c.id FROM ai_chat_conversations c WHERE c.owner_id = m.owner_id)
WHERE m.conversation_id IS NULL;
